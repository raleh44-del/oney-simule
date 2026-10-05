package com.podometre.simule

import android.app.AlertDialog
import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.SystemClock
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.PermissionController
import androidx.health.connect.client.records.StepsRecord
import androidx.health.connect.client.records.metadata.DataOrigin
import androidx.health.connect.client.request.AggregateRequest
import androidx.health.connect.client.request.ReadRecordsRequest
import androidx.health.connect.client.time.TimeRangeFilter
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.NumberFormat
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.Locale
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.roundToLong
import kotlin.random.Random

class MainActivity : ComponentActivity() {

    private class Plan(val steps: Long, val minutes: Long, val endAgo: Long)

    private val askPermissions =
        registerForActivityResult(PermissionController.createRequestPermissionResultContract()) { granted ->
            // Apres deux refus, Android n'affiche plus la demande : il faut passer par les reglages
            if (!granted.containsAll(STEPS_PERMISSIONS)) {
                toast("Autorisation refusée. Active-la dans Santé Connect › Autorisations des applis › Podomètre.")
            }
            refresh()
            runDiagnostic(force = true)
        }

    private var client: HealthConnectClient? = null
    private var liveJob: Job? = null

    private val numbers = NumberFormat.getIntegerInstance(Locale.FRANCE)
    private val clock = DateTimeFormatter.ofPattern("HH:mm")

    private lateinit var status: TextView
    private lateinit var setup: Button
    private lateinit var ready: View
    private lateinit var total: TextView
    private lateinit var mine: TextView
    private lateinit var steps: EditText
    private lateinit var duration: EditText
    private lateinit var endAgo: EditText
    private lateinit var preview: TextView
    private lateinit var add: Button
    private lateinit var live: TextView
    private lateinit var liveToggle: Button
    private lateinit var diagVerdict: TextView
    private lateinit var diagList: LinearLayout
    private var diagJob: Job? = null
    private var lastDiag = 0L
    private lateinit var openTreely: Button
    private lateinit var openFit: Button
    private lateinit var appAccess: Button


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        status = findViewById(R.id.status)
        setup = findViewById(R.id.setup)
        ready = findViewById(R.id.ready)
        total = findViewById(R.id.total)
        mine = findViewById(R.id.mine)
        steps = findViewById(R.id.steps)
        duration = findViewById(R.id.duration)
        endAgo = findViewById(R.id.endAgo)
        preview = findViewById(R.id.preview)
        add = findViewById(R.id.add)
        live = findViewById(R.id.live)
        liveToggle = findViewById(R.id.liveToggle)
        diagVerdict = findViewById(R.id.diagVerdict)
        diagList = findViewById(R.id.diagList)
        openTreely = findViewById(R.id.openTreely)
        openFit = findViewById(R.id.openFit)
        appAccess = findViewById(R.id.treelyAccess)

        findViewById<Button>(R.id.quick2k).setOnClickListener { steps.setText("2000") }
        findViewById<Button>(R.id.quick5k).setOnClickListener { steps.setText("5000") }
        findViewById<Button>(R.id.quick10k).setOnClickListener { steps.setText("10000") }

        val watcher = object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) = updatePreview()
        }
        steps.addTextChangedListener(watcher)
        duration.addTextChangedListener(watcher)
        endAgo.addTextChangedListener(watcher)

        add.setOnClickListener { addWalk() }
        findViewById<Button>(R.id.diagRun).setOnClickListener { runDiagnostic(force = true) }
        liveToggle.setOnClickListener { if (liveJob == null) startLive() else stopLive() }
        findViewById<Button>(R.id.openHc).setOnClickListener { openHealthConnect() }
        appAccess.setOnClickListener { openAccess(FIT_PACKAGE) }
        openTreely.setOnClickListener { openApp(TREELY_PACKAGE) }
        openFit.setOnClickListener { openApp(FIT_PACKAGE) }
        findViewById<Button>(R.id.clear).setOnClickListener { confirmClear() }

        updatePreview()
    }

    override fun onResume() {
        super.onResume()
        refresh()
        runDiagnostic()
    }

    // ---- Diagnostic automatique ----

    private fun runDiagnostic(force: Boolean = false) {
        if (diagJob?.isActive == true) return
        val now = SystemClock.elapsedRealtime()
        if (!force && lastDiag != 0L && now - lastDiag < DIAG_PAUSE_MS) return
        lastDiag = now
        diagVerdict.text = "Test en cours…"
        diagVerdict.setTextColor(getColor(R.color.muted))
        diagList.removeAllViews()
        diagJob = lifecycleScope.launch {
            val result = Diagnostic(this@MainActivity).run()
            val pad = (6 * resources.displayMetrics.density).toInt()
            for (check in result.checks) {
                val color = when (check.status) {
                    Diagnostic.Status.OK -> R.color.accent
                    Diagnostic.Status.FAIL -> R.color.error
                    Diagnostic.Status.UNKNOWN -> R.color.warn
                    Diagnostic.Status.INFO -> R.color.muted
                }
                diagList.addView(TextView(this@MainActivity).apply {
                    text = "${check.status.symbol}  ${check.text}"
                    textSize = 14f
                    setTextColor(getColor(color))
                    setPadding(0, pad, 0, 0)
                })
            }
            diagVerdict.text = result.verdict
            diagVerdict.setTextColor(getColor(if (result.working) R.color.accent else R.color.error))
            client?.let { refreshTotals(it) }
        }
    }

    // ---- Etat de Health Connect ----

    private fun refresh() {
        lifecycleScope.launch {
            when (HealthConnectClient.getSdkStatus(this@MainActivity)) {
                HealthConnectClient.SDK_UNAVAILABLE -> {
                    show(false, "Santé Connect n'est pas disponible sur ce téléphone (Android 9 minimum).", null)
                    return@launch
                }
                HealthConnectClient.SDK_UNAVAILABLE_PROVIDER_UPDATE_REQUIRED -> {
                    show(false, "Installe ou mets à jour Santé Connect pour continuer.", "Installer Santé Connect") {
                        openStore()
                    }
                    return@launch
                }
            }
            val hc = client ?: HealthConnectClient.getOrCreate(this@MainActivity).also { client = it }
            val granted = try {
                hc.permissionController.getGrantedPermissions()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                emptySet<String>()
            }
            if (!granted.containsAll(STEPS_PERMISSIONS)) {
                show(false, "Autorise Podomètre à lire et écrire les pas dans Santé Connect.", "Autoriser") {
                    askPermissions.launch(STEPS_PERMISSIONS)
                }
                return@launch
            }
            show(true, "Connecté à Santé Connect.", null)
            refreshTreely()
            refreshTotals(hc)
        }
    }

    private fun show(isReady: Boolean, message: String, action: String?, onAction: (() -> Unit)? = null) {
        status.text = message
        ready.visibility = if (isReady) View.VISIBLE else View.GONE
        setup.visibility = if (action != null) View.VISIBLE else View.GONE
        setup.text = action
        setup.setOnClickListener { onAction?.invoke() }
    }

    private suspend fun refreshTotals(hc: HealthConnectClient) {
        try {
            val all = stepsToday(hc)
            val ours = stepsToday(hc, setOf(DataOrigin(packageName)))
            total.text = fmt(all)
            mine.text = "dont ${fmt(ours)} ajoutés par Podomètre"
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            mine.text = "Lecture du total impossible : ${e.message}"
        }
    }

    /** Total du jour tel que Sante Connect le donne aux autres applis (priorite des sources appliquee). */
    private suspend fun stepsToday(hc: HealthConnectClient, origins: Set<DataOrigin> = emptySet()): Long =
        hc.aggregate(
            AggregateRequest(
                metrics = setOf(StepsRecord.COUNT_TOTAL),
                timeRangeFilter = TimeRangeFilter.between(startOfToday(), Instant.now()),
                dataOriginFilter = origins,
            )
        )[StepsRecord.COUNT_TOTAL] ?: 0L

    // ---- Ajout d'une marche en une fois ----

    /** Lit le formulaire : le plan s'il est valide, et le texte a afficher dessous. */
    private fun readPlan(): Pair<Plan?, String> {
        val n = number(steps)
        val ago = number(endAgo) ?: 0L
        if (n == null || n < 1) return null to "Indique un nombre de pas."
        if (n > MAX_STEPS) return null to "Maximum ${fmt(MAX_STEPS)} pas d'un coup."
        val minutes = number(duration) ?: ceil(n / AUTO_CADENCE).toLong()
        if (minutes < 1) return null to "La durée doit faire au moins 1 minute."
        if (minutes > MAX_MINUTES) return null to "Durée maximum : ${MAX_MINUTES / 60} h."
        if (ago > MAX_AGO) return null to "La marche doit dater d'aujourd'hui."
        val cadence = (n.toDouble() / minutes).roundToLong()
        if (cadence > MAX_CADENCE) {
            val needed = ceil(n / MAX_CADENCE.toDouble()).toLong()
            return null to "Trop rapide ($cadence pas/min) : mets au moins $needed minutes."
        }
        val pace = when {
            cadence < 70 -> "balade tranquille"
            cadence <= 125 -> "marche normale"
            else -> "marche rapide"
        }
        return Plan(n, minutes, ago) to "≈ $cadence pas/min ($pace) pendant ${hm(minutes)}"
    }

    private fun updatePreview() {
        val (plan, message) = readPlan()
        preview.text = message
        preview.setTextColor(getColor(if (plan == null) R.color.error else R.color.muted))
        add.isEnabled = plan != null && liveJob == null
    }

    private fun addWalk() {
        val hc = client ?: return
        val plan = readPlan().first ?: return
        add.isEnabled = false
        lifecycleScope.launch {
            try {
                val dayStart = startOfToday()
                val length = Duration.ofMinutes(plan.minutes)
                var end = Instant.now().truncatedTo(ChronoUnit.SECONDS).minus(Duration.ofMinutes(plan.endAgo))

                // Recule la marche tant qu'elle chevauche des pas deja ajoutes par l'appli,
                // sinon Health Connect ne compterait pas les deux.
                val own = ownRecords(hc, dayStart.minus(Duration.ofDays(1)), Instant.now())
                while (true) {
                    val start = end.minus(length)
                    val clash = own.filter { it.startTime < end && it.endTime > start }
                    if (clash.isEmpty()) break
                    end = clash.minOf { it.startTime }
                }
                val start = end.minus(length)
                if (start < dayStart) {
                    toast("Plus de place libre aujourd'hui pour cette durée : raccourcis-la ou efface les pas ajoutés.")
                    return@launch
                }

                val before = stepsToday(hc)
                split(start, plan.minutes.toInt(), plan.steps)
                    .chunked(INSERT_BATCH)
                    .forEach { hc.insertRecords(it) }
                val counted = stepsToday(hc) - before
                refreshTotals(hc)

                val zone = ZoneId.systemDefault()
                val message = StringBuilder()
                    .append("${fmt(plan.steps)} pas écrits de ${clock.format(start.atZone(zone))} à ${clock.format(end.atZone(zone))}.\n")
                    .append("Total du jour dans Santé Connect : +${fmt(counted)} pas.")
                if (counted < plan.steps * 9 / 10) {
                    message.append(
                        "\n\nAttention : sur ces heures, Santé Connect garde les pas d'une autre source " +
                            "(le téléphone ou Google Fit). Mets Podomètre en premier dans la priorité des sources " +
                            "(bouton dans la carte Treely), ou mets la marche à une heure où tu n'as pas bougé " +
                            "(champ « terminée il y a »)."
                    )
                }
                val treely = appSteps(packageManager, TREELY_PACKAGE)
                val dialog = AlertDialog.Builder(this@MainActivity)
                    .setTitle("Pas ajoutés")
                    .setNegativeButton("OK", null)
                if (treely.installed) {
                    // Treely compte les pas de Google Fit : Google Fit doit d'abord les recuperer
                    message.append("\n\nOuvre d'abord Google Fit pour qu'il récupère les pas, puis Treely.")
                    dialog.setPositiveButton("Google Fit") { _, _ -> openApp(FIT_PACKAGE) }
                    dialog.setNeutralButton("Treely") { _, _ -> openApp(TREELY_PACKAGE) }
                }
                dialog.setMessage(message).show()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                toast("Échec de l'ajout : ${e.message}")
            } finally {
                updatePreview()
            }
        }
    }

    /** Une mesure par minute, avec une cadence qui varie un peu comme une vraie marche. */
    private fun split(start: Instant, minutes: Int, total: Long): List<StepsRecord> {
        val weights = DoubleArray(minutes) { Random.nextDouble(0.85, 1.15) }
        val sum = weights.sum()
        val counts = LongArray(minutes) { floor(total * weights[it] / sum).toLong() }
        var rest = total - counts.sum()
        var i = 0
        while (rest > 0) {
            counts[i % minutes]++
            rest--
            i++
        }
        return counts.indices.filter { counts[it] > 0 }.map {
            val from = start.plus(Duration.ofMinutes(it.toLong()))
            stepsRecord(from, from.plus(Duration.ofMinutes(1)), counts[it])
        }
    }

    private suspend fun ownRecords(hc: HealthConnectClient, from: Instant, to: Instant): List<StepsRecord> {
        val out = mutableListOf<StepsRecord>()
        var token: String? = null
        do {
            val page = hc.readRecords(
                ReadRecordsRequest(
                    recordType = StepsRecord::class,
                    timeRangeFilter = TimeRangeFilter.between(from, to),
                    dataOriginFilter = setOf(DataOrigin(packageName)),
                    pageToken = token,
                )
            )
            out += page.records
            token = page.pageToken
        } while (!token.isNullOrEmpty())
        return out
    }

    // ---- Marche en direct ----

    private fun startLive() {
        val hc = client ?: return
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        liveToggle.text = "Arrêter la marche"
        live.visibility = View.VISIBLE
        live.text = "En marche…"
        add.isEnabled = false

        liveJob = lifecycleScope.launch {
            var walked = 0L
            var pending = 0.0
            var since = Instant.now()
            var cadence = LIVE_CADENCE
            try {
                while (isActive) {
                    delay(1000)
                    if (Random.nextInt(15) == 0) cadence = LIVE_CADENCE * Random.nextDouble(0.9, 1.1)
                    pending += cadence / 60.0
                    live.text = "En marche : ${fmt(walked + pending.toLong())} pas"

                    val now = Instant.now()
                    if (Duration.between(since, now).seconds >= LIVE_FLUSH_SECONDS) {
                        val n = pending.toLong()
                        if (n > 0) {
                            hc.insertRecords(listOf(stepsRecord(since, now, n)))
                            walked += n
                            pending -= n
                        }
                        since = now
                        refreshTotals(hc)
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                toast("Marche interrompue : ${e.message}")
                stopLive()
            } finally {
                // Ecrit les derniers pas avant l'arret
                withContext(NonCancellable) {
                    val n = pending.toLong()
                    val now = Instant.now()
                    if (n > 0 && since < now) {
                        runCatching { hc.insertRecords(listOf(stepsRecord(since, now, n))) }
                            .onSuccess { walked += n }
                    }
                    live.text = "Marche terminée : ${fmt(walked)} pas ajoutés"
                    refreshTotals(hc)
                }
            }
        }
    }

    private fun stopLive() {
        liveJob?.cancel()
        liveJob = null
        window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        liveToggle.text = "Démarrer la marche"
        updatePreview()
    }

    // ---- Reglages ----

    private fun confirmClear() {
        val hc = client ?: return
        AlertDialog.Builder(this)
            .setTitle("Effacer les pas ajoutés ?")
            .setMessage("Supprime tous les pas ajoutés aujourd'hui par Podomètre. Les vrais pas du téléphone ne sont pas touchés.")
            .setNegativeButton("Annuler", null)
            .setPositiveButton("Effacer") { _, _ ->
                lifecycleScope.launch {
                    try {
                        // Health Connect ne laisse une appli effacer que ses propres donnees
                        hc.deleteRecords(StepsRecord::class, TimeRangeFilter.between(startOfToday(), Instant.now()))
                        toast("Pas ajoutés aujourd'hui effacés.")
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        toast("Échec de l'effacement : ${e.message}")
                    }
                    refreshTotals(hc)
                }
            }
            .show()
    }

    private fun openHealthConnect() {
        try {
            startActivity(Intent(HealthConnectClient.ACTION_HEALTH_CONNECT_SETTINGS))
        } catch (e: ActivityNotFoundException) {
            toast("Santé Connect introuvable sur ce téléphone.")
        }
    }

    private fun refreshTreely() {
        val treely = appSteps(packageManager, TREELY_PACKAGE)
        val fit = appSteps(packageManager, FIT_PACKAGE)
        openTreely.text = if (treely.installed) "Ouvrir Treely" else "Installer Treely"
        openFit.text = if (fit.installed) "Ouvrir Google Fit" else "Installer Google Fit"
    }

    private fun appName(pkg: String) = if (pkg == FIT_PACKAGE) "Google Fit" else "Treely"

    /** Lance l'appli, ou sa page Play Store si elle n'est pas installee. */
    private fun openApp(pkg: String) {
        val launch = packageManager.getLaunchIntentForPackage(pkg)
        if (launch != null) startActivity(launch) else openStore(pkg)
    }

    /** Ecran des autorisations Sante Connect d'une appli, ou l'accueil de Sante Connect a defaut. */
    private fun openAccess(pkg: String) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            try {
                startActivity(
                    Intent("android.health.connect.action.MANAGE_HEALTH_PERMISSIONS")
                        .putExtra(Intent.EXTRA_PACKAGE_NAME, pkg)
                )
                return
            } catch (e: ActivityNotFoundException) {
                // ecran indisponible : on retombe sur l'accueil
            } catch (e: SecurityException) {
                // idem
            }
        }
        toast("Dans Santé Connect, ouvre Autorisations des applis › ${appName(pkg)} et autorise Pas.")
        openHealthConnect()
    }

    private fun openStore(pkg: String = HC_PACKAGE) {
        val extra = if (pkg == HC_PACKAGE) "&url=healthconnect%3A%2F%2Fonboarding" else ""
        try {
            startActivity(
                Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=$pkg$extra"))
                    .setPackage("com.android.vending")
            )
        } catch (e: ActivityNotFoundException) {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://play.google.com/store/apps/details?id=$pkg")))
        }
    }

    // ---- Outils ----

    private fun number(field: EditText): Long? = field.text.toString().trim().toLongOrNull()

    private fun fmt(n: Long): String = numbers.format(n)

    private fun hm(minutes: Long): String =
        if (minutes < 60) "$minutes min" else "${minutes / 60} h ${"%02d".format(minutes % 60)}"

    private fun toast(message: String) = Toast.makeText(this, message, Toast.LENGTH_LONG).show()

    private companion object {
        const val MAX_STEPS = 60_000L
        const val MAX_MINUTES = 720L
        const val MAX_AGO = 1440L
        const val AUTO_CADENCE = 105.0
        const val MAX_CADENCE = 180L
        const val LIVE_CADENCE = 110.0
        const val LIVE_FLUSH_SECONDS = 30L
        const val INSERT_BATCH = 200
        const val DIAG_PAUSE_MS = 15_000L
    }
}

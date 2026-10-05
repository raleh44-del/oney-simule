package com.podometre.simule

import android.content.Context
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.os.Build
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.StepsRecord
import androidx.health.connect.client.records.metadata.Device
import androidx.health.connect.client.records.metadata.Metadata
import androidx.health.connect.client.request.AggregateRequest
import androidx.health.connect.client.request.ReadRecordsRequest
import androidx.health.connect.client.time.TimeRangeFilter
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import java.text.NumberFormat
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.Locale

internal const val HC_PACKAGE = "com.google.android.apps.healthdata"
internal const val TREELY_PACKAGE = "com.treely.android"
internal const val FIT_PACKAGE = "com.google.android.apps.fitness"
internal const val READ_STEPS = "android.permission.health.READ_STEPS"

internal val STEPS_PERMISSIONS = setOf(
    HealthPermission.getWritePermission(StepsRecord::class),
    HealthPermission.getReadPermission(StepsRecord::class),
)

/** Ce qu'on peut savoir d'une autre appli : installee, et si elle lit les pas de Sante Connect. */
internal class AppSteps(val installed: Boolean, val version: String?, val readsHealthConnect: Boolean, val allowed: Boolean?)

/**
 * Lit les autorisations declarees par une autre appli. Depuis Android 14, Android sait aussi
 * si l'autorisation de lire les pas lui a ete accordee ; avant, c'est Sante Connect qui le garde.
 */
internal fun appSteps(pm: PackageManager, pkg: String): AppSteps {
    val info: PackageInfo = try {
        @Suppress("DEPRECATION")
        pm.getPackageInfo(pkg, PackageManager.GET_PERMISSIONS)
    } catch (e: PackageManager.NameNotFoundException) {
        return AppSteps(installed = false, version = null, readsHealthConnect = false, allowed = null)
    }
    val index = info.requestedPermissions?.indexOf(READ_STEPS) ?: -1
    val allowed = if (index >= 0 && Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
        val flags = info.requestedPermissionsFlags?.getOrNull(index) ?: 0
        (flags and PackageInfo.REQUESTED_PERMISSION_GRANTED) != 0
    } else {
        null
    }
    return AppSteps(installed = true, version = info.versionName, readsHealthConnect = index >= 0, allowed = allowed)
}

internal fun startOfToday(): Instant {
    val zone = ZoneId.systemDefault()
    return LocalDate.now(zone).atStartOfDay(zone).toInstant()
}

/** Pas declares comme enregistres automatiquement par ce telephone. */
internal fun stepsRecord(from: Instant, to: Instant, count: Long): StepsRecord {
    val rules = ZoneId.systemDefault().rules
    return StepsRecord(
        startTime = from,
        startZoneOffset = rules.getOffset(from),
        endTime = to,
        endZoneOffset = rules.getOffset(to),
        count = count,
        metadata = Metadata.autoRecorded(
            Device(type = Device.TYPE_PHONE, manufacturer = Build.MANUFACTURER, model = Build.MODEL)
        ),
    )
}

/**
 * Teste toute la chaine Podometre → Sante Connect → (Google Fit) → Treely, autant qu'on peut
 * la voir depuis le telephone, et dit ou ca bloque.
 */
internal class Diagnostic(private val context: Context) {

    enum class Status(val symbol: String) { OK("✓"), FAIL("✗"), UNKNOWN("?"), INFO("•") }

    class Check(val status: Status, val text: String)

    val checks = mutableListOf<Check>()
    var verdict = ""
        private set
    var working = false
        private set

    private val numbers = NumberFormat.getIntegerInstance(Locale.FRANCE)
    private val clock = DateTimeFormatter.ofPattern("HH:mm").withZone(ZoneId.systemDefault())
    private var viaFit = false
    private var fitHasOurs = false

    private fun ok(text: String) { checks += Check(Status.OK, text) }
    private fun fail(text: String) { checks += Check(Status.FAIL, text) }
    private fun unknown(text: String) { checks += Check(Status.UNKNOWN, text) }
    private fun info(text: String) { checks += Check(Status.INFO, text) }

    suspend fun run(): Diagnostic {
        try {
            checkPhone()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            fail("Erreur pendant le test : ${e.message}")
        }
        working = checks.none { it.status == Status.FAIL }
        verdict = when {
            !working -> "✗ Ça ne peut pas marcher pour l'instant : corrige les points en rouge."
            fitHasOurs -> "✓ Google Fit a bien les pas de Podomètre. Ouvre Treely et actualise : il les prend dans Google Fit."
            viaFit -> "✓ Tout est bon sur le téléphone. Reste à voir si Google Fit récupère les pas (dernier point)."
            else -> "✓ Tout est bon : les pas ajoutés devraient arriver dans Treely à sa prochaine synchro."
        }
        return this
    }

    private suspend fun checkPhone() {
        when (HealthConnectClient.getSdkStatus(context)) {
            HealthConnectClient.SDK_AVAILABLE -> ok("Santé Connect est disponible.")
            HealthConnectClient.SDK_UNAVAILABLE_PROVIDER_UPDATE_REQUIRED -> {
                fail("Santé Connect est à installer ou à mettre à jour (bouton en haut).")
                checkTreely(null)
                return
            }
            else -> {
                fail("Santé Connect n'existe pas sur ce téléphone (Android 9 minimum).")
                checkTreely(null)
                return
            }
        }

        val hc = HealthConnectClient.getOrCreate(context)
        if (!hc.permissionController.getGrantedPermissions().containsAll(STEPS_PERMISSIONS)) {
            fail("Podomètre n'a pas le droit de lire et écrire les pas : appuie sur « Autoriser » en haut.")
            checkTreely(null)
            return
        }
        ok("Podomètre a le droit de lire et écrire les pas.")

        val today = readToday(hc)
        listSources(today)
        testWrite(hc, today)
        checkTreely(today)
    }

    /** Ecrit quelques pas de test, regarde s'ils comptent dans le total, puis les efface. */
    private suspend fun testWrite(hc: HealthConnectClient, today: List<StepsRecord>) {
        val own = today.filter { it.metadata.dataOrigin.packageName == context.packageName }
        fun free(from: Instant, to: Instant) = own.none { it.startTime < to && it.endTime > from }

        // De preference sur une minute deja comptee par une autre source, pour tester la priorite
        val rival = today
            .filter { it.metadata.dataOrigin.packageName != context.packageName && free(it.startTime, it.endTime) }
            .maxByOrNull { it.endTime }
        var from: Instant
        var to: Instant
        if (rival != null) {
            from = rival.startTime
            to = minOf(rival.endTime, rival.startTime.plusSeconds(60))
        } else {
            to = Instant.now().truncatedTo(ChronoUnit.SECONDS)
            from = to.minusSeconds(60)
            while (!free(from, to)) {
                to = own.filter { it.startTime < to && it.endTime > from }.minOf { it.startTime }
                from = to.minusSeconds(60)
            }
            if (from < startOfToday()) {
                unknown("Pas de minute libre aujourd'hui pour le test d'écriture.")
                return
            }
        }

        val before = total(hc)
        val ids: List<String> = try {
            hc.insertRecords(listOf(stepsRecord(from, to, TEST_STEPS))).recordIdsList
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            fail("Podomètre n'arrive pas à écrire des pas : ${e.message}")
            return
        }
        try {
            val counted = total(hc) - before
            ok("Podomètre arrive à écrire des pas dans Santé Connect.")
            val rivalName = rival?.let { label(it.metadata.dataOrigin.packageName) }
            when {
                counted >= TEST_STEPS / 2 && rivalName != null ->
                    ok("Ses pas comptent dans le total, même aux heures où $rivalName a des pas (priorité OK).")
                counted >= TEST_STEPS / 2 ->
                    ok("Ses pas comptent dans le total de Santé Connect.")
                else ->
                    fail(
                        "Ses pas ne comptent pas : Santé Connect donne la priorité à ${rivalName ?: "une autre source"}. " +
                            "Bouton « Priorité des sources » : mets Podomètre en premier."
                    )
            }
        } finally {
            withContext(NonCancellable) {
                runCatching {
                    hc.deleteRecords(StepsRecord::class, recordIdsList = ids, clientRecordIdsList = emptyList())
                }
            }
        }
    }

    private fun listSources(today: List<StepsRecord>) {
        val bySource = today
            .groupBy { it.metadata.dataOrigin.packageName }
            .mapValues { (_, records) -> records.sumOf { it.count } }
            .entries.sortedByDescending { it.value }
        if (bySource.isEmpty()) {
            info("Aucun pas enregistré aujourd'hui dans Santé Connect.")
        } else {
            info("Pas reçus aujourd'hui : " + bySource.joinToString(" · ") { "${label(it.key)} ${numbers.format(it.value)}" })
        }
    }

    private fun checkTreely(today: List<StepsRecord>?) {
        val pm = context.packageManager
        val treely = appSteps(pm, TREELY_PACKAGE)
        if (!treely.installed) {
            fail("Treely n'est pas installé sur ce téléphone.")
            return
        }
        ok("Treely est installé" + (treely.version?.let { " (version $it)." } ?: "."))
        if (treely.readsHealthConnect && treely.allowed == true) {
            info("Treely a aussi accès à Santé Connect, mais d'après son aide, sur Android il compte les pas de Google Fit.")
        }

        // D'apres la FAQ de Treely : « Si tes pas ne sont pas comptes, c'est probablement qu'il te manque Google Fit »
        viaFit = true
        info("Chemin des pas : Podomètre → Santé Connect → Google Fit → Treely.")
        val fit = appSteps(pm, FIT_PACKAGE)
        if (!fit.installed) {
            fail(
                "Google Fit n'est pas installé : bouton « Installer Google Fit », ouvre-le, connecte le même " +
                    "compte Google que dans Treely, puis relance Treely."
            )
            return
        }
        ok("Google Fit est installé.")
        if (!fit.readsHealthConnect) {
            fail("Cette version de Google Fit ne sait pas lire Santé Connect : mets-la à jour dans le Play Store.")
            return
        }
        when (fit.allowed) {
            true -> ok("Google Fit a le droit de lire les pas de Santé Connect, donc ceux de Podomètre.")
            false -> fail(
                "Google Fit ne lit pas Santé Connect : Google Fit › Profil › ⚙ Paramètres › " +
                    "« Synchroniser Fit avec Santé Connect » → activer, puis tout autoriser."
            )
            null -> unknown("Vérifie dans Google Fit › Profil › ⚙ Paramètres que « Synchroniser Fit avec Santé Connect » est activé.")
        }
        if (today == null) return
        val fitRecords = today.filter { it.metadata.dataOrigin.packageName == FIT_PACKAGE }
        val last = fitRecords.maxOfOrNull { it.endTime }
        if (last == null) {
            unknown("Google Fit n'a encore rien envoyé à Santé Connect aujourd'hui : ouvre Google Fit pour lancer la synchro.")
            return
        }
        ok("Google Fit échange bien avec Santé Connect (dernières données à ${clock.format(last)}).")

        // Google Fit renvoie dans Sante Connect son propre total, qui inclut ce qu'il a recupere :
        // s'il depasse nettement les vrais pas, c'est qu'il a pris ceux de Podometre.
        val bySource = today.groupBy { it.metadata.dataOrigin.packageName }.mapValues { (_, r) -> r.sumOf { it.count } }
        val ours = bySource[context.packageName] ?: 0L
        val fitSteps = bySource[FIT_PACKAGE] ?: 0L
        val real = bySource.filterKeys { it != context.packageName && it != FIT_PACKAGE }.values.maxOrNull() ?: 0L
        if (ours == 0L) {
            info("Ajoute des pas pour vérifier que Google Fit les récupère.")
        } else if (fitSteps >= (ours + real) * 9 / 10) {
            fitHasOurs = true
            ok("Google Fit a récupéré les pas de Podomètre : il compte ${numbers.format(fitSteps)} pas aujourd'hui.")
        } else {
            unknown(
                "Google Fit ne compte que ${numbers.format(fitSteps)} pas, sans ceux de Podomètre (${numbers.format(ours)}). " +
                    "Ouvre Google Fit pour qu'il les récupère, puis relance le test."
            )
            return
        }
        unknown(
            "Dernier point, impossible à voir d'ici : Treely lit Google Fit par internet. Ouvre Treely et " +
                "actualise ; si rien ne bouge, redémarre le téléphone (conseil de l'aide Treely)."
        )
    }

    private suspend fun readToday(hc: HealthConnectClient): List<StepsRecord> {
        val out = mutableListOf<StepsRecord>()
        var token: String? = null
        do {
            val page = hc.readRecords(
                ReadRecordsRequest(
                    recordType = StepsRecord::class,
                    timeRangeFilter = TimeRangeFilter.between(startOfToday(), Instant.now()),
                    pageToken = token,
                )
            )
            out += page.records
            token = page.pageToken
        } while (!token.isNullOrEmpty())
        return out
    }

    private suspend fun total(hc: HealthConnectClient): Long =
        hc.aggregate(
            AggregateRequest(
                metrics = setOf(StepsRecord.COUNT_TOTAL),
                timeRangeFilter = TimeRangeFilter.between(startOfToday(), Instant.now()),
            )
        )[StepsRecord.COUNT_TOTAL] ?: 0L

    private fun label(pkg: String): String = when {
        pkg == context.packageName -> "Podomètre"
        // Pas comptes par Sante Connect lui-meme (Android 16) : com.android.healthconnect.phone.<id>
        pkg.startsWith("com.android.healthconnect.phone") -> "le téléphone"
        else -> KNOWN_APPS[pkg] ?: pkg
    }

    private companion object {
        const val TEST_STEPS = 300L

        val KNOWN_APPS = mapOf(
            TREELY_PACKAGE to "Treely",
            FIT_PACKAGE to "Google Fit",
            HC_PACKAGE to "le téléphone",
            "com.android.healthconnect.controller" to "le téléphone",
            "android" to "le téléphone",
            "com.sec.android.app.shealth" to "Samsung Health",
            "com.huawei.health" to "Huawei Santé",
            "com.fitbit.FitbitMobile" to "Fitbit",
            "com.garmin.android.apps.connectmobile" to "Garmin Connect",
            "com.xiaomi.wearable" to "Mi Fitness",
            "com.mi.health" to "Mi Fitness",
        )
    }
}

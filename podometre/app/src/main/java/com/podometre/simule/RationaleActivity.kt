package com.podometre.simule

import android.app.Activity
import android.os.Bundle
import android.widget.TextView

/** Ecran exige par Health Connect pour expliquer l'usage des donnees. */
class RationaleActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val pad = (24 * resources.displayMetrics.density).toInt()
        setContentView(TextView(this).apply {
            text = getString(R.string.rationale)
            textSize = 17f
            setPadding(pad, pad, pad, pad)
        })
    }
}

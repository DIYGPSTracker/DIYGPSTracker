package dev.csaba.diygpstracker.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.text.method.LinkMovementMethod
import android.widget.Button
import android.widget.TextView
import dev.csaba.diygpstracker.R
import dev.csaba.diygpstracker.data.hasAuthConfiguration


class StagingActivity : AppCompatActivityWithActionBar() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_staging)

        if (hasAuthConfiguration()) {
            val intent = Intent(this, MainActivity::class.java)
            startActivity(intent)
        }

        val settingHelpText: TextView = findViewById(R.id.settingsHelp)
        settingHelpText.movementMethod = LinkMovementMethod.getInstance()

        val settingButton: Button = findViewById(R.id.settingsButton)
        settingButton.setOnClickListener {
            val intent = Intent(this, SettingsActivity::class.java)
            startActivity(intent)
        }

        val privacyButton: Button = findViewById(R.id.privacyButton)
        privacyButton.setOnClickListener {
            openBrowserWindow(applicationContext.getString(R.string.privacy_policy_url), this)
        }

        val helpButton: Button = findViewById(R.id.helpButton)
        helpButton.setOnClickListener {
            openBrowserWindow(applicationContext.getString(R.string.home_page_url), this)
        }
    }

    private fun openBrowserWindow(url: String?, context: Context) {
        val uris = Uri.parse(url)
        val intent = Intent(Intent.ACTION_VIEW, uris)
        val bundle = Bundle()
        bundle.putBoolean("new_window", true)
        intent.putExtras(bundle)
        context.startActivity(intent)
    }
}

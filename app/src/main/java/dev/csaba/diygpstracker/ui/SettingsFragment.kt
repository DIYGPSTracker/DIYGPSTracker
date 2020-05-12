package dev.csaba.diygpstracker.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import com.google.android.material.snackbar.Snackbar
import dev.csaba.diygpstracker.R
import dev.csaba.diygpstracker.data.hasAuthConfiguration


class SettingsFragment : PreferenceFragmentCompat() {
    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        setPreferencesFromResource(R.xml.preferences, rootKey)
    }

    fun openBrowserWindow(url: String?, context: Context?) {
        val uris = Uri.parse(url)
        val intent = Intent(Intent.ACTION_VIEW, uris)
        val bundle = Bundle()
        bundle.putBoolean("new_window", true)
        intent.putExtras(bundle)
        context?.startActivity(intent)
    }

    override fun onPreferenceTreeClick(preference: Preference): Boolean {
        if (preference.key == getString(R.string.settings_help_key)) {
            openBrowserWindow(getString(R.string.home_page_url), context)
            return true
        } else if (preference.key == getString(R.string.connect_key)) {
            if (!requireActivity().hasAuthConfiguration()) {
                Snackbar.make(
                    requireView(),
                    getString(R.string.uncofigured_firestore),
                    Snackbar.LENGTH_SHORT
                ).show()
            } else {
                val mainPage = Intent(context, MainActivity::class.java)
                startActivity(mainPage)
            }
        }
        return false
    }
}

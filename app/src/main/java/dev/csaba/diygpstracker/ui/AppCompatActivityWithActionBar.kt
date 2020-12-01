package dev.csaba.diygpstracker.ui

import android.content.Intent
import android.view.Menu
import android.view.MenuItem
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.snackbar.Snackbar
import dev.csaba.diygpstracker.R
import dev.csaba.diygpstracker.data.hasAuthConfiguration
import timber.log.Timber


abstract class AppCompatActivityWithActionBar : AppCompatActivity() {

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        // Inflate the menu; this adds items to the action bar if it is present.
        menuInflater.inflate(R.menu.menu, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {

        if (item.itemId == R.id.settings_menu_button) {
            val intent = Intent(this, SettingsActivity::class.java)
            startActivity(intent)
            return true
        } else if (item.itemId == R.id.assets_menu_button) {
            if (!this.hasAuthConfiguration()) {
                Snackbar.make(
                    window.decorView.rootView,
                    getString(R.string.uncofigured_firestore),
                    Snackbar.LENGTH_INDEFINITE
                ).setAction(R.string.acknowledge) {
                    Timber.d(getString(R.string.uncofigured_firestore))
                }.show()
            } else {
                val intent = Intent(this, MainActivity::class.java)
                startActivity(intent)
            }
            return true
        }

        return super.onOptionsItemSelected(item)
    }

}

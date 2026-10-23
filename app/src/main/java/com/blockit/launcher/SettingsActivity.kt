package com.blockit.launcher

import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.widget.Button
import android.widget.Switch
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat

class SettingsActivity : AppCompatActivity() {

    private lateinit var prefs: Prefs

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)

        prefs = Prefs(this)

        findViewById<android.view.View>(R.id.settingsBackBtn).setOnClickListener { finish() }

        // Status bar switch
        val statusBarSwitch = findViewById<Switch>(R.id.switchStatusBar)
        statusBarSwitch.isChecked = prefs.showStatusBar
        statusBarSwitch.setOnCheckedChangeListener { _, isChecked ->
            prefs.showStatusBar = isChecked
            val controller = WindowInsetsControllerCompat(window, window.decorView)
            if (isChecked) {
                controller.show(WindowInsetsCompat.Type.statusBars())
            } else {
                controller.hide(WindowInsetsCompat.Type.statusBars())
                controller.systemBarsBehavior =
                    WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            }
        }

        // Wallpaper
        findViewById<android.view.View>(R.id.wallpaperRow).setOnClickListener {
            try {
                startActivity(Intent(Intent.ACTION_SET_WALLPAPER))
            } catch (e: Exception) {
                Toast.makeText(this, "No wallpaper picker found", Toast.LENGTH_SHORT).show()
            }
        }

        // Set as default launcher
        findViewById<Button>(R.id.setDefaultLauncherBtn).setOnClickListener {
            try {
                startActivity(Intent(Settings.ACTION_HOME_SETTINGS))
            } catch (e: Exception) {
                startActivity(Intent(Settings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS))
            }
        }

        // Clear all widgets
        findViewById<android.view.View>(R.id.clearWidgetsRow).setOnClickListener {
            AlertDialog.Builder(this)
                .setTitle("Remove all widgets?")
                .setMessage("This will remove all widgets from your home screen.")
                .setPositiveButton("Remove") { _, _ ->
                    prefs.widgetIds = emptyList()
                    Toast.makeText(this, "Widgets cleared. Restart launcher to apply.", Toast.LENGTH_LONG).show()
                }
                .setNegativeButton("Cancel", null)
                .show()
        }
    }
}

package com.blockit.launcher

import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import android.os.Bundle
import android.view.WindowManager
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SearchView
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import kotlinx.coroutines.*

class AppDrawerActivity : AppCompatActivity() {

    private lateinit var allAppsGrid: RecyclerView
    private lateinit var adapter: AppAdapter
    private var allApps = listOf<AppInfo>()
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private var pickMode = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_app_drawer)

        pickMode = intent.getBooleanExtra("pick_mode", false)
        allAppsGrid = findViewById(R.id.allAppsGrid)

        adapter = AppAdapter(
            apps = emptyList(),
            onAppClick = { app ->
                if (pickMode) {
                    // Return selected app to MainActivity for dock
                    val result = Intent().apply { putExtra("picked_package", app.packageName) }
                    setResult(Activity.RESULT_OK, result)
                    finish()
                } else {
                    launchApp(app.packageName)
                }
            },
            onAppLongClick = { app ->
                if (!pickMode) showAppOptions(app)
            }
        )

        allAppsGrid.layoutManager = GridLayoutManager(this, 4)
        allAppsGrid.adapter = adapter

        val searchView = findViewById<SearchView>(R.id.appSearch)
        searchView.queryHint = if (pickMode) "Pick an app…" else "Search apps…"
        searchView.setOnQueryTextListener(object : SearchView.OnQueryTextListener {
            override fun onQueryTextSubmit(query: String?) = true
            override fun onQueryTextChange(newText: String?): Boolean {
                filterApps(newText.orEmpty())
                return true
            }
        })

        findViewById<android.view.View>(R.id.drawerBackBtn).setOnClickListener {
            setResult(Activity.RESULT_CANCELED)
            finish()
        }

        loadApps()
    }

    private fun loadApps() {
        scope.launch {
            val apps = withContext(Dispatchers.IO) { getAllApps() }
            allApps = apps
            adapter.updateApps(apps)
        }
    }

    private fun getAllApps(): List<AppInfo> {
        val pm = packageManager
        val intent = Intent(Intent.ACTION_MAIN, null).apply {
            addCategory(Intent.CATEGORY_LAUNCHER)
        }
        return pm.queryIntentActivities(intent, PackageManager.MATCH_ALL)
            .mapNotNull { ri ->
                try {
                    val pkg = ri.activityInfo.packageName
                    val name = ri.loadLabel(pm).toString()
                    val icon: Drawable = ri.loadIcon(pm)
                    AppInfo(name, pkg, icon)
                } catch (e: Exception) { null }
            }
            .sortedBy { it.name.lowercase() }
    }

    private fun filterApps(query: String) {
        val filtered = if (query.isBlank()) allApps
        else allApps.filter { it.name.contains(query, ignoreCase = true) }
        adapter.updateApps(filtered)
    }

    private fun launchApp(packageName: String) {
        val intent = packageManager.getLaunchIntentForPackage(packageName)
        if (intent != null) startActivity(intent)
        else Toast.makeText(this, "Can't open app", Toast.LENGTH_SHORT).show()
    }

    private fun showAppOptions(app: AppInfo) {
        AlertDialog.Builder(this)
            .setTitle(app.name)
            .setItems(arrayOf("Add to dock", "App info")) { _, which ->
                when (which) {
                    0 -> {
                        val result = Intent().apply { putExtra("picked_package", app.packageName) }
                        setResult(Activity.RESULT_OK, result)
                        finish()
                    }
                    1 -> {
                        val intent = Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                        intent.data = android.net.Uri.parse("package:${app.packageName}")
                        startActivity(intent)
                    }
                }
            }.show()
    }

    override fun onDestroy() {
        super.onDestroy()
        scope.cancel()
    }
}

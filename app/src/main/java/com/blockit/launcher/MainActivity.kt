package com.blockit.launcher

import android.annotation.SuppressLint
import android.app.Activity
import android.appwidget.AppWidgetHost
import android.appwidget.AppWidgetHostView
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProviderInfo
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import android.os.Bundle
import android.view.*
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import kotlinx.coroutines.*
import java.text.SimpleDateFormat
import java.util.*

class MainActivity : AppCompatActivity() {

    companion object {
        const val APPWIDGET_HOST_ID = 1024
        const val REQUEST_PICK_APPWIDGET = 2001
        const val REQUEST_CREATE_APPWIDGET = 2002
        const val REQUEST_DOCK_APP = 3001
    }

    private lateinit var prefs: Prefs
    private lateinit var appWidgetManager: AppWidgetManager
    private lateinit var appWidgetHost: AppWidgetHost

    private lateinit var clockDisplay: TextView
    private lateinit var dateDisplay: TextView
    private lateinit var widgetContainer: LinearLayout
    private lateinit var contextMenu: LinearLayout
    private lateinit var rootLayout: View
    private lateinit var statusBarSpacer: View

    private val dockFrames = arrayOfNulls<FrameLayout>(4)
    private val dockIcons = arrayOfNulls<ImageView>(4)
    private val dockLabels = arrayOfNulls<TextView>(4)

    private var pendingWidgetId = -1
    private var pendingDockSlot = -1
    private var statusBarVisible = true

    private val clockScope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        prefs = Prefs(this)
        appWidgetManager = AppWidgetManager.getInstance(this)
        appWidgetHost = AppWidgetHost(this, APPWIDGET_HOST_ID)
        appWidgetHost.startListening()

        bindViews()
        setupWindowInsets()
        applyStatusBarPref()
        startClock()
        setupLongPress()
        setupContextMenu()
        loadDock()
        loadWidgets()
    }

    private fun bindViews() {
        rootLayout = findViewById(R.id.rootLayout)
        statusBarSpacer = findViewById(R.id.statusBarSpacer)
        clockDisplay = findViewById(R.id.clockDisplay)
        dateDisplay = findViewById(R.id.dateDisplay)
        widgetContainer = findViewById(R.id.widgetContainer)
        contextMenu = findViewById(R.id.contextMenu)

        dockFrames[0] = findViewById(R.id.dockApp1)
        dockFrames[1] = findViewById(R.id.dockApp2)
        dockFrames[2] = findViewById(R.id.dockApp3)
        dockFrames[3] = findViewById(R.id.dockApp4)

        dockIcons[0] = findViewById(R.id.dockIcon1)
        dockIcons[1] = findViewById(R.id.dockIcon2)
        dockIcons[2] = findViewById(R.id.dockIcon3)
        dockIcons[3] = findViewById(R.id.dockIcon4)

        dockLabels[0] = findViewById(R.id.dockLabel1)
        dockLabels[1] = findViewById(R.id.dockLabel2)
        dockLabels[2] = findViewById(R.id.dockLabel3)
        dockLabels[3] = findViewById(R.id.dockLabel4)
    }

    private fun setupWindowInsets() {
        ViewCompat.setOnApplyWindowInsetsListener(statusBarSpacer) { view, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.statusBars())
            view.layoutParams.height = if (statusBarVisible) bars.top else 0
            view.requestLayout()
            insets
        }
    }

    private fun applyStatusBarPref() {
        statusBarVisible = prefs.showStatusBar
        val controller = WindowInsetsControllerCompat(window, window.decorView)
        if (statusBarVisible) {
            controller.show(WindowInsetsCompat.Type.statusBars())
        } else {
            controller.hide(WindowInsetsCompat.Type.statusBars())
            controller.systemBarsBehavior =
                WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
        // Update spacer height
        statusBarSpacer.post {
            statusBarSpacer.layoutParams.height = if (statusBarVisible) {
                val resourceId = resources.getIdentifier("status_bar_height", "dimen", "android")
                if (resourceId > 0) resources.getDimensionPixelSize(resourceId) else 0
            } else 0
            statusBarSpacer.requestLayout()
        }
    }

    private fun startClock() {
        clockScope.launch {
            while (isActive) {
                val now = Calendar.getInstance()
                val timeFormat = SimpleDateFormat("HH:mm", Locale.getDefault())
                val dateFormat = SimpleDateFormat("EEEE, d MMM", Locale.getDefault())
                clockDisplay.text = timeFormat.format(now.time)
                dateDisplay.text = dateFormat.format(now.time)
                // Sleep until next minute
                val secondsLeft = 60 - now.get(Calendar.SECOND)
                delay(secondsLeft * 1000L)
            }
        }
        // Immediate first tick
        val now = Calendar.getInstance()
        clockDisplay.text = SimpleDateFormat("HH:mm", Locale.getDefault()).format(now.time)
        dateDisplay.text = SimpleDateFormat("EEEE, d MMM", Locale.getDefault()).format(now.time)
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun setupLongPress() {
        rootLayout.setOnLongClickListener { view ->
            showContextMenu(view.width / 2f, view.height / 3f)
            true
        }
        // Tap anywhere to dismiss context menu
        rootLayout.setOnTouchListener { _, event ->
            if (event.action == MotionEvent.ACTION_DOWN && contextMenu.visibility == View.VISIBLE) {
                hideContextMenu()
                true
            } else false
        }
    }

    private fun showContextMenu(x: Float, y: Float) {
        contextMenu.visibility = View.VISIBLE
        contextMenu.x = x.coerceIn(0f, (rootLayout.width - contextMenu.width).toFloat().coerceAtLeast(0f))
        contextMenu.y = y.coerceIn(0f, (rootLayout.height - 300).toFloat().coerceAtLeast(0f))
        contextMenu.animate().alpha(1f).scaleX(1f).scaleY(1f).setDuration(150).start()
        // Update status bar menu label
        findViewById<TextView>(R.id.menuToggleStatusBar).text =
            if (statusBarVisible) "Hide Status Bar" else "Show Status Bar"
    }

    private fun hideContextMenu() {
        contextMenu.animate().alpha(0f).scaleX(0.9f).scaleY(0.9f).setDuration(120)
            .withEndAction { contextMenu.visibility = View.GONE }.start()
    }

    private fun setupContextMenu() {
        contextMenu.alpha = 0f
        contextMenu.scaleX = 0.9f
        contextMenu.scaleY = 0.9f

        findViewById<TextView>(R.id.menuAllApps).setOnClickListener {
            hideContextMenu()
            startActivity(Intent(this, AppDrawerActivity::class.java))
        }

        findViewById<TextView>(R.id.menuAddWidget).setOnClickListener {
            hideContextMenu()
            pickWidget()
        }

        findViewById<TextView>(R.id.menuToggleStatusBar).setOnClickListener {
            hideContextMenu()
            statusBarVisible = !statusBarVisible
            prefs.showStatusBar = statusBarVisible
            applyStatusBarPref()
        }

        findViewById<TextView>(R.id.menuSettings).setOnClickListener {
            hideContextMenu()
            startActivity(Intent(this, SettingsActivity::class.java))
        }
    }

    // ── DOCK ──────────────────────────────────────────────────────────────────

    private fun loadDock() {
        val packages = prefs.dockPackages
        packages.forEachIndexed { slot, pkg ->
            if (pkg.isNotEmpty()) setDockSlot(slot, pkg)
            else clearDockSlot(slot)
        }
        // Set click listeners for each slot
        for (slot in 0..3) {
            dockFrames[slot]?.setOnClickListener {
                val pkg = prefs.getDockSlot(slot)
                if (pkg.isNotEmpty()) launchApp(pkg)
                else promptDockSlotPick(slot)
            }
            dockFrames[slot]?.setOnLongClickListener {
                showDockSlotOptions(slot)
                true
            }
        }
    }

    private fun setDockSlot(slot: Int, packageName: String) {
        try {
            val pm = packageManager
            val info = pm.getApplicationInfo(packageName, 0)
            val icon: Drawable = pm.getApplicationIcon(packageName)
            val name = pm.getApplicationLabel(info).toString()
            dockIcons[slot]?.setImageDrawable(icon)
            dockLabels[slot]?.text = name
            dockIcons[slot]?.visibility = View.VISIBLE
            dockLabels[slot]?.visibility = View.VISIBLE
        } catch (e: PackageManager.NameNotFoundException) {
            clearDockSlot(slot)
        }
    }

    private fun clearDockSlot(slot: Int) {
        dockIcons[slot]?.setImageDrawable(null)
        dockIcons[slot]?.setImageResource(android.R.drawable.ic_menu_add)
        dockIcons[slot]?.alpha = 0.3f
        dockLabels[slot]?.text = ""
    }

    private fun promptDockSlotPick(slot: Int) {
        pendingDockSlot = slot
        startActivityForResult(
            Intent(this, AppDrawerActivity::class.java).apply {
                putExtra("pick_mode", true)
            },
            REQUEST_DOCK_APP
        )
    }

    private fun showDockSlotOptions(slot: Int) {
        val pkg = prefs.getDockSlot(slot)
        val options = if (pkg.isNotEmpty())
            arrayOf("Change app", "Remove from dock")
        else
            arrayOf("Add app to dock")

        AlertDialog.Builder(this)
            .setTitle("Dock slot ${slot + 1}")
            .setItems(options) { _, which ->
                when {
                    pkg.isEmpty() -> promptDockSlotPick(slot)
                    which == 0 -> promptDockSlotPick(slot)
                    which == 1 -> {
                        prefs.setDockSlot(slot, "")
                        clearDockSlot(slot)
                        dockIcons[slot]?.alpha = 0.3f
                    }
                }
            }.show()
    }

    // ── WIDGETS ───────────────────────────────────────────────────────────────

    private fun pickWidget() {
        val appWidgetId = appWidgetHost.allocateAppWidgetId()
        pendingWidgetId = appWidgetId
        val pickIntent = Intent(AppWidgetManager.ACTION_APPWIDGET_PICK).apply {
            putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
        }
        startActivityForResult(pickIntent, REQUEST_PICK_APPWIDGET)
    }

    private fun configureWidget(appWidgetId: Int) {
        val appWidgetInfo = appWidgetManager.getAppWidgetInfo(appWidgetId)
        if (appWidgetInfo?.configure != null) {
            val intent = Intent(AppWidgetManager.ACTION_APPWIDGET_CONFIGURE).apply {
                component = appWidgetInfo.configure
                putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
            }
            startActivityForResult(intent, REQUEST_CREATE_APPWIDGET)
        } else {
            addWidgetToScreen(appWidgetId)
        }
    }

    private fun addWidgetToScreen(appWidgetId: Int) {
        val appWidgetInfo: AppWidgetProviderInfo =
            appWidgetManager.getAppWidgetInfo(appWidgetId) ?: run {
                appWidgetHost.deleteAppWidgetId(appWidgetId)
                return
            }

        val hostView: AppWidgetHostView = appWidgetHost.createView(this, appWidgetId, appWidgetInfo)
        hostView.setAppWidget(appWidgetId, appWidgetInfo)

        // Calculate widget height from minHeight
        val minHeightDp = appWidgetInfo.minHeight
        val density = resources.displayMetrics.density
        val minHeightPx = (minHeightDp * density).toInt().coerceAtLeast((120 * density).toInt())

        val params = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            minHeightPx
        ).apply { setMargins(0, 0, 0, (12 * density).toInt()) }

        // Wrap in a container with remove button
        val wrapper = FrameLayout(this).apply {
            layoutParams = params
            background = getDrawable(R.drawable.bg_widget_area)
            setPadding(8, 8, 8, 8)
        }

        hostView.layoutParams = FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT,
            FrameLayout.LayoutParams.MATCH_PARENT
        )
        wrapper.addView(hostView)

        // Remove button (top-right)
        val removeBtn = TextView(this).apply {
            text = "✕"
            textSize = 14f
            setTextColor(0x99000000.toInt())
            setPadding(16, 8, 16, 8)
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT,
                FrameLayout.LayoutParams.WRAP_CONTENT,
                Gravity.TOP or Gravity.END
            )
            setOnClickListener {
                widgetContainer.removeView(wrapper)
                appWidgetHost.deleteAppWidgetId(appWidgetId)
                prefs.removeWidgetId(appWidgetId)
            }
        }
        wrapper.addView(removeBtn)

        widgetContainer.addView(wrapper)
        prefs.addWidgetId(appWidgetId)
    }

    private fun loadWidgets() {
        val ids = prefs.widgetIds
        for (id in ids) {
            val info = appWidgetManager.getAppWidgetInfo(id)
            if (info != null) {
                addWidgetToScreenSilent(id, info)
            } else {
                prefs.removeWidgetId(id)
            }
        }
    }

    private fun addWidgetToScreenSilent(appWidgetId: Int, appWidgetInfo: AppWidgetProviderInfo) {
        val hostView: AppWidgetHostView = appWidgetHost.createView(this, appWidgetId, appWidgetInfo)
        hostView.setAppWidget(appWidgetId, appWidgetInfo)

        val density = resources.displayMetrics.density
        val minHeightPx = (appWidgetInfo.minHeight * density).toInt().coerceAtLeast((120 * density).toInt())

        val params = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            minHeightPx
        ).apply { setMargins(0, 0, 0, (12 * density).toInt()) }

        val wrapper = FrameLayout(this).apply {
            layoutParams = params
            background = getDrawable(R.drawable.bg_widget_area)
            setPadding(8, 8, 8, 8)
        }

        hostView.layoutParams = FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT,
            FrameLayout.LayoutParams.MATCH_PARENT
        )
        wrapper.addView(hostView)

        val removeBtn = TextView(this).apply {
            text = "✕"
            textSize = 14f
            setTextColor(0x99000000.toInt())
            setPadding(16, 8, 16, 8)
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT,
                FrameLayout.LayoutParams.WRAP_CONTENT,
                Gravity.TOP or Gravity.END
            )
            setOnClickListener {
                widgetContainer.removeView(wrapper)
                appWidgetHost.deleteAppWidgetId(appWidgetId)
                prefs.removeWidgetId(appWidgetId)
            }
        }
        wrapper.addView(removeBtn)

        widgetContainer.addView(wrapper)
    }

    // ── ACTIVITY RESULTS ──────────────────────────────────────────────────────

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        when (requestCode) {
            REQUEST_PICK_APPWIDGET -> {
                if (resultCode == Activity.RESULT_OK && data != null) {
                    val appWidgetId = data.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, -1)
                    if (appWidgetId != -1) configureWidget(appWidgetId)
                } else {
                    if (pendingWidgetId != -1) appWidgetHost.deleteAppWidgetId(pendingWidgetId)
                    pendingWidgetId = -1
                }
            }
            REQUEST_CREATE_APPWIDGET -> {
                if (resultCode == Activity.RESULT_OK && data != null) {
                    val appWidgetId = data.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, -1)
                    if (appWidgetId != -1) addWidgetToScreen(appWidgetId)
                } else {
                    if (pendingWidgetId != -1) appWidgetHost.deleteAppWidgetId(pendingWidgetId)
                    pendingWidgetId = -1
                }
            }
            REQUEST_DOCK_APP -> {
                if (resultCode == Activity.RESULT_OK && data != null) {
                    val pkg = data.getStringExtra("picked_package") ?: return
                    if (pendingDockSlot in 0..3) {
                        prefs.setDockSlot(pendingDockSlot, pkg)
                        setDockSlot(pendingDockSlot, pkg)
                        dockIcons[pendingDockSlot]?.alpha = 1f
                        pendingDockSlot = -1
                    }
                }
            }
        }
    }

    // ── HELPERS ───────────────────────────────────────────────────────────────

    private fun launchApp(packageName: String) {
        val intent = packageManager.getLaunchIntentForPackage(packageName)
        if (intent != null) startActivity(intent)
        else Toast.makeText(this, "Can't open app", Toast.LENGTH_SHORT).show()
    }

    override fun onResume() {
        super.onResume()
        appWidgetHost.startListening()
        // Refresh clock immediately
        val now = Calendar.getInstance()
        clockDisplay.text = SimpleDateFormat("HH:mm", Locale.getDefault()).format(now.time)
        dateDisplay.text = SimpleDateFormat("EEEE, d MMM", Locale.getDefault()).format(now.time)
    }

    override fun onPause() {
        super.onPause()
        appWidgetHost.stopListening()
    }

    override fun onDestroy() {
        super.onDestroy()
        clockScope.cancel()
        appWidgetHost.stopListening()
    }

    override fun onBackPressed() {
        if (contextMenu.visibility == View.VISIBLE) {
            hideContextMenu()
        }
        // Otherwise do nothing — launcher stays
    }
}

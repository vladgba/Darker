package x.vladgba.darker

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.database.ContentObserver
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.HapticFeedbackConstants
import android.view.View
import android.widget.ImageButton
import android.widget.SeekBar
import android.widget.TextView

class MainActivity : Activity(), SharedPreferences.OnSharedPreferenceChangeListener {

    private companion object {
        const val REQUEST_NOTIFICATIONS = 1
    }

    private lateinit var power: ImageButton
    private lateinit var state: TextView
    private lateinit var dimSlider: SeekBar
    private lateinit var dimValue: TextView
    private lateinit var brightSlider: SeekBar
    private lateinit var brightValue: TextView
    private lateinit var setupGroup: View
    private lateinit var overlayRow: View
    private lateinit var settingsRow: View
    private lateinit var a11yRow: View

    /** Set when the user tapped power but we had to send them to grant the overlay permission. */
    private var startWhenPermitted = false
    private var draggingBrightness = false

    /** Follows brightness changes made elsewhere (system slider, widget, auto-brightness). */
    private val brightnessObserver = object : ContentObserver(Handler(Looper.getMainLooper())) {
        override fun onChange(selfChange: Boolean) {
            if (!draggingBrightness) renderBrightness()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        power = findViewById(R.id.power)
        state = findViewById(R.id.state)
        dimSlider = findViewById(R.id.dim_slider)
        dimValue = findViewById(R.id.dim_value)
        brightSlider = findViewById(R.id.bright_slider)
        brightValue = findViewById(R.id.bright_value)
        setupGroup = findViewById(R.id.setup_group)
        overlayRow = findViewById(R.id.row_overlay)
        settingsRow = findViewById(R.id.row_settings)
        a11yRow = findViewById(R.id.row_a11y)

        // Dim
        updateDimRange()
        dimSlider.progress = Prefs.opacity(this)
        dimValue.text = getString(R.string.percent, Prefs.opacity(this))
        dimSlider.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(bar: SeekBar, v: Int, fromUser: Boolean) {
                dimValue.text = getString(R.string.percent, v)
                if (fromUser) Prefs.setOpacity(this@MainActivity, v)
            }
            override fun onStartTrackingTouch(bar: SeekBar) {}
            override fun onStopTrackingTouch(bar: SeekBar) {}
        })

        // Brightness
        brightSlider.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(bar: SeekBar, v: Int, fromUser: Boolean) {
                if (!fromUser) return
                Brightness.set(this@MainActivity, v)
                brightValue.text = getString(R.string.percent, v)
            }
            override fun onStartTrackingTouch(bar: SeekBar) { draggingBrightness = true }
            override fun onStopTrackingTouch(bar: SeekBar) {
                draggingBrightness = false
                Widgets.updateAll(this@MainActivity)
            }
        })

        power.setOnClickListener {
            it.performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK)
            onPowerClick()
        }
        findViewById<View>(R.id.btn_overlay).setOnClickListener {
            startWhenPermitted = true
            requestOverlayPermission()
        }
        findViewById<View>(R.id.btn_settings).setOnClickListener {
            startActivity(Brightness.permissionIntent(this))
        }
        findViewById<View>(R.id.btn_a11y).setOnClickListener {
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        }
    }

    override fun onResume() {
        super.onResume()
        Prefs.sp(this).registerOnSharedPreferenceChangeListener(this)
        contentResolver.registerContentObserver(
            Settings.System.getUriFor(Settings.System.SCREEN_BRIGHTNESS), false, brightnessObserver
        )
        contentResolver.registerContentObserver(
            Settings.System.getUriFor(Settings.System.SCREEN_BRIGHTNESS_MODE), false, brightnessObserver
        )
        if (startWhenPermitted && Settings.canDrawOverlays(this)) {
            startWhenPermitted = false
            askNotificationsThenStart()
        }
        updateDimRange()
        render()
        renderBrightness()
        // Permissions may have changed while we were away; refresh the widgets' actions.
        Widgets.updateAll(this)
    }

    override fun onPause() {
        Prefs.sp(this).unregisterOnSharedPreferenceChangeListener(this)
        contentResolver.unregisterContentObserver(brightnessObserver)
        super.onPause()
    }

    override fun onSharedPreferenceChanged(sp: SharedPreferences?, key: String?) {
        if (key == Prefs.KEY_OPACITY) {
            val v = Prefs.opacity(this)
            if (dimSlider.progress != v) dimSlider.progress = v
        }
        render()
    }

    // ---- power ----

    private fun onPowerClick() {
        when {
            // The accessibility service follows the stored state; no foreground service needed.
            DarkerAccessibilityService.isActive -> Prefs.setEnabled(this, !Prefs.isEnabled(this))
            Prefs.isEnabled(this) -> startService(
                Intent(this, OverlayService::class.java).setAction(OverlayService.ACTION_STOP)
            )
            !Settings.canDrawOverlays(this) -> {
                startWhenPermitted = true
                requestOverlayPermission()
            }
            else -> askNotificationsThenStart()
        }
    }

    private fun askNotificationsThenStart() {
        val needsAsk = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED &&
            !Prefs.askedNotifications(this)
        if (needsAsk) {
            Prefs.markAskedNotifications(this)
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), REQUEST_NOTIFICATIONS)
        } else {
            startOverlay()
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int, permissions: Array<out String>, grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        // Start either way: without the permission the service still runs, just without a visible notification.
        if (requestCode == REQUEST_NOTIFICATIONS) startOverlay()
    }

    private fun startOverlay() {
        if (DarkerAccessibilityService.isActive) {
            Prefs.setEnabled(this, true)
            return
        }
        startForegroundService(
            Intent(this, OverlayService::class.java).setAction(OverlayService.ACTION_START)
        )
    }

    private fun requestOverlayPermission() {
        startActivity(
            Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName"))
        )
    }

    // ---- rendering ----

    /** The dim cap depends on whether the accessibility service is running. */
    private fun updateDimRange() {
        dimSlider.max = Prefs.maxOpacity
    }

    private fun render() {
        val on = Prefs.isEnabled(this)
        val a11y = DarkerAccessibilityService.isActive
        power.isActivated = on
        power.contentDescription = getString(if (on) R.string.turn_off else R.string.turn_on)
        state.setText(if (on) R.string.state_on else R.string.state_off)
        state.setTextColor(getColor(if (on) R.color.accent else R.color.muted))
        dimValue.animate().alpha(if (on) 1f else 0.7f).setDuration(200).start()

        // Setup: one button per missing permission; each disappears once granted.
        val needOverlay = !a11y && !Settings.canDrawOverlays(this)
        val needSettings = !Brightness.canWrite(this)
        val needA11y = !a11y
        overlayRow.visibility = if (needOverlay) View.VISIBLE else View.GONE
        settingsRow.visibility = if (needSettings) View.VISIBLE else View.GONE
        a11yRow.visibility = if (needA11y) View.VISIBLE else View.GONE
        setupGroup.visibility =
            if (needOverlay || needSettings || needA11y) View.VISIBLE else View.GONE
    }

    private fun renderBrightness() {
        val writable = Brightness.canWrite(this)
        val pct = Brightness.percent(this).coerceIn(1, 100)
        brightSlider.progress = pct
        brightSlider.isEnabled = writable
        brightSlider.alpha = if (writable) 1f else 0.4f
        brightValue.text =
            if (Brightness.isAuto(this)) getString(R.string.auto) else getString(R.string.percent, pct)
        brightValue.alpha = if (writable) 1f else 0.5f
    }
}

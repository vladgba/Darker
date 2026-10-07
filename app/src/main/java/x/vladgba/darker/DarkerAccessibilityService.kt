package x.vladgba.darker

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.content.SharedPreferences
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent

/**
 * Optional host for the dim layer. An accessibility overlay sits above the status bar and
 * notification shade, and Android treats it as trusted, so it isn't capped at 80% opacity.
 * It reads no screen content and ignores all accessibility events.
 */
class DarkerAccessibilityService : AccessibilityService(),
    SharedPreferences.OnSharedPreferenceChangeListener {

    companion object {
        @Volatile
        private var instance: DarkerAccessibilityService? = null
        val isActive: Boolean get() = instance != null
    }

    private var window: DimWindow? = null

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        window = DimWindow(this, WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY)
        Prefs.sp(this).registerOnSharedPreferenceChangeListener(this)
        // Take over from the regular overlay if it is currently running.
        OverlayService.handOver()
        sync()
        Widgets.updateAll(this)
    }

    override fun onSharedPreferenceChanged(sp: SharedPreferences?, key: String?) {
        when (key) {
            Prefs.KEY_ENABLED -> sync()
            Prefs.KEY_OPACITY -> window?.setOpacity(Prefs.opacity(this))
        }
    }

    private fun sync() {
        val w = window ?: return
        if (Prefs.isEnabled(this)) w.show(Prefs.opacity(this)) else w.hide()
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) = Unit
    override fun onInterrupt() = Unit

    override fun onUnbind(intent: Intent?): Boolean {
        cleanup()
        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        cleanup()
        super.onDestroy()
    }

    private fun cleanup() {
        if (instance == null) return
        Prefs.sp(this).unregisterOnSharedPreferenceChangeListener(this)
        val wasOn = window?.isShown == true
        window?.hide()
        window = null
        instance = null
        if (wasOn) Prefs.setEnabled(this, false)
        Widgets.updateAll(this)
    }
}

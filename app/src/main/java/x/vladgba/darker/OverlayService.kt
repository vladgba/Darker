package x.vladgba.darker

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.ServiceInfo
import android.database.ContentObserver
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.provider.Settings
import android.view.WindowManager
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat

/**
 * Foreground service that hosts the dim layer as a regular app overlay.
 * Used when the accessibility service is off. If the accessibility service is on,
 * this service only records the requested state and hands the work over to it.
 */
class OverlayService : Service(), SharedPreferences.OnSharedPreferenceChangeListener {

    companion object {
        const val ACTION_START = "x.vladgba.darker.START"
        const val ACTION_STOP = "x.vladgba.darker.STOP"
        const val ACTION_TOGGLE = "x.vladgba.darker.TOGGLE"
        private const val CHANNEL_ID = "overlay"
        private const val NOTIFICATION_ID = 1

        @Volatile
        private var instance: OverlayService? = null

        /** Removes this service's overlay without changing the on/off state. */
        fun handOver() = instance?.release()
    }

    private lateinit var window: DimWindow

    /** Keeps the widget's brightness readout fresh while the service is alive. */
    private val brightnessObserver = object : ContentObserver(Handler(Looper.getMainLooper())) {
        override fun onChange(selfChange: Boolean) = Widgets.updateAll(this@OverlayService)
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        instance = this
        window = DimWindow(this, WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY)
        Prefs.sp(this).registerOnSharedPreferenceChangeListener(this)
        contentResolver.registerContentObserver(
            Settings.System.getUriFor(Settings.System.SCREEN_BRIGHTNESS), false, brightnessObserver
        )
        contentResolver.registerContentObserver(
            Settings.System.getUriFor(Settings.System.SCREEN_BRIGHTNESS_MODE), false, brightnessObserver
        )
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // Must be called promptly for every start, even one that only stops the service.
        goForeground()
        val a11y = DarkerAccessibilityService.isActive
        val want = when (intent?.action) {
            ACTION_STOP -> false
            ACTION_TOGGLE -> if (a11y) !Prefs.isEnabled(this) else !window.isShown
            ACTION_START -> true
            else -> Prefs.isEnabled(this) // null intent: restarted by the system after being killed
        }
        if (a11y) {
            // The accessibility service draws the layer; just record the state.
            Prefs.setEnabled(this, want)
            release()
            return START_NOT_STICKY
        }
        if (want && Settings.canDrawOverlays(this) && window.show(Prefs.opacity(this))) {
            Prefs.setEnabled(this, true)
            Widgets.updateAll(this)
            return START_STICKY
        }
        Prefs.setEnabled(this, false)
        release()
        return START_NOT_STICKY
    }

    override fun onSharedPreferenceChanged(sp: SharedPreferences?, key: String?) {
        if (key == Prefs.KEY_OPACITY) window.setOpacity(Prefs.opacity(this))
    }

    override fun onDestroy() {
        window.hide()
        instance = null
        Prefs.sp(this).unregisterOnSharedPreferenceChangeListener(this)
        contentResolver.unregisterContentObserver(brightnessObserver)
        super.onDestroy()
    }

    private fun release() {
        window.hide()
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        stopSelf()
        Widgets.updateAll(this)
    }

    // ---- notification ----

    private fun goForeground() {
        val nm = getSystemService(NotificationManager::class.java)
        if (nm.getNotificationChannel(CHANNEL_ID) == null) {
            nm.createNotificationChannel(
                NotificationChannel(
                    CHANNEL_ID, getString(R.string.notif_channel), NotificationManager.IMPORTANCE_LOW
                ).apply { setShowBadge(false) }
            )
        }
        val flags = PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        val open = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java), flags
        )
        val stop = PendingIntent.getService(
            this, 1, Intent(this, OverlayService::class.java).setAction(ACTION_STOP), flags
        )
        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_dim)
            .setContentTitle(getString(R.string.notif_title))
            .setContentText(getString(R.string.notif_text))
            .setContentIntent(open)
            .addAction(0, getString(R.string.turn_off), stop)
            .setOngoing(true)
            .setSilent(true)
            .setShowWhen(false)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .build()
        ServiceCompat.startForeground(
            this, NOTIFICATION_ID, notification,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
                ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE else 0
        )
    }
}

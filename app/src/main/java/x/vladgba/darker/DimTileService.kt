package x.vladgba.darker

import android.app.PendingIntent
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.os.Build
import android.provider.Settings
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService

/** Quick Settings tile: tap toggles dimming, long-press opens the app. */
class DimTileService : TileService(), SharedPreferences.OnSharedPreferenceChangeListener {

    companion object {
        /** Asks the system to refresh the tile after the state changed elsewhere. */
        fun refresh(ctx: Context) = runCatching {
            requestListeningState(ctx, ComponentName(ctx, DimTileService::class.java))
        }
    }

    override fun onStartListening() {
        super.onStartListening()
        Prefs.sp(this).registerOnSharedPreferenceChangeListener(this)
        render()
    }

    override fun onStopListening() {
        Prefs.sp(this).unregisterOnSharedPreferenceChangeListener(this)
        super.onStopListening()
    }

    override fun onSharedPreferenceChanged(sp: SharedPreferences?, key: String?) = render()

    override fun onClick() {
        super.onClick()
        when {
            DarkerAccessibilityService.isActive -> Prefs.setEnabled(this, !Prefs.isEnabled(this))
            Settings.canDrawOverlays(this) -> {
                val action = if (Prefs.isEnabled(this)) OverlayService.ACTION_STOP else OverlayService.ACTION_START
                startForegroundService(
                    Intent(this, OverlayService::class.java).setAction(action)
                )
            }
            else -> openApp()
        }
    }

    private fun openApp() {
        val intent = Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startActivityAndCollapse(
                PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_IMMUTABLE)
            )
        } else {
            @Suppress("DEPRECATION", "StartActivityAndCollapseDeprecated")
            startActivityAndCollapse(intent)
        }
    }

    private fun render() {
        val tile = qsTile ?: return
        val on = Prefs.isEnabled(this)
        tile.state = if (on) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
        tile.label = getString(R.string.app_name)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            tile.subtitle = if (on) getString(R.string.percent, Prefs.opacity(this))
                            else getString(R.string.state_off)
        }
        tile.updateTile()
    }
}

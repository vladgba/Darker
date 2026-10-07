package x.vladgba.darker

import android.content.Context
import android.content.Intent
import androidx.core.net.toUri
import android.provider.Settings
import kotlin.math.roundToInt

/** System screen brightness, via the "Modify system settings" permission. */
object Brightness {

    fun canWrite(ctx: Context): Boolean = Settings.System.canWrite(ctx)

    fun isAuto(ctx: Context): Boolean = Settings.System.getInt(
        ctx.contentResolver,
        Settings.System.SCREEN_BRIGHTNESS_MODE,
        Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL
    ) == Settings.System.SCREEN_BRIGHTNESS_MODE_AUTOMATIC

    fun percent(ctx: Context): Int {
        val raw = Settings.System.getInt(ctx.contentResolver, Settings.System.SCREEN_BRIGHTNESS, 128)
        return (raw.coerceIn(0, 255) * 100f / 255f).roundToInt()
    }

    /** Steps brightness up or down by 10% and switches auto-brightness off. */
    fun step(ctx: Context, up: Boolean) {
        if (!canWrite(ctx)) return
        set(ctx, Prefs.snap(percent(ctx), up, 1, 100))
    }

    fun set(ctx: Context, percent: Int) {
        if (!canWrite(ctx)) return
        val cr = ctx.contentResolver
        Settings.System.putInt(
            cr, Settings.System.SCREEN_BRIGHTNESS_MODE, Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL
        )
        val raw = (percent.coerceIn(1, 100) * 255f / 100f).roundToInt().coerceAtLeast(1)
        Settings.System.putInt(cr, Settings.System.SCREEN_BRIGHTNESS, raw)
    }

    fun permissionIntent(ctx: Context): Intent =
        Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS, "package:${ctx.packageName}".toUri())
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
}

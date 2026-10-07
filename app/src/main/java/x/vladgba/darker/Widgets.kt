package x.vladgba.darker

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.provider.Settings
import android.widget.RemoteViews

/*
 * Home-screen widgets. All four share view ids, so the bind* helpers work on any layout
 * that contains the matching views:
 *  - DimWidget          4x1  dim level  (− bar +)
 *  - BrightnessWidget   4x1  system brightness (− bar +)
 *  - ToggleWidget       1x1  dim on/off button
 *  - DarkerWidget       4x2  all of the above in one
 */

open class BaseWidget : AppWidgetProvider() {
    override fun onUpdate(ctx: Context, mgr: AppWidgetManager, ids: IntArray) =
        Widgets.updateAll(ctx)
}

class DimWidget : BaseWidget()
class BrightnessWidget : BaseWidget()
class ToggleWidget : BaseWidget()

/** Receives the widgets' button taps. */
class WidgetActionReceiver : BroadcastReceiver() {
    override fun onReceive(ctx: Context, intent: Intent) {
        when (intent.action) {
            Widgets.ACTION_POWER -> Prefs.setEnabled(ctx, !Prefs.isEnabled(ctx))
            Widgets.ACTION_DIM_UP, Widgets.ACTION_DIM_DOWN -> {
                val up = intent.action == Widgets.ACTION_DIM_UP
                // The running overlay picks this up through its preference listener.
                Prefs.setOpacity(ctx, Prefs.snap(Prefs.opacity(ctx), up, 0, Prefs.maxOpacity))
            }
            Widgets.ACTION_BRIGHT_UP, Widgets.ACTION_BRIGHT_DOWN ->
                Brightness.step(ctx, intent.action == Widgets.ACTION_BRIGHT_UP)
            else -> return
        }
        Widgets.updateAll(ctx)
    }
}

object Widgets {
    const val ACTION_POWER = "x.vladgba.darker.WIDGET_POWER"
    const val ACTION_DIM_UP = "x.vladgba.darker.DIM_UP"
    const val ACTION_DIM_DOWN = "x.vladgba.darker.DIM_DOWN"
    const val ACTION_BRIGHT_UP = "x.vladgba.darker.BRIGHT_UP"
    const val ACTION_BRIGHT_DOWN = "x.vladgba.darker.BRIGHT_DOWN"

    private const val COLOR_ACCENT = 0xFFF2EDE4.toInt()
    private const val COLOR_INK = 0xFF0A0A0B.toInt()
    private const val COLOR_TEXT = 0xFFF4F4F6.toInt()
    private const val COLOR_MUTED = 0xFFB4B4BC.toInt()

    private const val FLAGS = PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT

    fun updateAll(ctx: Context) {
        val mgr = AppWidgetManager.getInstance(ctx)
        fun push(cls: Class<*>, layout: Int, bind: (Context, RemoteViews) -> Unit) {
            val ids = mgr.getAppWidgetIds(ComponentName(ctx, cls))
            if (ids.isEmpty()) return
            val rv = RemoteViews(ctx.packageName, layout)
            bind(ctx, rv)
            mgr.updateAppWidget(ids, rv)
        }
        push(DimWidget::class.java, R.layout.widget_dim) { c, rv -> bindDim(c, rv) }
        push(BrightnessWidget::class.java, R.layout.widget_brightness) { c, rv -> bindBrightness(c, rv) }
        push(ToggleWidget::class.java, R.layout.widget_toggle) { c, rv -> bindPower(c, rv) }
        push(DarkerWidget::class.java, R.layout.widget_darker) { c, rv ->
            bindPower(c, rv); bindDim(c, rv); bindBrightness(c, rv)
        }
    }

    private fun bindPower(ctx: Context, rv: RemoteViews) {
        val on = Prefs.isEnabled(ctx)
        rv.setInt(R.id.w_power, "setBackgroundResource",
            if (on) R.drawable.widget_power_on else R.drawable.widget_power_off)
        rv.setInt(R.id.w_power, "setColorFilter", if (on) COLOR_INK else COLOR_TEXT)
        val pi = when {
            // The accessibility service follows the stored state directly.
            DarkerAccessibilityService.isActive -> action(ctx, ACTION_POWER, 10)
            Settings.canDrawOverlays(ctx) -> PendingIntent.getForegroundService(
                ctx, 11,
                Intent(ctx, OverlayService::class.java).setAction(OverlayService.ACTION_TOGGLE),
                FLAGS
            )
            else -> PendingIntent.getActivity(
                ctx, 12,
                Intent(ctx, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                FLAGS
            )
        }
        rv.setOnClickPendingIntent(R.id.w_power, pi)
    }

    private fun bindDim(ctx: Context, rv: RemoteViews) {
        val dim = Prefs.opacity(ctx)
        rv.setProgressBar(R.id.w_dim_bar, 100, dim, false)
        rv.setTextViewText(R.id.w_dim_value, "$dim%")
        rv.setTextColor(R.id.w_dim_value, if (Prefs.isEnabled(ctx)) COLOR_ACCENT else COLOR_MUTED)
        rv.setOnClickPendingIntent(R.id.w_dim_minus, action(ctx, ACTION_DIM_DOWN, 20))
        rv.setOnClickPendingIntent(R.id.w_dim_plus, action(ctx, ACTION_DIM_UP, 21))
    }

    private fun bindBrightness(ctx: Context, rv: RemoteViews) {
        val bright = Brightness.percent(ctx)
        rv.setProgressBar(R.id.w_bright_bar, 100, bright, false)
        rv.setTextViewText(R.id.w_bright_value,
            if (Brightness.isAuto(ctx)) ctx.getString(R.string.auto) else "$bright%")
        if (Brightness.canWrite(ctx)) {
            rv.setOnClickPendingIntent(R.id.w_bright_minus, action(ctx, ACTION_BRIGHT_DOWN, 30))
            rv.setOnClickPendingIntent(R.id.w_bright_plus, action(ctx, ACTION_BRIGHT_UP, 31))
        } else {
            val grant = PendingIntent.getActivity(ctx, 32, Brightness.permissionIntent(ctx), FLAGS)
            rv.setOnClickPendingIntent(R.id.w_bright_minus, grant)
            rv.setOnClickPendingIntent(R.id.w_bright_plus, grant)
        }
    }

    private fun action(ctx: Context, action: String, code: Int): PendingIntent =
        PendingIntent.getBroadcast(
            ctx, code, Intent(ctx, WidgetActionReceiver::class.java).setAction(action), FLAGS
        )
}

package x.vladgba.darker

import android.content.Context
import android.content.SharedPreferences
import android.os.Build

/** Single source of truth shared by the activity, the overlay service and the widget. */
object Prefs {
    const val KEY_ENABLED = "enabled"
    const val KEY_OPACITY = "opacity"
    private const val KEY_ASKED_NOTIF = "asked_notif"

    const val STEP = 10

    /**
     * Android 12+ blocks touches that pass through an app overlay whose opacity is above 0.8,
     * so that overlay is capped there. The accessibility overlay is trusted and goes to 95%.
     */
    val maxOpacity: Int get() =
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S || DarkerAccessibilityService.isActive) 95 else 80

    fun sp(ctx: Context): SharedPreferences =
        ctx.applicationContext.getSharedPreferences("darker", Context.MODE_PRIVATE)

    fun isEnabled(ctx: Context) = sp(ctx).getBoolean(KEY_ENABLED, false)
    fun setEnabled(ctx: Context, on: Boolean) = sp(ctx).edit().putBoolean(KEY_ENABLED, on).apply()

    fun opacity(ctx: Context) = sp(ctx).getInt(KEY_OPACITY, 50).coerceIn(0, maxOpacity)
    fun setOpacity(ctx: Context, value: Int) =
        sp(ctx).edit().putInt(KEY_OPACITY, value.coerceIn(0, maxOpacity)).apply()

    fun askedNotifications(ctx: Context) = sp(ctx).getBoolean(KEY_ASKED_NOTIF, false)
    fun markAskedNotifications(ctx: Context) = sp(ctx).edit().putBoolean(KEY_ASKED_NOTIF, true).apply()

    /** Moves [value] to the next multiple of [STEP] in the given direction. */
    fun snap(value: Int, up: Boolean, min: Int, max: Int): Int {
        val next = if (up) (value / STEP) * STEP + STEP else ((value + STEP - 1) / STEP) * STEP - STEP
        return next.coerceIn(min, max)
    }
}

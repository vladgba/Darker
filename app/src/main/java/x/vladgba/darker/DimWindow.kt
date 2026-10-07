package x.vladgba.darker

import android.content.Context
import android.graphics.Color
import android.graphics.PixelFormat
import android.os.Build
import android.util.DisplayMetrics
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import kotlin.math.max

/**
 * The black, non-touchable window that does the dimming.
 * Hosted either by [OverlayService] (TYPE_APPLICATION_OVERLAY) or by
 * [DarkerAccessibilityService] (TYPE_ACCESSIBILITY_OVERLAY, which also covers system bars).
 */
class DimWindow(ctx: Context, private val type: Int) {

    private val context = ctx
    private val wm = ctx.getSystemService(WindowManager::class.java)
    private var view: View? = null
    private var params: WindowManager.LayoutParams? = null

    val isShown: Boolean get() = view != null

    fun show(opacityPercent: Int): Boolean {
        if (view != null) {
            setOpacity(opacityPercent)
            return true
        }
        val side = coverSize()
        val p = WindowManager.LayoutParams(
            side, side, type,
            WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = 0
            y = 0
            alpha = opacityPercent / 100f
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                layoutInDisplayCutoutMode =
                    WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
                setFitInsetsTypes(0)
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                layoutInDisplayCutoutMode =
                    WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
            }
        }
        val v = View(context).apply { setBackgroundColor(Color.BLACK) }
        return try {
            wm.addView(v, p)
            view = v
            params = p
            true
        } catch (e: Exception) {
            false
        }
    }

    fun setOpacity(opacityPercent: Int) {
        val v = view ?: return
        val p = params ?: return
        p.alpha = opacityPercent / 100f
        runCatching { wm.updateViewLayout(v, p) }
    }

    fun hide() {
        view?.let { runCatching { wm.removeView(it) } }
        view = null
        params = null
    }

    /** A square larger than the screen's longest side, so it covers every rotation. */
    private fun coverSize(): Int {
        val (w, h) = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            wm.maximumWindowMetrics.bounds.let { it.width() to it.height() }
        } else {
            DisplayMetrics().also {
                @Suppress("DEPRECATION")
                wm.defaultDisplay.getRealMetrics(it)
            }.let { it.widthPixels to it.heightPixels }
        }
        return max(w, h) + 400
    }
}

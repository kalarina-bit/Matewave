package cc.skysparkle.matewave.settings

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager

/**
 * Vibration feedback through the Vibrator service directly, so it works even when
 * system touch feedback is disabled. Controlled by the in-app vibration switch.
 */
object Haptics {
    @Volatile private var appContext: Context? = null
    private var vibrator: Vibrator? = null

    fun init(context: Context) {
        val app = context.applicationContext
        appContext = app
        vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            (app.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager)?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            app.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        }
    }

    fun select() = play(VibrationEffect.EFFECT_TICK, fallbackMs = 8)

    fun move() = play(VibrationEffect.EFFECT_CLICK, fallbackMs = 15)

    fun capture() = play(VibrationEffect.EFFECT_HEAVY_CLICK, fallbackMs = 25)

    fun alert() = play(VibrationEffect.EFFECT_DOUBLE_CLICK, fallbackMs = 40)

    private fun play(predefined: Int, fallbackMs: Long) {
        val context = appContext ?: return
        val v = vibrator ?: return
        if (!v.hasVibrator()) return
        if (!FeedbackSettings(context).vibrationEnabled) return
        runCatching {
            val effect = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                VibrationEffect.createPredefined(predefined)
            } else {
                VibrationEffect.createOneShot(fallbackMs, VibrationEffect.DEFAULT_AMPLITUDE)
            }
            v.vibrate(effect)
        }
    }
}

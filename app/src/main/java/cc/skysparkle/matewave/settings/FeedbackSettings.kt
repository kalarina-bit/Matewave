package cc.skysparkle.matewave.settings

import android.content.Context

class FeedbackSettings(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    var vibrationEnabled: Boolean
        get() = prefs.getBoolean(KEY_VIBRATION, true)
        set(value) { prefs.edit().putBoolean(KEY_VIBRATION, value).apply() }

    private companion object {
        const val PREFS = "feedback_settings"
        const val KEY_VIBRATION = "vibration"
    }
}

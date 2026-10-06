package cc.skysparkle.matewave.presence

import android.content.Context

class PresenceSettings(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    var announceEnabled: Boolean
        get() = prefs.getBoolean(KEY_ANNOUNCE, true)
        set(value) { prefs.edit().putBoolean(KEY_ANNOUNCE, value).apply() }

    var backgroundEnabled: Boolean
        get() = prefs.getBoolean(KEY_BACKGROUND, false)
        set(value) { prefs.edit().putBoolean(KEY_BACKGROUND, value).apply() }

    private companion object {
        const val PREFS = "presence_settings"
        const val KEY_ANNOUNCE = "announce"
        const val KEY_BACKGROUND = "background"
    }
}

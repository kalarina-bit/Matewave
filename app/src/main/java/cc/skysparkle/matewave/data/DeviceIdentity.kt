package cc.skysparkle.matewave.data

import android.content.Context
import java.util.UUID

object DeviceIdentity {
    private const val PREFS = "device_identity"
    private const val KEY_DEVICE_ID = "deviceId"

    @Volatile private var cached: String? = null

    fun deviceId(context: Context): String {
        cached?.let { return it }
        synchronized(this) {
            cached?.let { return it }
            val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            val existing = prefs.getString(KEY_DEVICE_ID, null)
            val id = existing ?: UUID.randomUUID().toString().also {
                prefs.edit().putString(KEY_DEVICE_ID, it).apply()
            }
            cached = id
            return id
        }
    }
}

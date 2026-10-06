package cc.skysparkle.matewave.presence

import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob

/**
 * The one [LocalPresence] of the process. The app's screens and the background service share it,
 * so two discovery sessions never compete for the network service, and it runs while at least
 * one of them holds it.
 */
object PresenceHub {
    private val scope = CoroutineScope(SupervisorJob())

    @Volatile private var instance: LocalPresence? = null
    private var holders = 0

    fun get(context: Context): LocalPresence =
        instance ?: synchronized(this) {
            instance ?: LocalPresence(context.applicationContext, scope).also { instance = it }
        }

    fun acquire(context: Context) {
        val presence = get(context)
        val first = synchronized(this) { holders++ == 0 }
        if (first) presence.start()
    }

    fun release(context: Context) {
        val presence = get(context)
        val last = synchronized(this) {
            if (holders == 0) return
            --holders == 0
        }
        if (last) presence.stop()
    }
}

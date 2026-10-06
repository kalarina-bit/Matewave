package cc.skysparkle.matewave.network

import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob

/** Process-wide transport instances shared by presence and games. */
object Transports {
    private val scope = CoroutineScope(SupervisorJob())

    @Volatile private var ble: BleTransport? = null
    @Volatile private var lan: LanTransport? = null

    fun ble(context: Context): BleTransport =
        ble ?: synchronized(this) {
            ble ?: BleTransport(context.applicationContext, scope).also { ble = it }
        }

    fun lan(context: Context): LanTransport =
        lan ?: synchronized(this) {
            lan ?: LanTransport(context.applicationContext, scope).also { lan = it }
        }

    fun applyAnnounceSetting() {
        lan?.applyAnnounceSetting()
        ble?.applyAnnounceSetting()
    }

    fun releaseForGame() {
        ble?.stopDiscovery()
        lan?.stopDiscovery()
    }
}

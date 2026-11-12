package cc.skysparkle.matewave.network

import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

enum class DisconnectReason(val userText: String, val textRes: Int) {
    NONE("—", cc.skysparkle.matewave.R.string.reason_unknown),
    PERMISSION_DENIED("No nearby devices permission", cc.skysparkle.matewave.R.string.reason_permission_denied),
    BLUETOOTH_OFF("Bluetooth is off", cc.skysparkle.matewave.R.string.reason_bluetooth_off),
    PEER_NOT_FOUND("Opponent not found", cc.skysparkle.matewave.R.string.reason_peer_not_found),
    CONNECT_TIMEOUT("Opponent did not respond in time", cc.skysparkle.matewave.R.string.reason_connect_timeout),
    SERVICE_NOT_FOUND("The other device does not have this app", cc.skysparkle.matewave.R.string.reason_service_not_found),
    SUBSCRIBE_FAILED("Could not enable data reception", cc.skysparkle.matewave.R.string.reason_subscribe_failed),
    WRITE_FAILED("Could not send data", cc.skysparkle.matewave.R.string.reason_write_failed),
    MESSAGE_TOO_LARGE("Received message is too large", cc.skysparkle.matewave.R.string.reason_message_too_large),
    REMOTE_DISCONNECTED("Opponent disconnected", cc.skysparkle.matewave.R.string.reason_remote_disconnected),
    PORT_BUSY("Could not open port", cc.skysparkle.matewave.R.string.reason_port_busy)
}

object NetworkLog {
    private val _lastReason = MutableStateFlow(DisconnectReason.NONE)
    val lastReason: StateFlow<DisconnectReason> = _lastReason

    /** Debug output only: release builds strip Log.d (see proguard-rules.pro). */
    fun log(transport: String, text: String) {
        Log.d("Matewave/$transport", text)
    }

    fun reason(transport: String, reason: DisconnectReason, detail: String? = null) {
        _lastReason.value = reason
        log(transport, reason.userText + (detail?.let { ": $it" } ?: ""))
    }
}

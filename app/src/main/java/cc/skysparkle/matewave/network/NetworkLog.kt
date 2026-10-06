package cc.skysparkle.matewave.network

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update

enum class DisconnectReason(val userText: String, val textRes: Int) {
    NONE("—", cc.skysparkle.matewave.R.string.reason_unknown),
    PERMISSION_DENIED("No nearby devices permission", cc.skysparkle.matewave.R.string.reason_permission_denied),
    BLUETOOTH_OFF("Bluetooth is off", cc.skysparkle.matewave.R.string.reason_bluetooth_off),
    WIFI_UNAVAILABLE("Phone is not connected to Wi-Fi", cc.skysparkle.matewave.R.string.reason_wifi_unavailable),
    PEER_NOT_FOUND("Opponent not found", cc.skysparkle.matewave.R.string.reason_peer_not_found),
    CONNECT_TIMEOUT("Opponent did not respond in time", cc.skysparkle.matewave.R.string.reason_connect_timeout),
    SERVICE_NOT_FOUND("The other device does not have this app", cc.skysparkle.matewave.R.string.reason_service_not_found),
    SUBSCRIBE_FAILED("Could not enable data reception", cc.skysparkle.matewave.R.string.reason_subscribe_failed),
    WRITE_FAILED("Could not send data", cc.skysparkle.matewave.R.string.reason_write_failed),
    MESSAGE_TOO_LARGE("Received message is too large", cc.skysparkle.matewave.R.string.reason_message_too_large),
    REMOTE_DISCONNECTED("Opponent disconnected", cc.skysparkle.matewave.R.string.reason_remote_disconnected),
    PORT_BUSY("Could not open port", cc.skysparkle.matewave.R.string.reason_port_busy),
    UNKNOWN("Unknown connection error", cc.skysparkle.matewave.R.string.reason_unknown)
}

data class NetworkEvent(
    val at: Long,
    val transport: String,
    val text: String
)

/** In-memory connection log, copied into the diagnostics report from Settings. */
object NetworkLog {
    private const val CAPACITY = 60

    private val _events = MutableStateFlow<List<NetworkEvent>>(emptyList())

    private val _lastReason = MutableStateFlow(DisconnectReason.NONE)
    val lastReason: StateFlow<DisconnectReason> = _lastReason

    fun log(transport: String, text: String) {
        val event = NetworkEvent(System.currentTimeMillis(), transport, text)
        _events.update { (it + event).takeLast(CAPACITY) }
    }

    fun reason(transport: String, reason: DisconnectReason, detail: String? = null) {
        _lastReason.value = reason
        log(transport, reason.userText + (detail?.let { ": $it" } ?: ""))
    }

    fun asText(): String = _events.value.joinToString("\n") { e ->
        val time = java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.getDefault())
            .format(java.util.Date(e.at))
        "$time [${e.transport}] ${e.text}"
    }
}

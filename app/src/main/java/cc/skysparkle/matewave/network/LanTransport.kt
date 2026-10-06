package cc.skysparkle.matewave.network

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.net.wifi.WifiManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.io.BufferedReader
import java.io.IOException
import java.io.InputStreamReader
import java.io.OutputStream
import java.net.InetSocketAddress
import java.net.NetworkInterface
import java.net.ServerSocket
import java.net.Socket

data class LanPeer(
    val name: String,
    val host: String,
    val port: Int,
    val playerId: String = "",
    val displayName: String = "",
    val elo: Int = 0,
    val fingerprint: String = "",

    val signature: String = "",
    /** Raw NSD service name, used to remove the peer when the service is lost. */
    val serviceName: String = ""
) {
    val id: String get() = "$host:$port"
}

/**
 * LAN transport: plain TCP sockets carrying newline-delimited JSON, with the game
 * announced over NSD (mDNS). The listening socket stays open for discovery, but
 * incoming connections are accepted only while the user is hosting a game.
 */
class LanTransport(
    private val context: Context,
    private val scope: CoroutineScope
) : ConnectionTransport {
    companion object {
        // Wire identifiers from before the rename; changing them would split old and new installs.
        const val SERVICE_TYPE = "_chessapp._tcp."
        const val SERVICE_NAME = "ChessApp"

        const val CONNECT_TIMEOUT_MS = 8_000

        const val MAX_MESSAGE_CHARS = 1_400_000

        const val ACCEPT_POLL_MS = 1_000

        const val RESOLVE_RETRY_MS = 400L

        fun presencePayload(playerId: String, name: String, elo: Int): String =
            "presence|$playerId|$name|$elo"
    }

    override val type = TransportType.LAN

    private val _connectionState = MutableStateFlow(ConnectionState.DISCONNECTED)
    override val connectionState: StateFlow<ConnectionState> = _connectionState

    private val nsdManager: NsdManager? =
        context.getSystemService(Context.NSD_SERVICE) as? NsdManager

    private var serverSocket: ServerSocket? = null
    private var socket: Socket? = null

    private val socketLock = Any()

    private fun claimSocket(candidate: Socket): Boolean = synchronized(socketLock) {
        val existing = socket
        if (existing != null && !existing.isClosed) return false
        socket = candidate
        true
    }
    private var output: OutputStream? = null
    private val dispatcher = MessageDispatcher()

    @Volatile private var hosting = false

    private var registrationListener: NsdManager.RegistrationListener? = null

    private var registeredServiceName: String? = null

    private val ownServiceName: String by lazy {
        val fingerprint = cc.skysparkle.matewave.security.DeviceKeys.publicKeyFingerprint(context)
            ?: cc.skysparkle.matewave.data.DeviceIdentity.deviceId(context).take(12)
        "$SERVICE_NAME-$fingerprint"
    }

    fun fingerprintFromServiceName(serviceName: String): String? =
        serviceName.substringAfter("$SERVICE_NAME-", "").ifEmpty { null }
    private var discoveryListener: NsdManager.DiscoveryListener? = null

    @Volatile private var discoveryGeneration = 0

    private val resolveQueue = java.util.ArrayDeque<Pair<Int, NsdServiceInfo>>()
    private var resolving = false

    var lastError: String? = null
        private set

    private val outbox = Channel<String>(capacity = Channel.UNLIMITED)
    private var writerStarted = false

    override fun addMessageListener(listener: (GameMessage) -> Unit): () -> Unit =
        dispatcher.add(listener)

    private fun announceAllowed(): Boolean =
        hosting || cc.skysparkle.matewave.presence.PresenceSettings(context).announceEnabled

    fun applyAnnounceSetting() {
        val port = serverSocket?.localPort
        when {
            !announceAllowed() -> unregisterService()
            port != null -> registerService(port)
            else -> startPresence()
        }
    }

    fun republishProfile() {
        val port = serverSocket?.localPort ?: return
        unregisterService()
        if (!announceAllowed()) return
        scope.launch(Dispatchers.IO) {
            kotlinx.coroutines.delay(400)
            registerService(port)
        }
    }

    private val presenceLock = Any()
    private var presenceStarting = false

    fun startPresence() {
        synchronized(presenceLock) {
            if (serverSocket != null || presenceStarting) return
            presenceStarting = true
        }
        scope.launch(Dispatchers.IO) {
            try {
                val server = ServerSocket()
                server.reuseAddress = true

                server.bind(InetSocketAddress(0))

                server.soTimeout = ACCEPT_POLL_MS
                synchronized(presenceLock) { serverSocket = server; presenceStarting = false }
                if (announceAllowed()) registerService(server.localPort)
                acceptLoop(server)
            } catch (e: IOException) {
                synchronized(presenceLock) { presenceStarting = false }
                lastError = "Could not open port: ${e.message}"
                NetworkLog.reason("LAN", DisconnectReason.PORT_BUSY, e.message)
            }
        }
    }

    private suspend fun acceptLoop(server: ServerSocket) {
        while (!server.isClosed) {
            try {
                val client = server.accept()
                if (!hosting) {
                    try { client.close() } catch (_: IOException) {}
                    continue
                }

                if (socket != null && socket?.isClosed == false) {
                    try { client.close() } catch (_: IOException) {}
                    continue
                }
                client.tcpNoDelay = true
                client.keepAlive = true
                if (!claimSocket(client)) {
                    try { client.close() } catch (_: IOException) {}
                    continue
                }
                onConnected(client)
            } catch (e: java.net.SocketTimeoutException) {
                continue
            } catch (e: IOException) {
                if (server.isClosed) return
                NetworkLog.log("LAN", "Accept failed: ${e.message}")
                kotlinx.coroutines.delay(500)
            }
        }
    }

    override fun startHosting() {
        hosting = true
        _connectionState.value = ConnectionState.CONNECTING
        lastError = null

        val port = serverSocket?.localPort
        if (port != null) registerService(port) else startPresence()
    }

    private fun onHostingEnded() {
        if (!announceAllowed()) unregisterService()
    }

    private fun registerService(port: Int) {
        val nsd = nsdManager ?: return
        unregisterService()
        val info = NsdServiceInfo().apply {
            serviceName = ownServiceName
            serviceType = SERVICE_TYPE
            setPort(port)

            val profile = cc.skysparkle.matewave.data.ProfileStore.get()
            runCatching {
                val shownName = profile.name.take(40)
                setAttribute("id", profile.userId)
                setAttribute("name", shownName)
                setAttribute("elo", profile.elo.toString())
                setAttribute("fp", cc.skysparkle.matewave.security.DeviceKeys.publicKeyFingerprint(context) ?: "")

                cc.skysparkle.matewave.security.DeviceKeys.sign(
                    context, presencePayload(profile.userId, shownName, profile.elo)
                )?.let { setAttribute("sig", it) }
            }
        }
        val listener = object : NsdManager.RegistrationListener {
            override fun onServiceRegistered(info: NsdServiceInfo?) {
                registeredServiceName = info?.serviceName
            }
            override fun onRegistrationFailed(info: NsdServiceInfo?, errorCode: Int) {
                lastError = "Network service registration failed (code $errorCode). Connect by address manually."
            }
            override fun onServiceUnregistered(info: NsdServiceInfo?) {}
            override fun onUnregistrationFailed(info: NsdServiceInfo?, errorCode: Int) {}
        }
        registrationListener = listener
        try {
            nsd.registerService(info, NsdManager.PROTOCOL_DNS_SD, listener)
        } catch (e: Exception) {
            lastError = "Service registration: ${e.message}"
        }
    }

    private fun unregisterService() {
        val nsd = nsdManager ?: return
        registrationListener?.let {
            try { nsd.unregisterService(it) } catch (_: Exception) {}
        }
        registrationListener = null
        registeredServiceName = null
    }

    fun discoverPeers(onFound: (List<LanPeer>) -> Unit) {
        val nsd = nsdManager ?: return
        stopDiscovery()
        discoveryGeneration += 1
        val myGeneration = discoveryGeneration
        val found = LinkedHashMap<String, LanPeer>()
        synchronized(resolveQueue) { resolveQueue.clear(); resolving = false }

        val listener = object : NsdManager.DiscoveryListener {
            override fun onDiscoveryStarted(serviceType: String?) {}

            override fun onServiceFound(info: NsdServiceInfo) {
                if (myGeneration != discoveryGeneration) return
                if (info.serviceType?.contains("chessapp") != true) return

                if (isSelf(info.serviceName)) return

                synchronized(resolveQueue) { resolveQueue.addLast(myGeneration to info) }
                drainResolveQueue(nsd, found, onFound)
            }

            override fun onServiceLost(info: NsdServiceInfo?) {
                if (myGeneration != discoveryGeneration) return
                val name = info?.serviceName ?: return
                // Match the raw service name: LanPeer.name is a cleaned-up display form and never
                // matched, so peers who left stayed "online".
                val left = synchronized(found) { found.entries.removeAll { it.value.serviceName == name }; found.values.toList() }
                scope.launch(Dispatchers.Main) { onFound(left) }
            }

            override fun onDiscoveryStopped(serviceType: String?) {}
            override fun onStartDiscoveryFailed(serviceType: String?, errorCode: Int) {
                lastError = "Network discovery failed (code $errorCode). Enter the address manually."
                NetworkLog.log("LAN", "Network discovery failed to start, code $errorCode")
            }
            override fun onStopDiscoveryFailed(serviceType: String?, errorCode: Int) {}
        }
        discoveryListener = listener
        try {
            nsd.discoverServices(SERVICE_TYPE, NsdManager.PROTOCOL_DNS_SD, listener)
        } catch (e: Exception) {
            lastError = "Network discovery: ${e.message}"
            NetworkLog.log("LAN", "Network discovery: ${e.message}")
        }
    }

    @androidx.annotation.RequiresApi(34)
    private fun resolveModern(
        nsd: NsdManager,
        info: NsdServiceInfo,
        myGeneration: Int,
        found: LinkedHashMap<String, LanPeer>,
        onFound: (List<LanPeer>) -> Unit,
        finish: () -> Unit
    ) {
        val executor = java.util.concurrent.Executors.newSingleThreadExecutor()
        var done = false
        val callback = object : NsdManager.ServiceInfoCallback {
            override fun onServiceInfoCallbackRegistrationFailed(errorCode: Int) {
                NetworkLog.log("LAN", "Could not get peer address, code $errorCode")
                executor.shutdown(); finish()
            }

            override fun onServiceUpdated(updated: NsdServiceInfo) {
                if (done) return
                done = true

                if (myGeneration == discoveryGeneration) {
                    val host = hostAddressOf(updated)
                    if (host != null && !isSelfEndpoint(host, updated.port)) {
                        val peer = peerFrom(updated, host)
                        val now = synchronized(found) { found[peer.id] = peer; found.values.toList() }
                        scope.launch(Dispatchers.Main) { onFound(now) }
                    }
                }
                try { nsd.unregisterServiceInfoCallback(this) } catch (_: Exception) {}
            }

            override fun onServiceLost() {
                if (myGeneration != discoveryGeneration) return
                val left = synchronized(found) { found.entries.removeAll { it.value.serviceName == info.serviceName }; found.values.toList() }
                scope.launch(Dispatchers.Main) { onFound(left) }
            }

            override fun onServiceInfoCallbackUnregistered() {
                executor.shutdown(); finish()
            }
        }
        try {
            nsd.registerServiceInfoCallback(info, executor, callback)
        } catch (e: Exception) {
            NetworkLog.log("LAN", "Address resolution: ${e.message}")
            executor.shutdown(); finish()
        }
    }

    private fun peerFrom(info: NsdServiceInfo, host: String): LanPeer {
        fun attr(key: String): String = runCatching {
            info.attributes[key]?.toString(Charsets.UTF_8) ?: ""
        }.getOrDefault("")
        return LanPeer(
            name = displayNameOf(info.serviceName),
            host = host,
            port = info.port,
            playerId = attr("id"),
            displayName = attr("name"),
            elo = attr("elo").toIntOrNull() ?: 0,

            fingerprint = attr("fp").ifEmpty { fingerprintFromServiceName(info.serviceName ?: "") ?: "" },
            signature = attr("sig"),
            serviceName = info.serviceName ?: ""
        )
    }

    private fun displayNameOf(serviceName: String?): String {
        val raw = serviceName ?: return SERVICE_NAME
        return raw.substringBefore("-").ifBlank { SERVICE_NAME }
    }

    private fun isSelf(serviceName: String?): Boolean {
        if (serviceName == null) return false
        registeredServiceName?.let { if (serviceName == it) return true }

        if (serviceName == ownServiceName) return true

        return Regex("^${Regex.escape(ownServiceName)} \\(\\d+\\)$").matches(serviceName)
    }

    private fun isSelfEndpoint(host: String, port: Int): Boolean {
        val ourPort = serverSocket?.localPort ?: return false
        if (port != ourPort) return false

        val ourAddress = localIpAddress()
        return ourAddress == null || ourAddress == host
    }

    @Suppress("DEPRECATION")
    private fun hostAddressOf(info: NsdServiceInfo): String? {
        if (android.os.Build.VERSION.SDK_INT >= 34) {
            val ipv4 = info.hostAddresses.firstOrNull { it is java.net.Inet4Address }
            if (ipv4 != null) return ipv4.hostAddress
            return info.hostAddresses.firstOrNull()?.hostAddress
        }
        return info.host?.hostAddress
    }

    private fun drainResolveQueue(
        nsd: NsdManager,
        found: LinkedHashMap<String, LanPeer>,
        onFound: (List<LanPeer>) -> Unit
    ) {
        val nextGeneration: Int
        val next: NsdServiceInfo
        synchronized(resolveQueue) {
            if (resolving) return
            val entry = resolveQueue.pollFirst() ?: return
            nextGeneration = entry.first
            next = entry.second
            resolving = true
        }

        val finish = {
            synchronized(resolveQueue) { resolving = false }
            drainResolveQueue(nsd, found, onFound)
        }

        if (nextGeneration != discoveryGeneration) {
            finish()
            return
        }

        if (android.os.Build.VERSION.SDK_INT >= 34) {
            resolveModern(nsd, next, nextGeneration, found, onFound, finish)
            return
        }

        try {
            @Suppress("DEPRECATION")
            nsd.resolveService(next, object : NsdManager.ResolveListener {
                override fun onResolveFailed(info: NsdServiceInfo?, errorCode: Int) {
                    if (errorCode == NsdManager.FAILURE_ALREADY_ACTIVE) {
                        synchronized(resolveQueue) { resolveQueue.addLast(nextGeneration to next) }
                    } else {
                        NetworkLog.log("LAN", "Could not resolve peer address, code $errorCode")
                    }
                    scope.launch { kotlinx.coroutines.delay(RESOLVE_RETRY_MS); finish() }
                }

                override fun onServiceResolved(resolved: NsdServiceInfo) {
                    if (nextGeneration == discoveryGeneration) {
                        val host = hostAddressOf(resolved)
                        if (host != null && !isSelfEndpoint(host, resolved.port)) {
                            val peer = peerFrom(resolved, host)
                            val now = synchronized(found) { found[peer.id] = peer; found.values.toList() }
                            scope.launch(Dispatchers.Main) { onFound(now) }
                        }
                    }
                    finish()
                }
            })
        } catch (e: Exception) {
            NetworkLog.log("LAN", "Address resolution: ${e.message}")
            finish()
        }
    }

    fun stopDiscovery() {
        discoveryGeneration += 1
        val nsd = nsdManager ?: return
        discoveryListener?.let {
            try { nsd.stopServiceDiscovery(it) } catch (_: Exception) {}
        }
        discoveryListener = null
    }

    override fun startDiscoveryAndConnect(targetId: String?) {
        if (targetId.isNullOrBlank()) {
            _connectionState.value = ConnectionState.FAILED
            return
        }
        val host = targetId.substringBeforeLast(':', targetId).trim()

        val port = targetId.substringAfterLast(':', "").toIntOrNull() ?: run {
            lastError = "Address must look like 192.168.1.5:port"
            NetworkLog.reason("LAN", DisconnectReason.PEER_NOT_FOUND)
            _connectionState.value = ConnectionState.FAILED
            return
        }

        _connectionState.value = ConnectionState.CONNECTING
        lastError = null
        scope.launch(Dispatchers.IO) {
            try {
                val s = Socket()

                s.tcpNoDelay = true

                s.keepAlive = true
                s.connect(InetSocketAddress(host, port), CONNECT_TIMEOUT_MS)
                if (!claimSocket(s)) {
                    // Another connection won the race; report its state instead of hanging here.
                    try { s.close() } catch (_: IOException) {}
                    _connectionState.value =
                        if (socket?.isClosed == false) ConnectionState.CONNECTED else ConnectionState.FAILED
                    return@launch
                }
                onConnected(s)
            } catch (e: IOException) {
                lastError = "Could not connect to $host:$port: ${e.message}"
                NetworkLog.reason("LAN", DisconnectReason.CONNECT_TIMEOUT, "$host:$port")
                _connectionState.value = ConnectionState.FAILED
            }
        }
    }

    private fun onConnected(sock: Socket) {
        output = sock.getOutputStream()
        startWriterIfNeeded()
        _connectionState.value = ConnectionState.CONNECTED

        scope.launch(Dispatchers.IO) {
            try {
                val reader = BufferedReader(InputStreamReader(sock.getInputStream(), Charsets.UTF_8))
                while (true) {
                    val read = readBoundedLine(reader)
                    if (read is LineResult.EndOfStream) break
                    if (sock !== socket) break
                    if (read is LineResult.TooLong) {
                        NetworkLog.reason("LAN", DisconnectReason.MESSAGE_TOO_LARGE)
                        break
                    }
                    val line = (read as LineResult.Line).text
                    try {
                        dispatcher.dispatch(GameMessage.fromJson(line))
                    } catch (e: Exception) {
                        NetworkLog.log("LAN", "Malformed message skipped")
                    }
                }
            } catch (e: IOException) {
            } finally {
                releaseConnection(sock)
            }
        }
    }

    private fun releaseConnection(sock: Socket) {
        try { sock.close() } catch (_: IOException) {}
        val wasCurrent = synchronized(socketLock) {
            if (sock === socket) {
                socket = null
                output = null
                true
            } else {
                false
            }
        }
        if (wasCurrent) {
            hosting = false
            onHostingEnded()
            _connectionState.value = ConnectionState.DISCONNECTED
        }
    }

    private fun startWriterIfNeeded() {
        if (writerStarted) return
        writerStarted = true
        scope.launch(Dispatchers.IO) {
            for (message in outbox) {
                val currentSocket = socket
                val currentOutput = output
                if (currentSocket == null || currentOutput == null || currentSocket.isClosed) {
                    NetworkLog.log("LAN", "Message not sent: no connection")
                    continue
                }
                try {
                    currentOutput.write((message + "\n").toByteArray(Charsets.UTF_8))
                    currentOutput.flush()
                } catch (e: IOException) {
                    releaseConnection(currentSocket)
                }
            }
        }
    }

    private sealed class LineResult {
        data class Line(val text: String) : LineResult()
        object TooLong : LineResult()
        object EndOfStream : LineResult()
    }

    private fun readBoundedLine(reader: BufferedReader): LineResult {
        val builder = StringBuilder()
        while (true) {
            val ch = reader.read()
            if (ch == -1) {
                return if (builder.isEmpty()) LineResult.EndOfStream
                else LineResult.Line(builder.toString())
            }
            if (ch == '\n'.code) return LineResult.Line(builder.toString())
            if (ch == '\r'.code) continue
            if (builder.length >= MAX_MESSAGE_CHARS) return LineResult.TooLong
            builder.append(ch.toChar())
        }
    }

    override fun send(message: GameMessage) {
        val json = message.toJson()
        val size = json.length
        if (size > MAX_MESSAGE_CHARS) {
            NetworkLog.reason("LAN", DisconnectReason.MESSAGE_TOO_LARGE, "$size chars")
            return
        }
        outbox.trySend(json)
    }

    override fun disconnect() {
        val wasHosting = hosting
        hosting = false
        if (wasHosting) onHostingEnded()

        synchronized(socketLock) {
            try { socket?.close() } catch (_: IOException) {}
            socket = null
            output = null
        }

        var dropped = 0
        while (outbox.tryReceive().isSuccess) dropped++
        if (dropped > 0) {
            repeat(dropped) { NetworkMetrics.messageDropped() }
            NetworkLog.log("LAN", "Dropped unsent messages: $dropped")
        }
        _connectionState.value = ConnectionState.DISCONNECTED
    }

    fun shutdown() {
        stopDiscovery()
        unregisterService()
        disconnect()
        try { serverSocket?.close() } catch (_: IOException) {}
        synchronized(presenceLock) { serverSocket = null; presenceStarting = false }
    }

    private fun localIpAddress(): String? {
        try {
            val wifi = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
            @Suppress("DEPRECATION")
            val ip = wifi?.connectionInfo?.ipAddress ?: 0
            if (ip != 0) {
                return "${ip and 0xff}.${ip shr 8 and 0xff}.${ip shr 16 and 0xff}.${ip shr 24 and 0xff}"
            }
        } catch (_: Exception) { }

        try {
            for (nif in NetworkInterface.getNetworkInterfaces()) {
                if (!nif.isUp || nif.isLoopback) continue
                val name = nif.name.lowercase()
                if (name.startsWith("tun") || name.startsWith("ppp") || name.startsWith("rmnet")) continue
                for (addr in nif.inetAddresses) {
                    if (!addr.isLoopbackAddress && addr.hostAddress?.contains(':') == false) {
                        return addr.hostAddress
                    }
                }
            }
        } catch (_: Exception) { }
        return null
    }
}

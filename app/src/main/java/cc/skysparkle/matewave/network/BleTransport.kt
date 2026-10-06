package cc.skysparkle.matewave.network

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothGattServer
import android.bluetooth.BluetoothGattServerCallback
import android.bluetooth.BluetoothGattService
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.bluetooth.le.AdvertiseCallback
import android.bluetooth.le.AdvertiseData
import android.bluetooth.le.AdvertiseSettings
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.ParcelUuid
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.util.UUID

data class NearbyPlayer(
    val name: String,
    val address: String,

    val fingerprint: String? = null
)

/**
 * Bluetooth LE transport without pairing. The host runs a GATT server and advertises;
 * the guest scans and connects as a GATT client. Messages are split into MTU-sized
 * chunks with a CRC32 check and ACK/NACK retransmission. Large messages (images) use a
 * separate low-priority queue so moves are never stuck behind them.
 */
class BleTransport(
    private val context: Context,
    private val scope: CoroutineScope
) : ConnectionTransport {
    companion object {
        val SERVICE_UUID: UUID = UUID.fromString("6b1e7f10-2c3d-4a5b-8e9f-0a1b2c3d4e5f")

        val CHAR_WRITE_UUID: UUID = UUID.fromString("6b1e7f11-2c3d-4a5b-8e9f-0a1b2c3d4e5f")

        val CHAR_NOTIFY_UUID: UUID = UUID.fromString("6b1e7f12-2c3d-4a5b-8e9f-0a1b2c3d4e5f")

        val CCCD_UUID: UUID = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")

        private const val SAFE_CHUNK = 18
        private const val REQUESTED_MTU = 512

        private const val MTU_TIMEOUT_MS = 2_000L

        private const val CHUNK_HEADER_SIZE = 6
        private const val CRC_SIZE = 4

        private const val MAX_CHUNKS = 20_000

        private const val INCOMPLETE_TTL_MS = 30_000L

        private const val CTRL_TOTAL_MARKER = 0
        private const val CTRL_ACK = 1
        private const val CTRL_NACK = 2

        private const val ACK_TIMEOUT_MS = 1_500L

        private const val MAX_SEND_ATTEMPTS = 5

        private const val BULK_THRESHOLD_CHARS = 4_096

        private const val STALLED_PARTIAL_MS = 700L

        private const val SUBSCRIBE_TIMEOUT_MS = 8_000L

        private const val NEARBY_TTL_MS = 15_000L

        private const val NOTIFY_TIMEOUT_MS = 2_000L

        private const val FINGERPRINT_BYTES = 6

        private const val NAME_BYTES_LIMIT = 6

        const val MAX_MESSAGE_BYTES = 1_000_000

        fun fingerprintOf(publicKeyOrId: String): String =
            java.security.MessageDigest.getInstance("SHA-256")
                .digest(publicKeyOrId.toByteArray(Charsets.UTF_8))
                .take(FINGERPRINT_BYTES).joinToString("") { "%02x".format(it) }
    }

    override val type = TransportType.BLUETOOTH

    private val _connectionState = MutableStateFlow(ConnectionState.DISCONNECTED)
    override val connectionState: StateFlow<ConnectionState> = _connectionState

    private val manager = context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
    private val adapter: BluetoothAdapter? = manager?.adapter

    private val dispatcher = MessageDispatcher()
    var lastError: String? = null
        private set

    private enum class Role { NONE, PERIPHERAL, CENTRAL }
    private var role = Role.NONE

    private var gattServerActive = false
    private var advertisingActive = false

    private var shuttingDown = false

    private var gattServer: BluetoothGattServer? = null
    private var notifyCharacteristic: BluetoothGattCharacteristic? = null
    private var connectedCentral: BluetoothDevice? = null
    private var advertiseCallback: AdvertiseCallback? = null

    private var gattClient: BluetoothGatt? = null
    private var writeCharacteristic: BluetoothGattCharacteristic? = null
    private var scanCallback: ScanCallback? = null

    private var scanGeneration = 0
    private var discoveryCleanupJob: kotlinx.coroutines.Job? = null

    private val incoming = HashMap<Int, PartialMessage>()

    private val recentlyCompleted = HashMap<Int, Pair<Long, Long>>()

    @Volatile private var hosting = false

    private var subscribeWatchJob: kotlinx.coroutines.Job? = null

    private var outgoingMessageId = 0

    private var sendGeneration = 0
    private var partialWatcherStarted = false

    private val pendingAcks = HashMap<Int, kotlinx.coroutines.CompletableDeferred<Set<Int>>>()

    private var mtuSettled = false

    private var notificationsEnabled = false

    @Volatile private var notificationSent: kotlinx.coroutines.CompletableDeferred<Boolean>? = null

    @Volatile private var characteristicWritten: kotlinx.coroutines.CompletableDeferred<Boolean>? = null

    private var connectionGeneration = 0

    private fun markReady() {
        startWriterIfNeeded()
        _connectionState.value = ConnectionState.CONNECTED
    }

    private var chunkSize = SAFE_CHUNK

    private val outbox = Channel<String>(capacity = Channel.UNLIMITED)

    private val controlOutbox = Channel<ByteArray>(capacity = Channel.UNLIMITED)
    private var writerStarted = false

    private val bulkOutbox = Channel<String>(capacity = Channel.UNLIMITED)

    override fun addMessageListener(listener: (GameMessage) -> Unit): () -> Unit =
        dispatcher.add(listener)

    /** Turning Bluetooth off destroys the GATT server and any advertising: forget them. */
    private val adapterStateReceiver = object : BroadcastReceiver() {
        override fun onReceive(c: Context, intent: Intent) {
            val state = intent.getIntExtra(BluetoothAdapter.EXTRA_STATE, BluetoothAdapter.ERROR)
            if (state == BluetoothAdapter.STATE_TURNING_OFF || state == BluetoothAdapter.STATE_OFF) onBluetoothOff()
        }
    }

    init {
        runCatching {
            ContextCompat.registerReceiver(
                context, adapterStateReceiver,
                IntentFilter(BluetoothAdapter.ACTION_STATE_CHANGED),
                ContextCompat.RECEIVER_NOT_EXPORTED
            )
        }
    }

    @SuppressLint("MissingPermission")
    private fun onBluetoothOff() {
        if (!gattServerActive && gattClient == null && advertiseCallback == null) return
        stopDiscovery()
        advertiseCallback = null
        advertisingActive = false
        if (hosting || connectedCentral != null || gattClient != null) {
            NetworkLog.reason("BLE", DisconnectReason.BLUETOOTH_OFF)
            disconnect()
        }
        try { gattServer?.close() } catch (_: Exception) {}
        gattServer = null
        notifyCharacteristic = null
        gattServerActive = false
    }

    @SuppressLint("MissingPermission")
    fun startPresence() {
        shuttingDown = false
        val ad = adapter
        if (ad == null || !ad.isEnabled) {
            lastError = "Bluetooth is off"
            return
        }
        try {
            val serverWasReady = gattServerActive
            if (!gattServerActive) {
                if (!startGattServer()) {
                    lastError = "Could not start the Bluetooth service"
                    return
                }
                gattServerActive = true
            }

            if (serverWasReady && !advertisingActive) startAdvertising()
        } catch (e: SecurityException) {
            lastError = "No Bluetooth permission"
            NetworkLog.reason("BLE", DisconnectReason.PERMISSION_DENIED)
        }
    }

    @SuppressLint("MissingPermission")
    override fun startHosting() {
        val ad = adapter
        if (ad == null || !ad.isEnabled) {
            lastError = "Bluetooth is off"
            NetworkLog.reason("BLE", DisconnectReason.BLUETOOTH_OFF)
            _connectionState.value = ConnectionState.FAILED
            return
        }
        lastError = null
        role = Role.PERIPHERAL
        hosting = true

        startPresence()
        _connectionState.value = ConnectionState.CONNECTING
    }

    @SuppressLint("MissingPermission")
    private fun startGattServer(): Boolean {
        val server = manager?.openGattServer(context, serverCallback) ?: return false
        gattServer = server

        val service = BluetoothGattService(SERVICE_UUID, BluetoothGattService.SERVICE_TYPE_PRIMARY)

        val writeChar = BluetoothGattCharacteristic(
            CHAR_WRITE_UUID,
            BluetoothGattCharacteristic.PROPERTY_WRITE or BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE,
            BluetoothGattCharacteristic.PERMISSION_WRITE
        )
        val notifyChar = BluetoothGattCharacteristic(
            CHAR_NOTIFY_UUID,
            BluetoothGattCharacteristic.PROPERTY_NOTIFY,
            BluetoothGattCharacteristic.PERMISSION_READ
        ).apply {
            addDescriptor(
                BluetoothGattDescriptor(
                    CCCD_UUID,
                    BluetoothGattDescriptor.PERMISSION_READ or BluetoothGattDescriptor.PERMISSION_WRITE
                )
            )
        }
        service.addCharacteristic(writeChar)
        service.addCharacteristic(notifyChar)
        notifyCharacteristic = notifyChar

        server.addService(service)
        return true
    }

    private fun announceAllowed(): Boolean =
        hosting || cc.skysparkle.matewave.presence.PresenceSettings(context).announceEnabled

    /** Hiding takes effect at once; showing again waits until the nearby screen starts presence. */
    fun applyAnnounceSetting() {
        if (!announceAllowed() && (advertisingActive || advertiseCallback != null)) stopAdvertising()
    }

    /** Restarts advertising so nearby players see a changed name. */
    fun republishProfile() {
        if (advertisingActive || advertiseCallback != null) startAdvertising()
    }

    /**
     * Bluetooth presence runs only while the nearby screen is open: once it closes, advertising
     * stops unless a game is being hosted or played over Bluetooth.
     */
    fun stopPresenceIfIdle() {
        if (hosting || role != Role.NONE || connectedCentral != null || gattClient != null) return
        stopAdvertising()
    }

    @SuppressLint("MissingPermission")
    private fun startAdvertising() {
        if (!announceAllowed()) return
        val advertiser = adapter?.bluetoothLeAdvertiser
        if (advertiser == null) {
            lastError = "This device does not support Bluetooth advertising"
            if (hosting) _connectionState.value = ConnectionState.FAILED
            return
        }
        // One advertisement at a time: a second start would leak the first one.
        if (advertiseCallback != null) stopAdvertising()
        val settings = AdvertiseSettings.Builder()

            .setAdvertiseMode(AdvertiseSettings.ADVERTISE_MODE_LOW_LATENCY)
            .setTxPowerLevel(AdvertiseSettings.ADVERTISE_TX_POWER_HIGH)
            .setConnectable(true)
            .build()

        val data = AdvertiseData.Builder()
            .setIncludeDeviceName(false)
            .addServiceUuid(ParcelUuid(SERVICE_UUID))
            .build()

        val scanResponse = AdvertiseData.Builder()
            .setIncludeDeviceName(false)
            .addServiceData(ParcelUuid(SERVICE_UUID), buildIdentityPayload())
            .build()

        val cb = object : AdvertiseCallback() {
            override fun onStartSuccess(settingsInEffect: AdvertiseSettings?) {
                advertisingActive = true
            }

            override fun onStartFailure(errorCode: Int) {
                if (errorCode == AdvertiseCallback.ADVERTISE_FAILED_ALREADY_STARTED) {
                    advertisingActive = true
                    return
                }
                advertisingActive = false
                if (advertiseCallback === this) advertiseCallback = null
                lastError = "Advertising failed (code $errorCode)"
                NetworkLog.log("BLE", "Advertising failed, code $errorCode")
                // Without advertising nobody can find a hosted game; plain presence just goes quiet.
                if (hosting && connectedCentral == null) _connectionState.value = ConnectionState.FAILED
            }
        }
        advertiseCallback = cb
        try {
            advertiser.startAdvertising(settings, data, scanResponse, cb)
        } catch (e: Exception) {
            advertiseCallback = null
            lastError = "Advertising failed: ${e.message}"
            if (hosting) _connectionState.value = ConnectionState.FAILED
        }
    }

    private fun buildIdentityPayload(): ByteArray {
        val profile = cc.skysparkle.matewave.data.ProfileStore.get()

        val material = cc.skysparkle.matewave.security.DeviceKeys.publicKey(context)
            ?: profile.userId
        val digest = java.security.MessageDigest.getInstance("SHA-256")
            .digest(material.toByteArray(Charsets.UTF_8))

        var nameBytes = ByteArray(0)
        for (ch in profile.name) {
            val candidate = (String(nameBytes, Charsets.UTF_8) + ch).toByteArray(Charsets.UTF_8)
            if (candidate.size > NAME_BYTES_LIMIT) break
            nameBytes = candidate
        }
        return digest.copyOfRange(0, FINGERPRINT_BYTES) + nameBytes
    }

    private fun parseIdentityPayload(data: ByteArray?): Pair<String?, String?> {
        if (data == null || data.size < FINGERPRINT_BYTES) return null to null
        val fingerprint = data.take(FINGERPRINT_BYTES).joinToString("") { "%02x".format(it) }
        val name = if (data.size > FINGERPRINT_BYTES) {
            String(data, FINGERPRINT_BYTES, data.size - FINGERPRINT_BYTES, Charsets.UTF_8)
                .trim().ifBlank { null }
        } else null
        return fingerprint to name
    }

    private val serverCallback = object : BluetoothGattServerCallback() {
        @SuppressLint("MissingPermission")
        override fun onServiceAdded(status: Int, service: BluetoothGattService?) {
            if (status != BluetoothGatt.GATT_SUCCESS) {
                lastError = "Could not start the Bluetooth service (code $status)"
                NetworkLog.log("BLE", "Service not added, code $status")
                gattServerActive = false
                return
            }
            if (!advertisingActive && !shuttingDown) startAdvertising()
        }

        @SuppressLint("MissingPermission")
        override fun onConnectionStateChange(device: BluetoothDevice, status: Int, newState: Int) {
            if (newState == BluetoothProfile.STATE_CONNECTED) {
                if (!hosting) {
                    NetworkLog.log("BLE", "Incoming connection rejected: no game is being hosted")
                    try { gattServer?.cancelConnection(device) } catch (_: Exception) {}
                    return
                }

                if (role == Role.CENTRAL ||
                    (connectedCentral != null && connectedCentral?.address != device.address)
                ) {
                    NetworkLog.log("BLE", "Second connection rejected: channel is busy")
                    try { gattServer?.cancelConnection(device) } catch (_: Exception) {}
                    return
                }
                connectedCentral = device
                role = Role.PERIPHERAL
                // A new peer starts at the default MTU until it negotiates a larger one.
                chunkSize = SAFE_CHUNK
                startWriterIfNeeded()

                _connectionState.value = ConnectionState.CONNECTING
                stopAdvertising()
                advertisingActive = false

                subscribeWatchJob?.cancel()
                subscribeWatchJob = scope.launch {
                    kotlinx.coroutines.delay(SUBSCRIBE_TIMEOUT_MS)
                    if (!notificationsEnabled && connectedCentral?.address == device.address) {
                        NetworkLog.log("BLE", "Client did not enable notifications, disconnected")
                        dropPeripheralPeer(device)
                    }
                }
            } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                if (connectedCentral?.address != device.address) return
                resetPeripheralSession()
            }
        }

        @SuppressLint("MissingPermission")
        override fun onDescriptorWriteRequest(
            device: BluetoothDevice, requestId: Int, descriptor: BluetoothGattDescriptor,
            preparedWrite: Boolean, responseNeeded: Boolean, offset: Int, value: ByteArray
        ) {
            if (descriptor.uuid == CCCD_UUID) {
                @Suppress("DEPRECATION")
                descriptor.value = value
                if (responseNeeded) {
                    gattServer?.sendResponse(device, requestId, BluetoothGatt.GATT_SUCCESS, 0, null)
                }

                val enabled = value.contentEquals(
                    BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
                ) || value.contentEquals(
                    BluetoothGattDescriptor.ENABLE_INDICATION_VALUE
                )
                if (device.address != connectedCentral?.address) return
                notificationsEnabled = enabled
                if (enabled) {
                    subscribeWatchJob?.cancel()
                    subscribeWatchJob = null
                    _connectionState.value = ConnectionState.CONNECTED
                } else {
                    NetworkLog.log("BLE", "Opponent disabled notifications")
                    dropPeripheralPeer(device)
                }
                return
            }
            if (responseNeeded) {
                gattServer?.sendResponse(
                    device, requestId, BluetoothGatt.GATT_REQUEST_NOT_SUPPORTED, 0, null
                )
            }
        }

        @SuppressLint("MissingPermission")
        override fun onCharacteristicWriteRequest(
            device: BluetoothDevice, requestId: Int, characteristic: BluetoothGattCharacteristic,
            preparedWrite: Boolean, responseNeeded: Boolean, offset: Int, value: ByteArray
        ) {
            if (characteristic.uuid == CHAR_WRITE_UUID && device.address == connectedCentral?.address) {
                appendInbound(value)
            }
            if (responseNeeded) {
                gattServer?.sendResponse(device, requestId, BluetoothGatt.GATT_SUCCESS, 0, null)
            }
        }

        override fun onNotificationSent(device: BluetoothDevice, status: Int) {
            if (connectedCentral?.address != device.address) return
            notificationSent?.complete(status == BluetoothGatt.GATT_SUCCESS)
        }

        override fun onMtuChanged(device: BluetoothDevice, mtu: Int) {
            chunkSize = (mtu - 3).coerceAtLeast(SAFE_CHUNK)
        }
    }

    @SuppressLint("MissingPermission")
    private fun dropPeripheralPeer(device: BluetoothDevice?) {
        try { device?.let { gattServer?.cancelConnection(it) } } catch (_: Exception) {}
        resetPeripheralSession()
    }

    private fun resetPeripheralSession() {
        subscribeWatchJob?.cancel()
        subscribeWatchJob = null
        connectedCentral = null
        notificationsEnabled = false
        hosting = false
        if (role == Role.PERIPHERAL) role = Role.NONE
        sendGeneration += 1
        synchronized(pendingAcks) {
            pendingAcks.values.forEach { it.complete(emptySet()) }
            pendingAcks.clear()
        }
        synchronized(nackAccumulator) { nackAccumulator.clear() }
        synchronized(incoming) { incoming.clear() }
        synchronized(recentlyCompleted) { recentlyCompleted.clear() }
        while (outbox.tryReceive().isSuccess) {  }
        while (bulkOutbox.tryReceive().isSuccess) {  }
        while (controlOutbox.tryReceive().isSuccess) {  }
        _connectionState.value = ConnectionState.DISCONNECTED

        // The game is over: stop announcing it. The nearby screen starts presence again when opened.
        stopAdvertising()
    }

    @SuppressLint("MissingPermission")
    fun discoverNearby(onFound: (List<NearbyPlayer>) -> Unit) {
        val scanner = adapter?.bluetoothLeScanner ?: return
        stopDiscovery()
        scanGeneration += 1
        val myGeneration = scanGeneration
        val found = LinkedHashMap<String, NearbyPlayer>()

        val lastSeen = HashMap<String, Long>()

        val filter = ScanFilter.Builder().setServiceUuid(ParcelUuid(SERVICE_UUID)).build()
        val settings = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)

            .setCallbackType(ScanSettings.CALLBACK_TYPE_ALL_MATCHES)
            .setMatchMode(ScanSettings.MATCH_MODE_AGGRESSIVE)
            .build()

        val cb = object : ScanCallback() {
            override fun onScanResult(callbackType: Int, result: ScanResult) {
                if (myGeneration != scanGeneration) return
                val device = result.device ?: return

                val payload = result.scanRecord?.getServiceData(ParcelUuid(SERVICE_UUID))
                val (fingerprint, advertisedName) = parseIdentityPayload(payload)

                val bondedName = try { device.name } catch (e: SecurityException) { null }
                val player = NearbyPlayer(
                    name = advertisedName ?: bondedName
                        ?: context.getString(cc.skysparkle.matewave.R.string.nearby_player_default),
                    address = device.address,
                    fingerprint = fingerprint
                )
                val now = System.currentTimeMillis()
                val previous = found.put(player.address, player)
                lastSeen[player.address] = now

                val expired = lastSeen.filterValues { now - it > NEARBY_TTL_MS }.keys
                expired.forEach { found.remove(it); lastSeen.remove(it) }

                if (previous == null || previous.name != player.name || expired.isNotEmpty()) {
                    val snapshot = found.values.toList()
                    scope.launch(Dispatchers.Main) { onFound(snapshot) }
                }
            }

            override fun onScanFailed(errorCode: Int) {
                if (myGeneration != scanGeneration) return
                lastError = "Nearby scan failed to start (code $errorCode)"
            }
        }
        scanCallback = cb
        try {
            scanner.startScan(listOf(filter), settings, cb)
        } catch (e: SecurityException) {
            lastError = "No permission to scan for devices"
        }

        // Scan results arrive on the main thread, so the expiry check runs there too.
        discoveryCleanupJob = scope.launch(Dispatchers.Main) {
            while (true) {
                kotlinx.coroutines.delay(NEARBY_TTL_MS / 3)
                if (myGeneration != scanGeneration) return@launch
                val now = System.currentTimeMillis()
                val expired = lastSeen.filterValues { now - it > NEARBY_TTL_MS }.keys.toList()
                if (expired.isNotEmpty()) {
                    expired.forEach { found.remove(it); lastSeen.remove(it) }
                    onFound(found.values.toList())
                }
            }
        }
    }

    @SuppressLint("MissingPermission")
    fun stopDiscovery() {
        scanGeneration += 1
        scanCallback?.let {
            try { adapter?.bluetoothLeScanner?.stopScan(it) } catch (_: Exception) {}
        }
        scanCallback = null
        discoveryCleanupJob?.cancel()
        discoveryCleanupJob = null
    }

    @SuppressLint("MissingPermission")
    override fun startDiscoveryAndConnect(targetId: String?) {
        val ad = adapter
        if (ad == null || !ad.isEnabled || targetId.isNullOrBlank()) {
            lastError = "Bluetooth is off or no opponent selected"
            _connectionState.value = ConnectionState.FAILED
            return
        }

        if (role == Role.PERIPHERAL && connectedCentral != null) {
            lastError = "A connection is already active, close it first"
            _connectionState.value = ConnectionState.FAILED
            return
        }
        _connectionState.value = ConnectionState.CONNECTING
        lastError = null
        stopDiscovery()
        // A previous attempt must release its client: Android allows only a few at a time.
        gattClient?.let { old ->
            try { old.disconnect(); old.close() } catch (_: Exception) {}
        }
        gattClient = null
        try {
            val device = ad.getRemoteDevice(targetId)
            role = Role.CENTRAL
            connectionGeneration += 1
            val myGeneration = connectionGeneration
            chunkSize = SAFE_CHUNK

            val gatt = device.connectGatt(
                context, false, clientCallback(myGeneration), BluetoothDevice.TRANSPORT_LE
            )
            gattClient = gatt
        } catch (e: Exception) {
            lastError = "Connection failed: ${e.message}"
            _connectionState.value = ConnectionState.FAILED
        }
    }

    private fun clientCallback(generation: Int) = object : BluetoothGattCallback() {
        private fun isCurrent() = generation == connectionGeneration

        @SuppressLint("MissingPermission")
        override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
            if (!isCurrent()) {
                try { gatt.close() } catch (_: Exception) {}
                return
            }
            when (newState) {
                BluetoothProfile.STATE_CONNECTED -> {
                    mtuSettled = false
                    gatt.requestMtu(REQUESTED_MTU)
                    scope.launch {
                        kotlinx.coroutines.delay(MTU_TIMEOUT_MS)

                        if (generation != connectionGeneration) return@launch
                        if (!mtuSettled) {
                            mtuSettled = true
                            try { gatt.discoverServices() } catch (_: SecurityException) {}
                        }
                    }
                }
                BluetoothProfile.STATE_DISCONNECTED -> {
                    _connectionState.value = ConnectionState.DISCONNECTED

                    closeClient()
                }
            }
        }

        @SuppressLint("MissingPermission")
        override fun onMtuChanged(gatt: BluetoothGatt, mtu: Int, status: Int) {
            if (!isCurrent()) return
            if (mtuSettled) return
            mtuSettled = true

            if (status == BluetoothGatt.GATT_SUCCESS) {
                chunkSize = (mtu - 3).coerceAtLeast(SAFE_CHUNK)
            }
            gatt.discoverServices()
        }

        @SuppressLint("MissingPermission")
        override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
            if (!isCurrent()) return
            val service = if (status == BluetoothGatt.GATT_SUCCESS) gatt.getService(SERVICE_UUID) else null
            if (service == null) {
                lastError = "Peer does not expose this app's service"
                NetworkLog.reason("BLE", DisconnectReason.SERVICE_NOT_FOUND, "status $status")
                _connectionState.value = ConnectionState.FAILED
                closeClient()
                return
            }
            writeCharacteristic = service.getCharacteristic(CHAR_WRITE_UUID)
            if (writeCharacteristic == null) {
                lastError = "Peer does not support move exchange"
                NetworkLog.reason("BLE", DisconnectReason.SERVICE_NOT_FOUND, "no write characteristic")
                _connectionState.value = ConnectionState.FAILED
                closeClient()
                return
            }

            val notifyChar = service.getCharacteristic(CHAR_NOTIFY_UUID)
            if (notifyChar != null) {
                gatt.setCharacteristicNotification(notifyChar, true)
                val cccd = notifyChar.getDescriptor(CCCD_UUID)
                if (cccd != null) {
                    @Suppress("DEPRECATION")
                    cccd.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
                    @Suppress("DEPRECATION")
                    gatt.writeDescriptor(cccd)
                }
            }

            if (notifyChar == null || notifyChar.getDescriptor(CCCD_UUID) == null) {
                lastError = "Peer does not support the Bluetooth return channel"
                NetworkLog.reason("BLE", DisconnectReason.SERVICE_NOT_FOUND, "no notify characteristic")
                _connectionState.value = ConnectionState.FAILED
                closeClient()
                return
            }

            scope.launch {
                kotlinx.coroutines.delay(SUBSCRIBE_TIMEOUT_MS)
                if (!isCurrent()) return@launch
                if (_connectionState.value != ConnectionState.CONNECTED) {
                    lastError = "Peer did not confirm readiness"
                    NetworkLog.reason("BLE", DisconnectReason.SUBSCRIBE_FAILED)
                    _connectionState.value = ConnectionState.FAILED
                    closeClient()
                }
            }
        }

        override fun onCharacteristicWrite(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            status: Int
        ) {
            if (!isCurrent()) return
            characteristicWritten?.complete(status == BluetoothGatt.GATT_SUCCESS)
        }

        override fun onDescriptorWrite(gatt: BluetoothGatt, descriptor: BluetoothGattDescriptor, status: Int) {
            if (!isCurrent()) return
            if (descriptor.uuid != CCCD_UUID) return
            if (status == BluetoothGatt.GATT_SUCCESS) {
                markReady()
            } else {
                lastError = "Could not enable Bluetooth notifications (code $status)"
                NetworkLog.reason("BLE", DisconnectReason.SUBSCRIBE_FAILED, "code $status")
                _connectionState.value = ConnectionState.FAILED
                closeClient()
            }
        }

        override fun onCharacteristicChanged(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            value: ByteArray
        ) {
            if (characteristic.uuid == CHAR_NOTIFY_UUID) {
                appendInbound(value)
            }
        }

        @Deprecated("Used only below Android 13; newer versions call the overload with value")
        @Suppress("DEPRECATION")
        override fun onCharacteristicChanged(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic) {
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) return
            if (characteristic.uuid == CHAR_NOTIFY_UUID) {
                appendInbound(characteristic.value ?: return)
            }
        }
    }

    private fun appendInbound(chunk: ByteArray) {
        if (chunk.size < CHUNK_HEADER_SIZE) return
        val messageId = ((chunk[0].toInt() and 0xFF) shl 8) or (chunk[1].toInt() and 0xFF)
        val index = ((chunk[2].toInt() and 0xFF) shl 8) or (chunk[3].toInt() and 0xFF)
        val total = ((chunk[4].toInt() and 0xFF) shl 8) or (chunk[5].toInt() and 0xFF)

        if (total == CTRL_TOTAL_MARKER) {
            handleControlFrame(messageId, kind = index, body = chunk.copyOfRange(CHUNK_HEADER_SIZE, chunk.size))
            return
        }

        if (total <= 0 || total > MAX_CHUNKS || index >= total) return

        val payload = chunk.copyOfRange(CHUNK_HEADER_SIZE, chunk.size)
        var assembled: ByteArray? = null

        synchronized(incoming) {
            val now = System.currentTimeMillis()
            incoming.entries.removeAll { now - it.value.startedAt > INCOMPLETE_TTL_MS }

            val entry = incoming.getOrPut(messageId) { PartialMessage(total, now) }
            if (entry.total != total) {
                incoming[messageId] = PartialMessage(total, now).also { it.parts[index] = payload }
                return
            }
            if (entry.receivedBytes + payload.size > MAX_MESSAGE_BYTES) {
                incoming.remove(messageId)
                NetworkLog.reason("BLE", DisconnectReason.MESSAGE_TOO_LARGE)
                return
            }

            val previous = entry.parts.put(index, payload)
            if (previous == null) entry.receivedBytes += payload.size else NetworkMetrics.duplicateChunk()
            entry.lastChunkAt = System.currentTimeMillis()
            if (entry.parts.size == total) {
                assembled = ByteArray(entry.receivedBytes).also { buf ->
                    var pos = 0
                    for (i in 0 until total) {
                        val part = entry.parts[i] ?: return@also
                        System.arraycopy(part, 0, buf, pos, part.size)
                        pos += part.size
                    }
                }
                incoming.remove(messageId)
            }
        }

        val full = assembled ?: return
        if (full.size <= CRC_SIZE) return
        val body = full.copyOfRange(0, full.size - CRC_SIZE)
        val expected = java.util.zip.CRC32().apply { update(body) }.value
        val actual = ((full[full.size - 4].toLong() and 0xFF) shl 24) or
            ((full[full.size - 3].toLong() and 0xFF) shl 16) or
            ((full[full.size - 2].toLong() and 0xFF) shl 8) or
            (full[full.size - 1].toLong() and 0xFF)
        if (expected != actual) {
            NetworkMetrics.messageCorrupted()
            NetworkLog.log("BLE", "Corrupted message, retransmission requested")
            sendControlFrame(messageId, CTRL_NACK, (0 until total).toList())
            return
        }

        sendControlFrame(messageId, CTRL_ACK, emptyList())

        val now = System.currentTimeMillis()
        val isDuplicate = synchronized(recentlyCompleted) {
            recentlyCompleted.entries.removeAll { now - it.value.second > INCOMPLETE_TTL_MS }
            val previous = recentlyCompleted[messageId]
            if (previous != null && previous.first == expected) {
                true
            } else {
                recentlyCompleted[messageId] = expected to now
                false
            }
        }
        if (isDuplicate) {
            NetworkLog.log("BLE", "Duplicate of already received message #$messageId skipped")
            return
        }

        val line = String(body, Charsets.UTF_8)
        if (line.isBlank()) return
        try {
            dispatcher.dispatch(GameMessage.fromJson(line))
        } catch (_: Exception) {
            NetworkLog.log("BLE", "Malformed message skipped")
        }
    }

    private val nackAccumulator = HashMap<Int, MutableSet<Int>>()

    private fun handleControlFrame(messageId: Int, kind: Int, body: ByteArray) {
        when (kind) {
            CTRL_ACK -> {
                val waiting = synchronized(pendingAcks) { pendingAcks[messageId] }
                synchronized(nackAccumulator) { nackAccumulator.remove(messageId) }
                waiting?.complete(emptySet())
            }
            CTRL_NACK -> {
                if (body.isEmpty()) return

                val more = body[0].toInt() != 0
                val indices = HashSet<Int>()
                var i = 1
                while (i + 1 < body.size) {
                    indices.add(((body[i].toInt() and 0xFF) shl 8) or (body[i + 1].toInt() and 0xFF))
                    i += 2
                }
                val complete: Set<Int>? = synchronized(nackAccumulator) {
                    val acc = nackAccumulator.getOrPut(messageId) { HashSet() }
                    acc.addAll(indices)
                    if (more) {
                        null
                    } else {
                        nackAccumulator.remove(messageId)
                        acc
                    }
                }
                if (complete != null) {
                    val waiting = synchronized(pendingAcks) { pendingAcks[messageId] }
                    waiting?.complete(complete)
                }
            }
        }
    }

    @SuppressLint("MissingPermission")
    private fun sendControlFrame(messageId: Int, kind: Int, indices: List<Int>) {
        val room = ((chunkSize - CHUNK_HEADER_SIZE - 1) / 2).coerceAtLeast(1)
        val batches: List<List<Int>> =
            if (indices.isEmpty()) listOf(emptyList()) else indices.chunked(room)
        batches.forEachIndexed { batchIndex, batch ->
            val more = batchIndex < batches.size - 1
            val head = byteArrayOf(
                (messageId shr 8).toByte(), messageId.toByte(),
                (kind shr 8).toByte(), kind.toByte(),
                0, 0
            )
            val body = ByteArray(1 + batch.size * 2)
            body[0] = if (more) 1 else 0
            batch.forEachIndexed { n, value ->
                body[1 + n * 2] = (value shr 8).toByte()
                body[1 + n * 2 + 1] = value.toByte()
            }

            controlOutbox.trySend(head + body)
        }
    }

    private class PartialMessage(val total: Int, val startedAt: Long) {
        val parts = HashMap<Int, ByteArray>()
        var receivedBytes = 0

        var lastChunkAt: Long = startedAt
    }

    private fun startPartialWatcher() {
        if (partialWatcherStarted) return
        partialWatcherStarted = true
        scope.launch {
            while (true) {
                kotlinx.coroutines.delay(STALLED_PARTIAL_MS)
                val requests = ArrayList<Pair<Int, List<Int>>>()
                synchronized(incoming) {
                    val now = System.currentTimeMillis()
                    for ((messageId, entry) in incoming) {
                        if (now - entry.lastChunkAt < STALLED_PARTIAL_MS) continue
                        val missing = (0 until entry.total).filter { it !in entry.parts.keys }
                        if (missing.isNotEmpty()) requests.add(messageId to missing)
                        entry.lastChunkAt = now
                    }
                }
                for ((messageId, missing) in requests) {
                    sendControlFrame(messageId, CTRL_NACK, missing)
                }
            }
        }
    }

    private fun startWriterIfNeeded() {
        if (writerStarted) return
        writerStarted = true
        startPartialWatcher()
        scope.launch(Dispatchers.IO) {
            while (true) {
                var handled = false
                while (true) {
                    val control = controlOutbox.tryReceive().getOrNull() ?: break
                    writeRaw(control)
                    handled = true
                }
                val payload = outbox.tryReceive().getOrNull()
                if (payload != null) {
                    sendChunked(payload, bulk = false)
                    handled = true
                } else {
                    val bulkPayload = bulkOutbox.tryReceive().getOrNull()
                    if (bulkPayload != null) {
                        sendChunked(bulkPayload, bulk = true)
                        handled = true
                    }
                }
                if (!handled) {
                    kotlinx.coroutines.selects.select<Unit> {
                        controlOutbox.onReceive { writeRaw(it) }
                        outbox.onReceive { sendChunked(it, bulk = false) }
                        bulkOutbox.onReceive { sendChunked(it, bulk = true) }
                    }
                }
            }
        }
    }

    @SuppressLint("MissingPermission")
    private suspend fun sendChunked(payload: String, bulk: Boolean) {
        val body = payload.toByteArray(Charsets.UTF_8)
        val crc = java.util.zip.CRC32().apply { update(body) }.value
        val framed = body + byteArrayOf(
            (crc shr 24).toByte(), (crc shr 16).toByte(), (crc shr 8).toByte(), crc.toByte()
        )

        val usable = (chunkSize - CHUNK_HEADER_SIZE).coerceAtLeast(1)
        val total = (framed.size + usable - 1) / usable
        if (total > MAX_CHUNKS) {
            NetworkLog.reason("BLE", DisconnectReason.MESSAGE_TOO_LARGE, "parts: $total")
            return
        }
        val messageId = nextMessageId()
        val myGeneration = sendGeneration

        val chunks = Array(total) { index ->
            val from = index * usable
            val to = minOf(from + usable, framed.size)
            byteArrayOf(
                (messageId shr 8).toByte(), messageId.toByte(),
                (index shr 8).toByte(), index.toByte(),
                (total shr 8).toByte(), total.toByte()
            ) + framed.copyOfRange(from, to)
        }

        var toSend: List<Int> = (0 until total).toList()

        repeat(MAX_SEND_ATTEMPTS) {
            val waiter = kotlinx.coroutines.CompletableDeferred<Set<Int>>()
            synchronized(pendingAcks) { pendingAcks[messageId] = waiter }

            for (index in toSend) {
                if (myGeneration != sendGeneration) {
                    synchronized(pendingAcks) { pendingAcks.remove(messageId) }
                    return
                }
                NetworkMetrics.chunkSent()
                if (!writeRaw(chunks[index])) {
                    synchronized(pendingAcks) { pendingAcks.remove(messageId) }
                    NetworkLog.reason("BLE", DisconnectReason.WRITE_FAILED)
                    failDelivery(bulk)
                    return
                }
                if (bulk) {
                    while (true) {
                        val control = controlOutbox.tryReceive().getOrNull() ?: break
                        writeRaw(control)
                    }
                    while (true) {
                        val urgent = outbox.tryReceive().getOrNull() ?: break
                        sendChunked(urgent, bulk = false)
                        if (myGeneration != sendGeneration) {
                            synchronized(pendingAcks) { pendingAcks.remove(messageId) }
                            return
                        }
                    }
                }
            }

            val answer = kotlinx.coroutines.withTimeoutOrNull(ACK_TIMEOUT_MS) {
                while (!waiter.isCompleted) {
                    val control = controlOutbox.tryReceive().getOrNull()
                    if (control != null) writeRaw(control) else kotlinx.coroutines.delay(20)
                }
                waiter.await()
            }
            synchronized(pendingAcks) { pendingAcks.remove(messageId) }
            if (myGeneration != sendGeneration) return

            when {
                answer != null && answer.isEmpty() -> return

                answer != null -> {
                    toSend = answer.filter { it in 0 until total }
                    NetworkMetrics.chunkRetried(toSend.size)
                }

                else -> {
                    toSend = (0 until total).toList()
                    NetworkMetrics.chunkRetried(total)
                }
            }
            if (toSend.isEmpty()) return
        }

        NetworkLog.log("BLE", "Message not delivered after $MAX_SEND_ATTEMPTS attempts")
        failDelivery(bulk)
    }

    private fun failDelivery(bulk: Boolean) {
        if (bulk) return
        NetworkLog.reason("BLE", DisconnectReason.WRITE_FAILED, "game message not delivered, disconnecting")
        disconnect()
    }

    @SuppressLint("MissingPermission")
    private fun closeClient() {
        connectionGeneration += 1
        sendGeneration += 1
        try { gattClient?.disconnect() } catch (_: Exception) {}
        try { gattClient?.close() } catch (_: Exception) {}
        gattClient = null
        writeCharacteristic = null
        mtuSettled = false

        if (role == Role.CENTRAL) role = Role.NONE
        synchronized(pendingAcks) {
            pendingAcks.values.forEach { it.complete(emptySet()) }
            pendingAcks.clear()
        }
        synchronized(nackAccumulator) { nackAccumulator.clear() }
        synchronized(incoming) { incoming.clear() }
        synchronized(recentlyCompleted) { recentlyCompleted.clear() }
    }

    @SuppressLint("MissingPermission")
    private suspend fun writeRaw(chunk: ByteArray): Boolean {
        return try {
            val central = connectedCentral
            if (role == Role.PERIPHERAL && central != null) {
                val ch = notifyCharacteristic ?: return false
                @Suppress("DEPRECATION")
                ch.value = chunk
                val waiter = kotlinx.coroutines.CompletableDeferred<Boolean>()
                notificationSent = waiter
                @Suppress("DEPRECATION")
                val queued = gattServer?.notifyCharacteristicChanged(central, ch, false) ?: false
                if (!queued) {
                    notificationSent = null
                    return false
                }

                val sent = kotlinx.coroutines.withTimeoutOrNull(NOTIFY_TIMEOUT_MS) { waiter.await() }
                notificationSent = null
                sent == true
            } else {
                val ch = writeCharacteristic ?: return false
                @Suppress("DEPRECATION")
                ch.value = chunk

                ch.writeType = BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
                val waiter = kotlinx.coroutines.CompletableDeferred<Boolean>()
                characteristicWritten = waiter
                @Suppress("DEPRECATION")
                val queued = gattClient?.writeCharacteristic(ch) ?: false
                if (!queued) {
                    characteristicWritten = null
                    return false
                }
                val done = kotlinx.coroutines.withTimeoutOrNull(NOTIFY_TIMEOUT_MS) { waiter.await() }
                characteristicWritten = null
                done == true
            }
        } catch (e: SecurityException) {
            false
        }
    }

    private fun nextMessageId(): Int {
        outgoingMessageId = (outgoingMessageId + 1) and 0xFFFF
        return outgoingMessageId
    }

    private fun currentMaxMessageBytes(): Int {
        val usable = (chunkSize - CHUNK_HEADER_SIZE).coerceAtLeast(1)
        return minOf(MAX_MESSAGE_BYTES.toLong(), usable.toLong() * MAX_CHUNKS).toInt()
    }

    override fun send(message: GameMessage) {
        val json = message.toJson()

        if (json.toByteArray(Charsets.UTF_8).size > currentMaxMessageBytes()) {
            NetworkLog.reason(
                "BLE", DisconnectReason.MESSAGE_TOO_LARGE,
                "${json.length} chars, current limit ${currentMaxMessageBytes()}"
            )
            return
        }
        if (json.length > BULK_THRESHOLD_CHARS) bulkOutbox.trySend(json) else outbox.trySend(json)
    }

    @SuppressLint("MissingPermission")
    private fun stopAdvertising() {
        advertiseCallback?.let {
            try { adapter?.bluetoothLeAdvertiser?.stopAdvertising(it) } catch (_: Exception) {}
        }
        advertiseCallback = null
        advertisingActive = false
    }

    @SuppressLint("MissingPermission")
    override fun disconnect() {
        hosting = false
        stopAdvertising()
        subscribeWatchJob?.cancel()
        subscribeWatchJob = null
        try { gattClient?.disconnect(); gattClient?.close() } catch (_: Exception) {}
        gattClient = null

        connectedCentral?.let { central ->
            try { gattServer?.cancelConnection(central) } catch (_: Exception) {}
        }
        connectedCentral = null
        notificationsEnabled = false
        writeCharacteristic = null
        role = Role.NONE

        sendGeneration += 1
        synchronized(pendingAcks) {
            pendingAcks.values.forEach { it.complete(emptySet()) }
            pendingAcks.clear()
        }
        synchronized(nackAccumulator) { nackAccumulator.clear() }
        synchronized(incoming) { incoming.clear() }
        synchronized(recentlyCompleted) { recentlyCompleted.clear() }

        while (outbox.tryReceive().isSuccess) {  }
        while (bulkOutbox.tryReceive().isSuccess) {  }

        while (controlOutbox.tryReceive().isSuccess) {  }
        _connectionState.value = ConnectionState.DISCONNECTED
    }

    @SuppressLint("MissingPermission")
    fun shutdown() {
        shuttingDown = true
        stopDiscovery()
        stopAdvertising()
        disconnect()
        try { gattServer?.close() } catch (_: Exception) {}
        gattServer = null
        notifyCharacteristic = null
        gattServerActive = false
        advertisingActive = false
        shuttingDown = false
    }
}

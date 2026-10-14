package cc.skysparkle.matewave.audio

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.MediaPlayer
import android.media.SoundPool
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

enum class RadioState { IDLE, CONNECTING, PLAYING, ERROR }

object SoundManager {
    private val RADIO_SOURCES = listOf(
        "https://stream.srg-ssr.ch/srgssr/rsj/mp3/128",
        "https://stream.srg-ssr.ch/srgssr/rsj/aac/192",
        "https://stream.srg-ssr.ch/srgssr/rsj/aac/96"
    )

    // Played instead of the radio when it can't be reached
    private const val MENU_TRACK = "music/midnight_grove_conversations.ogg"
    private const val PLAY_TRACK = "music/raindrops_and_pawns.ogg"

    private const val PREFS = "chess_sound_prefs"
    private const val KEY_MUSIC = "music_enabled"
    private const val KEY_SFX = "sfx_enabled"
    private const val TAG = "SoundManager"

    private const val RADIO_START_DELAY_MS = 1_500L
    private const val MAX_RETRIES = 8
    private const val CONNECT_TIMEOUT_MS = 8_000L
    private val RETRY_DELAYS_MS = longArrayOf(1_000, 2_000, 4_000, 8_000, 15_000, 30_000)
    private const val BASE_VOLUME = 0.35f

    private var appContext: Context? = null
    private var musicPlayer: MediaPlayer? = null
    /** start/pause are only valid once the stream is prepared; before that they would throw. */
    private var playerPrepared = false
    private var initialized = false
    private var retryCount = 0
    private var sourceIndex = 0
    private var connectTimeoutRunnable: Runnable? = null
    private var audioManager: AudioManager? = null
    private var connectivity: ConnectivityManager? = null
    private var networkCallbackRegistered = false
    private var focusRequest: AudioFocusRequest? = null
    private var focusGranted = false
    private var noisyReceiverRegistered = false
    private val mainHandler = Handler(Looper.getMainLooper())

    private var offlinePlayer: MediaPlayer? = null
    private var offlinePrepared = false
    private var offlineTrack: String? = null
    private var playScreens = 0
    private var appInForeground = false

    /** Jazz radio. Off until the user turns it on, so nothing connects on start. */
    var musicEnabled: Boolean = false
        private set
    var sfxEnabled: Boolean = true
        private set

    private val _radioState = MutableStateFlow(RadioState.IDLE)
    val radioState: StateFlow<RadioState> = _radioState

    private fun setRadioState(state: RadioState) {
        _radioState.value = state
        updateOfflineMusic()
    }

    private fun pauseAll() {
        musicPlayer?.let { player -> if (playerPrepared) runCatching { if (player.isPlaying) player.pause() } }
        offlinePlayer?.let { player -> if (offlinePrepared) runCatching { if (player.isPlaying) player.pause() } }
    }

    private fun setVolumeAll(volume: Float) {
        runCatching { musicPlayer?.setVolume(volume, volume) }
        runCatching { offlinePlayer?.setVolume(volume, volume) }
    }

    private val noisyReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action == AudioManager.ACTION_AUDIO_BECOMING_NOISY) pauseAll()
        }
    }

    private val focusChangeListener = AudioManager.OnAudioFocusChangeListener { change ->
        when (change) {
            AudioManager.AUDIOFOCUS_LOSS -> {
                stopRadioInternal()
                releaseOfflinePlayer()
                abandonAudioFocus()
            }
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT -> {
                focusGranted = false
                pauseAll()
            }
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK -> setVolumeAll(BASE_VOLUME * 0.3f)
            AudioManager.AUDIOFOCUS_GAIN -> {
                focusGranted = true
                setVolumeAll(BASE_VOLUME)
                val player = musicPlayer
                if (player != null && playerPrepared) runCatching { if (!player.isPlaying) player.start() }
                val offline = offlinePlayer
                if (offline != null && offlinePrepared && shouldPlayOffline()) {
                    runCatching { if (!offline.isPlaying) offline.start() }
                }
            }
        }
    }

    // Brings the radio back once the device is online again
    private val networkCallback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) {
            mainHandler.post {
                if (musicEnabled && appInForeground && musicPlayer == null && _radioState.value == RadioState.ERROR) {
                    startRadio()
                }
            }
        }
    }

    fun init(context: Context) {
        if (initialized) return
        initialized = true
        val ctx = context.applicationContext
        appContext = ctx
        audioManager = ctx.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
        connectivity = ctx.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager

        val prefs = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        musicEnabled = prefs.getBoolean(KEY_MUSIC, false)
        sfxEnabled = prefs.getBoolean(KEY_SFX, true)

        ensureSoundPool(ctx)

        if (musicEnabled) {
            mainHandler.postDelayed({ if (musicEnabled) startRadio() }, RADIO_START_DELAY_MS)
        }
    }

    private fun requestAudioFocus(): Boolean {
        val am = audioManager ?: return false
        if (focusGranted) return true
        // Asked again after a denial or a transient loss, e.g. during a phone call
        val request = focusRequest ?: AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                    .build()
            )
            .setOnAudioFocusChangeListener(focusChangeListener, mainHandler)
            .build()
            .also { focusRequest = it }
        focusGranted = am.requestAudioFocus(request) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
        return focusGranted
    }

    private fun abandonAudioFocus() {
        val am = audioManager ?: return
        focusRequest?.let { am.abandonAudioFocusRequest(it) }
        focusRequest = null
        focusGranted = false
    }

    private fun registerNoisyReceiver() {
        val ctx = appContext ?: return
        if (noisyReceiverRegistered) return
        val filter = IntentFilter(AudioManager.ACTION_AUDIO_BECOMING_NOISY)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ctx.registerReceiver(noisyReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            @Suppress("UnspecifiedRegisterReceiverFlag")
            ctx.registerReceiver(noisyReceiver, filter)
        }
        noisyReceiverRegistered = true
    }

    private fun unregisterNoisyReceiver() {
        val ctx = appContext ?: return
        if (!noisyReceiverRegistered) return
        try { ctx.unregisterReceiver(noisyReceiver) } catch (_: IllegalArgumentException) {}
        noisyReceiverRegistered = false
    }

    private fun registerNetworkCallback() {
        val cm = connectivity ?: return
        if (networkCallbackRegistered) return
        runCatching { cm.registerDefaultNetworkCallback(networkCallback) }
            .onSuccess { networkCallbackRegistered = true }
    }

    private fun unregisterNetworkCallback() {
        val cm = connectivity ?: return
        if (!networkCallbackRegistered) return
        runCatching { cm.unregisterNetworkCallback(networkCallback) }
        networkCallbackRegistered = false
    }

    private fun isOnline(): Boolean {
        val cm = connectivity ?: return true
        val caps = cm.getNetworkCapabilities(cm.activeNetwork) ?: return false
        return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }

    private fun startRadio() {
        val ctx = appContext ?: return
        if (musicPlayer != null) return
        if (!requestAudioFocus()) {
            Log.w(TAG, "Audio focus denied, radio not started")
            setRadioState(RadioState.ERROR)
            return
        }
        registerNoisyReceiver()
        registerNetworkCallback()
        retryCount = 0
        if (!isOnline()) {
            // No point waiting for timeouts: play offline music until the network is back
            setRadioState(RadioState.ERROR)
            return
        }
        connect(ctx)
    }

    private fun connect(ctx: Context) {
        setRadioState(RadioState.CONNECTING)
        val url = RADIO_SOURCES[sourceIndex % RADIO_SOURCES.size]
        try {
            val player = MediaPlayer()
            musicPlayer = player
            playerPrepared = false
            player.setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                    .build()
            )
            player.setVolume(BASE_VOLUME, BASE_VOLUME)
            player.setDataSource(url)
            player.setOnPreparedListener {
                cancelConnectTimeout()
                playerPrepared = true
                retryCount = 0
                if (appInForeground) it.start()
                setRadioState(RadioState.PLAYING)
            }
            player.setOnErrorListener { mp, _, _ ->
                cancelConnectTimeout()
                try { mp.release() } catch (_: Exception) {}
                if (musicPlayer === mp) {
                    musicPlayer = null
                    playerPrepared = false
                }
                scheduleRetry(ctx)
                true
            }
            // A live stream only "completes" when the connection drops: reconnect.
            player.setOnCompletionListener { mp ->
                try { mp.release() } catch (_: Exception) {}
                if (musicPlayer === mp) {
                    musicPlayer = null
                    playerPrepared = false
                    retryCount = 0
                    scheduleRetry(ctx)
                }
            }
            player.prepareAsync()
            scheduleConnectTimeout(ctx, player, url)
        } catch (e: Exception) {
            Log.w(TAG, "Radio connection error ($url): ${e.message}")
            try { musicPlayer?.release() } catch (_: Exception) {}
            musicPlayer = null
            playerPrepared = false
            scheduleRetry(ctx)
        }
    }

    private fun scheduleConnectTimeout(ctx: Context, player: MediaPlayer, url: String) {
        cancelConnectTimeout()
        val runnable = Runnable {
            if (musicPlayer === player) {
                Log.w(TAG, "Radio connection timed out ($url)")
                try { player.release() } catch (_: Exception) {}
                musicPlayer = null
                playerPrepared = false
                scheduleRetry(ctx)
            }
        }
        connectTimeoutRunnable = runnable
        mainHandler.postDelayed(runnable, CONNECT_TIMEOUT_MS)
    }

    private fun cancelConnectTimeout() {
        connectTimeoutRunnable?.let { mainHandler.removeCallbacks(it) }
        connectTimeoutRunnable = null
    }

    private fun scheduleRetry(ctx: Context) {
        cancelConnectTimeout()
        if (!musicEnabled) {
            setRadioState(RadioState.IDLE)
            return
        }
        if (retryCount >= MAX_RETRIES || !isOnline()) {
            if (retryCount >= MAX_RETRIES) Log.w(TAG, "Could not connect to the radio after $MAX_RETRIES attempts")
            setRadioState(RadioState.ERROR)
            return
        }
        sourceIndex++
        val delay = RETRY_DELAYS_MS.getOrElse(retryCount) { RETRY_DELAYS_MS.last() }
        retryCount++
        setRadioState(RadioState.CONNECTING)
        mainHandler.postDelayed({
            if (musicEnabled && musicPlayer == null) connect(ctx)
        }, delay)
    }

    private fun stopRadioInternal() {
        cancelConnectTimeout()
        try { musicPlayer?.release() } catch (_: Exception) {}
        musicPlayer = null
        playerPrepared = false
        unregisterNoisyReceiver()
        unregisterNetworkCallback()
        abandonAudioFocus()
        mainHandler.removeCallbacksAndMessages(null)
        setRadioState(RadioState.IDLE)
    }

    fun onAppResumed() {
        appInForeground = true
        if (musicEnabled && musicPlayer == null) startRadio()
        updateOfflineMusic()
    }

    fun onAppPaused() {
        appInForeground = false
        releaseOfflinePlayer()
        stopRadioInternal()
    }

    fun setMusicEnabled(enabled: Boolean) {
        musicEnabled = enabled
        appContext?.getSharedPreferences(PREFS, Context.MODE_PRIVATE)?.edit()
            ?.putBoolean(KEY_MUSIC, enabled)?.apply()
        if (enabled) {
            startRadio()
        } else {
            releaseOfflinePlayer()
            stopRadioInternal()
        }
    }

    /** Games, puzzles and opening study call this while they are on screen. */
    fun enterPlayScreen() {
        playScreens++
        updateOfflineMusic()
    }

    fun leavePlayScreen() {
        playScreens = maxOf(0, playScreens - 1)
        updateOfflineMusic()
    }

    // The radio is on but can't be reached: no network, or the first attempt already failed
    private fun radioUnavailable(): Boolean = when (_radioState.value) {
        RadioState.ERROR -> true
        RadioState.CONNECTING -> retryCount > 0
        else -> false
    }

    private fun shouldPlayOffline() = musicEnabled && appInForeground && radioUnavailable()

    private fun updateOfflineMusic() {
        if (!shouldPlayOffline()) {
            releaseOfflinePlayer()
            return
        }
        val track = if (playScreens > 0) PLAY_TRACK else MENU_TRACK
        val player = offlinePlayer
        if (player != null && offlineTrack == track) {
            if (offlinePrepared && !player.isPlaying && requestAudioFocus()) runCatching { player.start() }
            return
        }
        releaseOfflinePlayer()
        if (!requestAudioFocus()) return
        registerNoisyReceiver()
        playOfflineTrack(track)
    }

    private fun playOfflineTrack(track: String) {
        val ctx = appContext ?: return
        try {
            val player = MediaPlayer()
            offlinePlayer = player
            offlineTrack = track
            offlinePrepared = false
            player.setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                    .build()
            )
            ctx.assets.openFd(track).use { fd ->
                player.setDataSource(fd.fileDescriptor, fd.startOffset, fd.length)
            }
            player.isLooping = true
            player.setVolume(BASE_VOLUME, BASE_VOLUME)
            player.setOnPreparedListener {
                offlinePrepared = true
                if (shouldPlayOffline() && focusGranted) it.start()
            }
            player.setOnErrorListener { mp, _, _ ->
                if (offlinePlayer === mp) releaseOfflinePlayer()
                true
            }
            player.prepareAsync()
        } catch (e: Exception) {
            Log.w(TAG, "Offline music error ($track): ${e.message}")
            releaseOfflinePlayer()
        }
    }

    private fun releaseOfflinePlayer() {
        try { offlinePlayer?.release() } catch (_: Exception) {}
        offlinePlayer = null
        offlineTrack = null
        offlinePrepared = false
    }

    fun setSfxEnabled(enabled: Boolean) {
        sfxEnabled = enabled
        appContext?.getSharedPreferences(PREFS, Context.MODE_PRIVATE)?.edit()
            ?.putBoolean(KEY_SFX, enabled)?.apply()
    }

    private var soundPool: SoundPool? = null
    private val sfxSoundIds = HashMap<Int, Int>()
    private val sfxLoaded = HashSet<Int>()

    private fun ensureSoundPool(ctx: Context): SoundPool {
        soundPool?.let { return it }
        val attributes = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_GAME)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build()
        val pool = SoundPool.Builder().setMaxStreams(4).setAudioAttributes(attributes).build()
        pool.setOnLoadCompleteListener { _, soundId, status ->
            if (status == 0) synchronized(sfxLoaded) { sfxLoaded.add(soundId) }
        }
        for (resId in intArrayOf(
            cc.skysparkle.matewave.R.raw.sfx_move,
            cc.skysparkle.matewave.R.raw.sfx_capture,
            cc.skysparkle.matewave.R.raw.sfx_select
        )) {
            sfxSoundIds[resId] = pool.load(ctx, resId, 1)
        }
        soundPool = pool
        return pool
    }

    private fun playSfx(resId: Int) {
        if (!sfxEnabled) return
        val ctx = appContext ?: return
        val pool = ensureSoundPool(ctx)
        val soundId = sfxSoundIds[resId] ?: return
        val ready = synchronized(sfxLoaded) { soundId in sfxLoaded }
        if (ready) pool.play(soundId, 1f, 1f, 1, 0, 1f)
    }

    fun playMove() { playSfx(cc.skysparkle.matewave.R.raw.sfx_move) }
    fun playCapture() { playSfx(cc.skysparkle.matewave.R.raw.sfx_capture) }
    fun playSelect() { playSfx(cc.skysparkle.matewave.R.raw.sfx_select) }
}

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
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

enum class RadioState { IDLE, CONNECTING, PLAYING, ERROR }

object SoundManager {
    private val RADIO_SOURCES = listOf(
        "http://stream.srg-ssr.ch/srgssr/rsj/aac/192",
        "https://stream.srg-ssr.ch/srgssr/rsj/mp3/128",
        "http://stream.srg-ssr.ch/srgssr/rsj/aac/96"
    )

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
    private var focusRequest: AudioFocusRequest? = null
    private var noisyReceiverRegistered = false
    private val mainHandler = Handler(Looper.getMainLooper())

    var musicEnabled: Boolean = true
        private set
    var sfxEnabled: Boolean = true
        private set

    private val _radioState = MutableStateFlow(RadioState.IDLE)
    val radioState: StateFlow<RadioState> = _radioState

    private fun pauseRadio() {
        val player = musicPlayer ?: return
        if (playerPrepared) runCatching { if (player.isPlaying) player.pause() }
    }

    private val noisyReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action == AudioManager.ACTION_AUDIO_BECOMING_NOISY) pauseRadio()
        }
    }

    private val focusChangeListener = AudioManager.OnAudioFocusChangeListener { change ->
        when (change) {
            AudioManager.AUDIOFOCUS_LOSS -> stopRadioInternal()
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT -> pauseRadio()
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK ->
                runCatching { musicPlayer?.setVolume(BASE_VOLUME * 0.3f, BASE_VOLUME * 0.3f) }
            AudioManager.AUDIOFOCUS_GAIN -> {
                val player = musicPlayer
                if (player != null && playerPrepared) runCatching {
                    player.setVolume(BASE_VOLUME, BASE_VOLUME)
                    if (!player.isPlaying) player.start()
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

        val prefs = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        musicEnabled = prefs.getBoolean(KEY_MUSIC, true)
        sfxEnabled = prefs.getBoolean(KEY_SFX, true)

        ensureSoundPool(ctx)

        if (musicEnabled) {
            mainHandler.postDelayed({ if (musicEnabled) startRadio() }, RADIO_START_DELAY_MS)
        }
    }

    private fun requestAudioFocus(): Boolean {
        val am = audioManager ?: return false
        val attrs = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_MEDIA)
            .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
            .build()
        val request = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
            .setAudioAttributes(attrs)
            .setOnAudioFocusChangeListener(focusChangeListener, mainHandler)
            .build()
        focusRequest = request
        return am.requestAudioFocus(request) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
    }

    private fun abandonAudioFocus() {
        val am = audioManager ?: return
        focusRequest?.let { am.abandonAudioFocusRequest(it) }
        focusRequest = null
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

    private fun startRadio() {
        val ctx = appContext ?: return
        if (musicPlayer != null) return
        if (!requestAudioFocus()) {
            Log.w(TAG, "Audio focus denied, radio not started")
            _radioState.value = RadioState.ERROR
            return
        }
        registerNoisyReceiver()
        retryCount = 0
        connect(ctx)
    }

    private fun connect(ctx: Context) {
        _radioState.value = RadioState.CONNECTING
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
                it.start()
                retryCount = 0
                _radioState.value = RadioState.PLAYING
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
            _radioState.value = RadioState.IDLE
            return
        }
        if (retryCount >= MAX_RETRIES) {
            _radioState.value = RadioState.ERROR
            Log.w(TAG, "Could not connect to the radio after $MAX_RETRIES attempts")
            return
        }
        sourceIndex++
        val delay = RETRY_DELAYS_MS.getOrElse(retryCount) { RETRY_DELAYS_MS.last() }
        retryCount++
        _radioState.value = RadioState.CONNECTING
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
        abandonAudioFocus()
        mainHandler.removeCallbacksAndMessages(null)
        _radioState.value = RadioState.IDLE
    }

    fun onAppResumed() {
        if (musicEnabled && musicPlayer == null) startRadio()
    }

    fun onAppPaused() {
        stopRadioInternal()
    }

    fun setMusicEnabled(enabled: Boolean) {
        musicEnabled = enabled
        appContext?.getSharedPreferences(PREFS, Context.MODE_PRIVATE)?.edit()
            ?.putBoolean(KEY_MUSIC, enabled)?.apply()
        if (enabled) startRadio() else stopRadioInternal()
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

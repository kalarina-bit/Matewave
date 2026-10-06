package cc.skysparkle.matewave.presence

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import cc.skysparkle.matewave.MainActivity
import cc.skysparkle.matewave.R
import cc.skysparkle.matewave.network.NetworkLog

/** Optional foreground service that keeps the player visible while the app is in the background. */
class PresenceService : Service() {
    private var holdsPresence = false

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (holdsPresence) return START_STICKY

        createChannel()
        val started = runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                startForeground(NOTIFICATION_ID, buildNotification(), ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE)
            } else {
                startForeground(NOTIFICATION_ID, buildNotification())
            }
        }.isSuccess

        if (!started) {
            NetworkLog.log("PRESENCE", "Background service not started: rejected by the system")
            PresenceSettings(applicationContext).backgroundEnabled = false
            stopSelf()
            return START_NOT_STICKY
        }

        PresenceHub.acquire(applicationContext)
        holdsPresence = true
        NetworkLog.log("PRESENCE", "Background presence started")
        return START_STICKY
    }

    override fun onDestroy() {
        if (holdsPresence) {
            holdsPresence = false
            PresenceHub.release(applicationContext)
            NetworkLog.log("PRESENCE", "Background presence stopped")
        }
        super.onDestroy()
    }

    private fun createChannel() {
        val manager = getSystemService(NotificationManager::class.java) ?: return
        val channel = NotificationChannel(
            CHANNEL_ID,
            getString(R.string.presence_service_channel),
            NotificationManager.IMPORTANCE_LOW
        )
        manager.createNotificationChannel(channel)
    }

    private fun buildNotification(): Notification {
        val intent = Intent(this, MainActivity::class.java)
        val pending = PendingIntent.getActivity(
            this, 0, intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        return Notification.Builder(this, CHANNEL_ID)
            .setContentTitle(getString(R.string.presence_service_title))
            .setContentText(getString(R.string.presence_service_text))
            .setSmallIcon(R.drawable.ic_stat_background)
            .setContentIntent(pending)
            .setOngoing(true)
            .build()
    }

    companion object {
        private const val CHANNEL_ID = "presence"
        private const val NOTIFICATION_ID = 42

        fun start(context: Context) {
            runCatching { context.startForegroundService(Intent(context, PresenceService::class.java)) }
        }

        fun stop(context: Context) {
            runCatching { context.stopService(Intent(context, PresenceService::class.java)) }
        }
    }
}

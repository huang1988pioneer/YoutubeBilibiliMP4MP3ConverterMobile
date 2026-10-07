package com.huang1988pioneer.mediaconverter.download

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import com.huang1988pioneer.mediaconverter.MainActivity
import com.huang1988pioneer.mediaconverter.R
import com.huang1988pioneer.mediaconverter.Session

class DownloadService : Service() {
    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createChannel()
        Session.bindService(this)
    }

    override fun onDestroy() {
        Session.unbindService(this)
        super.onDestroy()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val notification = buildNotification("準備下載", 0, true)
        if (Build.VERSION.SDK_INT >= 29) {
            startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
        if (intent?.action == ACTION_CANCEL) {
            Session.cancelCurrent()
            return START_NOT_STICKY
        }
        Session.onServiceStart(this)
        return START_NOT_STICKY
    }

    fun updateNotification(title: String, progress: Int, indeterminate: Boolean) {
        val manager = getSystemService(NotificationManager::class.java)
        manager.notify(NOTIFICATION_ID, buildNotification(title, progress, indeterminate))
    }

    fun stopForegroundAndSelf() {
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    fun acquireWakeLock(): PowerManager.WakeLock {
        val manager = getSystemService(PowerManager::class.java)
        return manager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "mediaconverter:download").apply {
            setReferenceCounted(false)
            acquire(3 * 60 * 60 * 1000L)
        }
    }

    fun releaseWakeLock(lock: PowerManager.WakeLock) {
        if (lock.isHeld) lock.release()
    }

    private fun createChannel() {
        val manager = getSystemService(NotificationManager::class.java)
        val channel = NotificationChannel(CHANNEL_ID, "轉換進度", NotificationManager.IMPORTANCE_LOW)
        channel.setShowBadge(false)
        manager.createNotificationChannel(channel)
    }

    private fun buildNotification(title: String, progress: Int, indeterminate: Boolean): Notification {
        val open = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val cancel = PendingIntent.getService(
            this,
            1,
            Intent(this, DownloadService::class.java).setAction(ACTION_CANCEL),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_download)
            .setContentTitle("影音轉換大師")
            .setContentText(title)
            .setContentIntent(open)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .addAction(0, "取消", cancel)
        if (indeterminate) {
            builder.setProgress(0, 0, true)
        } else {
            builder.setProgress(100, progress.coerceIn(0, 100), false)
        }
        return builder.build()
    }

    companion object {
        const val ACTION_CANCEL = "com.huang1988pioneer.mediaconverter.CANCEL"
        private const val CHANNEL_ID = "converter_downloads"
        private const val NOTIFICATION_ID = 42
    }
}

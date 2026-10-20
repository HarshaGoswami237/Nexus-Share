package com.example.localfileshare.network

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.example.localfileshare.data.SharedRepository
import com.example.localfileshare.ui.MainActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

class ServerService : Service() {

    private val serviceJob = Job()
    private val serviceScope = CoroutineScope(Dispatchers.IO + serviceJob)
    private var serverManager: ServerManager? = null
    private var wakeLock: PowerManager.WakeLock? = null

    companion object {
        const val CHANNEL_ID = "nexus_server_channel"
        const val NOTIFICATION_ID = 101
        const val ACTION_START = "ACTION_START"
        const val ACTION_STOP = "ACTION_STOP"
        const val ACTION_UPDATE_FILES = "ACTION_UPDATE_FILES"
        var isRunning = false
            private set
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> startServerEngine()
            ACTION_STOP -> stopServerEngine()
            ACTION_UPDATE_FILES -> pushDynamicNotification()
        }
        return START_NOT_STICKY
    }

    private fun startServerEngine() {
        if (isRunning) return
        isRunning = true

        val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
        wakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "NexusShare:ServerWakeLock").apply {
            acquire(60 * 60 * 1000L) // 1 hour max hold
        }

        tryPromoteToForeground()

        serviceScope.launch {
            try {
                serverManager = ServerManager(
                    context = applicationContext,
                    filesList = SharedRepository.hostedFiles
                )
                serverManager?.startServer()
            } catch (_: Exception) {
                stopServerEngine()
            }
        }
    }

    private fun tryPromoteToForeground() {
        try {
            val notification = buildServerNotification(SharedRepository.hostedFiles.size)
            startForeground(NOTIFICATION_ID, notification)
        } catch (_: SecurityException) {
            // Permission rejected: continue running background engine safely
        } catch (_: Exception) {
        }
    }

    private fun pushDynamicNotification() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                return
            }
        }

        try {
            val notification = buildServerNotification(SharedRepository.hostedFiles.size)
            NotificationManagerCompat.from(this).notify(NOTIFICATION_ID, notification)
        } catch (_: SecurityException) {
            // Android Studio lint guard
        } catch (_: Exception) {
        }
    }

    private fun stopServerEngine() {
        isRunning = false
        wakeLock?.let {
            if (it.isHeld) it.release()
        }
        serverManager?.stopServer()
        serverManager = null
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun buildServerNotification(fileCount: Int): Notification {
        val openAppIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val stopIntent = Intent(this, ServerService::class.java).apply {
            action = ACTION_STOP
        }
        val stopPendingIntent = PendingIntent.getService(
            this,
            1,
            stopIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val ipAddress = NetworkUtils.getLocalIpAddress()

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Nexus Server Active")
            .setContentText("http://$ipAddress:8080 • $fileCount Files Hosted")
            .setSmallIcon(android.R.drawable.stat_sys_upload)
            .setContentIntent(openAppIntent)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "STOP SERVER", stopPendingIntent)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Nexus Server Status",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Shows live running status of Nexus Server node"
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        serviceJob.cancel()
        stopServerEngine()
    }
}
package com.example.localfileshare.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import com.example.localfileshare.MainActivity
import com.example.localfileshare.data.SharedRepository
import com.example.localfileshare.network.NetworkUtils
import com.example.localfileshare.network.ServerManager
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
        }
        return START_NOT_STICKY
    }

    private fun startServerEngine() {
        if (isRunning) return
        isRunning = true

        val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
        wakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "NexusShare:ServerWakeLock").apply {
            acquire(10 * 60 * 1000L)
        }

        val ip = NetworkUtils.getLocalIpAddress()
        val notification = buildNotification("Node active at http://$ip:8080")
        startForeground(NOTIFICATION_ID, notification)

        serviceScope.launch {
            try {
                // Reads directly from shared in-memory repository
                serverManager = ServerManager(applicationContext, SharedRepository.hostedFiles)
                serverManager?.startServer()
            } catch (e: Exception) {
                stopServerEngine()
            }
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

    private fun buildNotification(contentText: String): Notification {
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Nexus Micro-Server Online")
            .setContentText(contentText)
            .setSmallIcon(android.R.drawable.stat_sys_upload)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .build()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Nexus Server Service",
                NotificationManager.IMPORTANCE_LOW
            )
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
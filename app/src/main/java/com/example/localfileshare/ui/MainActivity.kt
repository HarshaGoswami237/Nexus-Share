package com.example.localfileshare.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.View
import android.widget.Button
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.example.localfileshare.R
import com.example.localfileshare.data.SharedRepository
import com.example.localfileshare.network.NetworkUtils
import com.example.localfileshare.network.ServerService

class MainActivity : AppCompatActivity() {

    private lateinit var tvServerEndpoint: TextView
    private lateinit var tvMediaHubStatus: TextView
    private lateinit var viewStatusDot: View
    private lateinit var btnToggleServer: Button
    private lateinit var cardSlotMediaHub: View

    private val requestNotificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        // Start server regardless of permission result
        triggerServerStart()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        tvServerEndpoint = findViewById(R.id.tvServerEndpoint)
        tvMediaHubStatus = findViewById(R.id.tvMediaHubStatus)
        viewStatusDot = findViewById(R.id.viewStatusDot)
        btnToggleServer = findViewById(R.id.btnToggleServer)
        cardSlotMediaHub = findViewById(R.id.cardSlotMediaHub)

        cardSlotMediaHub.setOnClickListener {
            startActivity(Intent(this, MediaHubActivity::class.java))
        }

        btnToggleServer.setOnClickListener {
            btnToggleServer.isEnabled = false
            if (ServerService.isRunning) {
                stopServer()
            } else {
                handleServerStartRequest()
            }

            btnToggleServer.postDelayed({
                btnToggleServer.isEnabled = true
                updateStatus()
            }, 600)
        }
    }

    private fun handleServerStartRequest() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val permission = Manifest.permission.POST_NOTIFICATIONS
            when {
                ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED -> {
                    triggerServerStart()
                }
                ActivityCompat.shouldShowRequestPermissionRationale(this, permission) -> {
                    // Show explanation before prompting again
                    AlertDialog.Builder(this)
                        .setTitle("Notification Permission Needed")
                        .setMessage("Notifications keep the server running in the background while your phone screen is turned off.")
                        .setPositiveButton("Allow") { _, _ ->
                            requestNotificationPermissionLauncher.launch(permission)
                        }
                        .setNegativeButton("Start Without Notification") { _, _ ->
                            triggerServerStart()
                        }
                        .show()
                }
                else -> {
                    // First time asking or permanently denied
                    requestNotificationPermissionLauncher.launch(permission)
                }
            }
        } else {
            triggerServerStart()
        }
    }

    private fun triggerServerStart() {
        val startIntent = Intent(this, ServerService::class.java).apply {
            action = ServerService.ACTION_START
        }
        try {
            ContextCompat.startForegroundService(this, startIntent)
        } catch (_: Exception) {
            // Fallback for strict background limits
            startService(startIntent)
        }
        updateStatus()
    }

    private fun stopServer() {
        val stopIntent = Intent(this, ServerService::class.java).apply {
            action = ServerService.ACTION_STOP
        }
        startService(stopIntent)
    }

    override fun onResume() {
        super.onResume()
        updateStatus()
    }

    private fun updateStatus() {
        tvMediaHubStatus.text = "${SharedRepository.hostedFiles.size} files hosted"

        if (ServerService.isRunning) {
            val ipAddress = NetworkUtils.getLocalIpAddress()
            tvServerEndpoint.text = "http://$ipAddress:8080"
            viewStatusDot.setBackgroundColor(Color.parseColor("#10B981"))
            btnToggleServer.text = "STOP SERVER"
            btnToggleServer.setBackgroundColor(Color.parseColor("#DC2626"))
        } else {
            tvServerEndpoint.text = "Server Offline"
            viewStatusDot.setBackgroundColor(Color.parseColor("#64748B"))
            btnToggleServer.text = "START SERVER"
            btnToggleServer.setBackgroundColor(Color.parseColor("#2563EB"))
        }
    }
}
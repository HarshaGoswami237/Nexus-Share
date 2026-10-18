package com.example.localfileshare

import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.example.localfileshare.data.FileHelper
import com.example.localfileshare.data.SharedRepository
import com.example.localfileshare.network.NetworkUtils
import com.example.localfileshare.service.ServerService

class MainActivity : AppCompatActivity() {

    private lateinit var tvServerEndpoint: TextView
    private lateinit var tvMediaHubStatus: TextView
    private lateinit var viewStatusDot: View
    private lateinit var btnToggleServer: Button
    private lateinit var cardSlotMediaHub: View

    private val pickMultipleFilesLauncher = registerForActivityResult(
        ActivityResultContracts.GetMultipleContents()
    ) { uris: List<Uri> ->
        if (uris.isNotEmpty()) {
            for (uri in uris) {
                val newFile = FileHelper.createSharedFile(this, uri)
                SharedRepository.hostedFiles.add(newFile)
            }
            updateStatus()
        }
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
            pickMultipleFilesLauncher.launch("*/*")
        }

        btnToggleServer.setOnClickListener {
            btnToggleServer.isEnabled = false
            if (ServerService.isRunning) {
                val stopIntent = Intent(this, ServerService::class.java).apply {
                    action = ServerService.ACTION_STOP
                }
                startService(stopIntent)
            } else {
                val startIntent = Intent(this, ServerService::class.java).apply {
                    action = ServerService.ACTION_START
                }
                ContextCompat.startForegroundService(this, startIntent)
            }
            btnToggleServer.postDelayed({
                btnToggleServer.isEnabled = true
                updateStatus()
            }, 600)
        }
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
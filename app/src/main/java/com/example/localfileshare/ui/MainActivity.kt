package com.example.localfileshare.ui

import android.net.Uri
import android.net.wifi.WifiManager
import android.os.Bundle
import android.text.format.Formatter
import android.util.Log
import android.widget.Button
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.localfileshare.R
import com.example.localfileshare.data.FileHelper
import com.example.localfileshare.data.SharedFile
import com.example.localfileshare.network.ServerManager
import com.example.localfileshare.network.NetworkUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : AppCompatActivity() {

    private lateinit var statusText: TextView
    private lateinit var selectFileBtn: Button
    private lateinit var toggleServerBtn: Button
    private lateinit var fileRecyclerView: RecyclerView
    private lateinit var fileAdapter: FileAdapter

    private val sharedFilesList = mutableListOf<SharedFile>()
    private var serverManager: ServerManager? = null
    private var isServerRunning = false

    private val pickMultipleFilesLauncher = registerForActivityResult(
        ActivityResultContracts.GetMultipleContents()
    ) { uris: List<Uri> ->
        if (uris.isNotEmpty()) {
            for (uri in uris) {
                val newFile = FileHelper.createSharedFile(this, uri)
                sharedFilesList.add(newFile)
            }
            fileAdapter.notifyDataSetChanged()
            updateStatus()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        statusText = findViewById(R.id.statusText)
        selectFileBtn = findViewById(R.id.selectFileButton)
        toggleServerBtn = findViewById(R.id.toggleServerButton)
        fileRecyclerView = findViewById(R.id.fileRecyclerView)

        fileAdapter = FileAdapter(sharedFilesList) { fileToRemove ->
            val index = sharedFilesList.indexOf(fileToRemove)
            if (index != -1) {
                sharedFilesList.removeAt(index)
                fileAdapter.notifyItemRemoved(index)
                updateStatus()
            }
        }

        fileRecyclerView.layoutManager = LinearLayoutManager(this)
        fileRecyclerView.adapter = fileAdapter

        selectFileBtn.setOnClickListener {
            pickMultipleFilesLauncher.launch("*/*")
        }

        toggleServerBtn.setOnClickListener {
            if (isServerRunning) {
                stopLocalServer()
            } else {
                startLocalServer()
            }
        }

        updateStatus()
    }

    private fun startLocalServer() {
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                serverManager = ServerManager(applicationContext, sharedFilesList)
                serverManager?.startServer()

                withContext(Dispatchers.Main) {
                    isServerRunning = true
                    toggleServerBtn.text = "STOP HOSTING"
                    updateStatus()
                }
            } catch (e: Throwable) {
                Log.e("SERVER_ERROR", "Failed to start Ktor server", e)
                withContext(Dispatchers.Main) {
                    statusText.text = "Server Error: ${e.localizedMessage}"
                }
            }
        }
    }

    private fun stopLocalServer() {
        serverManager?.stopServer()
        serverManager = null
        isServerRunning = false
        toggleServerBtn.text = "START HOSTING"
        updateStatus()
    }

    private fun updateStatus() {
        val count = sharedFilesList.size
        if (isServerRunning) {
            val ipAddress = NetworkUtils.getLocalIpAddress()
            statusText.text = "$count Files Queued | Server: http://$ipAddress:8080"
        } else {
            statusText.text = "$count Files Queued | Server Offline"
        }
    }

    private fun getLocalIpAddress(): String {
        return try {
            val wifiManager = applicationContext.getSystemService(WIFI_SERVICE) as WifiManager
            Formatter.formatIpAddress(wifiManager.connectionInfo.ipAddress)
        } catch (e: Exception) {
            "0.0.0.0"
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        stopLocalServer()
    }
}
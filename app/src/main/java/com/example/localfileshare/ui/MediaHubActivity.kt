package com.example.localfileshare.ui

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.localfileshare.R
import com.example.localfileshare.data.FileHelper
import com.example.localfileshare.data.SharedFile
import com.example.localfileshare.data.SharedRepository
import com.example.localfileshare.network.ServerService

class MediaHubActivity : AppCompatActivity() {

    private lateinit var tvStatusSummary: TextView
    private lateinit var rvFiles: RecyclerView
    private lateinit var fileAdapter: FileAdapter

    private val selectFilesLauncher = registerForActivityResult(
        ActivityResultContracts.OpenMultipleDocuments()
    ) { uris: List<Uri> ->
        if (uris.isNotEmpty()) {
            for (uri in uris) {
                try {
                    contentResolver.takePersistableUriPermission(
                        uri,
                        Intent.FLAG_GRANT_READ_URI_PERMISSION
                    )
                } catch (_: Exception) {}

                val newFile = FileHelper.createSharedFile(this, uri)
                SharedRepository.hostedFiles.add(newFile)
            }
            fileAdapter.notifyDataSetChanged()
            updateHeaderStatus()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_media_hub)

        tvStatusSummary = findViewById(R.id.tvQueueStatusSummary)
        rvFiles = findViewById(R.id.rvQueueFiles)

        fileAdapter = FileAdapter(SharedRepository.hostedFiles) { fileToRemove ->
            val index = SharedRepository.hostedFiles.indexOf(fileToRemove)
            if (index != -1) {
                SharedRepository.hostedFiles.removeAt(index)
                fileAdapter.notifyItemRemoved(index)
                updateHeaderStatus()
            }
        }

        rvFiles.layoutManager = LinearLayoutManager(this)
        rvFiles.adapter = fileAdapter

        findViewById<Button>(R.id.btnAddFiles).setOnClickListener {
            selectFilesLauncher.launch(arrayOf("*/*"))
        }

        findViewById<Button>(R.id.btnClearAllFiles).setOnClickListener {
            val total = SharedRepository.hostedFiles.size
            if (total > 0) {
                SharedRepository.hostedFiles.clear()
                fileAdapter.notifyItemRangeRemoved(0, total)
                updateHeaderStatus()
            }
        }

        updateHeaderStatus()
    }

    override fun onResume() {
        super.onResume()
        fileAdapter.notifyDataSetChanged()
        updateHeaderStatus()
    }

    private fun updateHeaderStatus() {
        val count = SharedRepository.hostedFiles.size
        val serverStatus = if (ServerService.isRunning) "Server Active" else "Server Offline"
        tvStatusSummary.text = "$count Files Queued • $serverStatus"

        if (ServerService.isRunning) {
            val refreshIntent = Intent(this, ServerService::class.java).apply {
                action = ServerService.ACTION_UPDATE_FILES
            }
            startService(refreshIntent)
        }
    }
}
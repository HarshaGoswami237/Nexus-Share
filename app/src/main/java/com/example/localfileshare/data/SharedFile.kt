package com.example.localfileshare.data

import android.net.Uri

data class SharedFile(
    val id: String,
    val name: String,
    val sizeBytes: Long,
    val sizeFormatted: String,
    val mimeType: String,
    val uri: Uri
)
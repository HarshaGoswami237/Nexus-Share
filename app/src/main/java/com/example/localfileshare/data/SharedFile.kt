package com.example.localfileshare.data
import android.net.Uri
@kotlinx.serialization.Serializable
data class SharedFile(
    val id: String,         // Unique ID for list operations
    // Uri cannot be directly converted to JSON text, so we mark it @Transient
    @Transient val uri: Uri? = null,     // Content URI from Android picker
    val name: String,       // Formatted filename
    val sizeFormatted: String, // E.g., "14.2 MB"
    val sizeBytes: Long,    // Raw byte count for server streaming
    val mimeType: String    // E.g., "video/mp4", "image/png"


)
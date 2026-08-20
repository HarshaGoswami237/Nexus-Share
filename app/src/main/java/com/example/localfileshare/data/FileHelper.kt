package com.example.localfileshare.data

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import java.util.UUID

object FileHelper {

    fun getFileName(context: Context, uri: Uri): String {
        var name = "Unknown"
        val cursor = context.contentResolver.query(uri, null, null, null, null)
        cursor?.use {
            if (it.moveToFirst()) {
                val nameIndex = it.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (nameIndex != -1) {
                    name = it.getString(nameIndex) ?: "Unknown"
                }
            }
        }
        return name
    }

    fun getFileSizeBytes(context: Context, uri: Uri): Long {
        var sizeBytes: Long = 0
        val cursor = context.contentResolver.query(uri, null, null, null, null)
        cursor?.use {
            if (it.moveToFirst()) {
                val sizeIndex = it.getColumnIndex(OpenableColumns.SIZE)
                if (sizeIndex != -1) {
                    sizeBytes = it.getLong(sizeIndex)
                }
            }
        }
        return sizeBytes
    }

    fun formatFileSize(bytes: Long): String {
        return when {
            bytes >= 1000 * 1000 * 1000 -> String.format("%.2f GB", bytes / (1000.0 * 1000.0 * 1000.0))
            bytes >= 1000 * 1000 -> String.format("%.2f MB", bytes / (1000.0 * 1000.0))
            bytes >= 1000 -> String.format("%.2f KB", bytes / 1000.0)
            else -> "$bytes Bytes"
        }
    }

    fun getMimeType(context: Context, uri: Uri): String {
        return context.contentResolver.getType(uri) ?: "*/*"
    }

    fun createSharedFile(context: Context, uri: Uri): SharedFile {
        val name = getFileName(context, uri)
        val bytes = getFileSizeBytes(context, uri)
        val formattedSize = formatFileSize(bytes)
        val mime = getMimeType(context, uri)
        return SharedFile(
            id = UUID.randomUUID().toString(),
            uri = uri,
            name = name,
            sizeFormatted = formattedSize,
            sizeBytes = bytes,
            mimeType = mime
        )
    }
}
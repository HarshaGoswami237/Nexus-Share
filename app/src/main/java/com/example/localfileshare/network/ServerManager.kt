package com.example.localfileshare.network

import android.content.Context
import android.os.ParcelFileDescriptor
import com.example.localfileshare.data.SharedFile
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.ApplicationCall
import io.ktor.server.application.call
import io.ktor.server.engine.ApplicationEngine
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import io.ktor.server.response.header
import io.ktor.server.response.respond
import io.ktor.server.response.respondOutputStream
import io.ktor.server.response.respondText
import io.ktor.server.routing.get
import io.ktor.server.routing.routing
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.FileInputStream
import java.io.OutputStream
import java.util.concurrent.atomic.AtomicInteger

class ServerManager(
    private val context: Context,
    private val filesList: List<SharedFile>,
    private val onClientCountChanged: ((Int) -> Unit)? = null
) {
    private var engine: ApplicationEngine? = null
    private val activeClients = AtomicInteger(0)

    fun startServer(port: Int = 8080) {
        engine = embeddedServer(Netty, port = port, host = "0.0.0.0") {
            routing {
                get("/") {
                    val html = loadHtmlFromAssets()
                    call.respondText(html, ContentType.Text.Html)
                }

                get("/style.css") {
                    val css = loadAssetString("style.css")
                    call.respondText(css, ContentType.Text.CSS)
                }

                get("/preview/{id}") {
                    val fileId = call.parameters["id"]
                    val file = filesList.find { it.id == fileId }

                    if (file == null) {
                        call.respond(HttpStatusCode.NotFound, "File Not Found")
                        return@get
                    }

                    trackConnection {
                        serveFileUniversal(call, file, asAttachment = false)
                    }
                }

                get("/download/{id}") {
                    val fileId = call.parameters["id"]
                    val file = filesList.find { it.id == fileId }

                    if (file == null) {
                        call.respond(HttpStatusCode.NotFound, "File Not Found")
                        return@get
                    }

                    trackConnection {
                        serveFileUniversal(call, file, asAttachment = true)
                    }
                }
            }
        }.start(wait = false)
    }

    private suspend fun trackConnection(block: suspend () -> Unit) {
        val current = activeClients.incrementAndGet()
        onClientCountChanged?.invoke(current)
        try {
            block()
        } finally {
            val updated = activeClients.decrementAndGet().coerceAtLeast(0)
            onClientCountChanged?.invoke(updated)
        }
    }

    private fun loadAssetString(fileName: String): String {
        return try {
            this@ServerManager.context.assets.open(fileName).bufferedReader().use { it.readText() }
        } catch (_: Exception) {
            ""
        }
    }

    private fun loadHtmlFromAssets(): String {
        return try {
            val template = loadAssetString("index.html")
            val countStr = filesList.size.toString()

            val rowsHtml = if (filesList.isEmpty()) {
                "<div class='empty-state'>No files currently hosted.</div>"
            } else {
                filesList.joinToString("") { f ->
                    """
                    <div class="file-row">
                        <div class="file-info">
                            <span class="file-name" title="${f.name}">${f.name}</span>
                            <span class="file-size">${f.sizeFormatted}</span>
                        </div>
                        <div class="btn-group">
                            <a class="btn btn-open" href="/preview/${f.id}" target="_blank" rel="noopener">Preview</a>
                            <a class="btn btn-download" href="/download/${f.id}">Download</a>
                        </div>
                    </div>
                    """
                }
            }

            template
                .replace("{{COUNT}}", countStr)
                .replace("{{FILE_ROWS}}", rowsHtml)
        } catch (e: Exception) {
            "<html><body><h3>Failed to load UI template: ${e.message}</h3></body></html>"
        }
    }

    private suspend fun serveFileUniversal(
        call: ApplicationCall,
        file: SharedFile,
        asAttachment: Boolean
    ) = withContext(Dispatchers.IO) {
        val totalBytes = file.sizeBytes
        val mimeType = resolveMimeType(file.name, file.mimeType)
        val parsedContentType = try {
            ContentType.parse(mimeType)
        } catch (_: Exception) {
            ContentType.Application.OctetStream
        }

        val rangeHeader = call.request.headers[HttpHeaders.Range]
        val disposition = if (asAttachment) "attachment" else "inline"

        call.response.header(HttpHeaders.ContentDisposition, "$disposition; filename=\"${file.name}\"")
        call.response.header(HttpHeaders.AcceptRanges, "bytes")

        // Try getting native ParcelFileDescriptor first; if security denies it, fallback to InputStream
        var pfd: ParcelFileDescriptor? = null
        try {
            pfd = this@ServerManager.context.contentResolver.openFileDescriptor(file.uri, "r")
        } catch (_: Throwable) {
            // Permission denial caught safely — will use InputStream fallback below
            pfd = null
        }

        try {
            if (pfd != null) {
                // High-speed descriptor pipeline
                if (!asAttachment && rangeHeader != null && rangeHeader.startsWith("bytes=")) {
                    val ranges = rangeHeader.removePrefix("bytes=").split("-")
                    val start = ranges[0].toLongOrNull() ?: 0L
                    val end = if (ranges.size > 1 && ranges[1].isNotEmpty()) {
                        ranges[1].toLongOrNull() ?: (totalBytes - 1)
                    } else {
                        totalBytes - 1
                    }.coerceAtMost(totalBytes - 1)

                    val contentLength = (end - start) + 1
                    call.response.status(HttpStatusCode.PartialContent)
                    call.response.header(HttpHeaders.ContentRange, "bytes $start-$end/$totalBytes")
                    call.response.header(HttpHeaders.ContentLength, contentLength.toString())

                    call.respondOutputStream(contentType = parsedContentType, status = HttpStatusCode.PartialContent) {
                        pipeRange(pfd, start, contentLength, this)
                    }
                } else {
                    call.response.status(HttpStatusCode.OK)
                    call.response.header(HttpHeaders.ContentLength, totalBytes.toString())

                    call.respondOutputStream(contentType = parsedContentType, status = HttpStatusCode.OK) {
                        pipeFull(pfd, this)
                    }
                }
            } else {
                // 100% Reliable Fallback: Pure InputStream (Bypasses DownloadStorageProvider restrictions)
                val inputStream = this@ServerManager.context.contentResolver.openInputStream(file.uri)
                if (inputStream == null) {
                    call.respond(HttpStatusCode.InternalServerError, "File stream unreadable")
                    return@withContext
                }

                if (!asAttachment && rangeHeader != null && rangeHeader.startsWith("bytes=")) {
                    val ranges = rangeHeader.removePrefix("bytes=").split("-")
                    val start = ranges[0].toLongOrNull() ?: 0L
                    val end = if (ranges.size > 1 && ranges[1].isNotEmpty()) {
                        ranges[1].toLongOrNull() ?: (totalBytes - 1)
                    } else {
                        totalBytes - 1
                    }.coerceAtMost(totalBytes - 1)

                    val contentLength = (end - start) + 1
                    call.response.status(HttpStatusCode.PartialContent)
                    call.response.header(HttpHeaders.ContentRange, "bytes $start-$end/$totalBytes")
                    call.response.header(HttpHeaders.ContentLength, contentLength.toString())

                    call.respondOutputStream(contentType = parsedContentType, status = HttpStatusCode.PartialContent) {
                        inputStream.use { stream ->
                            stream.skip(start)
                            val buffer = ByteArray(64 * 1024)
                            var remaining = contentLength
                            while (remaining > 0) {
                                val toRead = minOf(buffer.size.toLong(), remaining).toInt()
                                val bytesRead = stream.read(buffer, 0, toRead)
                                if (bytesRead == -1) break
                                write(buffer, 0, bytesRead)
                                remaining -= bytesRead
                            }
                            flush()
                        }
                    }
                } else {
                    call.response.status(HttpStatusCode.OK)
                    call.response.header(HttpHeaders.ContentLength, totalBytes.toString())

                    call.respondOutputStream(contentType = parsedContentType, status = HttpStatusCode.OK) {
                        inputStream.use { stream ->
                            val buffer = ByteArray(64 * 1024)
                            var bytesRead: Int
                            while (stream.read(buffer).also { bytesRead = it } != -1) {
                                write(buffer, 0, bytesRead)
                            }
                            flush()
                        }
                    }
                }
            }
        } catch (_: io.ktor.utils.io.ClosedWriteChannelException) {
        } catch (_: java.io.IOException) {
        } catch (e: Exception) {
            try {
                call.respond(HttpStatusCode.InternalServerError, "Stream Error: ${e.message}")
            } catch (_: Exception) {}
        } finally {
            try {
                pfd?.close()
            } catch (_: Exception) {}
        }
    }

    private fun openFallbackDescriptor(uri: android.net.Uri, fileName: String): ParcelFileDescriptor? {
        return try {
            val inputStream = this@ServerManager.context.contentResolver.openInputStream(uri) ?: return null
            val tempFile = java.io.File(this@ServerManager.context.cacheDir, "stream_${System.currentTimeMillis()}_$fileName")
            tempFile.outputStream().use { out ->
                inputStream.copyTo(out)
            }
            tempFile.deleteOnExit()
            ParcelFileDescriptor.open(tempFile, ParcelFileDescriptor.MODE_READ_ONLY)
        } catch (_: Exception) {
            null
        }
    }

    private fun pipeFull(pfd: ParcelFileDescriptor, output: OutputStream) {
        val fis = FileInputStream(pfd.fileDescriptor)
        val buffer = ByteArray(64 * 1024)
        var read: Int
        while (fis.read(buffer).also { read = it } != -1) {
            output.write(buffer, 0, read)
        }
        output.flush()
    }

    private fun pipeRange(pfd: ParcelFileDescriptor, start: Long, length: Long, output: OutputStream) {
        val fis = FileInputStream(pfd.fileDescriptor)
        fis.channel.position(start)

        val buffer = ByteArray(64 * 1024)
        var remaining = length
        while (remaining > 0) {
            val toRead = minOf(buffer.size.toLong(), remaining).toInt()
            val read = fis.read(buffer, 0, toRead)
            if (read == -1) break
            output.write(buffer, 0, read)
            remaining -= read
        }
        output.flush()
    }

    private fun resolveMimeType(fileName: String, defaultMime: String): String {
        return when {
            fileName.endsWith(".pdf", ignoreCase = true) -> "application/pdf"
            fileName.endsWith(".png", ignoreCase = true) -> "image/png"
            fileName.endsWith(".jpg", ignoreCase = true) || fileName.endsWith(".jpeg", ignoreCase = true) -> "image/jpeg"
            fileName.endsWith(".gif", ignoreCase = true) -> "image/gif"
            fileName.endsWith(".mp4", ignoreCase = true) -> "video/mp4"
            fileName.endsWith(".mkv", ignoreCase = true) -> "video/webm"
            fileName.endsWith(".webm", ignoreCase = true) -> "video/webm"
            fileName.endsWith(".mp3", ignoreCase = true) -> "audio/mpeg"
            fileName.endsWith(".wav", ignoreCase = true) -> "audio/wav"
            else -> defaultMime
        }
    }

    fun stopServer() {
        try {
            engine?.stop(500, 1000)
            engine = null
        } catch (_: Exception) {}
    }
}
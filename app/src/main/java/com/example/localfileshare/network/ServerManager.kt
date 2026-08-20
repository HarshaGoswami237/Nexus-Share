package com.example.localfileshare.network

import android.content.Context
import android.net.Uri
import android.os.ParcelFileDescriptor
import com.example.localfileshare.data.SharedFile
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.call
import io.ktor.server.application.install
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import io.ktor.server.netty.NettyApplicationEngine
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.plugins.partialcontent.PartialContent
import io.ktor.server.response.header
import io.ktor.server.response.respond
import io.ktor.server.response.respondOutputStream
import io.ktor.server.response.respondText
import io.ktor.server.routing.get
import io.ktor.server.routing.routing
import java.io.FileInputStream
import java.io.InputStream

class ServerManager(
    private val appContext: Context,
    private val sharedFiles: List<SharedFile>
) {

    private var server: NettyApplicationEngine? = null

    // Class-level helpers to avoid Ktor scope shadowing
    private fun getInputStream(uri: Uri): InputStream? {
        return appContext.contentResolver.openInputStream(uri)
    }

    private fun getFileDescriptor(uri: Uri): ParcelFileDescriptor? {
        return appContext.contentResolver.openFileDescriptor(uri, "r")
    }

    fun startServer() {
        server = embeddedServer(Netty, port = 8080, host = "0.0.0.0") {

            install(ContentNegotiation) {
                json()
            }

            install(PartialContent) {
                maxRangeCount = 10
            }

            routing {
                get("/") {
                    val htmlContent = buildString {
                        append("<!DOCTYPE html><html><head><title>NEXUS SHARE</title>")
                        append("<meta name='viewport' content='width=device-width, initial-scale=1.0'>")
                        append("<style>")
                        append("body { background-color: #0d1117; color: #00FFCC; font-family: 'Courier New', monospace; padding: 20px; }")
                        append("h1 { text-shadow: 0 0 10px #00FFCC; text-align: center; border-bottom: 1px solid #00FFCC; padding-bottom: 10px; margin-bottom: 20px; }")
                        append(".file-card { background: #161b22; border: 1px solid #FF0055; padding: 15px; margin-bottom: 15px; border-radius: 4px; box-shadow: 0 0 8px rgba(255, 0, 85, 0.3); }")
                        append(".file-name { font-size: 1.1em; font-weight: bold; color: #FF0055; word-wrap: break-word; margin-bottom: 5px; }")
                        append(".file-size { color: #8b949e; font-size: 0.9em; margin-bottom: 15px; }")
                        append(".btn-group { display: flex; gap: 10px; }")
                        append(".btn { display: inline-block; text-decoration: none; padding: 10px 15px; font-weight: bold; border-radius: 2px; text-transform: uppercase; text-align: center; }")
                        append(".preview-btn { background: transparent; color: #00FFCC; border: 1px solid #00FFCC; }")
                        append(".preview-btn:hover { background: #00FFCC; color: #000; }")
                        append(".download-btn { background: #00FFCC; color: #000; }")
                        append(".download-btn:hover { background: #FFF; box-shadow: 0 0 10px #00FFCC; }")
                        append("</style></head><body>")

                        append("<h1>[ NEXUS SHARE NODE ]</h1>")

                        if (sharedFiles.isEmpty()) {
                            append("<p style='text-align: center; color: #8b949e;'>No files currently hosted in this sector.</p>")
                        } else {
                            sharedFiles.forEach { file ->
                                val sizeMb = String.format("%.2f", file.sizeBytes / (1024.0 * 1024.0))

                                append("<div class='file-card'>")
                                append("<div class='file-name'>${file.name}</div>")
                                append("<div class='file-size'>SIZE: $sizeMb MB</div>")
                                append("<div class='btn-group'>")
                                append("<a class='btn preview-btn' href='/preview/${file.id}' target='_blank'>PREVIEW</a>")
                                append("<a class='btn download-btn' href='/download/${file.id}'>DOWNLOAD</a>")
                                append("</div>")
                                append("</div>")
                            }
                        }
                        append("</body></html>")
                    }
                    call.respondText(htmlContent, ContentType.Text.Html)
                }

                get("/download/{id}") {
                    val fileId = call.parameters["id"]
                    val targetFile = sharedFiles.find { it.id == fileId }

                    if (targetFile?.uri != null) {
                        try {
                            val inputStream = getInputStream(targetFile.uri)

                            if (inputStream != null) {
                                call.response.header(
                                    HttpHeaders.ContentDisposition,
                                    "attachment; filename=\"${targetFile.name}\""
                                )

                                call.respondOutputStream(
                                    contentType = ContentType.Application.OctetStream,
                                    contentLength = targetFile.sizeBytes
                                ) {
                                    inputStream.use { input ->
                                        val buffer = ByteArray(128 * 1024)
                                        var bytesRead: Int
                                        while (input.read(buffer).also { bytesRead = it } != -1) {
                                            write(buffer, 0, bytesRead)
                                        }
                                    }
                                }
                            } else {
                                call.respond(HttpStatusCode.NotFound, "Unable to open stream")
                            }
                        } catch (e: Exception) {
                            call.respond(HttpStatusCode.InternalServerError, "Error reading file")
                        }
                    } else {
                        call.respond(HttpStatusCode.NotFound, "Invalid File ID")
                    }
                }

                get("/preview/{id}") {
                    val fileId = call.parameters["id"]
                    val targetFile = sharedFiles.find { it.id == fileId }

                    if (targetFile?.uri != null) {
                        val totalLength = targetFile.sizeBytes
                        val mimeType = when {
                            targetFile.name.endsWith(".mp4", ignoreCase = true) -> ContentType.Video.MP4
                            targetFile.name.endsWith(".mkv", ignoreCase = true) -> ContentType.parse("video/x-matroska")
                            targetFile.name.endsWith(".webm", ignoreCase = true) -> ContentType.parse("video/webm")
                            else -> ContentType.parse(targetFile.mimeType)
                        }

                        val rangeHeader = call.request.headers[HttpHeaders.Range]

                        if (rangeHeader != null && rangeHeader.startsWith("bytes=")) {
                            val rangeValues = rangeHeader.removePrefix("bytes=").split("-")
                            val start = rangeValues[0].toLongOrNull() ?: 0L
                            val end = rangeValues.getOrNull(1)?.toLongOrNull()?.takeIf { it in start until totalLength } ?: (totalLength - 1)
                            val contentLength = (end - start) + 1

                            call.response.status(HttpStatusCode.PartialContent)
                            call.response.header(HttpHeaders.ContentDisposition, "inline; filename=\"${targetFile.name}\"")
                            call.response.header(HttpHeaders.AcceptRanges, "bytes")
                            call.response.header(HttpHeaders.ContentRange, "bytes $start-$end/$totalLength")

                            call.respondOutputStream(
                                contentType = mimeType,
                                contentLength = contentLength
                            ) {
                                getFileDescriptor(targetFile.uri)?.use { pfd ->
                                    FileInputStream(pfd.fileDescriptor).use { fis ->
                                        fis.skip(start)
                                        val buffer = ByteArray(64 * 1024)
                                        var bytesLeft = contentLength
                                        while (bytesLeft > 0) {
                                            val readLen = Math.min(buffer.size.toLong(), bytesLeft).toInt()
                                            val bytesRead = fis.read(buffer, 0, readLen)
                                            if (bytesRead <= 0) break
                                            write(buffer, 0, bytesRead)
                                            bytesLeft -= bytesRead
                                        }
                                    }
                                }
                            }
                        } else {
                            call.response.header(HttpHeaders.ContentDisposition, "inline; filename=\"${targetFile.name}\"")
                            call.response.header(HttpHeaders.AcceptRanges, "bytes")

                            call.respondOutputStream(
                                contentType = mimeType,
                                contentLength = totalLength
                            ) {
                                getInputStream(targetFile.uri)?.use { input ->
                                    val buffer = ByteArray(64 * 1024)
                                    var bytesRead: Int
                                    while (input.read(buffer).also { bytesRead = it } != -1) {
                                        write(buffer, 0, bytesRead)
                                    }
                                }
                            }
                        }
                    } else {
                        call.respond(HttpStatusCode.NotFound, "File not found")
                    }
                }

                get("/files") {
                    call.respond(sharedFiles)
                }
            }
        }

        server?.start(wait = false)
    }

    fun stopServer() {
        server?.stop(1000, 2000)
    }
}
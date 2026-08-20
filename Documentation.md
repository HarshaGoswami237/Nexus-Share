# Nexus Share — System Architecture & Technical Documentation

A lightweight local file-sharing and media-streaming server built for Android using Kotlin and Ktor.

---

## 1. System Overview & Data Flow

When initiating a file session, the application executes a handshake between the UI controller, Android OS storage frameworks, and the local web engine.

                       ┌─────────────────────────┐
                       │      MainActivity       │
                       └────┬───────────────▲────┘
                            │               │
     1. Triggers Selector   │               │ 3. Updates List & UI
                            ▼               │
                       ┌────────────────────┴────┐
                       │   System File Picker    │
                       └────┬────────────────────┘
                            │
                            │ 2. Grants Content URI
                            ▼



---

## 2. Core Project Components

* **`MainActivity.kt` (UI Controller)**: Manages UI interactions, file queue state, and the **START / STOP HOSTING** toggle. Launches `ActivityResultContracts.GetMultipleContents()` to read files safely without broad storage permissions.
* **`ServerManager.kt` (Local Web Engine)**: Runs an embedded **Ktor 2.3.8 (Netty)** server on port `8080` bound to `0.0.0.0`. Serves the web dashboard, returns JSON metadata, and streams raw bytes using byte-range headers (`HTTP 206 Partial Content`).
* **`FileHelper.kt` (Storage Helper)**: Contains top-level helper functions interfacing with Android's `ContentResolver` to query file names, sizes, and MIME types into `SharedFile` objects.
* **`NetworkUtils.kt` (Network Resolver)**: Inspects active network interfaces to dynamically detect the current IPv4 address across standard Wi-Fi and Portable Hotspot modes.

---

## 3. Server Endpoints

| Route | Method | Header Behavior | Purpose |
| :--- | :--- | :--- | :--- |
| `/` | `GET` | `text/html` | Serves the Dark Sci-Fi web portal |
| `/download/{id}` | `GET` | `Content-Disposition: attachment` | Triggers direct browser download |
| `/preview/{id}` | `GET` | `Content-Disposition: inline` & `Accept-Ranges: bytes` | Streams video/audio with seek support |
| `/files` | `GET` | `application/json` | Returns JSON array of queued files |

---

## 4. Key Architecture & Fixes Implemented

* **Byte-Range Streaming (`HTTP 206`)**: Added manual byte skipping (`FileInputStream.skip()`) via `ParcelFileDescriptor` to prevent browser media player buffering.
* **Ktor Scope Shadowing Fix**: Isolated `ContentResolver` calls into helper functions outside Ktor routing closures to prevent reference collisions.
* **Hotspot Multi-Interface Binding**: Configured Ktor host binding to `0.0.0.0` so both Wi-Fi and SoftAP (Hotspot) clients can connect.
* **Coroutines Compatibility**: Pinned `kotlinx-coroutines` to `1.7.3` in Gradle to maintain runtime binary compatibility with Netty on Android.

---

## 5. Development Roadmap

1. **Foreground Service with Auto-Timeout**: Run Ktor in the background with a persistent notification and shut down on prolonged idle.
2. **Transfer Efficiency Tuning**: Optimize I/O buffers for higher transfer speeds.
3. **Multi-Client Concurrency**: Configure thread pooling to support up to 4 concurrent clients smoothly.
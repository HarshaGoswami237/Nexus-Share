# Nexus Share - System Architecture

## 1. System Overview
Nexus Share is a local network file-sharing app for Android.
This document explains how the app selects local files and extracts their details securely.

---

## 2. Data Flow

[User Interface]           [System File Picker]           [Android OS]
MainActivity   ──(1)──>  GetContent Contract  ──(2)──> Content Provider
│                                                         │
│                                                         │
└───(4) Display Name & Size  <───(3) Return Metadata ─────┘
(via FileHelper.kt)


1. **User Action:** User taps "Choose File" in `MainActivity`.
2. **Intent Launch:** `ActivityResultContracts.GetContent` opens the system file picker safely.
3. **Token Retrieval:** System returns a secure `Uri` (content token) rather than a raw file path.
4. **Metadata Extraction:** `FileHelper.kt` queries the system clerk (`ContentResolver`) to read file details.
5. **UI Update:** File name and formatted size appear on the screen.

---

## 3. Core Modules

### A. View Layer (`MainActivity.kt`)
* **Role:** Manages what the user sees and interacts with.
* **Design:** Holds references to UI elements (`TextView`, `Button`) and triggers the file selector.
* **Clean Code Rule:** View lookups (`findViewById`) happen once during setup (`onCreate`) to keep performance fast.

### B. Storage Helper (`FileHelper.kt`)
* **Role:** Extracts metadata from Android storage.
* **Design:** Uses **Top-Level Functions** (`getFileName`, `getFileSize`) so they can be called directly without creating class instances.

---

## 4. Key Concepts & Definitions

* **`Uri` (Uniform Resource Identifier):** An abstract security token (e.g., `content://...`) provided by Android to safely reference files without exposing real device file paths.
* **`ContentResolver`:** The internal gateway used to query system data safely using the `Uri`.
* **`Cursor`:** A database pointer that reads file information columns (`DISPLAY_NAME`, `SIZE`).

---

## 5. Math & Formatting Rules

* **Size Calculation:** Raw file sizes return in Bytes.
* **Conversion Standard:**
    * `Bytes / 1000.0` or `1024.0` $\rightarrow$ KB
    * `Bytes / (1000.0 * 1000.0)` or `(1024.0 * 1024.0)` $\rightarrow$ MB
* **Floating-Point Division:** Double values (`.0`) preserve accurate decimal points (e.g., `1.45 MB` instead of `1.00 MB`).
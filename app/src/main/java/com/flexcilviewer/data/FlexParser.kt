package com.flexcilviewer.data

import android.content.Context
import android.net.Uri
import com.google.gson.Gson
import com.google.gson.annotations.SerializedName
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.util.zip.ZipInputStream

private val gson = Gson()

private data class RawBackupInfo(
    @SerializedName("appName") val appName: String? = null,
    @SerializedName("backupDate") val backupDate: String? = null,
    @SerializedName("appVersion") val appVersion: String? = null,
    @SerializedName("version") val version: String? = null
)

private data class RawDocInfo(
    @SerializedName("name") val name: String? = null,
    @SerializedName("createDate") val createDate: Double? = null,
    @SerializedName("modifiedDate") val modifiedDate: Double? = null,
    @SerializedName("type") val type: Int? = null,
    @SerializedName("key") val key: String? = null,
    @SerializedName("id") val id: String? = null,
    @SerializedName("uuid") val uuid: String? = null
)

private data class RawAttachmentPage(
    @SerializedName("index") val index: Int? = null,
    @SerializedName("width") val width: Double? = null,
    @SerializedName("pageWidth") val pageWidth: Double? = null,
    @SerializedName("height") val height: Double? = null,
    @SerializedName("pageHeight") val pageHeight: Double? = null,
    @SerializedName("rotation") val rotation: Int? = null,
    @SerializedName("rotate") val rotate: Int? = null,
    @SerializedName("pdfPageIndex") val pdfPageIndex: Int? = null,
    @SerializedName("attachmentPageIndex") val attachmentPageIndex: Int? = null,
    @SerializedName("pageIndex") val pageIndex: Int? = null
)

private data class RawPageEntry(
    @SerializedName("index") val index: Int? = null,
    @SerializedName("width") val width: Double? = null,
    @SerializedName("pageWidth") val pageWidth: Double? = null,
    @SerializedName("height") val height: Double? = null,
    @SerializedName("pageHeight") val pageHeight: Double? = null,
    @SerializedName("rotation") val rotation: Int? = null,
    @SerializedName("rotate") val rotate: Int? = null,
    @SerializedName("pdfPageIndex") val pdfPageIndex: Int? = null,
    @SerializedName("attachmentPageIndex") val attachmentPageIndex: Int? = null,
    @SerializedName("pageIndex") val pageIndex: Int? = null,
    @SerializedName("attachmentPage") val attachmentPage: RawAttachmentPage? = null
)

private fun RawPageEntry.toPageInfo(fallbackIndex: Int): PageInfo {
    val ap = attachmentPage
    val idx = ap?.index ?: index ?: fallbackIndex
    val w = (ap?.width ?: ap?.pageWidth ?: width ?: pageWidth ?: 0.0).toFloat()
    val h = (ap?.height ?: ap?.pageHeight ?: height ?: pageHeight ?: 0.0).toFloat()
    val rot = ap?.rotation ?: ap?.rotate ?: rotation ?: rotate ?: 0
    val pdfIdx = ap?.pdfPageIndex ?: ap?.attachmentPageIndex ?: ap?.pageIndex
        ?: pdfPageIndex ?: attachmentPageIndex ?: pageIndex ?: fallbackIndex
    return PageInfo(index = idx, width = w, height = h, rotation = rot, pdfPage = pdfIdx)
}

/** Buffer size for streaming large ZIP entries. */
private const val STREAM_BUF = 64 * 1024 // 64 KB

/**
 * Parse a .flex backup file.
 *
 * PDFs are streamed directly to temp files in [context.cacheDir] — never fully loaded into RAM.
 * Call [cleanupTempPdfs] (or delete files in [FlexBackup] via ViewModel) when done.
 */
suspend fun parseFlexFile(context: Context, uri: Uri): FlexBackup = withContext(Dispatchers.IO) {
    var backupInfo = FlexBackupInfo()
    val rootFolders = mutableListOf<FolderNode>()

    val inputStream = if (uri.scheme == "file") {
        FileInputStream(File(uri.path ?: throw IllegalStateException("Invalid file path")))
    } else {
        context.contentResolver.openInputStream(uri)
            ?: throw IllegalStateException("Cannot open file")
    }

    ZipInputStream(inputStream.buffered(STREAM_BUF)).use { outerZip ->
        var entry = outerZip.nextEntry
        while (entry != null) {
            val entryName = entry.name

            when {
                entryName == "flexcilbackup/info" -> {
                    try {
                        val bytes = outerZip.readBytes()
                        val raw = gson.fromJson(String(bytes, Charsets.UTF_8), RawBackupInfo::class.java)
                        backupInfo = FlexBackupInfo(
                            appName = raw.appName ?: "Flexcil",
                            backupDate = raw.backupDate ?: "",
                            appVersion = raw.appVersion ?: "",
                            version = raw.version ?: ""
                        )
                    } catch (_: Exception) {}
                }

                entryName.endsWith(".flx") -> {
                    val parts = entryName.split("/")
                    if (parts.size >= 4 && parts[0] == "flexcilbackup" && parts[1] == "Documents") {
                        val folderParts = parts.subList(2, parts.size - 1)
                        val fileName = parts.last().removeSuffix(".flx")
                        if (folderParts.isNotEmpty()) {
                            // ── Stream the .flx entry to a temp file (no full ByteArray) ──
                            val flxTemp = File.createTempFile("flx_entry_", ".flx", context.cacheDir)
                            try {
                                FileOutputStream(flxTemp).use { out -> outerZip.copyTo(out, STREAM_BUF) }
                                val leafFolder = ensureNestedFolder(rootFolders, folderParts)
                                val doc = parseFlxDoc(fileName, flxTemp, context.cacheDir)
                                leafFolder.documents.add(doc)
                            } finally {
                                flxTemp.delete() // temp outer entry no longer needed
                            }
                        }
                    }
                }
            }

            outerZip.closeEntry()
            entry = outerZip.nextEntry
        }
    }

    sortTree(rootFolders)
    FlexBackup(
        info = backupInfo,
        rootFolders = rootFolders,
        totalDocuments = countDocuments(rootFolders)
    )
}

private fun ensureNestedFolder(roots: MutableList<FolderNode>, parts: List<String>): FolderNode {
    var current = roots
    var fullPath = ""
    var leaf: FolderNode? = null
    for (part in parts) {
        fullPath = if (fullPath.isEmpty()) part else "$fullPath/$part"
        leaf = current.find { it.name == part }
        if (leaf == null) {
            leaf = FolderNode(name = part, fullPath = fullPath)
            current.add(leaf)
        }
        current = leaf.subfolders
    }
    return leaf!!
}

/**
 * Parse a single .flx document (itself a ZIP), streaming the PDF to a temp file.
 * The returned [FlexDocument.pdfFilePath] points to a temp file that the caller must eventually delete.
 */
private fun parseFlxDoc(name: String, flxFile: File, cacheDir: File): FlexDocument {
    var info: FlxDocInfo? = null
    var thumbnail: ByteArray? = null
    var pdfFilePath: String? = null
    var pages = emptyList<PageInfo>()
    var pageCount = 0
    var annotationFileCount = 0
    var strokeFileCount = 0
    var highlightFileCount = 0
    val flxSize = flxFile.length()

    try {
        ZipInputStream(FileInputStream(flxFile).buffered(STREAM_BUF)).use { inner ->
            var entry = inner.nextEntry
            while (entry != null) {
                val n = entry.name
                when {
                    n == "info" -> {
                        try {
                            val raw = gson.fromJson(String(inner.readBytes(), Charsets.UTF_8), RawDocInfo::class.java)
                            info = FlxDocInfo(
                                name = raw.name ?: name,
                                createDate = raw.createDate?.toLong() ?: 0L,
                                modifiedDate = raw.modifiedDate?.toLong() ?: 0L,
                                type = raw.type ?: 0,
                                key = raw.key?.takeIf { it.isNotBlank() }
                                    ?: raw.id?.takeIf { it.isNotBlank() }
                                    ?: raw.uuid ?: ""
                            )
                        } catch (_: Exception) {}
                    }
                    n == "thumbnail" || n == "thumbnail@2x" -> {
                        // Thumbnails are small (< 1 MB typically) — safe to keep in RAM
                        if (thumbnail == null) thumbnail = inner.readBytes()
                    }
                    n.startsWith("attachment/PDF/") -> {
                        // ── Stream PDF directly to temp file — never into RAM ──
                        val pdfTemp = File.createTempFile("flex_pdf_", ".pdf", cacheDir)
                        FileOutputStream(pdfTemp).use { out -> inner.copyTo(out, STREAM_BUF) }
                        pdfFilePath = pdfTemp.absolutePath
                    }
                    n == "pages.index" -> {
                        try {
                            val pagesJson = String(inner.readBytes(), Charsets.UTF_8)
                            val rawPages = gson.fromJson(pagesJson, Array<RawPageEntry>::class.java)
                            pages = rawPages.mapIndexed { i, raw -> raw.toPageInfo(i) }
                            pageCount = pages.size
                        } catch (_: Exception) { inner.skip(Long.MAX_VALUE) }
                    }
                    n.endsWith(".drawings") -> { strokeFileCount++; inner.skip(Long.MAX_VALUE) }
                    n.endsWith(".annotations") -> { annotationFileCount++; inner.skip(Long.MAX_VALUE) }
                    n.endsWith(".highlights") -> { highlightFileCount++; inner.skip(Long.MAX_VALUE) }
                    else -> inner.skip(Long.MAX_VALUE)
                }
                inner.closeEntry()
                entry = inner.nextEntry
            }
        }
    } catch (_: Exception) {}

    return FlexDocument(
        name = info?.name?.takeIf { it.isNotBlank() } ?: name,
        flxSize = flxSize,
        info = info,
        thumbnail = thumbnail,
        pdfFilePath = pdfFilePath,
        pageCount = pageCount,
        pages = pages,
        annotationFileCount = annotationFileCount,
        strokeFileCount = strokeFileCount,
        highlightFileCount = highlightFileCount
    )
}

private fun sortTree(folders: MutableList<FolderNode>) {
    folders.sortBy { it.name }
    for (folder in folders) {
        folder.documents.sortByDescending { it.info?.modifiedDate ?: 0L }
        sortTree(folder.subfolders)
    }
}

private fun countDocuments(folders: List<FolderNode>): Int =
    folders.sumOf { it.documents.size + countDocuments(it.subfolders) }

// ── Helpers ────────────────────────────────────────────────────────────────────

// Apple Core Data epoch (NSDate) is seconds since 2001-01-01 UTC
// Unix epoch is seconds since 1970-01-01 UTC; difference = 978307200 s
private const val APPLE_EPOCH_OFFSET_S = 978_307_200L

fun formatDate(timestamp: Long): String {
    if (timestamp == 0L) return "Unknown"
    val unixMs: Long = when {
        timestamp > 1_000_000_000_000L -> timestamp           // already ms
        timestamp > 1_000_000_000L    -> timestamp * 1000L   // Unix seconds (year 2001+)
        else                          -> (timestamp + APPLE_EPOCH_OFFSET_S) * 1000L // Apple epoch seconds
    }
    val sdf = java.text.SimpleDateFormat("dd MMM yyyy, hh:mm a", java.util.Locale.getDefault())
    return sdf.format(java.util.Date(unixMs))
}

fun formatFileSize(bytes: Long): String = when {
    bytes < 1024 -> "$bytes B"
    bytes < 1024 * 1024 -> "%.1f KB".format(bytes / 1024.0)
    else -> "%.1f MB".format(bytes / (1024.0 * 1024.0))
}

fun friendlyPath(path: String): String =
    path.trimStart('/').replace('/', " › ").ifBlank { "Root" }

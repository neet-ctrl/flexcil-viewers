package com.flexcilviewer.data

import android.content.Context
import android.net.Uri
import com.google.gson.Gson
import com.google.gson.annotations.SerializedName
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
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
    @SerializedName("createDate") val createDate: Long? = null,
    @SerializedName("modifiedDate") val modifiedDate: Long? = null,
    @SerializedName("type") val type: Int? = null,
    @SerializedName("key") val key: String? = null
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
    @SerializedName("attachmentPage") val attachmentPage: RawAttachmentPage? = null,
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

suspend fun parseFlexFile(context: Context, uri: Uri): FlexBackup = withContext(Dispatchers.IO) {
    var backupInfo = FlexBackupInfo()
    val rootFolders = mutableListOf<FolderNode>()

    val inputStream = if (uri.scheme == "file") {
        java.io.FileInputStream(java.io.File(uri.path ?: throw IllegalStateException("Invalid file path")))
    } else {
        context.contentResolver.openInputStream(uri)
            ?: throw IllegalStateException("Cannot open file")
    }

    ZipInputStream(inputStream.buffered(1024 * 64)).use { outerZip ->
        var entry = outerZip.nextEntry
        while (entry != null) {
            val entryName = entry.name

            when {
                entryName == "flexcilbackup/info" -> {
                    val bytes = outerZip.readBytes()
                    try {
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
                            val flxBytes = outerZip.readBytes()
                            val leafFolder = ensureNestedFolder(rootFolders, folderParts)
                            val doc = parseFlxDoc(fileName, flxBytes)
                            leafFolder.documents.add(doc)
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

private fun parseFlxDoc(name: String, bytes: ByteArray): FlexDocument {
    var info: FlxDocInfo? = null
    var thumbnail: ByteArray? = null
    var pdfData: ByteArray? = null
    var pages = emptyList<PageInfo>()
    var pageCount = 0
    var annotationFileCount = 0
    var strokeFileCount = 0
    var highlightFileCount = 0

    try {
        ZipInputStream(ByteArrayInputStream(bytes)).use { inner ->
            var entry = inner.nextEntry
            while (entry != null) {
                val n = entry.name
                when {
                    n == "info" -> {
                        try {
                            val raw = gson.fromJson(String(inner.readBytes(), Charsets.UTF_8), RawDocInfo::class.java)
                            info = FlxDocInfo(
                                name = raw.name ?: name,
                                createDate = raw.createDate ?: 0L,
                                modifiedDate = raw.modifiedDate ?: 0L,
                                type = raw.type ?: 0,
                                key = raw.key ?: ""
                            )
                        } catch (_: Exception) {}
                    }
                    n == "thumbnail" || n == "thumbnail@2x" -> {
                        if (thumbnail == null) thumbnail = inner.readBytes()
                    }
                    n.startsWith("attachment/PDF/") -> {
                        pdfData = inner.readBytes()
                    }
                    n == "pages.index" -> {
                        try {
                            val pagesJson = String(inner.readBytes(), Charsets.UTF_8)
                            val rawPages = gson.fromJson(pagesJson, Array<RawPageEntry>::class.java)
                            pages = rawPages.mapIndexed { i, raw -> raw.toPageInfo(i) }
                            pageCount = pages.size
                        } catch (_: Exception) { inner.skip(Long.MAX_VALUE) }
                    }
                    n.endsWith(".drawings") -> {
                        strokeFileCount++
                        inner.skip(Long.MAX_VALUE)
                    }
                    n.endsWith(".annotations") -> {
                        annotationFileCount++
                        inner.skip(Long.MAX_VALUE)
                    }
                    n.endsWith(".highlights") -> {
                        highlightFileCount++
                        inner.skip(Long.MAX_VALUE)
                    }
                    else -> inner.skip(Long.MAX_VALUE)
                }
                inner.closeEntry()
                entry = inner.nextEntry
            }
        }
    } catch (_: Exception) {}

    return FlexDocument(
        name = info?.name?.takeIf { it.isNotBlank() } ?: name,
        flxSize = bytes.size.toLong(),
        info = info,
        thumbnail = thumbnail,
        pdfData = pdfData,
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

fun formatDate(timestamp: Long): String {
    if (timestamp == 0L) return "Unknown"
    val ms = if (timestamp > 1_000_000_000_000L) timestamp else timestamp * 1000L
    val sdf = java.text.SimpleDateFormat("dd MMM yyyy, hh:mm a", java.util.Locale.getDefault())
    return sdf.format(java.util.Date(ms))
}

fun formatFileSize(bytes: Long): String = when {
    bytes < 1024 -> "$bytes B"
    bytes < 1024 * 1024 -> "%.1f KB".format(bytes / 1024.0)
    else -> "%.1f MB".format(bytes / (1024.0 * 1024.0))
}

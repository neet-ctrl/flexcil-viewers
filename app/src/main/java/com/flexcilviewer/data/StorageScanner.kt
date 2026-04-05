package com.flexcilviewer.data

import android.content.ContentUris
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

data class ScannedFlexFile(
    val uri: Uri,
    val name: String,
    val path: String,
    val size: Long,
    val dateModified: Long
)

suspend fun scanForFlexFiles(context: Context): List<ScannedFlexFile> = withContext(Dispatchers.IO) {
    val results = mutableListOf<ScannedFlexFile>()
    val seen = mutableSetOf<String>()

    // ── 1. MediaStore scan (works on all API levels for indexed files) ────────
    try {
        val collection = MediaStore.Files.getContentUri("external")
        val projection = arrayOf(
            MediaStore.Files.FileColumns._ID,
            MediaStore.Files.FileColumns.DISPLAY_NAME,
            MediaStore.Files.FileColumns.DATA,
            MediaStore.Files.FileColumns.SIZE,
            MediaStore.Files.FileColumns.DATE_MODIFIED
        )
        val selection = "${MediaStore.Files.FileColumns.DISPLAY_NAME} LIKE ?"
        val selectionArgs = arrayOf("%.flex")
        val sortOrder = "${MediaStore.Files.FileColumns.DATE_MODIFIED} DESC"

        context.contentResolver.query(collection, projection, selection, selectionArgs, sortOrder)
            ?.use { cursor ->
                val idCol = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns._ID)
                val nameCol = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.DISPLAY_NAME)
                val dataCol = cursor.getColumnIndex(MediaStore.Files.FileColumns.DATA)
                val sizeCol = cursor.getColumnIndex(MediaStore.Files.FileColumns.SIZE)
                val dateCol = cursor.getColumnIndex(MediaStore.Files.FileColumns.DATE_MODIFIED)

                while (cursor.moveToNext()) {
                    val id = cursor.getLong(idCol)
                    val name = cursor.getString(nameCol) ?: continue
                    if (!name.endsWith(".flex", ignoreCase = true)) continue
                    val path = if (dataCol >= 0) cursor.getString(dataCol) ?: "" else ""
                    val size = if (sizeCol >= 0) cursor.getLong(sizeCol) else 0L
                    val date = if (dateCol >= 0) cursor.getLong(dateCol) * 1000L else 0L
                    val uri = ContentUris.withAppendedId(collection, id)
                    val key = path.ifBlank { id.toString() }
                    if (seen.add(key)) {
                        results.add(ScannedFlexFile(uri = uri, name = name, path = path, size = size, dateModified = date))
                    }
                }
            }
    } catch (_: Exception) {}

    // ── 2. Filesystem scan of common directories (covers non-indexed files) ──
    val scanDirs = buildList {
        add(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS))
        add(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS))
        add(Environment.getExternalStorageDirectory())
        // Flexcil-specific app data directories
        File(Environment.getExternalStorageDirectory(), "Android/data").let { androidData ->
            if (androidData.exists()) {
                androidData.listFiles()
                    ?.filter { it.name.contains("flexcil", ignoreCase = true) }
                    ?.forEach { add(it) }
            }
        }
    }

    for (dir in scanDirs) {
        try {
            if (!dir.exists() || !dir.canRead()) continue
            dir.walkTopDown()
                .onEnter { it.canRead() }
                .filter { it.isFile && it.name.endsWith(".flex", ignoreCase = true) }
                .forEach { file ->
                    val key = file.absolutePath
                    if (seen.add(key)) {
                        val uri = Uri.fromFile(file)
                        results.add(
                            ScannedFlexFile(
                                uri = uri,
                                name = file.name,
                                path = file.absolutePath,
                                size = file.length(),
                                dateModified = file.lastModified()
                            )
                        )
                    }
                }
        } catch (_: Exception) {}
    }

    // Sort by most recently modified
    results.sortedByDescending { it.dateModified }
}

fun friendlyPath(path: String): String {
    val extStorage = Environment.getExternalStorageDirectory().absolutePath
    return path.removePrefix(extStorage).trimStart('/').ifBlank { path }
}

package com.flexcilviewer.viewmodel

import android.app.Application
import android.content.Context
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.flexcilviewer.data.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

sealed class ParseState {
    object Idle : ParseState()
    data class Loading(val progress: String = "Opening file…") : ParseState()
    data class Success(val backup: FlexBackup) : ParseState()
    data class Error(val message: String) : ParseState()
}

sealed class ExportState {
    object Idle : ExportState()
    object Running : ExportState()
    data class Done(val count: Int) : ExportState()
    data class Error(val message: String) : ExportState()
}

class FlexViewModel(application: Application) : AndroidViewModel(application) {

    private val _parseState = MutableStateFlow<ParseState>(ParseState.Idle)
    val parseState: StateFlow<ParseState> = _parseState.asStateFlow()

    private val _selectedDocument = MutableStateFlow<FlexDocument?>(null)
    val selectedDocument: StateFlow<FlexDocument?> = _selectedDocument.asStateFlow()

    private val _selectedFolder = MutableStateFlow<FolderNode?>(null)
    val selectedFolder: StateFlow<FolderNode?> = _selectedFolder.asStateFlow()

    private val _checkedDocs = MutableStateFlow<Set<String>>(emptySet())
    val checkedDocs: StateFlow<Set<String>> = _checkedDocs.asStateFlow()

    private val _exportState = MutableStateFlow<ExportState>(ExportState.Idle)
    val exportState: StateFlow<ExportState> = _exportState.asStateFlow()

    fun openFile(context: Context, uri: Uri) {
        viewModelScope.launch {
            _parseState.value = ParseState.Loading("Reading file…")
            _selectedDocument.value = null
            _selectedFolder.value = null
            _checkedDocs.value = emptySet()
            try {
                _parseState.value = ParseState.Loading("Parsing backup…")
                val backup = parseFlexFile(context, uri)
                _parseState.value = ParseState.Success(backup)
                // auto-select first folder
                _selectedFolder.value = backup.rootFolders.firstOrNull()
            } catch (e: Exception) {
                _parseState.value = ParseState.Error("Failed to read file: ${e.message}")
            }
        }
    }

    fun selectDocument(doc: FlexDocument) {
        _selectedDocument.value = doc
    }

    fun selectFolder(folder: FolderNode) {
        _selectedFolder.value = folder
        _selectedDocument.value = null
    }

    fun toggleDocCheck(key: String) {
        val current = _checkedDocs.value.toMutableSet()
        if (key in current) current.remove(key) else current.add(key)
        _checkedDocs.value = current
    }

    fun checkAllInFolder(folder: FolderNode) {
        val all = getAllDocuments(listOf(folder)).map { "${it.second}/${it.first.name}" }.toSet()
        _checkedDocs.value = _checkedDocs.value + all
    }

    fun deselectAll() {
        _checkedDocs.value = emptySet()
    }

    fun exportToFolder(context: Context, folderUri: Uri, docs: List<Pair<FlexDocument, String>>) {
        viewModelScope.launch {
            _exportState.value = ExportState.Running
            try {
                var count = 0
                for ((doc, folderPath) in docs) {
                    val pdfBytes = doc.pdfData ?: continue
                    val safeName = doc.name.replace(Regex("[/\\\\:*?\"<>|]"), "_") + ".pdf"
                    val docUri = context.contentResolver.let { cr ->
                        val parentUri = folderUri
                        val newDocUri = androidx.documentfile.provider.DocumentFile
                            .fromTreeUri(context, parentUri)
                            ?.createFile("application/pdf", safeName)
                            ?.uri ?: return@let null
                        newDocUri
                    }
                    if (docUri != null) {
                        context.contentResolver.openOutputStream(docUri)?.use { out ->
                            out.write(pdfBytes)
                        }
                        count++
                    }
                }
                _exportState.value = ExportState.Done(count)
            } catch (e: Exception) {
                _exportState.value = ExportState.Error(e.message ?: "Export failed")
            }
        }
    }

    fun exportAsZip(context: Context, folderUri: Uri, docs: List<Pair<FlexDocument, String>>) {
        viewModelScope.launch {
            _exportState.value = ExportState.Running
            try {
                val zipName = "flexcil_export_${System.currentTimeMillis()}.zip"
                val zipDocUri = androidx.documentfile.provider.DocumentFile
                    .fromTreeUri(context, folderUri)
                    ?.createFile("application/zip", zipName)
                    ?.uri ?: throw Exception("Cannot create ZIP file")

                context.contentResolver.openOutputStream(zipDocUri)?.use { out ->
                    ZipOutputStream(out).use { zos ->
                        for ((doc, folderPath) in docs) {
                            val pdfBytes = doc.pdfData ?: continue
                            val entryName = "$folderPath/${doc.name}.pdf"
                            zos.putNextEntry(ZipEntry(entryName))
                            zos.write(pdfBytes)
                            zos.closeEntry()
                        }
                    }
                }
                _exportState.value = ExportState.Done(docs.size)
            } catch (e: Exception) {
                _exportState.value = ExportState.Error(e.message ?: "Export failed")
            }
        }
    }

    fun clearExportState() { _exportState.value = ExportState.Idle }
    fun clearError() { _parseState.value = ParseState.Idle }
}

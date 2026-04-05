package com.flexcilviewer.data

data class FlexBackupInfo(
    val appName: String = "Flexcil",
    val backupDate: String = "",
    val appVersion: String = "",
    val version: String = ""
)

data class FlxDocInfo(
    val name: String = "",
    val createDate: Long = 0L,
    val modifiedDate: Long = 0L,
    val type: Int = 0,
    val key: String = ""
)

data class PageInfo(
    val index: Int = 0,
    val width: Float = 0f,
    val height: Float = 0f,
    val rotation: Int = 0,
    val pdfPage: Int = 0
)

data class FlexDocument(
    val name: String,
    val flxSize: Long,
    val info: FlxDocInfo? = null,
    val thumbnail: ByteArray? = null,
    /** Path to a temp file containing the extracted PDF, or null if no PDF in this doc. */
    val pdfFilePath: String? = null,
    val pageCount: Int = 0,
    val pages: List<PageInfo> = emptyList(),
    val annotationFileCount: Int = 0,
    val strokeFileCount: Int = 0,
    val highlightFileCount: Int = 0
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is FlexDocument) return false
        return name == other.name && flxSize == other.flxSize
    }
    override fun hashCode(): Int = 31 * name.hashCode() + flxSize.hashCode()
}

data class FolderNode(
    val name: String,
    val fullPath: String,
    val subfolders: MutableList<FolderNode> = mutableListOf(),
    val documents: MutableList<FlexDocument> = mutableListOf()
) {
    val totalDocuments: Int get() = documents.size + subfolders.sumOf { it.totalDocuments }
}

data class FlexBackup(
    val info: FlexBackupInfo,
    val rootFolders: List<FolderNode>,
    val totalDocuments: Int
)

fun getAllDocuments(folders: List<FolderNode>): List<Pair<FlexDocument, String>> {
    val result = mutableListOf<Pair<FlexDocument, String>>()
    for (folder in folders) {
        for (doc in folder.documents) result.add(doc to folder.fullPath)
        result.addAll(getAllDocuments(folder.subfolders))
    }
    return result
}

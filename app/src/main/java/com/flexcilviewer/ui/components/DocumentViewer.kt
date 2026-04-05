package com.flexcilviewer.ui.components

import android.graphics.BitmapFactory
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.flexcilviewer.data.FlexDocument
import com.flexcilviewer.data.formatDate
import com.flexcilviewer.data.formatFileSize
import com.flexcilviewer.ui.theme.*

private enum class ViewerTab(val label: String, val icon: androidx.compose.ui.graphics.vector.ImageVector) {
    PDF("PDF", Icons.Default.PictureAsPdf),
    PREVIEW("Preview", Icons.Default.Image),
    ANNOTATIONS("Annotations", Icons.Default.Draw),
    DETAILS("Details", Icons.Default.Info)
}

@Composable
fun DocumentViewer(
    doc: FlexDocument,
    folderPath: String = "",
    onExportClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val annotationCount = doc.strokeFileCount + doc.annotationFileCount + doc.highlightFileCount
    val displayName = doc.info?.name?.takeIf { it.isNotBlank() } ?: doc.name

    val visibleTabs = remember(doc) {
        buildList {
            if (doc.pdfData != null) add(ViewerTab.PDF)
            if (doc.thumbnail != null) add(ViewerTab.PREVIEW)
            if (annotationCount > 0) add(ViewerTab.ANNOTATIONS)
            add(ViewerTab.DETAILS)
        }
    }

    var selectedTab by remember(doc) {
        mutableStateOf(
            when {
                doc.pdfData != null -> ViewerTab.PDF
                doc.thumbnail != null -> ViewerTab.PREVIEW
                else -> ViewerTab.DETAILS
            }
        )
    }

    LaunchedEffect(visibleTabs) {
        if (selectedTab !in visibleTabs) selectedTab = visibleTabs.first()
    }

    var copiedName by remember { mutableStateOf(false) }
    val clipboardManager = androidx.compose.ui.platform.LocalClipboardManager.current

    // ── Pending save state for file-picker based saving ──────────────────────
    var pendingSaveBytes by remember { mutableStateOf<ByteArray?>(null) }
    var pendingSaveName by remember { mutableStateOf("") }
    var pendingSaveMime by remember { mutableStateOf("application/octet-stream") }

    val saveLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("*/*")
    ) { uri: Uri? ->
        uri?.let { dest ->
            val bytes = pendingSaveBytes ?: return@let
            try {
                context.contentResolver.openOutputStream(dest)?.use { it.write(bytes) }
            } catch (_: Exception) {}
            pendingSaveBytes = null
        }
    }

    fun requestSave(bytes: ByteArray, fileName: String, mimeType: String) {
        pendingSaveBytes = bytes
        pendingSaveName = fileName
        pendingSaveMime = mimeType
        saveLauncher.launch(fileName)
    }

    Column(modifier = modifier.background(BackgroundDark)) {

        // ── Header ───────────────────────────────────────────────────────────
        Surface(color = SurfaceDark, modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(start = 16.dp, end = 8.dp, top = 12.dp, bottom = 8.dp)) {

                if (folderPath.isNotBlank()) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(Icons.Default.Layers, contentDescription = null, tint = TextMuted, modifier = Modifier.size(12.dp))
                        Text(
                            folderPath,
                            style = MaterialTheme.typography.labelSmall,
                            color = TextMuted,
                            maxLines = 1,
                            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                        )
                    }
                    Spacer(Modifier.height(2.dp))
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        displayName,
                        style = MaterialTheme.typography.titleMedium,
                        color = TextPrimary,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 2,
                        modifier = Modifier.weight(1f)
                    )
                    IconButton(
                        onClick = {
                            clipboardManager.setText(androidx.compose.ui.text.AnnotatedString(displayName))
                            copiedName = true
                        },
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            if (copiedName) Icons.Default.Check else Icons.Default.ContentCopy,
                            contentDescription = "Copy name",
                            tint = if (copiedName) AccentGreen else TextMuted,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                    LaunchedEffect(copiedName) {
                        if (copiedName) {
                            kotlinx.coroutines.delay(2000)
                            copiedName = false
                        }
                    }
                }

                Row(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.padding(top = 2.dp)
                ) {
                    doc.info?.createDate?.takeIf { it > 0 }?.let {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                            Icon(Icons.Default.CalendarToday, contentDescription = null, tint = TextMuted, modifier = Modifier.size(11.dp))
                            Text("Created: ${formatDate(it)}", style = MaterialTheme.typography.labelSmall, color = TextMuted)
                        }
                    }
                    doc.info?.modifiedDate?.takeIf { it > 0 }?.let {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                            Icon(Icons.Default.Schedule, contentDescription = null, tint = TextMuted, modifier = Modifier.size(11.dp))
                            Text("Modified: ${formatDate(it)}", style = MaterialTheme.typography.labelSmall, color = TextMuted)
                        }
                    }
                    if (doc.flxSize > 0) {
                        Text(formatFileSize(doc.flxSize), style = MaterialTheme.typography.labelSmall, color = TextMuted)
                    }
                }

                Spacer(Modifier.height(8.dp))

                // ── Download buttons (PDF, Preview, ZIP) ──────────────────
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (doc.pdfData != null) {
                        Button(
                            onClick = { requestSave(doc.pdfData, "$displayName.pdf", "application/pdf") },
                            colors = ButtonDefaults.buttonColors(containerColor = PrimaryIndigo),
                            shape = RoundedCornerShape(8.dp),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                        ) {
                            Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(14.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("PDF", style = MaterialTheme.typography.labelMedium)
                        }
                    }
                    if (doc.thumbnail != null) {
                        OutlinedButton(
                            onClick = { requestSave(doc.thumbnail, "${displayName}_preview.jpg", "image/jpeg") },
                            shape = RoundedCornerShape(8.dp),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = TextSecondary)
                        ) {
                            Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(14.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Preview", style = MaterialTheme.typography.labelMedium)
                        }
                    }
                    if (doc.pdfData != null || doc.thumbnail != null) {
                        OutlinedButton(
                            onClick = onExportClick,
                            shape = RoundedCornerShape(8.dp),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = TextMuted)
                        ) {
                            Icon(Icons.Default.Inventory2, contentDescription = null, modifier = Modifier.size(14.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("ZIP", style = MaterialTheme.typography.labelMedium)
                        }
                    }
                }
            }
        }

        // ── Tab row ──────────────────────────────────────────────────────────
        Surface(color = SurfaceDark, modifier = Modifier.fillMaxWidth()) {
            ScrollableTabRow(
                selectedTabIndex = visibleTabs.indexOf(selectedTab).coerceAtLeast(0),
                containerColor = SurfaceDark,
                contentColor = PrimaryIndigoLight,
                edgePadding = 0.dp,
                indicator = { tabPositions ->
                    val idx = visibleTabs.indexOf(selectedTab).coerceAtLeast(0)
                    if (idx < tabPositions.size) {
                        TabRowDefaults.SecondaryIndicator(
                            modifier = Modifier.tabIndicatorOffset(tabPositions[idx]),
                            color = PrimaryIndigo
                        )
                    }
                },
                divider = { HorizontalDivider(color = DividerColor) }
            ) {
                visibleTabs.forEach { tab ->
                    Tab(
                        selected = selectedTab == tab,
                        onClick = { selectedTab = tab },
                        text = {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Icon(
                                    tab.icon,
                                    contentDescription = null,
                                    modifier = Modifier.size(14.dp),
                                    tint = if (selectedTab == tab) PrimaryIndigoLight else TextSecondary
                                )
                                Text(
                                    tab.label,
                                    style = MaterialTheme.typography.labelMedium,
                                    color = if (selectedTab == tab) PrimaryIndigoLight else TextSecondary
                                )
                                if (tab == ViewerTab.ANNOTATIONS && annotationCount > 0) {
                                    Surface(
                                        color = if (selectedTab == tab) PrimaryIndigoLight.copy(alpha = 0.2f) else PrimaryIndigoDark.copy(alpha = 0.4f),
                                        shape = RoundedCornerShape(50)
                                    ) {
                                        Text(
                                            "$annotationCount",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = PrimaryIndigoLight,
                                            fontWeight = FontWeight.Bold,
                                            modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp)
                                        )
                                    }
                                }
                            }
                        }
                    )
                }
            }
        }

        // ── Tab content ───────────────────────────────────────────────────────
        when (selectedTab) {
            ViewerTab.PDF -> {
                if (doc.pdfData != null) PdfViewer(pdfBytes = doc.pdfData, modifier = Modifier.fillMaxSize())
            }
            ViewerTab.PREVIEW -> PreviewPane(doc = doc, displayName = displayName, modifier = Modifier.fillMaxSize())
            ViewerTab.ANNOTATIONS -> AnnotationsPane(doc = doc, modifier = Modifier.fillMaxSize())
            ViewerTab.DETAILS -> DetailsPane(
                doc = doc,
                displayName = displayName,
                folderPath = folderPath,
                onSaveFile = ::requestSave,
                modifier = Modifier.fillMaxSize()
            )
        }
    }
}

// ─── Preview tab ──────────────────────────────────────────────────────────────

@Composable
private fun PreviewPane(doc: FlexDocument, displayName: String, modifier: Modifier = Modifier) {
    val bitmap = remember(doc.thumbnail) {
        doc.thumbnail?.let { bytes ->
            runCatching { BitmapFactory.decodeByteArray(bytes, 0, bytes.size) }.getOrNull()
        }
    }

    Box(
        modifier = modifier.background(BackgroundDark).padding(20.dp),
        contentAlignment = Alignment.Center
    ) {
        if (bitmap != null) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Surface(shape = RoundedCornerShape(12.dp), color = CardDark, shadowElevation = 4.dp) {
                    Image(
                        bitmap = bitmap.asImageBitmap(),
                        contentDescription = "Preview for $displayName",
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 500.dp),
                        contentScale = ContentScale.Fit
                    )
                }
                Text(
                    "Document cover preview generated by Flexcil",
                    color = TextMuted,
                    style = MaterialTheme.typography.labelSmall
                )
            }
        } else {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(Icons.Default.HideImage, contentDescription = null, tint = TextMuted, modifier = Modifier.size(48.dp))
                Spacer(Modifier.height(12.dp))
                Text("No preview available", color = TextSecondary, style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(4.dp))
                Text("This document does not have a thumbnail image.", color = TextMuted, style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}

// ─── Annotations tab ──────────────────────────────────────────────────────────

@Composable
private fun AnnotationsPane(doc: FlexDocument, modifier: Modifier = Modifier) {
    val total = doc.strokeFileCount + doc.annotationFileCount + doc.highlightFileCount
    val scroll = rememberScrollState()

    Column(
        modifier = modifier.verticalScroll(scroll).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            "Handwriting & Annotations",
            style = MaterialTheme.typography.titleMedium,
            color = TextPrimary,
            fontWeight = FontWeight.SemiBold
        )
        Text(
            "This document contains $total annotation page${if (total != 1) "s" else ""} (pen strokes, highlights, text marks).",
            style = MaterialTheme.typography.bodyMedium,
            color = TextSecondary
        )

        if (doc.strokeFileCount > 0) {
            AnnotationCard(
                title = "Pen Strokes",
                badge = "${doc.strokeFileCount} page${if (doc.strokeFileCount != 1) "s" else ""}",
                types = listOf("Pen"),
                icon = Icons.Default.Draw,
                iconTint = PrimaryIndigoLight
            )
        }
        if (doc.annotationFileCount > 0) {
            AnnotationCard(
                title = "Annotations",
                badge = "${doc.annotationFileCount} page${if (doc.annotationFileCount != 1) "s" else ""}",
                types = listOf("Text", "Mark"),
                icon = Icons.Default.EditNote,
                iconTint = AccentAmber
            )
        }
        if (doc.highlightFileCount > 0) {
            AnnotationCard(
                title = "Highlights",
                badge = "${doc.highlightFileCount} page${if (doc.highlightFileCount != 1) "s" else ""}",
                types = listOf("Highlight"),
                icon = Icons.Default.Highlight,
                iconTint = AccentGreen
            )
        }
        if (doc.pageCount > 0) {
            Surface(color = CardDark, shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth()) {
                Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Icon(Icons.Default.Pages, contentDescription = null, tint = TextSecondary, modifier = Modifier.size(20.dp))
                    Column {
                        Text("Total Pages", style = MaterialTheme.typography.bodyMedium, color = TextSecondary)
                        Text("${doc.pageCount} pages in document", style = MaterialTheme.typography.bodyLarge, color = TextPrimary)
                    }
                }
            }
        }
    }
}

@Composable
private fun AnnotationCard(
    title: String,
    badge: String,
    types: List<String>,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    iconTint: androidx.compose.ui.graphics.Color
) {
    Surface(color = CardDark, shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier.size(32.dp).clip(RoundedCornerShape(8.dp)).background(iconTint.copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(icon, contentDescription = null, tint = iconTint, modifier = Modifier.size(18.dp))
                }
                Spacer(Modifier.width(10.dp))
                Text(title, style = MaterialTheme.typography.bodyMedium, color = TextPrimary, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f))
                Surface(color = SurfaceVariantDark, shape = RoundedCornerShape(50)) {
                    Text(badge, style = MaterialTheme.typography.labelSmall, color = TextSecondary, modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp))
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                types.forEach { type ->
                    Surface(color = PrimaryIndigoDark.copy(alpha = 0.4f), shape = RoundedCornerShape(50)) {
                        Text(type, style = MaterialTheme.typography.labelSmall, color = PrimaryIndigoLight, modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp))
                    }
                }
            }
        }
    }
}

// ─── Details tab ─────────────────────────────────────────────────────────────

@Composable
private fun DetailsPane(
    doc: FlexDocument,
    displayName: String,
    folderPath: String,
    onSaveFile: (ByteArray, String, String) -> Unit,
    modifier: Modifier = Modifier
) {
    val scroll = rememberScrollState()
    Column(
        modifier = modifier.verticalScroll(scroll).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {

        // ── Document Details ──────────────────────────────────────────────────
        DetailSection(title = "Document Details") {
            InfoRow("Name", displayName)
            if (folderPath.isNotBlank()) InfoRow("Folder", folderPath)
            doc.info?.let { info ->
                if (info.key.isNotBlank()) InfoRow("Document ID", info.key)
                InfoRow("Type", when (info.type) {
                    0 -> "PDF Document"
                    1 -> "Notebook"
                    2 -> "PDF Annotation"
                    else -> "Type ${info.type}"
                })
                if (info.createDate > 0) InfoRow("Created", formatDate(info.createDate))
                if (info.modifiedDate > 0) InfoRow("Modified", formatDate(info.modifiedDate))
            }
            InfoRow(".flx File Size", formatFileSize(doc.flxSize))
            if (doc.pdfData != null) InfoRow("PDF Size", formatFileSize(doc.pdfData.size.toLong()))
            if (doc.thumbnail != null) InfoRow("Preview Size", formatFileSize(doc.thumbnail.size.toLong()))
        }

        // ── Status badges ─────────────────────────────────────────────────────
        if (doc.pdfData != null || doc.thumbnail != null || doc.strokeFileCount > 0 || doc.annotationFileCount > 0) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (doc.pdfData != null) BadgeChip(icon = Icons.Default.PictureAsPdf, label = "PDF", bgColor = PrimaryIndigoDark.copy(alpha = 0.5f), tint = PrimaryIndigoLight)
                if (doc.thumbnail != null) BadgeChip(icon = Icons.Default.Image, label = "Preview", bgColor = AccentGreen.copy(alpha = 0.2f), tint = AccentGreen)
                if (doc.strokeFileCount > 0 || doc.annotationFileCount > 0) BadgeChip(icon = Icons.Default.Draw, label = "Annotations", bgColor = AccentAmber.copy(alpha = 0.2f), tint = AccentAmber)
            }
        }

        // ── Pages section (matches web: Width, Height, Rotation, PDF page) ────
        if (doc.pages.isNotEmpty()) {
            DetailSection(title = "Pages (${doc.pages.size})") {
                doc.pages.forEachIndexed { i, page ->
                    if (i > 0) HorizontalDivider(color = DividerColor, thickness = 0.5.dp, modifier = Modifier.padding(vertical = 6.dp))
                    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                        Text(
                            "Page ${i + 1}",
                            style = MaterialTheme.typography.labelMedium,
                            color = TextPrimary,
                            fontWeight = FontWeight.Medium
                        )
                        Spacer(Modifier.height(4.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                            if (page.width > 0f) {
                                Column {
                                    Text("Width", style = MaterialTheme.typography.labelSmall, color = TextMuted)
                                    Text("${"%.0f".format(page.width)} pt", style = MaterialTheme.typography.bodySmall, color = TextSecondary)
                                }
                            }
                            if (page.height > 0f) {
                                Column {
                                    Text("Height", style = MaterialTheme.typography.labelSmall, color = TextMuted)
                                    Text("${"%.0f".format(page.height)} pt", style = MaterialTheme.typography.bodySmall, color = TextSecondary)
                                }
                            }
                            Column {
                                Text("Rotation", style = MaterialTheme.typography.labelSmall, color = TextMuted)
                                Text("${page.rotation}°", style = MaterialTheme.typography.bodySmall, color = TextSecondary)
                            }
                            Column {
                                Text("PDF page", style = MaterialTheme.typography.labelSmall, color = TextMuted)
                                Text("${page.pdfPage + 1}", style = MaterialTheme.typography.bodySmall, color = TextSecondary)
                            }
                        }
                    }
                }
            }
        } else if (doc.pageCount > 0) {
            DetailSection(title = "Pages (${doc.pageCount})") {
                Text("Page layout details not available.", style = MaterialTheme.typography.bodySmall, color = TextMuted)
            }
        }

        // ── Embedded Files ────────────────────────────────────────────────────
        DetailSection(title = "Embedded Files") {
            if (doc.pdfData != null) {
                FileRow(
                    emoji = "📄",
                    name = "PDF Document",
                    size = doc.pdfData.size.toLong(),
                    onDownload = { onSaveFile(doc.pdfData, "$displayName.pdf", "application/pdf") }
                )
            }
            if (doc.thumbnail != null) {
                FileRow(
                    emoji = "🖼️",
                    name = "Cover Preview (JPEG)",
                    size = doc.thumbnail.size.toLong(),
                    onDownload = { onSaveFile(doc.thumbnail, "${displayName}_preview.jpg", "image/jpeg") }
                )
            }
            if (doc.pageCount > 0) {
                FileRow(emoji = "📋", name = "Page Index (JSON)", size = 0, onDownload = null)
            }
            val totalAnnotations = doc.strokeFileCount + doc.annotationFileCount + doc.highlightFileCount
            if (totalAnnotations > 0) {
                FileRow(emoji = "✏️", name = "Annotation Data ($totalAnnotations layers)", size = 0, onDownload = null)
            }
            if (doc.pdfData == null && doc.thumbnail == null && doc.pageCount == 0 && totalAnnotations == 0) {
                Text("No embedded files detected.", style = MaterialTheme.typography.bodySmall, color = TextMuted)
            }
        }
    }
}

// ─── Shared composables ───────────────────────────────────────────────────────

@Composable
private fun DetailSection(title: String, content: @Composable ColumnScope.() -> Unit) {
    Column {
        Text(title, style = MaterialTheme.typography.titleSmall, color = TextPrimary, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(8.dp))
        Surface(color = CardDark, shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(2.dp), content = content)
        }
    }
}

@Composable
private fun FileRow(
    emoji: String,
    name: String,
    size: Long,
    onDownload: (() -> Unit)?
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text(emoji, style = MaterialTheme.typography.titleMedium)
        Text(name, style = MaterialTheme.typography.bodySmall, color = TextPrimary, modifier = Modifier.weight(1f))
        if (size > 0) Text(formatFileSize(size), style = MaterialTheme.typography.labelSmall, color = TextMuted)
        if (onDownload != null) {
            IconButton(onClick = onDownload, modifier = Modifier.size(28.dp)) {
                Icon(Icons.Default.Download, contentDescription = "Download", tint = TextSecondary, modifier = Modifier.size(16.dp))
            }
        }
    }
}

@Composable
private fun BadgeChip(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    bgColor: androidx.compose.ui.graphics.Color,
    tint: androidx.compose.ui.graphics.Color
) {
    Surface(color = bgColor, shape = RoundedCornerShape(20.dp)) {
        Row(
            Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(14.dp), tint = tint)
            Text(label, color = tint, style = MaterialTheme.typography.labelSmall)
        }
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    Column(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Text(label, color = TextSecondary, style = MaterialTheme.typography.labelSmall)
        Text(value, color = TextPrimary, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Medium)
        HorizontalDivider(color = DividerColor, thickness = 0.5.dp, modifier = Modifier.padding(top = 4.dp))
    }
}

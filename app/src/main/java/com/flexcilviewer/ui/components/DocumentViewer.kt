package com.flexcilviewer.ui.components

import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.flexcilviewer.data.FlexDocument
import com.flexcilviewer.data.formatDate
import com.flexcilviewer.data.formatFileSize
import com.flexcilviewer.ui.theme.*

@Composable
fun DocumentViewer(
    doc: FlexDocument,
    onExportClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    var showPdf by remember(doc) { mutableStateOf(false) }

    Column(modifier = modifier.background(BackgroundDark)) {
        // Top bar
        Surface(
            color = SurfaceDark,
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(Modifier.weight(1f)) {
                    Text(
                        text = doc.name,
                        style = MaterialTheme.typography.titleMedium,
                        color = TextPrimary,
                        maxLines = 2
                    )
                    doc.info?.let {
                        Text(
                            text = "Modified: ${formatDate(it.modifiedDate)}",
                            style = MaterialTheme.typography.labelSmall,
                            color = TextSecondary
                        )
                    }
                }
                Spacer(Modifier.width(8.dp))
                if (doc.pdfData != null) {
                    TextButton(
                        onClick = { showPdf = !showPdf },
                        colors = ButtonDefaults.textButtonColors(contentColor = PrimaryIndigoLight)
                    ) {
                        Icon(
                            if (showPdf) Icons.Default.Description else Icons.Default.PictureAsPdf,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(Modifier.width(4.dp))
                        Text(if (showPdf) "Info" else "View PDF")
                    }
                }
                IconButton(onClick = onExportClick) {
                    Icon(Icons.Default.FileDownload, contentDescription = "Export", tint = PrimaryIndigoLight)
                }
            }
        }

        HorizontalDivider(color = DividerColor)

        if (showPdf && doc.pdfData != null) {
            PdfViewer(
                pdfBytes = doc.pdfData,
                modifier = Modifier.fillMaxSize()
            )
        } else {
            DocumentInfoPane(doc = doc, modifier = Modifier.fillMaxSize())
        }
    }
}

@Composable
private fun DocumentInfoPane(doc: FlexDocument, modifier: Modifier = Modifier) {
    val scroll = rememberScrollState()
    Column(
        modifier = modifier
            .verticalScroll(scroll)
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Thumbnail
        doc.thumbnail?.let { thumbBytes ->
            val bitmap = remember(thumbBytes) {
                runCatching { BitmapFactory.decodeByteArray(thumbBytes, 0, thumbBytes.size) }.getOrNull()
            }
            bitmap?.let {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(220.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(CardDark),
                    contentAlignment = Alignment.Center
                ) {
                    Image(
                        bitmap = it.asImageBitmap(),
                        contentDescription = "Thumbnail",
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Fit
                    )
                }
            }
        }

        // Document Info Card
        Surface(
            color = CardDark,
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Document Details", style = MaterialTheme.typography.titleMedium, color = PrimaryIndigoLight, fontWeight = FontWeight.SemiBold)
                HorizontalDivider(color = DividerColor)
                InfoRow(label = "Name", value = doc.name)
                InfoRow(label = "File size", value = formatFileSize(doc.flxSize))
                doc.info?.let { info ->
                    if (info.createDate > 0) InfoRow(label = "Created", value = formatDate(info.createDate))
                    if (info.modifiedDate > 0) InfoRow(label = "Modified", value = formatDate(info.modifiedDate))
                    InfoRow(label = "Type", value = when (info.type) {
                        0 -> "Document"
                        1 -> "Notebook"
                        2 -> "PDF Annotation"
                        else -> "Unknown (${info.type})"
                    })
                }
                if (doc.pageCount > 0) InfoRow(label = "Pages", value = "${doc.pageCount}")
            }
        }

        // Status badges
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (doc.pdfData != null) {
                Badge(containerColor = PrimaryIndigoDark) {
                    Row(Modifier.padding(horizontal = 8.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.PictureAsPdf, contentDescription = null, modifier = Modifier.size(14.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Has PDF")
                    }
                }
            }
            if (doc.thumbnail != null) {
                Badge(containerColor = AccentGreen.copy(alpha = 0.25f)) {
                    Row(Modifier.padding(horizontal = 8.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Image, contentDescription = null, modifier = Modifier.size(14.dp), tint = AccentGreen)
                        Spacer(Modifier.width(4.dp))
                        Text("Thumbnail", color = AccentGreen)
                    }
                }
            }
        }

        if (doc.pdfData == null && doc.thumbnail == null) {
            Surface(
                color = SurfaceVariantDark,
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Icon(Icons.Default.Info, contentDescription = null, tint = TextMuted)
                    Text(
                        "This document contains annotations and drawings data but no embedded PDF.",
                        color = TextSecondary,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
        }
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Top
    ) {
        Text(label, color = TextSecondary, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(0.4f))
        Text(value, color = TextPrimary, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(0.6f))
    }
}

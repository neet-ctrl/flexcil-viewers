package com.flexcilviewer.ui.components

import android.graphics.BitmapFactory
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.flexcilviewer.data.FlexDocument
import com.flexcilviewer.data.FolderNode
import com.flexcilviewer.data.formatDate
import com.flexcilviewer.data.formatFileSize
import com.flexcilviewer.ui.theme.*

@Composable
fun SidebarContent(
    folders: List<FolderNode>,
    selectedDoc: FlexDocument?,
    selectedFolder: FolderNode?,
    checkedDocs: Set<String>,
    onDocClick: (FlexDocument) -> Unit,
    onFolderClick: (FolderNode) -> Unit,
    onCheckToggle: (String) -> Unit,
    onCheckAll: (FolderNode) -> Unit,
    onDeselectAll: () -> Unit,
    onExportSelected: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.background(SurfaceDark)) {
        // Header
        Surface(color = CardDark, modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Default.FolderOpen, contentDescription = null, tint = PrimaryIndigoLight, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(8.dp))
                Text("Folders", style = MaterialTheme.typography.titleMedium, color = TextPrimary, modifier = Modifier.weight(1f))
                if (checkedDocs.isNotEmpty()) {
                    TextButton(onClick = onDeselectAll, contentPadding = PaddingValues(horizontal = 4.dp)) {
                        Text("Clear ${checkedDocs.size}", color = TextSecondary, style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
        }

        if (checkedDocs.isNotEmpty()) {
            Surface(color = PrimaryIndigoDark.copy(alpha = 0.3f), modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "${checkedDocs.size} selected",
                        color = PrimaryIndigoLight,
                        style = MaterialTheme.typography.labelLarge,
                        modifier = Modifier.weight(1f)
                    )
                    TextButton(onClick = onExportSelected, contentPadding = PaddingValues(horizontal = 8.dp)) {
                        Icon(Icons.Default.FileDownload, contentDescription = null, modifier = Modifier.size(16.dp), tint = PrimaryIndigoLight)
                        Spacer(Modifier.width(4.dp))
                        Text("Export", color = PrimaryIndigoLight, style = MaterialTheme.typography.labelLarge)
                    }
                }
            }
        }

        HorizontalDivider(color = DividerColor)

        LazyColumn(modifier = Modifier.fillMaxSize()) {
            for (folder in folders) {
                item(key = folder.fullPath) {
                    FolderNode(
                        folder = folder,
                        depth = 0,
                        selectedDoc = selectedDoc,
                        selectedFolder = selectedFolder,
                        checkedDocs = checkedDocs,
                        onDocClick = onDocClick,
                        onFolderClick = onFolderClick,
                        onCheckToggle = onCheckToggle,
                        onCheckAll = onCheckAll
                    )
                }
            }
        }
    }
}

@Composable
private fun FolderNode(
    folder: FolderNode,
    depth: Int,
    selectedDoc: FlexDocument?,
    selectedFolder: FolderNode?,
    checkedDocs: Set<String>,
    onDocClick: (FlexDocument) -> Unit,
    onFolderClick: (FolderNode) -> Unit,
    onCheckToggle: (String) -> Unit,
    onCheckAll: (FolderNode) -> Unit
) {
    var expanded by remember(folder.fullPath) { mutableStateOf(depth == 0) }
    val indent: Dp = (depth * 16).dp

    // Folder header row
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable {
                expanded = !expanded
                onFolderClick(folder)
            }
            .background(
                if (selectedFolder?.fullPath == folder.fullPath)
                    SelectedBg else SurfaceDark
            )
            .padding(start = 8.dp + indent, end = 8.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            if (expanded) Icons.Default.ExpandMore else Icons.Default.ChevronRight,
            contentDescription = null,
            tint = TextSecondary,
            modifier = Modifier.size(18.dp)
        )
        Spacer(Modifier.width(4.dp))
        Icon(
            if (expanded) Icons.Default.FolderOpen else Icons.Default.Folder,
            contentDescription = null,
            tint = PrimaryIndigoLight,
            modifier = Modifier.size(18.dp)
        )
        Spacer(Modifier.width(8.dp))
        Column(Modifier.weight(1f)) {
            Text(folder.name, style = MaterialTheme.typography.bodyMedium, color = TextPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                "${folder.totalDocuments} docs",
                style = MaterialTheme.typography.labelSmall,
                color = TextMuted
            )
        }
        IconButton(onClick = { onCheckAll(folder) }, modifier = Modifier.size(28.dp)) {
            Icon(Icons.Default.CheckBox, contentDescription = "Select all", tint = TextMuted, modifier = Modifier.size(16.dp))
        }
    }

    AnimatedVisibility(
        visible = expanded,
        enter = expandVertically(),
        exit = shrinkVertically()
    ) {
        Column {
            // Sub-folders
            for (sub in folder.subfolders) {
                FolderNode(
                    folder = sub,
                    depth = depth + 1,
                    selectedDoc = selectedDoc,
                    selectedFolder = selectedFolder,
                    checkedDocs = checkedDocs,
                    onDocClick = onDocClick,
                    onFolderClick = onFolderClick,
                    onCheckToggle = onCheckToggle,
                    onCheckAll = onCheckAll
                )
            }
            // Documents
            for (doc in folder.documents) {
                DocumentRow(
                    doc = doc,
                    folderPath = folder.fullPath,
                    indent = indent + 24.dp,
                    isSelected = selectedDoc?.name == doc.name,
                    isChecked = "${folder.fullPath}/${doc.name}" in checkedDocs,
                    onDocClick = onDocClick,
                    onCheckToggle = { onCheckToggle("${folder.fullPath}/${doc.name}") }
                )
            }
        }
    }
    HorizontalDivider(color = DividerColor, thickness = 0.5.dp)
}

@Composable
private fun DocumentRow(
    doc: FlexDocument,
    folderPath: String,
    indent: Dp,
    isSelected: Boolean,
    isChecked: Boolean,
    onDocClick: (FlexDocument) -> Unit,
    onCheckToggle: () -> Unit
) {
    val thumbBitmap = remember(doc.thumbnail) {
        doc.thumbnail?.let { bytes ->
            runCatching { BitmapFactory.decodeByteArray(bytes, 0, bytes.size) }.getOrNull()
        }
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onDocClick(doc) }
            .background(if (isSelected) SelectedBg else SurfaceDark)
            .padding(start = indent, end = 8.dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Checkbox(
            checked = isChecked,
            onCheckedChange = { onCheckToggle() },
            colors = CheckboxDefaults.colors(
                checkedColor = PrimaryIndigo,
                uncheckedColor = TextMuted
            ),
            modifier = Modifier.size(20.dp)
        )
        Spacer(Modifier.width(8.dp))

        // Thumbnail or icon
        if (thumbBitmap != null) {
            Image(
                bitmap = thumbBitmap.asImageBitmap(),
                contentDescription = null,
                modifier = Modifier
                    .size(36.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(CardDark),
                contentScale = ContentScale.Crop
            )
        } else {
            Box(
                Modifier
                    .size(36.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(SurfaceVariantDark),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    if (doc.pdfData != null) Icons.Default.PictureAsPdf else Icons.Default.Description,
                    contentDescription = null,
                    tint = PrimaryIndigoLight,
                    modifier = Modifier.size(20.dp)
                )
            }
        }

        Spacer(Modifier.width(8.dp))
        Column(Modifier.weight(1f)) {
            Text(
                doc.name,
                style = MaterialTheme.typography.bodyMedium,
                color = if (isSelected) TextPrimary else TextPrimary.copy(alpha = 0.9f),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                buildString {
                    append(formatFileSize(doc.flxSize))
                    doc.info?.modifiedDate?.takeIf { it > 0 }?.let { append("  •  ${formatDate(it)}") }
                },
                style = MaterialTheme.typography.labelSmall,
                color = TextMuted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

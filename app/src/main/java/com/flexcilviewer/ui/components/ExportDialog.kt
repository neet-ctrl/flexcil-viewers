package com.flexcilviewer.ui.components

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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.flexcilviewer.data.FlexDocument
import com.flexcilviewer.data.formatFileSize
import com.flexcilviewer.ui.theme.*

@Composable
fun ExportDialog(
    docs: List<Pair<FlexDocument, String>>,
    onDismiss: () -> Unit,
    onExportToFolder: () -> Unit,
    onExportAsZip: () -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = SurfaceDark,
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp)
        ) {
            Column(Modifier.padding(20.dp)) {
                // Title
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.FileDownload, contentDescription = null, tint = PrimaryIndigoLight)
                    Spacer(Modifier.width(10.dp))
                    Text("Export ${docs.size} Document${if (docs.size == 1) "" else "s"}", style = MaterialTheme.typography.titleLarge, color = TextPrimary)
                }

                Spacer(Modifier.height(12.dp))

                // Doc list
                val pdfDocs = docs.filter { it.first.pdfData != null }
                val noPdfDocs = docs.filter { it.first.pdfData == null }

                if (pdfDocs.isNotEmpty()) {
                    Text("PDFs to export (${pdfDocs.size}):", style = MaterialTheme.typography.labelLarge, color = TextSecondary)
                    Spacer(Modifier.height(6.dp))
                    LazyColumn(modifier = Modifier.heightIn(max = 200.dp)) {
                        items(pdfDocs) { (doc, folderPath) ->
                            Row(
                                Modifier.padding(vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Default.PictureAsPdf, contentDescription = null, tint = AccentRed, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(6.dp))
                                Column {
                                    Text(doc.name, style = MaterialTheme.typography.bodyMedium, color = TextPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    Text(folderPath, style = MaterialTheme.typography.labelSmall, color = TextMuted, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                }
                            }
                        }
                    }
                }

                if (noPdfDocs.isNotEmpty()) {
                    Spacer(Modifier.height(8.dp))
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .padding(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.Warning, contentDescription = null, tint = AccentAmber, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(
                            "${noPdfDocs.size} document${if (noPdfDocs.size == 1) "" else "s"} without PDF will be skipped",
                            style = MaterialTheme.typography.bodyMedium,
                            color = AccentAmber
                        )
                    }
                }

                if (pdfDocs.isEmpty()) {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "None of the selected documents have embedded PDFs. Nothing will be exported.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = AccentAmber
                    )
                }

                Spacer(Modifier.height(16.dp))
                HorizontalDivider(color = DividerColor)
                Spacer(Modifier.height(16.dp))

                // Export buttons
                if (pdfDocs.isNotEmpty()) {
                    Button(
                        onClick = onExportToFolder,
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(containerColor = PrimaryIndigo)
                    ) {
                        Icon(Icons.Default.CreateNewFolder, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Save PDFs to Folder")
                    }
                    Spacer(Modifier.height(8.dp))
                    OutlinedButton(
                        onClick = onExportAsZip,
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = PrimaryIndigoLight)
                    ) {
                        Icon(Icons.Default.Archive, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Export as ZIP")
                    }
                    Spacer(Modifier.height(8.dp))
                }

                TextButton(onClick = onDismiss, modifier = Modifier.fillMaxWidth()) {
                    Text("Cancel", color = TextSecondary)
                }
            }
        }
    }
}

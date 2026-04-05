package com.flexcilviewer.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.flexcilviewer.ui.theme.*

@Composable
fun HomeScreen(onOpenFile: () -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .background(BackgroundDark)
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        // Logo
        Box(
            modifier = Modifier
                .size(88.dp)
                .clip(RoundedCornerShape(22.dp))
                .background(PrimaryIndigoDark.copy(alpha = 0.3f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                Icons.Default.FolderZip,
                contentDescription = null,
                tint = PrimaryIndigoLight,
                modifier = Modifier.size(48.dp)
            )
        }

        Spacer(Modifier.height(24.dp))

        Text(
            "Flexcil Backup Viewer",
            style = MaterialTheme.typography.headlineMedium,
            color = TextPrimary,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center
        )

        Spacer(Modifier.height(8.dp))

        Text(
            "Open a .flex backup file to browse your Flexcil notes, PDFs, and folder structure",
            style = MaterialTheme.typography.bodyLarge,
            color = TextSecondary,
            textAlign = TextAlign.Center,
            modifier = Modifier.widthIn(max = 360.dp)
        )

        Spacer(Modifier.height(36.dp))

        Button(
            onClick = onOpenFile,
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 300.dp)
                .height(52.dp),
            colors = ButtonDefaults.buttonColors(containerColor = PrimaryIndigo),
            shape = RoundedCornerShape(12.dp)
        ) {
            Icon(Icons.Default.FileOpen, contentDescription = null, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(10.dp))
            Text("Open .flex File", style = MaterialTheme.typography.titleMedium)
        }

        Spacer(Modifier.height(32.dp))

        // Feature list
        Column(
            modifier = Modifier.widthIn(max = 340.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            FeatureItem(icon = Icons.Default.AccountTree, text = "Browse nested folder & subfolder structure")
            FeatureItem(icon = Icons.Default.PictureAsPdf, text = "View embedded PDFs with pinch-to-zoom")
            FeatureItem(icon = Icons.Default.Image, text = "See document thumbnails and previews")
            FeatureItem(icon = Icons.Default.CreateNewFolder, text = "Export PDFs to any folder on your device")
            FeatureItem(icon = Icons.Default.Archive, text = "Batch export selected documents as ZIP")
        }
    }
}

@Composable
private fun FeatureItem(icon: androidx.compose.ui.graphics.vector.ImageVector, text: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Box(
            modifier = Modifier
                .size(32.dp)
                .clip(CircleShape)
                .background(PrimaryIndigoDark.copy(alpha = 0.25f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, contentDescription = null, tint = PrimaryIndigoLight, modifier = Modifier.size(18.dp))
        }
        Text(text, style = MaterialTheme.typography.bodyMedium, color = TextSecondary, modifier = Modifier.weight(1f))
    }
}

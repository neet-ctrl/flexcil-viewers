package com.flexcilviewer.ui.screens

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.flexcilviewer.data.ScannedFlexFile
import com.flexcilviewer.data.formatDate
import com.flexcilviewer.data.formatFileSize
import com.flexcilviewer.data.friendlyPath
import com.flexcilviewer.data.scanForFlexFiles
import com.flexcilviewer.ui.theme.*
import kotlinx.coroutines.launch

private sealed interface ScanState {
    object Idle : ScanState
    object NeedsPermission : ScanState
    object NeedsManagePermission : ScanState
    object Scanning : ScanState
    data class Done(val files: List<ScannedFlexFile>) : ScanState
    object Empty : ScanState
}

@Composable
fun HomeScreen(
    onOpenFile: () -> Unit,
    onOpenUri: (Uri) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var scanState by remember { mutableStateOf<ScanState>(ScanState.Idle) }

    // ── Permission launcher for READ_EXTERNAL_STORAGE (API < 33) ─────────────
    val permLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            scope.launch {
                scanState = ScanState.Scanning
                val files = scanForFlexFiles(context)
                scanState = if (files.isEmpty()) ScanState.Empty else ScanState.Done(files)
            }
        } else {
            scanState = ScanState.NeedsPermission
        }
    }

    fun startScan() {
        when {
            // Android 11+ (API 30+): check MANAGE_EXTERNAL_STORAGE for full access
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.R -> {
                if (Environment.isExternalStorageManager()) {
                    scope.launch {
                        scanState = ScanState.Scanning
                        val files = scanForFlexFiles(context)
                        scanState = if (files.isEmpty()) ScanState.Empty else ScanState.Done(files)
                    }
                } else {
                    scanState = ScanState.NeedsManagePermission
                }
            }
            // Android 9-10 (API 28-29): use READ_EXTERNAL_STORAGE
            else -> {
                permLauncher.launch(Manifest.permission.READ_EXTERNAL_STORAGE)
            }
        }
    }

    Column(
        modifier = modifier
            .background(BackgroundDark)
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(Modifier.height(24.dp))

        // ── Logo ──────────────────────────────────────────────────────────────
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

        Spacer(Modifier.height(32.dp))

        // ── Open file button ──────────────────────────────────────────────────
        Button(
            onClick = onOpenFile,
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp),
            colors = ButtonDefaults.buttonColors(containerColor = PrimaryIndigo),
            shape = RoundedCornerShape(12.dp)
        ) {
            Icon(Icons.Default.FileOpen, contentDescription = null, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(10.dp))
            Text("Open .flex File", style = MaterialTheme.typography.titleMedium)
        }

        Spacer(Modifier.height(12.dp))

        // ── Scan storage button ───────────────────────────────────────────────
        OutlinedButton(
            onClick = { startScan() },
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp),
            shape = RoundedCornerShape(12.dp),
            colors = ButtonDefaults.outlinedButtonColors(contentColor = PrimaryIndigoLight),
            border = androidx.compose.foundation.BorderStroke(1.dp, PrimaryIndigoDark)
        ) {
            Icon(Icons.Default.ManageSearch, contentDescription = null, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(10.dp))
            Text("Scan Storage for .flex Files", style = MaterialTheme.typography.titleMedium)
        }

        Spacer(Modifier.height(20.dp))

        // ── Scan results area ─────────────────────────────────────────────────
        when (val state = scanState) {
            ScanState.Idle -> {}

            ScanState.NeedsPermission -> {
                PermissionCard(
                    message = "Storage permission is required to scan for .flex files.",
                    buttonLabel = "Grant Permission",
                    onAction = { permLauncher.launch(Manifest.permission.READ_EXTERNAL_STORAGE) }
                )
            }

            ScanState.NeedsManagePermission -> {
                PermissionCard(
                    message = "To scan all storage locations, allow \"All files access\" for this app in Settings.",
                    buttonLabel = "Open Settings",
                    onAction = {
                        val intent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION).apply {
                            data = Uri.parse("package:${context.packageName}")
                        }
                        context.startActivity(intent)
                    }
                )
                Spacer(Modifier.height(8.dp))
                TextButton(onClick = {
                    scope.launch {
                        scanState = ScanState.Scanning
                        val files = scanForFlexFiles(context)
                        scanState = if (files.isEmpty()) ScanState.Empty else ScanState.Done(files)
                    }
                }) {
                    Text("Try Without Full Access", color = TextSecondary, style = MaterialTheme.typography.bodyMedium)
                }
            }

            ScanState.Scanning -> {
                Surface(
                    color = CardDark,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        Modifier.padding(20.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(24.dp),
                            color = PrimaryIndigoLight,
                            strokeWidth = 2.5.dp
                        )
                        Text("Scanning storage for .flex files…", color = TextSecondary, style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }

            ScanState.Empty -> {
                Surface(
                    color = CardDark,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        Modifier.padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(Icons.Default.SearchOff, contentDescription = null, tint = TextMuted, modifier = Modifier.size(36.dp))
                        Text("No .flex files found", color = TextPrimary, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                        Text(
                            "No .flex backup files were found in your storage. Try using the file picker above to locate your file manually.",
                            color = TextMuted,
                            style = MaterialTheme.typography.bodySmall,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }

            is ScanState.Done -> {
                ScanResultsList(
                    files = state.files,
                    onFileClick = { file -> onOpenUri(file.uri) },
                    onRescan = { startScan() }
                )
            }
        }

        // Only show feature list when not showing scan results
        if (scanState is ScanState.Idle || scanState is ScanState.NeedsPermission) {
            Spacer(Modifier.height(28.dp))
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                FeatureItem(icon = Icons.Default.AccountTree, text = "Browse nested folder & subfolder structure")
                FeatureItem(icon = Icons.Default.PictureAsPdf, text = "View embedded PDFs with pinch-to-zoom")
                FeatureItem(icon = Icons.Default.Image, text = "See document thumbnails and previews")
                FeatureItem(icon = Icons.Default.CreateNewFolder, text = "Export PDFs to any folder on your device")
                FeatureItem(icon = Icons.Default.Archive, text = "Batch export selected documents as ZIP")
            }
        }

        Spacer(Modifier.height(24.dp))
    }
}

// ── Scan results list ─────────────────────────────────────────────────────────

@Composable
private fun ScanResultsList(
    files: List<ScannedFlexFile>,
    onFileClick: (ScannedFlexFile) -> Unit,
    onRescan: () -> Unit
) {
    Surface(
        color = CardDark,
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column {
            // Header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Default.CheckCircle, contentDescription = null, tint = AccentGreen, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(
                    "${files.size} .flex file${if (files.size != 1) "s" else ""} found",
                    style = MaterialTheme.typography.titleSmall,
                    color = TextPrimary,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f)
                )
                TextButton(onClick = onRescan, contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)) {
                    Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(14.dp), tint = TextMuted)
                    Spacer(Modifier.width(4.dp))
                    Text("Rescan", color = TextMuted, style = MaterialTheme.typography.labelSmall)
                }
            }

            HorizontalDivider(color = DividerColor)

            // File list (capped height so it doesn't push everything off screen)
            LazyColumn(modifier = Modifier.heightIn(max = 400.dp)) {
                items(files, key = { it.path.ifBlank { it.name } }) { file ->
                    FlexFileRow(file = file, onClick = { onFileClick(file) })
                    HorizontalDivider(color = DividerColor, thickness = 0.5.dp, modifier = Modifier.padding(horizontal = 16.dp))
                }
            }
        }
    }
}

@Composable
private fun FlexFileRow(file: ScannedFlexFile, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(PrimaryIndigoDark.copy(alpha = 0.4f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                Icons.Default.FolderZip,
                contentDescription = null,
                tint = PrimaryIndigoLight,
                modifier = Modifier.size(22.dp)
            )
        }

        Column(modifier = Modifier.weight(1f)) {
            Text(
                file.name,
                style = MaterialTheme.typography.bodyMedium,
                color = TextPrimary,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.height(2.dp))
            Text(
                friendlyPath(file.path),
                style = MaterialTheme.typography.labelSmall,
                color = TextMuted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (file.size > 0 || file.dateModified > 0) {
                Spacer(Modifier.height(2.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (file.size > 0) {
                        Text(formatFileSize(file.size), style = MaterialTheme.typography.labelSmall, color = TextSecondary)
                    }
                    if (file.dateModified > 0) {
                        Text("· ${formatDate(file.dateModified)}", style = MaterialTheme.typography.labelSmall, color = TextMuted)
                    }
                }
            }
        }

        Icon(Icons.Default.ChevronRight, contentDescription = "Open", tint = TextMuted, modifier = Modifier.size(20.dp))
    }
}

// ── Permission card ───────────────────────────────────────────────────────────

@Composable
private fun PermissionCard(message: String, buttonLabel: String, onAction: () -> Unit) {
    Surface(
        color = AccentAmber.copy(alpha = 0.1f),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Icon(Icons.Default.Lock, contentDescription = null, tint = AccentAmber, modifier = Modifier.size(20.dp))
                Text("Permission Required", style = MaterialTheme.typography.titleSmall, color = AccentAmber, fontWeight = FontWeight.SemiBold)
            }
            Text(message, style = MaterialTheme.typography.bodySmall, color = TextSecondary)
            Button(
                onClick = onAction,
                colors = ButtonDefaults.buttonColors(containerColor = AccentAmber.copy(alpha = 0.8f)),
                shape = RoundedCornerShape(8.dp),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)
            ) {
                Text(buttonLabel, style = MaterialTheme.typography.labelMedium)
            }
        }
    }
}

// ── Feature bullet ────────────────────────────────────────────────────────────

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

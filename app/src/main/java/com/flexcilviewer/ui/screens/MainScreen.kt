package com.flexcilviewer.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.flexcilviewer.data.*
import com.flexcilviewer.ui.components.DocumentViewer
import com.flexcilviewer.ui.components.ExportDialog
import com.flexcilviewer.ui.components.SidebarContent
import com.flexcilviewer.ui.theme.*
import com.flexcilviewer.viewmodel.ExportState
import com.flexcilviewer.viewmodel.FlexViewModel
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    viewModel: FlexViewModel,
    backup: FlexBackup,
    onOpenNewFile: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val drawerState = rememberDrawerState(DrawerValue.Open)

    val selectedDoc by viewModel.selectedDocument.collectAsState()
    val selectedDocFolderPath by viewModel.selectedDocFolderPath.collectAsState()
    val selectedFolder by viewModel.selectedFolder.collectAsState()
    val checkedDocs by viewModel.checkedDocs.collectAsState()
    val exportState by viewModel.exportState.collectAsState()
    val searchQuery by viewModel.searchQuery.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    var showExportDialog by remember { mutableStateOf(false) }
    var exportTarget by remember { mutableStateOf<List<Pair<FlexDocument, String>>>(emptyList()) }
    var pendingExportMode by remember { mutableStateOf("folder") }

    val folderPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree()
    ) { uri: Uri? ->
        if (uri != null) {
            when (pendingExportMode) {
                "zip"      -> viewModel.exportAsZip(context, uri, exportTarget)
                "previews" -> viewModel.exportPreviewsAsZip(context, uri, exportTarget)
                "all"      -> viewModel.exportAllAsZip(context, uri, exportTarget)
                else       -> viewModel.exportToFolder(context, uri, exportTarget)
            }
        }
    }

    fun openExportFor(docs: List<Pair<FlexDocument, String>>) {
        exportTarget = docs
        showExportDialog = true
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        scrimColor = BackgroundDark.copy(alpha = 0.6f),
        drawerContent = {
            ModalDrawerSheet(
                drawerContainerColor = SurfaceDark,
                modifier = Modifier.width(300.dp)
            ) {
                SidebarContent(
                    folders = backup.rootFolders,
                    backupInfo = backup.info,
                    totalDocuments = backup.totalDocuments,
                    selectedDoc = selectedDoc,
                    selectedFolder = selectedFolder,
                    checkedDocs = checkedDocs,
                    searchQuery = searchQuery,
                    onSearchQueryChange = { viewModel.setSearchQuery(it) },
                    onDocClick = { doc, path ->
                        viewModel.selectDocument(doc, path)
                        scope.launch { drawerState.close() }
                    },
                    onFolderClick = { folder -> viewModel.selectFolder(folder) },
                    onCheckToggle = { key -> viewModel.toggleDocCheck(key) },
                    onCheckAll = { folder -> viewModel.checkAllInFolder(folder) },
                    onDeselectAll = { viewModel.deselectAll() },
                    onExportSelected = {
                        val allDocs = getAllDocuments(backup.rootFolders)
                        val selected = allDocs.filter { (doc, path) -> "${path}/${doc.name}" in checkedDocs }
                        openExportFor(selected)
                    },
                    onReset = {
                        scope.launch { drawerState.close() }
                        onOpenNewFile()
                    },
                    modifier = Modifier.fillMaxSize()
                )
            }
        }
    ) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = {
                        Column {
                            Text(
                                backup.info.appName.ifBlank { "Flexcil Backup Viewer" },
                                style = MaterialTheme.typography.titleMedium,
                                color = TextPrimary,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1
                            )
                            Text(
                                "${backup.totalDocuments} documents",
                                style = MaterialTheme.typography.labelSmall,
                                color = TextSecondary
                            )
                        }
                    },
                    navigationIcon = {
                        IconButton(onClick = { scope.launch { drawerState.open() } }) {
                            Icon(Icons.Default.Menu, contentDescription = "Folders", tint = TextPrimary)
                        }
                    },
                    actions = {
                        if (checkedDocs.isNotEmpty()) {
                            IconButton(onClick = {
                                val allDocs = getAllDocuments(backup.rootFolders)
                                val selected = allDocs.filter { (doc, path) -> "${path}/${doc.name}" in checkedDocs }
                                openExportFor(selected)
                            }) {
                                Icon(Icons.Default.FileDownload, contentDescription = "Export selected", tint = PrimaryIndigoLight)
                            }
                        }
                        IconButton(onClick = onOpenNewFile) {
                            Icon(Icons.Default.FolderOpen, contentDescription = "Open new file", tint = TextPrimary)
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = SurfaceDark,
                        scrolledContainerColor = SurfaceDark
                    )
                )
            },
            snackbarHost = { SnackbarHost(snackbarHostState) },
            containerColor = BackgroundDark
        ) { paddingValues ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
            ) {
                when {
                    selectedDoc != null -> {
                        DocumentViewer(
                            doc = selectedDoc!!,
                            folderPath = selectedDocFolderPath,
                            onExportClick = {
                                val allDocs = getAllDocuments(backup.rootFolders)
                                val current = selectedDoc!!
                                val docEntry = allDocs.find { it.first == current }
                                    ?: allDocs.find { it.first.name == current.name && it.second == selectedDocFolderPath }
                                    ?: allDocs.find { it.first.name == current.name }
                                if (docEntry != null) openExportFor(listOf(docEntry))
                                else openExportFor(listOf(current to selectedDocFolderPath))
                            },
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                    else -> {
                        WelcomePane(
                            backup = backup,
                            onExportAllPdfs = {
                                val docs = getAllDocuments(backup.rootFolders).filter { it.first.pdfData != null }
                                openExportFor(docs)
                            },
                            onExportAll = {
                                openExportFor(getAllDocuments(backup.rootFolders))
                            },
                            onDocClick = { doc, path ->
                                viewModel.selectDocument(doc, path)
                            },
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                }
            }
        }
    }

    if (showExportDialog) {
        ExportDialog(
            docs = exportTarget,
            onDismiss = { showExportDialog = false },
            onExportToFolder = {
                pendingExportMode = "folder"
                showExportDialog = false
                folderPickerLauncher.launch(null)
            },
            onExportPdfsZip = {
                pendingExportMode = "zip"
                showExportDialog = false
                folderPickerLauncher.launch(null)
            },
            onExportPreviewsZip = {
                pendingExportMode = "previews"
                showExportDialog = false
                folderPickerLauncher.launch(null)
            },
            onExportAllZip = {
                pendingExportMode = "all"
                showExportDialog = false
                folderPickerLauncher.launch(null)
            }
        )
    }

    LaunchedEffect(exportState) {
        when (val state = exportState) {
            is ExportState.Done -> {
                snackbarHostState.showSnackbar("Exported ${state.count} file${if (state.count == 1) "" else "s"} successfully")
                viewModel.clearExportState()
            }
            is ExportState.Error -> {
                snackbarHostState.showSnackbar("Export failed: ${state.message}")
                viewModel.clearExportState()
            }
            else -> {}
        }
    }
}

// ─── Welcome Pane — matches web WelcomePane exactly ───────────────────────────

@Composable
private fun WelcomePane(
    backup: FlexBackup,
    onExportAllPdfs: () -> Unit,
    onExportAll: () -> Unit,
    onDocClick: (FlexDocument, String) -> Unit,
    modifier: Modifier = Modifier
) {
    val allDocs = remember(backup) { getAllDocuments(backup.rootFolders) }
    val totalFolders = remember(backup) { countFolders(backup.rootFolders) }
    val totalPdfs = remember(allDocs) { allDocs.count { it.first.pdfData != null } }
    val totalSize = remember(allDocs) { allDocs.sumOf { it.first.flxSize } }

    Column(
        modifier = modifier
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Header
        Column {
            Text(
                "Backup Opened",
                style = MaterialTheme.typography.headlineMedium,
                color = TextPrimary,
                fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.height(4.dp))
            Text(
                "Select a document from the sidebar to view it, or export documents below.",
                style = MaterialTheme.typography.bodyMedium,
                color = TextSecondary
            )
        }

        // Stat cards row
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            StatCard(
                icon = Icons.Default.Folder,
                iconTint = PrimaryIndigoLight,
                value = "$totalFolders",
                label = "Folders",
                modifier = Modifier.weight(1f)
            )
            StatCard(
                icon = Icons.Default.Description,
                iconTint = AccentGreen,
                value = "${backup.totalDocuments}",
                label = "Documents",
                modifier = Modifier.weight(1f)
            )
            StatCard(
                icon = Icons.Default.PictureAsPdf,
                iconTint = AccentAmber,
                value = "$totalPdfs",
                label = "With PDF",
                modifier = Modifier.weight(1f)
            )
            StatCard(
                icon = Icons.Default.Info,
                iconTint = PrimaryIndigoLight.copy(alpha = 0.7f),
                value = formatFileSize(totalSize),
                label = "Total Size",
                modifier = Modifier.weight(1f)
            )
        }

        // Backup Info card
        Surface(
            color = CardDark,
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(Modifier.padding(16.dp)) {
                Text(
                    "Backup Info",
                    style = MaterialTheme.typography.labelLarge,
                    color = TextPrimary,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(Modifier.height(10.dp))
                val rows = listOf(
                    "App" to backup.info.appName,
                    "Version" to backup.info.appVersion,
                    "Backup Date" to backup.info.backupDate,
                    "Format" to "v${backup.info.version}"
                )
                rows.forEachIndexed { i, (label, value) ->
                    if (i > 0) HorizontalDivider(color = DividerColor, thickness = 0.5.dp, modifier = Modifier.padding(vertical = 6.dp))
                    Row(Modifier.fillMaxWidth()) {
                        Text(label, color = TextSecondary, style = MaterialTheme.typography.bodySmall, modifier = Modifier.width(110.dp))
                        Text(value.ifBlank { "—" }, color = TextPrimary, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Medium)
                    }
                }
            }
        }

        // Quick Export card (only when PDFs exist)
        if (totalPdfs > 0) {
            Surface(
                color = CardDark,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(Modifier.padding(16.dp)) {
                    Text(
                        "Quick Export",
                        style = MaterialTheme.typography.labelLarge,
                        color = TextPrimary,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "Export all $totalPdfs PDFs from this backup at once. Files are organized in folders matching your Flexcil structure.",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary
                    )
                    Spacer(Modifier.height(12.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Button(
                            onClick = onExportAllPdfs,
                            colors = ButtonDefaults.buttonColors(containerColor = PrimaryIndigo),
                            shape = RoundedCornerShape(8.dp),
                            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp)
                        ) {
                            Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Export All $totalPdfs PDFs", style = MaterialTheme.typography.labelMedium)
                        }
                        OutlinedButton(
                            onClick = onExportAll,
                            shape = RoundedCornerShape(8.dp),
                            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = TextSecondary)
                        ) {
                            Icon(Icons.Default.Inventory2, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("PDFs + Previews", style = MaterialTheme.typography.labelMedium)
                        }
                    }
                }
            }
        }

        // Folder Contents card
        Surface(
            color = CardDark,
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(Modifier.padding(16.dp)) {
                Text(
                    "Folder Contents",
                    style = MaterialTheme.typography.labelLarge,
                    color = TextPrimary,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(Modifier.height(10.dp))
                backup.rootFolders.forEach { folder ->
                    FolderSummaryCard(folder = folder, depth = 0, onDocClick = onDocClick)
                }
            }
        }
    }
}

private fun countFolders(folders: List<FolderNode>): Int =
    folders.sumOf { 1 + countFolders(it.subfolders) }

@Composable
private fun StatCard(
    icon: ImageVector,
    iconTint: androidx.compose.ui.graphics.Color,
    value: String,
    label: String,
    modifier: Modifier = Modifier
) {
    Surface(
        color = SurfaceVariantDark,
        shape = RoundedCornerShape(10.dp),
        modifier = modifier
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Icon(icon, contentDescription = null, tint = iconTint, modifier = Modifier.size(20.dp))
            Text(value, style = MaterialTheme.typography.titleMedium, color = TextPrimary, fontWeight = FontWeight.Bold)
            Text(label, style = MaterialTheme.typography.labelSmall, color = TextSecondary)
        }
    }
}

@Composable
private fun FolderSummaryCard(
    folder: FolderNode,
    depth: Int,
    onDocClick: (FlexDocument, String) -> Unit
) {
    val pdfCount = folder.documents.count { it.pdfData != null }
    val paddingStart = (depth * 14).dp

    Column(modifier = Modifier.padding(start = paddingStart, bottom = 6.dp)) {
        Surface(
            color = SurfaceDark,
            shape = RoundedCornerShape(8.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(Modifier.padding(10.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Folder, contentDescription = null, tint = PrimaryIndigoLight, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(
                        folder.name,
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextPrimary,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.weight(1f)
                    )
                    if (folder.documents.isNotEmpty()) {
                        Text(
                            "$pdfCount/${folder.documents.size} PDFs",
                            style = MaterialTheme.typography.labelSmall,
                            color = TextMuted
                        )
                    }
                }

                if (folder.documents.isNotEmpty()) {
                    Spacer(Modifier.height(6.dp))
                    val shown = folder.documents.take(5)
                    shown.forEach { doc ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onDocClick(doc, folder.fullPath) }
                                .padding(vertical = 3.dp, horizontal = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.Description, contentDescription = null, tint = TextMuted, modifier = Modifier.size(12.dp))
                            Spacer(Modifier.width(6.dp))
                            Text(
                                doc.info?.name?.takeIf { it.isNotBlank() } ?: doc.name,
                                style = MaterialTheme.typography.labelSmall,
                                color = TextSecondary,
                                modifier = Modifier.weight(1f),
                                maxLines = 1,
                                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                            )
                            if (doc.pdfData != null) {
                                Spacer(Modifier.width(4.dp))
                                Text("PDF", style = MaterialTheme.typography.labelSmall, color = PrimaryIndigoLight)
                            }
                            doc.info?.modifiedDate?.takeIf { it > 0 }?.let {
                                Spacer(Modifier.width(4.dp))
                                Text(formatDate(it), style = MaterialTheme.typography.labelSmall, color = TextMuted)
                            }
                        }
                    }
                    if (folder.documents.size > 5) {
                        Text(
                            "+${folder.documents.size - 5} more…",
                            style = MaterialTheme.typography.labelSmall,
                            color = TextMuted,
                            modifier = Modifier.padding(start = 18.dp, top = 2.dp)
                        )
                    }
                }
            }
        }

        // Subfolders
        folder.subfolders.forEach { sub ->
            FolderSummaryCard(folder = sub, depth = depth + 1, onDocClick = onDocClick)
        }
    }
}

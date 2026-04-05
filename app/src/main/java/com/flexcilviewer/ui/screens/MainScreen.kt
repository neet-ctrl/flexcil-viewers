package com.flexcilviewer.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.flexcilviewer.data.FlexDocument
import com.flexcilviewer.data.FolderNode
import com.flexcilviewer.data.getAllDocuments
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
    rootFolders: List<FolderNode>,
    totalDocuments: Int,
    backupName: String,
    onOpenNewFile: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val drawerState = rememberDrawerState(DrawerValue.Open)

    val selectedDoc by viewModel.selectedDocument.collectAsState()
    val selectedFolder by viewModel.selectedFolder.collectAsState()
    val checkedDocs by viewModel.checkedDocs.collectAsState()
    val exportState by viewModel.exportState.collectAsState()

    var showExportDialog by remember { mutableStateOf(false) }
    var exportTarget by remember { mutableStateOf<List<Pair<FlexDocument, String>>>(emptyList()) }
    var pendingExportMode by remember { mutableStateOf("folder") }

    val folderPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree()
    ) { uri: Uri? ->
        if (uri != null) {
            if (pendingExportMode == "zip") {
                viewModel.exportAsZip(context, uri, exportTarget)
            } else {
                viewModel.exportToFolder(context, uri, exportTarget)
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
                    folders = rootFolders,
                    selectedDoc = selectedDoc,
                    selectedFolder = selectedFolder,
                    checkedDocs = checkedDocs,
                    onDocClick = { doc ->
                        viewModel.selectDocument(doc)
                        scope.launch { drawerState.close() }
                    },
                    onFolderClick = { folder -> viewModel.selectFolder(folder) },
                    onCheckToggle = { key -> viewModel.toggleDocCheck(key) },
                    onCheckAll = { folder -> viewModel.checkAllInFolder(folder) },
                    onDeselectAll = { viewModel.deselectAll() },
                    onExportSelected = {
                        val allDocs = getAllDocuments(rootFolders)
                        val selected = allDocs.filter { (doc, path) -> "${path}/${doc.name}" in checkedDocs }
                        openExportFor(selected)
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
                                backupName.ifBlank { "Flexcil Backup Viewer" },
                                style = MaterialTheme.typography.titleMedium,
                                color = TextPrimary,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1
                            )
                            Text(
                                "$totalDocuments documents",
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
                                val allDocs = getAllDocuments(rootFolders)
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
                            onExportClick = {
                                val allDocs = getAllDocuments(rootFolders)
                                val docEntry = allDocs.find { it.first.name == selectedDoc!!.name }
                                if (docEntry != null) openExportFor(listOf(docEntry))
                            },
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                    selectedFolder != null -> {
                        FolderOverview(
                            folder = selectedFolder!!,
                            onDocClick = { viewModel.selectDocument(it) },
                            onExportFolder = {
                                val docs = getAllDocuments(listOf(selectedFolder!!))
                                openExportFor(docs)
                            }
                        )
                    }
                    else -> {
                        WelcomeContent(
                            totalDocuments = totalDocuments,
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                }
            }
        }
    }

    // Export dialog
    if (showExportDialog) {
        ExportDialog(
            docs = exportTarget,
            onDismiss = { showExportDialog = false },
            onExportToFolder = {
                pendingExportMode = "folder"
                showExportDialog = false
                folderPickerLauncher.launch(null)
            },
            onExportAsZip = {
                pendingExportMode = "zip"
                showExportDialog = false
                folderPickerLauncher.launch(null)
            }
        )
    }

    // Export result snackbar
    val snackbarHostState = remember { SnackbarHostState() }
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

@Composable
private fun WelcomeContent(totalDocuments: Int, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(Icons.Default.TouchApp, contentDescription = null, tint = TextMuted, modifier = Modifier.size(56.dp))
        Spacer(Modifier.height(16.dp))
        Text("Select a document", style = MaterialTheme.typography.headlineMedium, color = TextSecondary, fontWeight = FontWeight.Medium)
        Spacer(Modifier.height(8.dp))
        Text("Tap a document in the sidebar to view it, or tap a folder to see its contents", style = MaterialTheme.typography.bodyMedium, color = TextMuted)
        Spacer(Modifier.height(8.dp))
        Text("$totalDocuments documents loaded", style = MaterialTheme.typography.labelLarge, color = PrimaryIndigoLight)
    }
}

@Composable
private fun FolderOverview(
    folder: FolderNode,
    onDocClick: (FlexDocument) -> Unit,
    onExportFolder: () -> Unit
) {
    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.FolderOpen, contentDescription = null, tint = PrimaryIndigoLight)
            Spacer(Modifier.width(10.dp))
            Text(folder.name, style = MaterialTheme.typography.titleLarge, color = TextPrimary, modifier = Modifier.weight(1f))
            IconButton(onClick = onExportFolder) {
                Icon(Icons.Default.FileDownload, contentDescription = "Export folder", tint = PrimaryIndigoLight)
            }
        }
        Text(
            "${folder.totalDocuments} documents • ${folder.subfolders.size} subfolders",
            style = MaterialTheme.typography.bodyMedium,
            color = TextSecondary
        )
        Spacer(Modifier.height(12.dp))
        HorizontalDivider(color = DividerColor)
        Spacer(Modifier.height(12.dp))

        Text("Tap a document in the sidebar to view it", style = MaterialTheme.typography.bodyMedium, color = TextMuted)
    }
}

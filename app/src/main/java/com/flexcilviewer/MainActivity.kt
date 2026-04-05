package com.flexcilviewer

import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.flexcilviewer.ui.screens.HomeScreen
import com.flexcilviewer.ui.screens.MainScreen
import com.flexcilviewer.ui.theme.BackgroundDark
import com.flexcilviewer.ui.theme.FlexcilViewerTheme
import com.flexcilviewer.ui.theme.PrimaryIndigoLight
import com.flexcilviewer.ui.theme.TextPrimary
import com.flexcilviewer.ui.theme.TextSecondary
import com.flexcilviewer.viewmodel.FlexViewModel
import com.flexcilviewer.viewmodel.ParseState

class MainActivity : ComponentActivity() {

    private val viewModel: FlexViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Handle file opened externally (e.g. from Files app)
        val intentUri: Uri? = intent?.data

        setContent {
            FlexcilViewerTheme {
                FlexcilApp(viewModel = viewModel, initialUri = intentUri)
            }
        }
    }
}

@Composable
fun FlexcilApp(viewModel: FlexViewModel, initialUri: Uri? = null) {
    val context = LocalContext.current
    val parseState by viewModel.parseState.collectAsState()

    // Open the intent URI once on launch
    LaunchedEffect(initialUri) {
        if (initialUri != null && parseState is ParseState.Idle) {
            viewModel.openFile(context, initialUri)
        }
    }

    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) viewModel.openFile(context, uri)
    }

    fun launchFilePicker() {
        filePickerLauncher.launch(arrayOf("*/*", "application/octet-stream"))
    }

    Box(modifier = Modifier.fillMaxSize().background(BackgroundDark)) {
        when (val state = parseState) {
            is ParseState.Idle -> {
                HomeScreen(
                    onOpenFile = { launchFilePicker() },
                    onOpenUri = { uri -> viewModel.openFile(context, uri) },
                    modifier = Modifier.fillMaxSize()
                )
            }

            is ParseState.Loading -> {
                Column(
                    modifier = Modifier.fillMaxSize(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    CircularProgressIndicator(color = PrimaryIndigoLight)
                    Spacer(Modifier.height(20.dp))
                    Text(state.progress, color = TextPrimary, style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(8.dp))
                    Text("Please wait…", color = TextSecondary, style = MaterialTheme.typography.bodyMedium)
                }
            }

            is ParseState.Success -> {
                MainScreen(
                    viewModel = viewModel,
                    backup = state.backup,
                    onOpenNewFile = { launchFilePicker() }
                )
            }

            is ParseState.Error -> {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Text("Failed to open file", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.headlineSmall)
                    Spacer(Modifier.height(12.dp))
                    Text(state.message, color = TextSecondary, style = MaterialTheme.typography.bodyMedium)
                    Spacer(Modifier.height(24.dp))
                    Button(onClick = { viewModel.clearError() }) {
                        Text("Try Again")
                    }
                }
            }
        }
    }
}

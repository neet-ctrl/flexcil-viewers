package com.flexcilviewer.ui.components

import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.flexcilviewer.ui.theme.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

@Composable
fun PdfViewer(pdfBytes: ByteArray, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    var pages by remember { mutableStateOf<List<Bitmap>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var globalScale by remember { mutableFloatStateOf(1.2f) }
    var horizontalMode by remember { mutableStateOf(false) }
    var maximized by remember { mutableStateOf(false) }

    LaunchedEffect(pdfBytes) {
        loading = true
        error = null
        try {
            val result = withContext(Dispatchers.IO) {
                val tmpFile = File.createTempFile("flex_pdf_", ".pdf", context.cacheDir)
                tmpFile.writeBytes(pdfBytes)
                val pfd = ParcelFileDescriptor.open(tmpFile, ParcelFileDescriptor.MODE_READ_ONLY)
                val renderer = PdfRenderer(pfd)
                val bitmaps = mutableListOf<Bitmap>()
                for (i in 0 until renderer.pageCount) {
                    val page = renderer.openPage(i)
                    val scale = 2.0f
                    val bmp = Bitmap.createBitmap(
                        (page.width * scale).toInt(),
                        (page.height * scale).toInt(),
                        Bitmap.Config.ARGB_8888
                    )
                    bmp.eraseColor(android.graphics.Color.WHITE)
                    page.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                    page.close()
                    bitmaps.add(bmp)
                }
                renderer.close()
                pfd.close()
                tmpFile.delete()
                bitmaps
            }
            pages = result
        } catch (e: Exception) {
            error = e.message ?: "Failed to render PDF"
        }
        loading = false
    }

    // Fullscreen dialog
    if (maximized && pages.isNotEmpty()) {
        Dialog(
            onDismissRequest = { maximized = false },
            properties = DialogProperties(usePlatformDefaultWidth = false, dismissOnBackPress = true, dismissOnClickOutside = false)
        ) {
            Box(modifier = Modifier.fillMaxSize().background(BackgroundDark)) {
                PdfContent(
                    pages = pages,
                    globalScale = globalScale,
                    horizontalMode = horizontalMode,
                    onScaleChange = { globalScale = it },
                    onHorizontalToggle = { horizontalMode = it },
                    modifier = Modifier.fillMaxSize(),
                    isMaximized = true,
                    onToggleMaximize = { maximized = false }
                )
            }
        }
    }

    Box(modifier = modifier.background(BackgroundDark)) {
        when {
            loading -> {
                Column(
                    modifier = Modifier.fillMaxSize(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    CircularProgressIndicator(color = PrimaryIndigoLight)
                    Spacer(Modifier.height(12.dp))
                    Text("Rendering PDF…", color = TextSecondary, style = MaterialTheme.typography.bodyMedium)
                }
            }
            error != null -> {
                Column(
                    modifier = Modifier.fillMaxSize().padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Icon(Icons.Default.PictureAsPdf, contentDescription = null, tint = AccentRed, modifier = Modifier.size(48.dp))
                    Spacer(Modifier.height(12.dp))
                    Text("Unable to render PDF", color = AccentRed, style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(8.dp))
                    Text(error ?: "", color = TextSecondary, style = MaterialTheme.typography.bodyMedium)
                    if (error?.contains("password", ignoreCase = true) == true ||
                        error?.contains("encrypt", ignoreCase = true) == true) {
                        Spacer(Modifier.height(12.dp))
                        Surface(color = AccentAmber.copy(alpha = 0.15f), shape = RoundedCornerShape(8.dp)) {
                            Row(
                                Modifier.padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(Icons.Default.Lock, contentDescription = null, tint = AccentAmber, modifier = Modifier.size(16.dp))
                                Text("This PDF is password-protected.", color = AccentAmber, style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                }
            }
            else -> {
                PdfContent(
                    pages = pages,
                    globalScale = globalScale,
                    horizontalMode = horizontalMode,
                    onScaleChange = { globalScale = it },
                    onHorizontalToggle = { horizontalMode = it },
                    modifier = Modifier.fillMaxSize(),
                    isMaximized = false,
                    onToggleMaximize = { maximized = true }
                )
            }
        }
    }
}

// ── Shared PDF content (used in both normal and maximized modes) ──────────────

@Composable
private fun PdfContent(
    pages: List<Bitmap>,
    globalScale: Float,
    horizontalMode: Boolean,
    onScaleChange: (Float) -> Unit,
    onHorizontalToggle: (Boolean) -> Unit,
    isMaximized: Boolean,
    onToggleMaximize: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier) {

        // ── Toolbar ───────────────────────────────────────────────────────────
        Surface(color = SurfaceDark, modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Return / back button when maximized
                if (isMaximized) {
                    IconButton(onClick = onToggleMaximize, modifier = Modifier.size(36.dp)) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Exit fullscreen", tint = PrimaryIndigoLight, modifier = Modifier.size(20.dp))
                    }
                    Spacer(Modifier.width(4.dp))
                }

                // Zoom out
                IconButton(onClick = { onScaleChange((globalScale - 0.2f).coerceAtLeast(0.5f)) }, modifier = Modifier.size(36.dp)) {
                    Icon(Icons.Default.ZoomOut, contentDescription = "Zoom out", tint = TextSecondary, modifier = Modifier.size(20.dp))
                }

                Text(
                    "${(globalScale * 100).toInt()}%",
                    color = TextSecondary,
                    style = MaterialTheme.typography.labelMedium,
                    modifier = Modifier.width(48.dp),
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                )

                // Zoom in
                IconButton(onClick = { onScaleChange((globalScale + 0.2f).coerceAtMost(4f)) }, modifier = Modifier.size(36.dp)) {
                    Icon(Icons.Default.ZoomIn, contentDescription = "Zoom in", tint = TextSecondary, modifier = Modifier.size(20.dp))
                }

                // Reset zoom
                IconButton(onClick = { onScaleChange(1.2f) }, modifier = Modifier.size(36.dp)) {
                    Icon(Icons.Default.RestartAlt, contentDescription = "Reset zoom", tint = TextMuted, modifier = Modifier.size(18.dp))
                }

                Spacer(Modifier.weight(1f))

                Text(
                    "${pages.size} page${if (pages.size != 1) "s" else ""}",
                    color = TextMuted,
                    style = MaterialTheme.typography.labelSmall
                )

                Spacer(Modifier.width(4.dp))

                // Horizontal/vertical toggle
                IconButton(onClick = { onHorizontalToggle(!horizontalMode) }, modifier = Modifier.size(36.dp)) {
                    Icon(
                        if (horizontalMode) Icons.Default.SwapVert else Icons.Default.SwapHoriz,
                        contentDescription = if (horizontalMode) "Switch to vertical" else "Switch to horizontal",
                        tint = PrimaryIndigoLight,
                        modifier = Modifier.size(20.dp)
                    )
                }

                // Maximize / restore button
                IconButton(onClick = onToggleMaximize, modifier = Modifier.size(36.dp)) {
                    Icon(
                        if (isMaximized) Icons.Default.FullscreenExit else Icons.Default.Fullscreen,
                        contentDescription = if (isMaximized) "Exit fullscreen" else "Fullscreen",
                        tint = TextSecondary,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }

        HorizontalDivider(color = DividerColor)

        // ── Pages ─────────────────────────────────────────────────────────────
        if (horizontalMode) {
            HorizontalPdfPages(pages = pages, globalScale = globalScale)
        } else {
            VerticalPdfPages(pages = pages, globalScale = globalScale)
        }
    }
}

// ── Vertical scroll with scrollbar indicator ──────────────────────────────────

@Composable
private fun VerticalPdfPages(pages: List<Bitmap>, globalScale: Float) {
    val listState = rememberLazyListState()

    Box(modifier = Modifier.fillMaxSize()) {
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            itemsIndexed(pages) { index, bmp ->
                PdfPageItem(bitmap = bmp, pageNumber = index + 1, total = pages.size, globalScale = globalScale)
            }
        }

        // Wider scrollbar track + thumb
        if (pages.size > 1) {
            Box(
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .fillMaxHeight()
                    .width(12.dp)
                    .padding(vertical = 8.dp, horizontal = 2.dp)
                    .drawWithContent {
                        // Track
                        drawRoundRect(
                            color = SurfaceVariantDark,
                            cornerRadius = CornerRadius(6f)
                        )
                        // Thumb
                        val totalItems = listState.layoutInfo.totalItemsCount.takeIf { it > 0 } ?: return@drawWithContent
                        val visibleItems = listState.layoutInfo.visibleItemsInfo
                        if (visibleItems.isEmpty()) return@drawWithContent
                        val thumbFraction = (visibleItems.size.toFloat() / totalItems).coerceIn(0.1f, 1f)
                        val scrollFraction = listState.firstVisibleItemIndex.toFloat() / totalItems
                        val trackHeight = size.height
                        val thumbHeight = (trackHeight * thumbFraction).coerceAtLeast(40f)
                        val thumbTop = ((trackHeight - thumbHeight) * scrollFraction).coerceIn(0f, trackHeight - thumbHeight)
                        drawRoundRect(
                            color = PrimaryIndigoLight.copy(alpha = if (listState.isScrollInProgress) 0.9f else 0.55f),
                            topLeft = Offset(0f, thumbTop),
                            size = Size(size.width, thumbHeight),
                            cornerRadius = CornerRadius(6f)
                        )
                    }
            )
        }

        // Page counter pill
        val currentPage by remember { derivedStateOf { (listState.firstVisibleItemIndex + 1).coerceIn(1, pages.size) } }
        Surface(
            color = BackgroundDark.copy(alpha = 0.80f),
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 12.dp)
        ) {
            Text(
                "$currentPage / ${pages.size}",
                color = TextPrimary,
                style = MaterialTheme.typography.labelSmall,
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
            )
        }
    }
}

// ── Horizontal page swipe mode ────────────────────────────────────────────────

@Composable
private fun HorizontalPdfPages(pages: List<Bitmap>, globalScale: Float) {
    val pagerState = rememberPagerState(pageCount = { pages.size })

    Box(modifier = Modifier.fillMaxSize()) {
        HorizontalPager(
            state = pagerState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 16.dp),
            pageSpacing = 12.dp
        ) { index ->
            PdfPageItem(bitmap = pages[index], pageNumber = index + 1, total = pages.size, globalScale = globalScale)
        }

        // Page counter pill
        Surface(
            color = BackgroundDark.copy(alpha = 0.80f),
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 12.dp)
        ) {
            Text(
                "${pagerState.currentPage + 1} / ${pages.size}",
                color = TextPrimary,
                style = MaterialTheme.typography.labelSmall,
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
            )
        }

        // Dot indicators for small page counts
        if (pages.size in 2..10) {
            Row(
                modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 40.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                pages.indices.forEach { i ->
                    Box(
                        modifier = Modifier
                            .size(if (i == pagerState.currentPage) 8.dp else 5.dp)
                            .clip(RoundedCornerShape(50))
                            .background(if (i == pagerState.currentPage) PrimaryIndigoLight else TextMuted.copy(alpha = 0.5f))
                    )
                }
            }
        }
    }
}

// ── Single page item ──────────────────────────────────────────────────────────

@Composable
private fun PdfPageItem(bitmap: Bitmap, pageNumber: Int, total: Int, globalScale: Float) {
    var pinchScale by remember { mutableFloatStateOf(1f) }
    var offsetX by remember { mutableFloatStateOf(0f) }
    var offsetY by remember { mutableFloatStateOf(0f) }

    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text("Page $pageNumber / $total", color = TextMuted, style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(bottom = 6.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                .background(Color.White)
                .pointerInput(Unit) {
                    detectTransformGestures { _, pan, zoom, _ ->
                        pinchScale = (pinchScale * zoom).coerceIn(0.5f, 4f)
                        offsetX += pan.x
                        offsetY += pan.y
                    }
                }
        ) {
            Image(
                bitmap = bitmap.asImageBitmap(),
                contentDescription = "Page $pageNumber",
                modifier = Modifier
                    .fillMaxWidth()
                    .graphicsLayer(
                        scaleX = globalScale * pinchScale,
                        scaleY = globalScale * pinchScale,
                        translationX = offsetX,
                        translationY = offsetY
                    ),
                contentScale = ContentScale.FillWidth
            )
        }
    }
}

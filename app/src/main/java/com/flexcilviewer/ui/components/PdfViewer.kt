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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.flexcilviewer.ui.theme.BackgroundDark
import com.flexcilviewer.ui.theme.CardDark
import com.flexcilviewer.ui.theme.TextSecondary
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

@Composable
fun PdfViewer(pdfBytes: ByteArray, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    var pageCount by remember { mutableIntStateOf(0) }
    var pages by remember { mutableStateOf<List<Bitmap>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }

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
            pageCount = result.size
        } catch (e: Exception) {
            error = e.message ?: "Failed to render PDF"
        }
        loading = false
    }

    Box(modifier = modifier.background(BackgroundDark)) {
        when {
            loading -> {
                Column(
                    modifier = Modifier.fillMaxSize(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
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
                    Text("Unable to render PDF", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(8.dp))
                    Text(error ?: "", color = TextSecondary, style = MaterialTheme.typography.bodyMedium)
                }
            }
            else -> {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    itemsIndexed(pages) { index, bmp ->
                        PdfPageItem(bitmap = bmp, pageNumber = index + 1, total = pageCount)
                    }
                }
            }
        }
    }
}

@Composable
private fun PdfPageItem(bitmap: Bitmap, pageNumber: Int, total: Int) {
    var scale by remember { mutableFloatStateOf(1f) }
    var offsetX by remember { mutableFloatStateOf(0f) }
    var offsetY by remember { mutableFloatStateOf(0f) }

    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            "Page $pageNumber / $total",
            color = TextSecondary,
            style = MaterialTheme.typography.labelSmall,
            modifier = Modifier.padding(bottom = 4.dp)
        )
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                .background(Color.White)
                .pointerInput(Unit) {
                    detectTransformGestures { _, pan, zoom, _ ->
                        scale = (scale * zoom).coerceIn(0.5f, 4f)
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
                        scaleX = scale,
                        scaleY = scale,
                        translationX = offsetX,
                        translationY = offsetY
                    ),
                contentScale = ContentScale.FillWidth
            )
        }
    }
}

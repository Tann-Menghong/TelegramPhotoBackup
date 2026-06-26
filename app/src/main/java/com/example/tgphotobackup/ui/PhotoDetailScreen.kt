package com.example.tgphotobackup.ui

import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.VideoFile
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.example.tgphotobackup.data.UploadedPhoto
import com.example.tgphotobackup.data.contentUri
import com.example.tgphotobackup.data.isVideo
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun PhotoDetailScreen(
    photos: List<UploadedPhoto>,
    initialIndex: Int,
    vm: MainViewModel,
    onBack: () -> Unit
) {
    val pagerState    = rememberPagerState(initialPage = initialIndex.coerceIn(0, photos.lastIndex),
                                          pageCount = { photos.size })
    var scale         by remember { mutableFloatStateOf(1f) }
    var offsetX       by remember { mutableFloatStateOf(0f) }
    var offsetY       by remember { mutableFloatStateOf(0f) }
    val restoreStatus by vm.restoreStatus.collectAsState()
    val currentPhoto  = photos.getOrNull(pagerState.currentPage) ?: return
    val context       = LocalContext.current

    LaunchedEffect(pagerState.currentPage) { scale = 1f; offsetX = 0f; offsetY = 0f }

    Box(Modifier.fillMaxSize().background(Color.Black)) {

        // ── Swipe pager ────────────────────────────────────────
        HorizontalPager(
            state = pagerState,
            userScrollEnabled = scale <= 1.05f,
            modifier = Modifier.fillMaxSize()
        ) { page ->
            AsyncImage(
                model = photos[page].contentUri(),
                contentDescription = photos[page].displayName,
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(Unit) {
                        detectTransformGestures { _, pan, zoom, _ ->
                            scale = (scale * zoom).coerceIn(0.5f, 8f)
                            if (scale > 1f) { offsetX += pan.x; offsetY += pan.y }
                            else { offsetX = 0f; offsetY = 0f }
                        }
                    }
                    .graphicsLayer(
                        scaleX = scale, scaleY = scale,
                        translationX = offsetX, translationY = offsetY
                    ),
                contentScale = ContentScale.Fit
            )
        }

        // ── Top bar ────────────────────────────────────────────
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .background(Color.Black.copy(alpha = 0.45f))
                .padding(horizontal = 4.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = {
                vm.clearRestoreStatus()
                scale = 1f; offsetX = 0f; offsetY = 0f
                onBack()
            }) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = Color.White)
            }
            Text(
                currentPhoto.displayName,
                color = Color.White,
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.weight(1f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                "${pagerState.currentPage + 1} / ${photos.size}",
                color = Color.White.copy(alpha = 0.6f),
                style = MaterialTheme.typography.labelSmall,
                modifier = Modifier.padding(end = 4.dp)
            )

            // Video type badge
            Icon(
                if (currentPhoto.isVideo()) Icons.Default.VideoFile else Icons.Default.Image,
                null, tint = Color.White.copy(alpha = 0.7f),
                modifier = Modifier.size(20.dp)
            )

            // Play button for videos (opens system video player)
            if (currentPhoto.isVideo()) {
                IconButton(onClick = {
                    val intent = Intent(Intent.ACTION_VIEW).apply {
                        setDataAndType(currentPhoto.contentUri(), currentPhoto.mimeType)
                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    }
                    runCatching { context.startActivity(intent) }
                }) {
                    Icon(Icons.Default.PlayCircle, "Play video", tint = Color.White)
                }
            }

            // Share button
            IconButton(onClick = {
                val shareIntent = Intent.createChooser(
                    Intent(Intent.ACTION_SEND).apply {
                        type = currentPhoto.mimeType
                        putExtra(Intent.EXTRA_STREAM, currentPhoto.contentUri())
                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    }, "Share"
                )
                context.startActivity(shareIntent)
            }) {
                Icon(Icons.Default.Share, "Share", tint = Color.White)
            }

            // Restore / download button
            IconButton(onClick = { vm.restorePhoto(currentPhoto) }) {
                if (restoreStatus == "Downloading…") {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        color = Color.White,
                        strokeWidth = 2.dp
                    )
                } else {
                    Icon(Icons.Default.Download, "Restore to gallery", tint = Color.White)
                }
            }
        }

        // ── Bottom info ────────────────────────────────────────
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .background(Color.Black.copy(alpha = 0.5f))
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(3.dp)
        ) {
            restoreStatus?.let { msg ->
                val isProgress = msg == "Downloading…"
                Text(msg,
                    style = MaterialTheme.typography.labelMedium,
                    color = when {
                        isProgress        -> Color.White.copy(alpha = 0.7f)
                        msg.contains("✓") -> Color(0xFF80FF80)
                        else              -> Color(0xFFFF8080)
                    })
                Spacer(Modifier.height(4.dp))
            }
            val fmt = SimpleDateFormat("MMM d, yyyy  HH:mm", Locale.getDefault())
            InfoRow("Backed up", fmt.format(Date(currentPhoto.uploadedAt)))
            InfoRow("File size", formatBytes(currentPhoto.sizeBytes))
            InfoRow("Album",     currentPhoto.bucketName.ifBlank { "—" })
            InfoRow("Quality",   "Original (no compression)")
        }
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, style = MaterialTheme.typography.bodySmall,
            color = Color.White.copy(alpha = 0.55f), fontWeight = FontWeight.Medium)
        Text(value, style = MaterialTheme.typography.bodySmall, color = Color.White)
    }
}

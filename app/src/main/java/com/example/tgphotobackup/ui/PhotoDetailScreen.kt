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
import androidx.compose.material.icons.filled.CloudOff
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.shape.CircleShape
import coil.compose.AsyncImage
import coil.compose.SubcomposeAsyncImage
import com.example.tgphotobackup.backup.ThumbnailCache
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
    val shareStatus   by vm.shareStatus.collectAsState()
    val playStatus    by vm.playStatus.collectAsState()
    val currentPhoto  = photos.getOrNull(pagerState.currentPage) ?: return
    val context       = LocalContext.current

    LaunchedEffect(pagerState.currentPage) {
        scale = 1f; offsetX = 0f; offsetY = 0f
        vm.clearShareStatus()
        vm.clearPlayStatus()
        if (currentPhoto.isVideo()) vm.playVideo(currentPhoto, context)
    }

    Box(Modifier.fillMaxSize().background(Color.Black)) {

        // ── Swipe pager ────────────────────────────────────────
        HorizontalPager(
            state = pagerState,
            userScrollEnabled = scale <= 1.05f,
            modifier = Modifier.fillMaxSize()
        ) { page ->
            SubcomposeAsyncImage(
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
                contentScale = ContentScale.Fit,
                error = {
                    val pageCtx   = LocalContext.current
                    val pagePhoto = photos[page]
                    val thumbFile = ThumbnailCache.file(pageCtx, pagePhoto.contentHash)
                    if (pagePhoto.isVideo() && thumbFile.exists()) {
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            AsyncImage(
                                model = thumbFile,
                                contentDescription = pagePhoto.displayName,
                                modifier = Modifier.fillMaxSize(),
                                contentScale = ContentScale.Fit
                            )
                            Box(
                                Modifier
                                    .size(72.dp)
                                    .background(Color.Black.copy(alpha = 0.45f), CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(Icons.Default.PlayCircle, "Play", Modifier.size(56.dp),
                                    tint = Color.White.copy(alpha = 0.85f))
                            }
                        }
                    } else {
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(12.dp),
                                modifier = Modifier.padding(40.dp)
                            ) {
                                Icon(Icons.Default.CloudOff, null, Modifier.size(64.dp),
                                    tint = Color.White.copy(alpha = 0.25f))
                                Text("Deleted from device",
                                    color = Color.White.copy(alpha = 0.6f),
                                    style = MaterialTheme.typography.bodyLarge,
                                    textAlign = TextAlign.Center)
                                Text(
                                    if (pagePhoto.isVideo())
                                        "Tap  ▶  Play to stream\nor  ↓  Download to save back"
                                    else
                                        "Tap  🔗  Share to send directly\nor  ↓  Download to save back to gallery",
                                    color = Color.White.copy(alpha = 0.38f),
                                    style = MaterialTheme.typography.bodySmall,
                                    textAlign = TextAlign.Center)
                            }
                        }
                    }
                }
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
                vm.clearShareStatus()
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

            // Play button for videos — downloads from Telegram if local file was deleted
            if (currentPhoto.isVideo()) {
                val isPlaying = playStatus != null && playStatus?.contains("failed") == false
                IconButton(
                    onClick = { vm.playVideo(currentPhoto, context) },
                    enabled = !isPlaying
                ) {
                    if (isPlaying) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            color = Color.White,
                            strokeWidth = 2.dp
                        )
                    } else {
                        Icon(Icons.Default.PlayCircle, "Play video", tint = Color.White)
                    }
                }
            }

            // Share button — downloads from Telegram first if local file was deleted
            val isSharing = shareStatus != null && shareStatus?.contains("failed") == false
            IconButton(
                onClick = { vm.sharePhoto(currentPhoto, context) },
                enabled = !isSharing
            ) {
                if (isSharing) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        color = Color.White,
                        strokeWidth = 2.dp
                    )
                } else {
                    Icon(Icons.Default.Share, "Share", tint = Color.White)
                }
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
            shareStatus?.let { msg ->
                Row(verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    if (!msg.contains("failed")) {
                        CircularProgressIndicator(Modifier.size(12.dp),
                            color = Color.White.copy(0.7f), strokeWidth = 1.5.dp)
                    }
                    Text(msg,
                        style = MaterialTheme.typography.labelMedium,
                        color = if (msg.contains("failed")) Color(0xFFFF8080)
                                else Color.White.copy(alpha = 0.7f))
                }
                Spacer(Modifier.height(4.dp))
            }
            playStatus?.let { msg ->
                Row(verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    if (!msg.contains("failed")) {
                        CircularProgressIndicator(Modifier.size(12.dp),
                            color = Color.White.copy(0.7f), strokeWidth = 1.5.dp)
                    }
                    Text(msg,
                        style = MaterialTheme.typography.labelMedium,
                        color = if (msg.contains("failed")) Color(0xFFFF8080)
                                else Color.White.copy(alpha = 0.7f))
                }
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

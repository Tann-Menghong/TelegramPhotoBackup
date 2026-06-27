package com.example.tgphotobackup.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.compose.SubcomposeAsyncImage
import coil.decode.VideoFrameDecoder
import coil.request.ImageRequest
import com.example.tgphotobackup.backup.ThumbnailCache
import com.example.tgphotobackup.data.UploadedPhoto
import com.example.tgphotobackup.data.contentUri
import com.example.tgphotobackup.data.isVideo
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

enum class SortMode(val label: String) {
    DATE_DESC("Newest first"),
    DATE_ASC("Oldest first"),
    SIZE_DESC("Largest first"),
    NAME("A – Z")
}

enum class TypeFilter(val label: String) {
    ALL("All"), PHOTOS("Photos"), VIDEOS("Videos")
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun GalleryScreen(
    vm: MainViewModel,
    onPhotoClick: (index: Int) -> Unit
) {
    val allPhotos by vm.allBackedUpPhotos.collectAsState()
    if (allPhotos.isEmpty()) { EmptyGallery(); return }

    var selectedHashes by remember { mutableStateOf(setOf<String>()) }
    var sortMode       by remember { mutableStateOf(SortMode.DATE_DESC) }
    var typeFilter     by remember { mutableStateOf(TypeFilter.ALL) }
    var showSortMenu   by remember { mutableStateOf(false) }
    var selectedMonth  by remember { mutableStateOf<String?>(null) }
    var showMonthMenu  by remember { mutableStateOf(false) }
    val monthFmt       = remember { SimpleDateFormat("MMMM yyyy", Locale.getDefault()) }
    val context        = LocalContext.current
    val inSelectionMode = selectedHashes.isNotEmpty()

    val availableMonths = remember(allPhotos) {
        allPhotos.map { monthFmt.format(Date(it.uploadedAt)) }.distinct()
            .sortedByDescending { runCatching { monthFmt.parse(it)?.time ?: 0L }.getOrDefault(0L) }
    }

    // Apply sort + type filter to the full list
    val displayPhotos = remember(allPhotos, sortMode, typeFilter, selectedMonth) {
        allPhotos
            .filter { photo ->
                when (typeFilter) {
                    TypeFilter.PHOTOS -> !photo.isVideo()
                    TypeFilter.VIDEOS ->  photo.isVideo()
                    TypeFilter.ALL    ->  true
                }
            }
            .filter { photo ->
                selectedMonth == null || monthFmt.format(Date(photo.uploadedAt)) == selectedMonth
            }
            .let { list ->
                when (sortMode) {
                    SortMode.DATE_DESC -> list.sortedByDescending { it.uploadedAt }
                    SortMode.DATE_ASC  -> list.sortedBy { it.uploadedAt }
                    SortMode.SIZE_DESC -> list.sortedByDescending { it.sizeBytes }
                    SortMode.NAME      -> list.sortedBy { it.displayName }
                }
            }
    }

    Column(Modifier.fillMaxSize()) {

        // ── Selection action bar ───────────────────────────────
        if (inSelectionMode) {
            Surface(color = MaterialTheme.colorScheme.primaryContainer,
                modifier = Modifier.fillMaxWidth()) {
                Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = { selectedHashes = emptySet() }) {
                        Icon(Icons.Default.Close, "Clear selection",
                            tint = MaterialTheme.colorScheme.onPrimaryContainer)
                    }
                    Text("${selectedHashes.size} selected",
                        modifier = Modifier.weight(1f),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer)
                    // Share selected — downloads from Telegram if local files were deleted
                    IconButton(onClick = {
                        val photos = allPhotos.filter { it.contentHash in selectedHashes }
                        vm.sharePhotos(photos, context)
                        selectedHashes = emptySet()
                    }) {
                        Icon(Icons.Default.Share, "Share",
                            tint = MaterialTheme.colorScheme.onPrimaryContainer)
                    }
                    // Delete local copies
                    IconButton(onClick = {
                        vm.batchDeleteLocal(selectedHashes)
                        selectedHashes = emptySet()
                    }) {
                        Icon(Icons.Default.DeleteSweep, "Delete local",
                            tint = MaterialTheme.colorScheme.error)
                    }
                }
            }
        } else {
            // ── Filter row (type chips + month + sort) ─────────
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                TypeFilter.entries.forEach { filter ->
                    FilterChip(
                        selected = typeFilter == filter,
                        onClick  = { typeFilter = filter },
                        label    = { Text(filter.label, style = MaterialTheme.typography.labelMedium) }
                    )
                }
                Box {
                    FilterChip(
                        selected = selectedMonth != null,
                        onClick = { showMonthMenu = true },
                        label = { Text(selectedMonth ?: "Month", style = MaterialTheme.typography.labelMedium) }
                    )
                    DropdownMenu(showMonthMenu, { showMonthMenu = false }) {
                        DropdownMenuItem(text = { Text("All months") },
                            onClick = { selectedMonth = null; showMonthMenu = false })
                        availableMonths.forEach { month ->
                            DropdownMenuItem(
                                text = { Text(month) },
                                onClick = { selectedMonth = month; showMonthMenu = false },
                                leadingIcon = if (selectedMonth == month) {
                                    { Icon(Icons.Default.Check, null, Modifier.size(16.dp)) }
                                } else null
                            )
                        }
                    }
                }
                Spacer(Modifier.weight(1f))
                Box {
                    IconButton(onClick = { showSortMenu = true }, modifier = Modifier.size(32.dp)) {
                        Icon(Icons.AutoMirrored.Filled.Sort, "Sort", Modifier.size(20.dp),
                            tint = if (sortMode != SortMode.DATE_DESC)
                                MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    DropdownMenu(showSortMenu, { showSortMenu = false }) {
                        SortMode.entries.forEach { mode ->
                            DropdownMenuItem(
                                text = { Text(mode.label) },
                                onClick = { sortMode = mode; showSortMenu = false },
                                leadingIcon = if (sortMode == mode) {
                                    { Icon(Icons.Default.Check, null, Modifier.size(16.dp)) }
                                } else null
                            )
                        }
                    }
                }
            }
        }

        // ── Date-grouped grid ──────────────────────────────────
        DateGroupedGrid(
            displayPhotos = displayPhotos,
            allPhotos = allPhotos,
            selectedHashes = selectedHashes,
            onPhotoClick = onPhotoClick,
            onSelectionChange = { selectedHashes = it }
        )
    }
}

// ─── Date-grouped grid with selection ────────────────────────────────────────

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun DateGroupedGrid(
    displayPhotos: List<UploadedPhoto>,
    allPhotos: List<UploadedPhoto>,
    selectedHashes: Set<String>,
    onPhotoClick: (index: Int) -> Unit,
    onSelectionChange: (Set<String>) -> Unit
) {
    val inSelectionMode = selectedHashes.isNotEmpty()
    if (displayPhotos.isEmpty()) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("No photos found", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        return
    }

    LazyVerticalGrid(
        columns = GridCells.Fixed(3),
        contentPadding = PaddingValues(bottom = 8.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
        modifier = Modifier.fillMaxSize()
    ) {
        val grouped = groupPhotosByDate(displayPhotos)
        grouped.forEach { entry ->
            when (entry) {
                is String -> item(span = { GridItemSpan(maxLineSpan) }) {
                    Text(
                        entry,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 4.dp),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
                is UploadedPhoto -> item(key = entry.contentHash) {
                    val isSelected = entry.contentHash in selectedHashes
                    Thumbnail(
                        photo = entry,
                        isSelected = isSelected,
                        inSelectionMode = inSelectionMode,
                        onClick = {
                            if (inSelectionMode) {
                                onSelectionChange(
                                    if (isSelected) selectedHashes - entry.contentHash
                                    else selectedHashes + entry.contentHash
                                )
                            } else {
                                onPhotoClick(allPhotos.indexOf(entry).coerceAtLeast(0))
                            }
                        },
                        onLongClick = { onSelectionChange(selectedHashes + entry.contentHash) }
                    )
                }
            }
        }
    }
}

// ─── Thumbnail — Google Photos style ─────────────────────────────────────────

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun Thumbnail(
    photo: UploadedPhoto,
    isSelected: Boolean,
    inSelectionMode: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit
) {
    val context = LocalContext.current
    val thumbModel = remember(photo.contentHash) {
        if (photo.isVideo()) {
            ImageRequest.Builder(context)
                .data(photo.contentUri())
                .decoderFactory(VideoFrameDecoder.Factory())
                .build()
        } else photo.contentUri()
    }
    Box(
        Modifier
            .aspectRatio(1f)
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
    ) {
        SubcomposeAsyncImage(
            model = thumbModel,
            contentDescription = photo.displayName,
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop,
            loading = {
                Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surfaceVariant))
            },
            error = {
                val thumbFile = ThumbnailCache.file(LocalContext.current, photo.contentHash)
                if (thumbFile.exists()) {
                    AsyncImage(model = thumbFile, contentDescription = null,
                        modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                } else {
                    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surfaceVariant),
                        contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.padding(4.dp)) {
                            Icon(Icons.Default.CloudOff, null, Modifier.size(20.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f))
                            Text(photo.displayName, fontSize = 7.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                                maxLines = 2, overflow = TextOverflow.Ellipsis,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.padding(top = 2.dp))
                        }
                    }
                }
            }
        )

        // Gradient scrim at top for selection circle visibility
        if (inSelectionMode) {
            Box(Modifier.fillMaxWidth().height(48.dp).align(Alignment.TopStart)
                .background(Brush.verticalGradient(
                    listOf(Color.Black.copy(alpha = 0.3f), Color.Transparent))))
        }

        // Selection indicator — top-left (Google Photos style)
        Box(Modifier.align(Alignment.TopStart).padding(6.dp)) {
            if (inSelectionMode) {
                if (isSelected) {
                    Icon(Icons.Default.CheckCircle, null, Modifier.size(22.dp),
                        tint = MaterialTheme.colorScheme.primary)
                } else {
                    Box(Modifier.size(22.dp).border(2.dp, Color.White, CircleShape))
                }
            }
        }

        // Selected overlay tint
        if (isSelected) {
            Box(Modifier.fillMaxSize().background(
                MaterialTheme.colorScheme.primary.copy(alpha = 0.25f)))
        }

        // Video play icon — bottom right
        if (photo.isVideo()) {
            Icon(Icons.Default.PlayArrow, null,
                modifier = Modifier.align(Alignment.BottomEnd).padding(4.dp).size(18.dp),
                tint = Color.White)
        }

        // Multi-part badge — bottom left
        if (photo.totalChunks > 1) {
            Box(Modifier.align(Alignment.BottomStart).padding(4.dp)
                .background(Color.Black.copy(alpha = 0.5f), RoundedCornerShape(3.dp))
                .padding(horizontal = 3.dp, vertical = 1.dp)) {
                Text("${photo.totalChunks}pt", fontSize = 7.sp, color = Color.White,
                    style = MaterialTheme.typography.labelSmall)
            }
        }
    }
}

// ─── Empty state ──────────────────────────────────────────────────────────────

@Composable
private fun EmptyGallery() {
    Column(
        Modifier.fillMaxSize().padding(48.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Surface(shape = CircleShape, color = MaterialTheme.colorScheme.surfaceVariant,
            modifier = Modifier.size(80.dp)) {
            Box(contentAlignment = Alignment.Center) {
                Icon(Icons.Default.CloudOff, null, Modifier.size(36.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Spacer(Modifier.height(24.dp))
        Text("No photos backed up yet",
            style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(8.dp))
        Text("Go to Backup and tap Back up now\nto start your first backup.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center)
    }
}

// ─── Date grouping ────────────────────────────────────────────────────────────

private fun groupPhotosByDate(photos: List<UploadedPhoto>): List<Any> {
    val cal = Calendar.getInstance()
    val todayStart = cal.apply {
        set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
    }.timeInMillis
    val yesterdayStart = todayStart - 86_400_000L
    val thisYear = Calendar.getInstance().get(Calendar.YEAR)

    val result = mutableListOf<Any>()
    var lastLabel = ""
    photos.forEach { photo ->
        val label = when {
            photo.uploadedAt >= todayStart    -> "Today"
            photo.uploadedAt >= yesterdayStart -> "Yesterday"
            else -> {
                val c = Calendar.getInstance().apply { timeInMillis = photo.uploadedAt }
                val d = c.get(Calendar.DAY_OF_MONTH)
                val m = c.getDisplayName(Calendar.MONTH, Calendar.SHORT, Locale.getDefault()) ?: ""
                val y = c.get(Calendar.YEAR)
                if (y == thisYear) "$d $m" else "$d $m $y"
            }
        }
        if (label != lastLabel) { result.add(label); lastLabel = label }
        result.add(photo)
    }
    return result
}

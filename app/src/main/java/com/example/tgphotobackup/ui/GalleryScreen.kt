package com.example.tgphotobackup.ui

import android.content.Intent
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Sort
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.SubcomposeAsyncImage
import com.example.tgphotobackup.data.UploadedPhoto
import com.example.tgphotobackup.data.contentUri
import com.example.tgphotobackup.data.isVideo
import java.text.SimpleDateFormat
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

    var selectedTab    by remember { mutableIntStateOf(0) }
    var searchQuery    by remember { mutableStateOf("") }
    var selectedAlbum  by remember { mutableStateOf<String?>(null) }
    var selectedHashes by remember { mutableStateOf(setOf<String>()) }
    var sortMode       by remember { mutableStateOf(SortMode.DATE_DESC) }
    var typeFilter     by remember { mutableStateOf(TypeFilter.ALL) }
    var showSortMenu   by remember { mutableStateOf(false) }
    val context        = LocalContext.current
    val inSelectionMode = selectedHashes.isNotEmpty()

    // Apply sort + type filter to the full list
    val displayPhotos = remember(allPhotos, sortMode, typeFilter) {
        allPhotos
            .filter { photo ->
                when (typeFilter) {
                    TypeFilter.PHOTOS -> !photo.isVideo()
                    TypeFilter.VIDEOS ->  photo.isVideo()
                    TypeFilter.ALL    ->  true
                }
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
            // ── Search + sort button ───────────────────────────
            Row(
                Modifier.fillMaxWidth().padding(start = 16.dp, end = 8.dp, top = 8.dp, bottom = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it; selectedAlbum = null },
                    placeholder = { Text("Search photos…") },
                    leadingIcon = { Icon(Icons.Default.Search, null, Modifier.size(18.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant) },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.weight(1f)
                )
                Box {
                    IconButton(onClick = { showSortMenu = true }) {
                        Icon(Icons.Default.Sort, "Sort",
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

            // ── Type filter chips ──────────────────────────────
            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp).padding(bottom = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TypeFilter.entries.forEach { filter ->
                    FilterChip(
                        selected = typeFilter == filter,
                        onClick  = { typeFilter = filter },
                        label    = { Text(filter.label, style = MaterialTheme.typography.labelMedium) }
                    )
                }
            }

            // ── Tabs ───────────────────────────────────────────
            if (searchQuery.isBlank()) {
                TabRow(selectedTabIndex = selectedTab) {
                    Tab(selected = selectedTab == 0,
                        onClick = { selectedTab = 0; selectedAlbum = null },
                        text = { Text("All") })
                    Tab(selected = selectedTab == 1,
                        onClick = { selectedTab = 1; searchQuery = "" },
                        text = { Text("Albums") })
                }
            }
        }

        // ── Content ────────────────────────────────────────────
        when {
            searchQuery.isNotBlank() -> {
                val filtered = displayPhotos.filter {
                    it.displayName.contains(searchQuery, ignoreCase = true)
                }
                SelectableGrid(filtered, displayPhotos, selectedHashes, onPhotoClick) {
                    selectedHashes = it
                }
            }
            selectedTab == 1 && selectedAlbum == null -> {
                AlbumList(displayPhotos) { selectedAlbum = it }
            }
            selectedTab == 1 && selectedAlbum != null -> {
                val albumPhotos = displayPhotos.filter {
                    it.bucketName.ifBlank { "Other" } == selectedAlbum
                }
                Column {
                    Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = { selectedAlbum = null }) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back to albums")
                        }
                        Text(selectedAlbum ?: "",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold)
                        Spacer(Modifier.weight(1f))
                        Text("${albumPhotos.size} files",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(end = 16.dp))
                    }
                    SelectableGrid(albumPhotos, displayPhotos, selectedHashes, onPhotoClick) {
                        selectedHashes = it
                    }
                }
            }
            else -> {
                DateGroupedGrid(displayPhotos, selectedHashes, onPhotoClick) { selectedHashes = it }
            }
        }
    }
}

// ─── Selectable flat grid ─────────────────────────────────────────────────────

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun SelectableGrid(
    photos: List<UploadedPhoto>,
    allPhotos: List<UploadedPhoto>,
    selectedHashes: Set<String>,
    onPhotoClick: (index: Int) -> Unit,
    onSelectionChange: (Set<String>) -> Unit
) {
    val inSelectionMode = selectedHashes.isNotEmpty()
    if (photos.isEmpty()) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("No photos found", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        return
    }
    LazyVerticalGrid(
        columns = GridCells.Fixed(3),
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
        verticalArrangement   = Arrangement.spacedBy(2.dp)
    ) {
        items(photos, key = { it.contentHash }) { photo ->
            val isSelected = photo.contentHash in selectedHashes
            Thumbnail(
                photo = photo,
                isSelected = isSelected,
                inSelectionMode = inSelectionMode,
                onClick = {
                    if (inSelectionMode) {
                        onSelectionChange(
                            if (isSelected) selectedHashes - photo.contentHash
                            else selectedHashes + photo.contentHash
                        )
                    } else {
                        onPhotoClick(allPhotos.indexOf(photo).coerceAtLeast(0))
                    }
                },
                onLongClick = { onSelectionChange(selectedHashes + photo.contentHash) }
            )
        }
    }
}

// ─── Date-grouped grid with selection ────────────────────────────────────────

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun DateGroupedGrid(
    photos: List<UploadedPhoto>,
    selectedHashes: Set<String>,
    onClick: (index: Int) -> Unit,
    onSelectionChange: (Set<String>) -> Unit
) {
    val inSelectionMode = selectedHashes.isNotEmpty()
    val monthFmt = remember { SimpleDateFormat("MMMM yyyy", Locale.getDefault()) }
    val grouped  = remember(photos) {
        photos.groupBy { monthFmt.format(Date(it.uploadedAt)) }
    }

    LazyVerticalGrid(
        columns = GridCells.Fixed(3),
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
        verticalArrangement   = Arrangement.spacedBy(2.dp)
    ) {
        grouped.forEach { (month, group) ->
            item(span = { GridItemSpan(3) }) {
                Text(month, style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(start = 16.dp, top = 16.dp, bottom = 4.dp))
            }
            items(group, key = { it.contentHash }) { photo ->
                val isSelected = photo.contentHash in selectedHashes
                Thumbnail(
                    photo = photo,
                    isSelected = isSelected,
                    inSelectionMode = inSelectionMode,
                    onClick = {
                        if (inSelectionMode) {
                            onSelectionChange(
                                if (isSelected) selectedHashes - photo.contentHash
                                else selectedHashes + photo.contentHash
                            )
                        } else {
                            onClick(photos.indexOf(photo).coerceAtLeast(0))
                        }
                    },
                    onLongClick = { onSelectionChange(selectedHashes + photo.contentHash) }
                )
            }
        }
    }
}

// ─── Album list ───────────────────────────────────────────────────────────────

@Composable
private fun AlbumList(photos: List<UploadedPhoto>, onAlbumSelected: (String) -> Unit) {
    val albums = remember(photos) {
        photos.groupBy { it.bucketName.ifBlank { "Other" } }
              .entries.sortedBy { it.key }
    }
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items(albums, key = { it.key }) { (name, group) ->
            Row(
                Modifier.fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .clickable { onAlbumSelected(name) }
                    .padding(8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Box(
                    Modifier.size(64.dp).clip(RoundedCornerShape(8.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    SubcomposeAsyncImage(
                        model = group.first().contentUri(),
                        contentDescription = null,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop,
                        error = {
                            Icon(Icons.Default.Folder, null,
                                Modifier.align(Alignment.Center).size(32.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    )
                }
                Column(Modifier.weight(1f)) {
                    Text(name, style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Medium, maxLines = 1,
                        overflow = TextOverflow.Ellipsis)
                    Text("${group.size} items", style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Icon(Icons.AutoMirrored.Filled.ArrowBack, null,
                    modifier = Modifier.size(20.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

// ─── Thumbnail with selection overlay ────────────────────────────────────────

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun Thumbnail(
    photo: UploadedPhoto,
    isSelected: Boolean,
    inSelectionMode: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit
) {
    Box(
        Modifier.aspectRatio(1f)
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
    ) {
        SubcomposeAsyncImage(
            model = photo.contentUri(),
            contentDescription = photo.displayName,
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop,
            error = {
                Box(
                    Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surfaceVariant),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.padding(4.dp)
                    ) {
                        Icon(Icons.Default.CloudOff, null, Modifier.size(20.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f))
                        Text(
                            photo.displayName,
                            fontSize = 7.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            textAlign = TextAlign.Center,
                            style = MaterialTheme.typography.labelSmall,
                            modifier = Modifier.padding(top = 2.dp)
                        )
                    }
                }
            }
        )

        // Selection overlay
        if (isSelected) {
            Box(Modifier.fillMaxSize()
                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.35f)))
            Icon(Icons.Default.CheckCircle, null,
                modifier = Modifier.align(Alignment.TopEnd).padding(4.dp).size(20.dp),
                tint = MaterialTheme.colorScheme.primary)
        } else if (inSelectionMode) {
            Icon(Icons.Default.RadioButtonUnchecked, null,
                modifier = Modifier.align(Alignment.TopEnd).padding(4.dp).size(20.dp),
                tint = Color.White.copy(alpha = 0.8f))
        }

        // Video badge
        if (photo.isVideo()) {
            Box(
                Modifier.align(Alignment.BottomStart).padding(4.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(Color.Black.copy(alpha = 0.55f))
                    .padding(horizontal = 4.dp, vertical = 2.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                    Icon(Icons.Default.PlayArrow, null, Modifier.size(10.dp), tint = Color.White)
                    Text("VID", fontSize = 8.sp, color = Color.White,
                        style = MaterialTheme.typography.labelSmall)
                }
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
        Text("Go to Home and tap Back up now\nto start your first backup.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center)
    }
}

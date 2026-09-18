package com.c99.komikureader.ui.detail

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.c99.komikureader.data.model.MangaDetail
import com.c99.komikureader.data.repository.MangaRepository
import com.c99.komikureader.ui.components.LoadingView
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MangaDetailScreen(
    slug: String,
    repository: MangaRepository,
    onChapterClick: (String) -> Unit,
    onBack: () -> Unit
) {
    var detail by remember { mutableStateOf<MangaDetail?>(null) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var isBookmarked by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    fun loadDetail() {
        scope.launch {
            loading = true
            error = null
            try {
                detail = repository.getMangaDetail(slug)
                if (detail == null) {
                    error = "Gagal memuat data manga. Periksa koneksi."
                    loading = false
                    return@launch
                }
                isBookmarked = repository.isBookmarked(slug)
            } catch (e: Exception) {
                error = e.message ?: "Gagal memuat detail"
            }
            loading = false
        }
    }

    fun toggleBookmark() {
        val d = detail ?: return
        if (isBookmarked) {
            repository.removeBookmark(slug)
            isBookmarked = false
        } else {
            repository.addBookmark(
                slug = slug,
                title = d.title,
                thumbnailUrl = d.thumbnailUrl,
                type = d.type,
                status = d.status
            )
            isBookmarked = true
        }
    }

    LaunchedEffect(slug) { loadDetail() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(detail?.title ?: "Loading...", maxLines = 1) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Kembali")
                    }
                },
                actions = {
                    IconButton(onClick = { toggleBookmark() }) {
                        Icon(
                            if (isBookmarked) Icons.Default.Bookmark else Icons.Default.BookmarkBorder,
                            contentDescription = "Bookmark"
                        )
                    }
                }
            )
        }
    ) { padding ->
        if (loading) {
            LoadingView(modifier = Modifier.padding(padding))
            return@Scaffold
        }

        if (error != null) {
            Column(modifier = Modifier.padding(padding).padding(32.dp)) {
                Text(error!!, color = MaterialTheme.colorScheme.error)
                Spacer(modifier = Modifier.height(16.dp))
                Button(onClick = { loadDetail() }) { Text("Coba Lagi") }
            }
            return@Scaffold
        }

        val d = detail ?: return@Scaffold

        LazyColumn(
            modifier = Modifier.padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Header with cover
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    AsyncImage(
                        model = d.thumbnailUrl,
                        contentDescription = d.title,
                        modifier = Modifier
                            .width(120.dp)
                            .height(170.dp)
                            .clip(RoundedCornerShape(12.dp)),
                        contentScale = ContentScale.Crop
                    )
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = d.title,
                            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        InfoRow("Tipe", d.type)
                        InfoRow("Genre", d.genre)
                        InfoRow("Author", d.author)
                        InfoRow("Status", d.status)
                        InfoRow("Rating", d.rating)
                    }
                }
            }

            // Synopsis
            if (d.synopsis.isNotEmpty()) {
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text(
                                "Sinopsis",
                                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold)
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                d.synopsis,
                                style = MaterialTheme.typography.bodySmall,
                                maxLines = 10
                            )
                        }
                    }
                }
            }

            // Stats
            if (d.totalViews.isNotEmpty()) {
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(24.dp)
                    ) {
                        Column {
                            Text("Total Views", style = MaterialTheme.typography.labelSmall)
                            Text(d.totalViews, style = MaterialTheme.typography.titleSmall)
                        }
                        Column {
                            Text("Minggu Ini", style = MaterialTheme.typography.labelSmall)
                            Text(d.weeklyViews, style = MaterialTheme.typography.titleSmall)
                        }
                    }
                }
            }

            // Chapter list header
            item {
                Text(
                    "${d.chapters.size} Chapter",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                )
            }

            // Chapter list
            items(d.chapters) { chapter ->
                ChapterItemRow(
                    chapter = chapter,
                    onClick = {
                        repository.addHistory(
                            slug = slug,
                            chapterUrl = chapter.url,
                            chapterTitle = chapter.title,
                            mangaTitle = d.title
                        )
                        onChapterClick(chapter.url)
                    }
                )
            }
        }
    }
}

@Composable
fun InfoRow(label: String, value: String) {
    if (value.isNotEmpty()) {
        Row(modifier = Modifier.padding(vertical = 1.dp)) {
            Text(
                "$label: ",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
            )
            Text(
                value,
                style = MaterialTheme.typography.labelSmall
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChapterItemRow(
    chapter: com.c99.komikureader.data.model.ChapterItem,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        shape = RoundedCornerShape(8.dp)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                chapter.title,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.weight(1f)
            )
            if (chapter.date.isNotEmpty()) {
                Text(
                    chapter.date,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                )
            }
        }
    }
}
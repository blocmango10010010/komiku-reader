package com.c99.komikureader.ui.detail

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateContentSize
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import coil.compose.AsyncImage
import com.c99.komikureader.data.local.HistoryEntry
import com.c99.komikureader.data.model.ChapterItem
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
    var readUrls by remember { mutableStateOf<Set<String>>(emptySet()) }
    var lastRead by remember { mutableStateOf<HistoryEntry?>(null) }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    // Asked the first time something is bookmarked, since that is when update notifications start to matter.
    val notificationPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }

    fun loadDetail(force: Boolean = false) {
        scope.launch {
            if (detail == null) loading = true
            error = null
            try {
                val d = repository.getMangaDetail(slug, force)
                if (d == null) {
                    error = "Gagal memuat data manga. Periksa koneksi."
                } else {
                    detail = d
                    isBookmarked = repository.isBookmarked(slug)
                    readUrls = repository.getReadChapterUrls(slug)
                    lastRead = repository.getLastRead(slug)
                    // Opening the title counts as seeing its new chapters.
                    val latest = d.chapters.firstOrNull()?.url
                    if (isBookmarked && latest != null) {
                        repository.updateBookmarkLatest(slug, latest, hasUpdate = false)
                    }
                }
            } catch (e: Exception) {
                error = e.message ?: "Gagal memuat detail"
            }
            loading = false
        }
    }

    fun toggleBookmark() {
        val d = detail ?: return
        scope.launch {
            if (isBookmarked) {
                repository.removeBookmark(slug)
                isBookmarked = false
            } else {
                repository.addBookmark(
                    slug = slug,
                    title = d.title,
                    thumbnailUrl = d.thumbnailUrl,
                    type = d.type,
                    status = d.status,
                    lastChapterUrl = d.chapters.firstOrNull()?.url ?: ""
                )
                isBookmarked = true
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                    ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS)
                    != PackageManager.PERMISSION_GRANTED
                ) {
                    notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
                }
            }
        }
    }

    LaunchedEffect(slug) { loadDetail() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(detail?.title ?: "Loading...", maxLines = 1, overflow = TextOverflow.Ellipsis) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Kembali")
                    }
                },
                actions = {
                    IconButton(onClick = { loadDetail(force = true) }) {
                        Icon(Icons.Default.Refresh, contentDescription = "Muat ulang")
                    }
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

        val d = detail
        if (d == null) {
            Column(modifier = Modifier.padding(padding).padding(32.dp)) {
                Text(error ?: "Gagal memuat detail", color = MaterialTheme.colorScheme.error)
                Spacer(modifier = Modifier.height(16.dp))
                Button(onClick = { loadDetail() }) { Text("Coba Lagi") }
            }
            return@Scaffold
        }

        LazyColumn(
            modifier = Modifier.padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
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

            // Continue / start reading
            val resume = lastRead?.let { it.chapterUrl to "Lanjutkan ${it.chapterTitle.ifEmpty { "membaca" }}" }
                ?: d.chapters.lastOrNull()?.let { it.url to "Mulai Baca ${it.title}" }
            if (resume != null) {
                item {
                    Button(
                        onClick = { onChapterClick(resume.first) },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(20.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(resume.second, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            }

            // Synopsis
            if (d.synopsis.isNotEmpty()) {
                item { SynopsisCard(d.synopsis) }
            }

            // Chapter list header
            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "${d.chapters.size} Chapter",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        modifier = Modifier.weight(1f)
                    )
                    val readCount = d.chapters.count { it.url in readUrls }
                    if (readCount > 0) {
                        Text(
                            "$readCount dibaca",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }

            // Chapter list
            items(d.chapters, key = { it.url }) { chapter ->
                ChapterItemRow(
                    chapter = chapter,
                    isRead = chapter.url in readUrls,
                    isLastRead = chapter.url == lastRead?.chapterUrl,
                    onClick = { onChapterClick(chapter.url) }
                )
            }
        }
    }
}

@Composable
private fun SynopsisCard(synopsis: String) {
    var expanded by remember { mutableStateOf(false) }
    Card(
        modifier = Modifier.fillMaxWidth().clickable { expanded = !expanded },
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp).animateContentSize()) {
            Text(
                "Sinopsis",
                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold)
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                synopsis,
                style = MaterialTheme.typography.bodySmall,
                maxLines = if (expanded) Int.MAX_VALUE else 4,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                if (expanded) "Sembunyikan" else "Selengkapnya",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(top = 4.dp)
            )
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

@Composable
fun ChapterItemRow(
    chapter: ChapterItem,
    isRead: Boolean,
    isLastRead: Boolean,
    onClick: () -> Unit
) {
    val contentAlpha = if (isRead && !isLastRead) 0.45f else 1f
    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        shape = RoundedCornerShape(8.dp),
        colors = if (isLastRead) {
            CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f))
        } else CardDefaults.cardColors()
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (isRead) {
                Icon(
                    if (isLastRead) Icons.Default.Bookmark else Icons.Default.Check,
                    contentDescription = if (isLastRead) "Terakhir dibaca" else "Sudah dibaca",
                    tint = MaterialTheme.colorScheme.primary.copy(alpha = contentAlpha),
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
            }
            Text(
                chapter.title,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = contentAlpha),
                modifier = Modifier.weight(1f)
            )
            if (chapter.date.isNotEmpty()) {
                Text(
                    chapter.date,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f * contentAlpha)
                )
            }
        }
    }
}

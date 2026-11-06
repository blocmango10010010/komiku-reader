package com.c99.komikureader.ui.home

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.c99.komikureader.data.local.HistoryEntry
import com.c99.komikureader.data.model.MangaItem
import com.c99.komikureader.data.repository.MangaRepository
import com.c99.komikureader.ui.components.LoadingView
import com.c99.komikureader.ui.components.MangaCard
import com.c99.komikureader.ui.components.SectionHeader
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    repository: MangaRepository,
    onMangaClick: (String) -> Unit,
    onSearchClick: () -> Unit,
    onBrowseClick: () -> Unit,
    onLibraryClick: () -> Unit,
    onContinueClick: (slug: String, chapterUrl: String) -> Unit
) {
    val scope = rememberCoroutineScope()

    var ranking by remember { mutableStateOf<List<MangaItem>>(emptyList()) }
    var latest by remember { mutableStateOf<List<MangaItem>>(emptyList()) }
    var recent by remember { mutableStateOf<List<HistoryEntry>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }

    fun loadData() {
        scope.launch {
            loading = true
            error = null
            try {
                recent = repository.getAllHistory(limit = 10)
                ranking = repository.getHomeRanking()
                latest = repository.getHomeLatest()
            } catch (e: Exception) {
                error = e.message ?: "Gagal memuat data. Periksa koneksi internet."
            }
            loading = false
        }
    }

    LaunchedEffect(Unit) { loadData() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Komiku Reader") },
                actions = {
                    IconButton(onClick = onSearchClick) {
                        Icon(Icons.Default.Search, contentDescription = "Cari")
                    }
                    IconButton(onClick = onLibraryClick) {
                        Icon(Icons.Default.Bookmarks, contentDescription = "Koleksi")
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
            Column(
                modifier = Modifier.padding(padding).fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Icon(Icons.Default.WifiOff, contentDescription = null, modifier = Modifier.size(64.dp),
                    tint = MaterialTheme.colorScheme.error.copy(alpha = 0.5f))
                Spacer(modifier = Modifier.height(16.dp))
                Text(error!!, color = MaterialTheme.colorScheme.error)
                Spacer(modifier = Modifier.height(16.dp))
                Button(onClick = { loadData() }) { Text("Coba Lagi") }
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedButton(onClick = onBrowseClick) { Text("Buka Semua Komik") }
            }
            return@Scaffold
        }

        val isEmpty = ranking.isEmpty() && latest.isEmpty()

        LazyColumn(
            modifier = Modifier.padding(padding),
            contentPadding = PaddingValues(vertical = 8.dp)
        ) {
            // Navigation chips
            item {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    FilterChip(
                        selected = true,
                        onClick = onBrowseClick,
                        label = { Text("📚 Semua Komik") },
                        leadingIcon = { Icon(Icons.Default.GridView, contentDescription = null, modifier = Modifier.size(18.dp)) }
                    )
                    FilterChip(
                        selected = false,
                        onClick = onLibraryClick,
                        label = { Text("⭐ Koleksi") },
                        leadingIcon = { Icon(Icons.Default.Bookmarks, contentDescription = null, modifier = Modifier.size(18.dp)) }
                    )
                }
            }

            // Empty state
            if (isEmpty) {
                item {
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(32.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(Icons.Default.AutoStories, contentDescription = null,
                            modifier = Modifier.size(64.dp),
                            tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f))
                        Spacer(modifier = Modifier.height(16.dp))
                        Text("Jelajahi ribuan komik", style = MaterialTheme.typography.titleMedium)
                        Spacer(modifier = Modifier.height(8.dp))
                        Text("Manga, Manhwa, dan Manhua Bahasa Indonesia",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f))
                        Spacer(modifier = Modifier.height(24.dp))
                        Button(onClick = onBrowseClick) { Text("📚 Jelajahi Sekarang") }
                    }
                }
            }

            // Continue reading
            if (recent.isNotEmpty()) {
                item { SectionHeader(title = "📖 Lanjutkan Membaca") }
                item {
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        items(recent, key = { it.slug }) { entry ->
                            ContinueCard(entry, onClick = { onContinueClick(entry.slug, entry.chapterUrl) })
                        }
                    }
                }
                item { Spacer(modifier = Modifier.height(8.dp)) }
            }

            // Ranking section
            if (ranking.isNotEmpty()) {
                item { SectionHeader(title = "🏆 Populer Hari Ini") }
                item {
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        items(ranking.take(15)) { manga ->
                            MangaCard(item = manga, onClick = { onMangaClick(manga.slug) })
                        }
                    }
                }
            }

            // Latest section
            if (latest.isNotEmpty()) {
                item { Spacer(modifier = Modifier.height(16.dp)) }
                item { SectionHeader(title = "🆕 Update Terbaru") }
                item {
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        items(latest.take(30)) { manga ->
                            MangaCard(item = manga, onClick = { onMangaClick(manga.slug) })
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ContinueCard(entry: HistoryEntry, onClick: () -> Unit) {
    Card(
        onClick = onClick,
        modifier = Modifier.width(200.dp),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(
                entry.mangaTitle.ifEmpty { entry.slug },
                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                maxLines = 1, overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(4.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Default.PlayArrow, contentDescription = null,
                    modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    entry.chapterTitle.ifEmpty { "Lanjutkan" },
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                    maxLines = 1, overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

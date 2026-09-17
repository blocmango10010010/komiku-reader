package com.c99.komikureader.ui.home

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
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
    onLibraryClick: () -> Unit
) {
    val scope = rememberCoroutineScope()

    var ranking by remember { mutableStateOf<List<MangaItem>>(emptyList()) }
    var latest by remember { mutableStateOf<List<MangaItem>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }

    fun loadData() {
        scope.launch {
            loading = true
            error = null
            try {
                ranking = repository.getHomeRanking()
                latest = repository.getHomeLatest()
            } catch (e: Exception) {
                error = e.message ?: "Gagal memuat data"
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
            Column(modifier = Modifier.padding(padding).padding(32.dp)) {
                Text(error!!, color = MaterialTheme.colorScheme.error)
                Spacer(modifier = Modifier.height(16.dp))
                Button(onClick = { loadData() }) { Text("Coba Lagi") }
            }
            return@Scaffold
        }

        LazyColumn(
            modifier = Modifier.padding(padding),
            contentPadding = PaddingValues(vertical = 8.dp)
        ) {
            // Browse button row
            item {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    FilterChip(
                        selected = false,
                        onClick = onBrowseClick,
                        label = { Text("Semua Komik") },
                        leadingIcon = { Icon(Icons.Default.GridView, contentDescription = null, modifier = Modifier.size(18.dp)) }
                    )
                    FilterChip(
                        selected = false,
                        onClick = onLibraryClick,
                        label = { Text("Koleksi Saya") },
                        leadingIcon = { Icon(Icons.Default.Bookmarks, contentDescription = null, modifier = Modifier.size(18.dp)) }
                    )
                }
            }

            // Ranking section
            if (ranking.isNotEmpty()) {
                item { SectionHeader(title = "🏆 Peringkat Populer") }
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
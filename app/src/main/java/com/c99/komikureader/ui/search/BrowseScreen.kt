package com.c99.komikureader.ui.search

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.c99.komikureader.data.model.MangaItem
import com.c99.komikureader.data.repository.MangaRepository
import com.c99.komikureader.ui.components.LoadingView
import com.c99.komikureader.ui.components.MangaCard
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BrowseScreen(
    repository: MangaRepository,
    onMangaClick: (String) -> Unit,
    onBack: () -> Unit
) {
    var mangaList by remember { mutableStateOf<List<MangaItem>>(emptyList()) }
    var page by remember { mutableStateOf(1) }
    var loading by remember { mutableStateOf(true) }
    var loadingMore by remember { mutableStateOf(false) }
    var hasMore by remember { mutableStateOf(true) }
    var selectedType by remember { mutableStateOf("") } // "" = semua
    val scope = rememberCoroutineScope()

    fun loadPage(p: Int, filterType: String = "") {
        scope.launch {
            try {
                val items = repository.getMangaList(p, type = filterType)
                if (p == 1) {
                    mangaList = items
                } else {
                    mangaList = mangaList + items
                }
                hasMore = items.size >= 50
            } catch (e: Exception) {
                // Keep existing data
            }
            loading = false
            loadingMore = false
        }
    }

    fun loadMore() {
        if (!loadingMore && hasMore) {
            loadingMore = true
            page++
            loadPage(page, selectedType)
        }
    }

    fun switchType(newType: String) {
        if (newType == selectedType) return
        selectedType = newType
        page = 1
        loading = true
        mangaList = emptyList()
        loadPage(1, newType)
    }

    LaunchedEffect(Unit) { loadPage(1) }

    Scaffold(
        topBar = {
            Column {
                TopAppBar(
                    title = { Text("Semua Komik") },
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(Icons.Default.ArrowBack, contentDescription = "Kembali")
                        }
                    }
                )
                // Type filter chips
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FilterChip(
                        selected = selectedType == "",
                        onClick = { switchType("") },
                        label = { Text("Semua") }
                    )
                    FilterChip(
                        selected = selectedType == "manga",
                        onClick = { switchType("manga") },
                        label = { Text("🇯🇵 Manga") }
                    )
                    FilterChip(
                        selected = selectedType == "manhwa",
                        onClick = { switchType("manhwa") },
                        label = { Text("🇰🇷 Manhwa") }
                    )
                    FilterChip(
                        selected = selectedType == "manhua",
                        onClick = { switchType("manhua") },
                        label = { Text("🇨🇳 Manhua") }
                    )
                }
            }
        }
    ) { padding ->
        if (loading) {
            LoadingView(modifier = Modifier.padding(padding))
            return@Scaffold
        }

        if (mangaList.isEmpty()) {
            Box(
                modifier = Modifier.padding(padding).fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        "Tidak ada komik ditemukan",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedButton(onClick = { switchType("") }) {
                        Text("Tampilkan Semua")
                    }
                }
            }
            return@Scaffold
        }

        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            modifier = Modifier.padding(padding),
            contentPadding = PaddingValues(12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(mangaList) { manga ->
                MangaCard(
                    item = manga,
                    onClick = { onMangaClick(manga.slug) },
                    modifier = Modifier.fillMaxWidth()
                )
            }

            if (hasMore) {
                item {
                    Box(
                        modifier = Modifier.fillMaxWidth().padding(16.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        if (loadingMore) {
                            CircularProgressIndicator(modifier = Modifier.size(24.dp))
                        } else {
                            OutlinedButton(onClick = { loadMore() }) {
                                Text("Muat Lebih Banyak")
                            }
                        }
                    }
                }
            }
        }
    }
}
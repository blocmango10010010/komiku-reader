package com.c99.komikureader.ui.reader

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.c99.komikureader.data.model.ChapterPage
import com.c99.komikureader.data.repository.MangaRepository
import com.c99.komikureader.ui.components.LoadingView
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChapterReaderScreen(
    slug: String,
    chapterUrl: String,
    repository: MangaRepository,
    onBack: () -> Unit,
    onChapterChange: (String) -> Unit
) {
    var chapter by remember { mutableStateOf<ChapterPage?>(null) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    val screenWidth = LocalConfiguration.current.screenWidthDp.dp

    fun loadChapter() {
        scope.launch {
            loading = true
            error = null
            try {
                chapter = repository.getChapterImages(chapterUrl)
                if (chapter == null) {
                    error = "Gagal memuat chapter. Periksa koneksi."
                    loading = false
                    return@launch
                }
                // Save to history
                repository.addHistory(
                    slug = slug,
                    chapterUrl = chapterUrl,
                    chapterTitle = chapter?.chapterTitle ?: "",
                    mangaTitle = chapter?.mangaTitle ?: ""
                )
            } catch (e: Exception) {
                error = e.message ?: "Gagal memuat chapter"
            }
            loading = false
        }
    }

    LaunchedEffect(chapterUrl) { loadChapter() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            chapter?.mangaTitle ?: "Loading...",
                            style = MaterialTheme.typography.labelSmall
                        )
                        Text(
                            chapter?.chapterTitle ?: "",
                            style = MaterialTheme.typography.titleSmall
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Kembali")
                    }
                },
                actions = {
                    if (chapter?.prevChapterUrl != null) {
                        IconButton(onClick = {
                            chapter?.prevChapterUrl?.let { onChapterChange(it) }
                        }) {
                            Icon(Icons.Default.SkipPrevious, contentDescription = "Chapter Sebelumnya")
                        }
                    }
                    if (chapter?.nextChapterUrl != null) {
                        IconButton(onClick = {
                            chapter?.nextChapterUrl?.let { onChapterChange(it) }
                        }) {
                            Icon(Icons.Default.SkipNext, contentDescription = "Chapter Selanjutnya")
                        }
                    }
                }
            )
        },
        bottomBar = {
            // Navigation bar
            Surface(
                tonalElevation = 3.dp,
                shadowElevation = 8.dp
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(8.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (chapter?.prevChapterUrl != null) {
                        FilledTonalButton(
                            onClick = { chapter?.prevChapterUrl?.let { onChapterChange(it) } },
                            modifier = Modifier.padding(horizontal = 4.dp)
                        ) {
                            Icon(Icons.Default.SkipPrevious, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Prev")
                        }
                    }
                    if (chapter?.nextChapterUrl != null) {
                        FilledTonalButton(
                            onClick = { chapter?.nextChapterUrl?.let { onChapterChange(it) } },
                            modifier = Modifier.padding(horizontal = 4.dp)
                        ) {
                            Text("Next")
                            Spacer(modifier = Modifier.width(4.dp))
                            Icon(Icons.Default.SkipNext, contentDescription = null, modifier = Modifier.size(18.dp))
                        }
                    }
                }
            }
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
                Button(onClick = { loadChapter() }) { Text("Coba Lagi") }
            }
            return@Scaffold
        }

        val images = chapter?.imageUrls ?: emptyList()

        if (images.isEmpty()) {
            Box(
                modifier = Modifier.padding(padding).fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Text("Tidak ada gambar", color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f))
            }
            return@Scaffold
        }

        LazyColumn(
            modifier = Modifier.padding(padding).fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(0.dp)
        ) {
            itemsIndexed(images) { index, url ->
                AsyncImage(
                    model = url,
                    contentDescription = "Halaman ${index + 1}",
                    modifier = Modifier
                        .fillMaxWidth()
                        .then(
                            // Use width as key sizing dimension
                            Modifier.wrapContentHeight()
                        ),
                    contentScale = ContentScale.FillWidth
                )
            }
        }
    }
}
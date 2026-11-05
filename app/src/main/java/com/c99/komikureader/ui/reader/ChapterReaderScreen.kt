package com.c99.komikureader.ui.reader

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.imageLoader
import coil.request.ImageRequest
import com.c99.komikureader.data.model.ChapterPage
import com.c99.komikureader.data.repository.MangaRepository
import com.c99.komikureader.ui.components.LoadingView
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
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
    var showBars by remember { mutableStateOf(true) }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val screenWidth = LocalConfiguration.current.screenWidthDp.dp
    val listState = rememberLazyListState()

    fun loadChapter() {
        scope.launch {
            loading = true
            error = null
            try {
                val page = repository.getChapterImages(chapterUrl)
                if (page == null) {
                    error = "Gagal memuat chapter. Periksa koneksi."
                } else {
                    repository.addHistory(slug, chapterUrl, page.chapterTitle, page.mangaTitle)
                    val savedPage = repository.getLastPage(slug, chapterUrl)
                    chapter = page
                    loading = false
                    if (savedPage in 1 until page.imageUrls.size) listState.scrollToItem(savedPage)
                    launch { prefetchNext(page, repository, context) }
                }
            } catch (e: Exception) {
                error = e.message ?: "Gagal memuat chapter"
            }
            loading = false
        }
    }

    LaunchedEffect(chapterUrl) { loadChapter() }

    // Persist the reading position, debounced so fast scrolling doesn't hammer the database.
    LaunchedEffect(chapterUrl) {
        snapshotFlow { listState.firstVisibleItemIndex }.collectLatest { index ->
            if (chapter == null) return@collectLatest
            delay(800)
            repository.updateLastPage(slug, chapterUrl, index)
        }
    }

    val images = chapter?.imageUrls ?: emptyList()
    val currentPage by remember { derivedStateOf { listState.firstVisibleItemIndex + 1 } }

    Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
        when {
            loading -> LoadingView()
            error != null -> Column(
                modifier = Modifier.fillMaxSize().padding(32.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(error!!, color = MaterialTheme.colorScheme.error)
                Spacer(modifier = Modifier.height(16.dp))
                Button(onClick = { loadChapter() }) { Text("Coba Lagi") }
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedButton(onClick = onBack) { Text("Kembali") }
            }
            images.isEmpty() -> Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("Tidak ada gambar", color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f))
            }
            else -> LazyColumn(
                state = listState,
                modifier = Modifier
                    .fillMaxSize()
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) { showBars = !showBars },
                contentPadding = WindowInsets.systemBars.asPaddingValues()
            ) {
                itemsIndexed(images) { index, url ->
                    // Unloaded pages have zero height, which would make the list compose and
                    // download every page at once; reserve space until each one loads.
                    var loaded by remember(url) { mutableStateOf(false) }
                    AsyncImage(
                        model = url,
                        contentDescription = "Halaman ${index + 1}",
                        modifier = Modifier
                            .fillMaxWidth()
                            .then(if (loaded) Modifier.wrapContentHeight() else Modifier.height(screenWidth * 1.4f)),
                        contentScale = ContentScale.FillWidth,
                        onSuccess = { loaded = true }
                    )
                }
                item {
                    EndOfChapter(
                        nextUrl = chapter?.nextChapterUrl,
                        onNext = onChapterChange,
                        onBack = onBack
                    )
                }
            }
        }

        // Overlaid bars so hiding them doesn't shift the pages.
        AnimatedVisibility(
            visible = showBars || loading || error != null,
            enter = fadeIn() + slideInVertically { -it },
            exit = fadeOut() + slideOutVertically { -it },
            modifier = Modifier.align(Alignment.TopCenter)
        ) {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            chapter?.mangaTitle ?: "Loading...",
                            style = MaterialTheme.typography.labelSmall,
                            maxLines = 1, overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            chapter?.chapterTitle ?: "",
                            style = MaterialTheme.typography.titleSmall,
                            maxLines = 1, overflow = TextOverflow.Ellipsis
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Kembali")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.92f)
                )
            )
        }

        AnimatedVisibility(
            visible = showBars && images.isNotEmpty(),
            enter = fadeIn() + slideInVertically { it },
            exit = fadeOut() + slideOutVertically { it },
            modifier = Modifier.align(Alignment.BottomCenter)
        ) {
            Surface(
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.92f),
                shadowElevation = 8.dp
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().navigationBarsPadding().padding(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = { chapter?.prevChapterUrl?.let(onChapterChange) },
                        enabled = chapter?.prevChapterUrl != null
                    ) {
                        Icon(Icons.Default.SkipPrevious, contentDescription = "Chapter Sebelumnya")
                    }
                    Text(
                        "${currentPage.coerceAtMost(images.size)} / ${images.size}",
                        style = MaterialTheme.typography.labelLarge,
                        modifier = Modifier.weight(1f),
                        textAlign = TextAlign.Center
                    )
                    IconButton(
                        onClick = { chapter?.nextChapterUrl?.let(onChapterChange) },
                        enabled = chapter?.nextChapterUrl != null
                    ) {
                        Icon(Icons.Default.SkipNext, contentDescription = "Chapter Selanjutnya")
                    }
                }
            }
        }
    }
}

@Composable
private fun EndOfChapter(nextUrl: String?, onNext: (String) -> Unit, onBack: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 48.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("Akhir chapter", style = MaterialTheme.typography.titleMedium, color = Color.White)
        Spacer(modifier = Modifier.height(16.dp))
        if (nextUrl != null) {
            Button(onClick = { onNext(nextUrl) }, modifier = Modifier.fillMaxWidth()) {
                Text("Chapter Selanjutnya")
                Spacer(modifier = Modifier.width(8.dp))
                Icon(Icons.Default.SkipNext, contentDescription = null, modifier = Modifier.size(18.dp))
            }
        } else {
            Text(
                "Ini chapter terbaru",
                style = MaterialTheme.typography.bodySmall,
                color = Color.White.copy(alpha = 0.6f)
            )
            Spacer(modifier = Modifier.height(8.dp))
            OutlinedButton(onClick = onBack, modifier = Modifier.fillMaxWidth()) { Text("Kembali ke Detail") }
        }
    }
}

/** Warms the chapter cache and the first few images so moving to the next chapter feels instant. */
private suspend fun prefetchNext(page: ChapterPage, repository: MangaRepository, context: android.content.Context) {
    val next = page.nextChapterUrl ?: return
    try {
        val nextPage = repository.getChapterImages(next) ?: return
        nextPage.imageUrls.take(3).forEach { url ->
            context.imageLoader.enqueue(ImageRequest.Builder(context).data(url).build())
        }
    } catch (e: Exception) {
        // Best effort only.
    }
}

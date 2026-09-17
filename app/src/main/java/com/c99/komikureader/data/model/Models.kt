package com.c99.komikureader.data.model

data class MangaItem(
    val title: String,
    val slug: String,          // from /manga/{slug}/
    val thumbnailUrl: String,
    val type: String = "",     // Manga, Manhwa, Manhua
    val status: String = "",   // Ongoing, Completed
    val genre: String = "",
    val rating: String = "",
    val latestChapter: String = ""
)

data class MangaDetail(
    val title: String,
    val slug: String,
    val alternativeTitle: String = "",
    val type: String = "",
    val genre: String = "",
    val author: String = "",
    val status: String = "",
    val rating: String = "",
    val synopsis: String = "",
    val thumbnailUrl: String = "",
    val totalViews: String = "",
    val weeklyViews: String = "",
    val chapters: List<ChapterItem> = emptyList()
)

data class ChapterItem(
    val title: String,          // e.g. "Chapter 1193"
    val url: String,            // e.g. "/one-piece-chapter-1193/"
    val date: String = ""       // e.g. "11/09/2026"
)

data class ChapterPage(
    val mangaTitle: String = "",
    val chapterTitle: String = "",
    val imageUrls: List<String> = emptyList(),
    val prevChapterUrl: String? = null,
    val nextChapterUrl: String? = null
)

data class SearchResult(
    val mangaSlug: String,
    val displayTitle: String,
    val firstChapterUrl: String?,
    val latestChapterUrl: String?,
    val updated: String = ""
)
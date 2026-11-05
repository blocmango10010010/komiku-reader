package com.c99.komikureader.data.repository

import com.c99.komikureader.data.local.BookmarkDatabase
import com.c99.komikureader.data.local.BookmarkEntry
import com.c99.komikureader.data.local.HistoryEntry
import com.c99.komikureader.data.model.*
import com.c99.komikureader.data.remote.KomikuApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class MangaRepository(
    private val api: KomikuApi,
    private val db: BookmarkDatabase
) {
    // Remote
    suspend fun getHomeRanking() = api.getHomeRanking()
    suspend fun getHomeLatest() = api.getHomeLatest()
    suspend fun getMangaList(page: Int = 1, type: String = "") = api.getMangaList(page, type)
    suspend fun search(query: String) = api.search(query)
    suspend fun getMangaDetail(slug: String) = api.getMangaDetail(slug)
    suspend fun getChapterImages(chapterUrl: String) = api.getChapterImages(chapterUrl)

    // Local (SQLite is blocking, so keep it off the main thread)
    private suspend fun <T> io(block: () -> T): T = withContext(Dispatchers.IO) { block() }

    // Bookmarks
    suspend fun addBookmark(slug: String, title: String, thumbnailUrl: String = "", type: String = "", status: String = "") =
        io { db.addBookmark(slug, title, thumbnailUrl, type, status) }
    suspend fun removeBookmark(slug: String) = io { db.removeBookmark(slug) }
    suspend fun isBookmarked(slug: String) = io { db.isBookmarked(slug) }
    suspend fun getAllBookmarks() = io { db.getAllBookmarks() }

    // History
    suspend fun addHistory(slug: String, chapterUrl: String, chapterTitle: String, mangaTitle: String) =
        io { db.addHistory(slug, chapterUrl, chapterTitle, mangaTitle) }
    suspend fun getLastRead(slug: String) = io { db.getLastRead(slug) }
    suspend fun getAllHistory() = io { db.getAllHistory() }
}
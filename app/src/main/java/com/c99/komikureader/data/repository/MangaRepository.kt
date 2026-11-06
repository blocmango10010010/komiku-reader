package com.c99.komikureader.data.repository

import com.c99.komikureader.data.local.BookmarkDatabase
import com.c99.komikureader.data.model.*
import com.c99.komikureader.data.remote.KomikuApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.ConcurrentHashMap

class MangaRepository(
    private val api: KomikuApi,
    private val db: BookmarkDatabase
) {
    private class Entry(val value: Any, val storedAt: Long)

    // Screens lose their state when navigated away from, so this keeps "back" and
    // rotation instant and spares the site repeated requests.
    private val cache = ConcurrentHashMap<String, Entry>()

    @Suppress("UNCHECKED_CAST")
    private suspend fun <T : Any> cached(key: String, ttlMs: Long, force: Boolean = false, fetch: suspend () -> T?): T? {
        val hit = cache[key]
        if (!force && hit != null && System.currentTimeMillis() - hit.storedAt < ttlMs) return hit.value as T
        val fresh = fetch()
        val isEmpty = fresh == null || (fresh is Collection<*> && fresh.isEmpty())
        if (!isEmpty) cache[key] = Entry(fresh!!, System.currentTimeMillis())
        return fresh
    }

    // Remote
    suspend fun getHomeRanking(force: Boolean = false) =
        cached("home:ranking", LIST_TTL, force) { api.getHomeRanking() } ?: emptyList()
    suspend fun getHomeLatest(force: Boolean = false) =
        cached("home:latest", LATEST_TTL, force) { api.getHomeLatest() } ?: emptyList()
    suspend fun getMangaList(page: Int = 1, type: String = "") =
        cached("list:$type:$page", LIST_TTL) { api.getMangaList(page, type) } ?: emptyList()
    suspend fun search(query: String) = api.search(query)
    suspend fun getMangaDetail(slug: String, force: Boolean = false) =
        cached("detail:$slug", DETAIL_TTL, force) { api.getMangaDetail(slug) }
    suspend fun getChapterImages(chapterUrl: String) =
        cached("chapter:$chapterUrl", CHAPTER_TTL) { api.getChapterImages(chapterUrl) }

    // Local (SQLite is blocking, so keep it off the main thread)
    private suspend fun <T> io(block: () -> T): T = withContext(Dispatchers.IO) { block() }

    // Bookmarks
    suspend fun addBookmark(
        slug: String, title: String, thumbnailUrl: String = "", type: String = "",
        status: String = "", lastChapterUrl: String = ""
    ) = io { db.addBookmark(slug, title, thumbnailUrl, type, status, lastChapterUrl) }
    suspend fun removeBookmark(slug: String) = io { db.removeBookmark(slug) }
    suspend fun isBookmarked(slug: String) = io { db.isBookmarked(slug) }
    suspend fun getAllBookmarks() = io { db.getAllBookmarks() }
    suspend fun updateBookmarkLatest(slug: String, latestChapterUrl: String, hasUpdate: Boolean) =
        io { db.updateBookmarkLatest(slug, latestChapterUrl, hasUpdate) }

    // History
    suspend fun addHistory(slug: String, chapterUrl: String, chapterTitle: String, mangaTitle: String) =
        io { db.addHistory(slug, chapterUrl, chapterTitle, mangaTitle) }
    suspend fun updateLastPage(slug: String, chapterUrl: String, page: Int) =
        io { db.updateLastPage(slug, chapterUrl, page) }
    suspend fun getLastPage(slug: String, chapterUrl: String) = io { db.getLastPage(slug, chapterUrl) }
    suspend fun getReadChapterUrls(slug: String) = io { db.getReadChapterUrls(slug) }
    suspend fun getLastRead(slug: String) = io { db.getLastRead(slug) }
    suspend fun getAllHistory(limit: Int = 50) = io { db.getAllHistory(limit) }

    private companion object {
        const val LIST_TTL = 10 * 60_000L
        const val LATEST_TTL = 2 * 60_000L
        const val DETAIL_TTL = 5 * 60_000L
        const val CHAPTER_TTL = 30 * 60_000L
    }
}

package com.c99.komikureader.data.repository

import com.c99.komikureader.data.local.BookmarkDatabase
import com.c99.komikureader.data.local.BookmarkEntry
import com.c99.komikureader.data.local.HistoryEntry
import com.c99.komikureader.data.model.*
import com.c99.komikureader.data.remote.KomikuApi

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

    // Bookmarks
    fun addBookmark(slug: String, title: String, thumbnailUrl: String = "", type: String = "", status: String = "") {
        db.addBookmark(slug, title, thumbnailUrl, type, status)
    }
    fun removeBookmark(slug: String) = db.removeBookmark(slug)
    fun isBookmarked(slug: String) = db.isBookmarked(slug)
    fun getAllBookmarks() = db.getAllBookmarks()

    // History
    fun addHistory(slug: String, chapterUrl: String, chapterTitle: String, mangaTitle: String) {
        db.addHistory(slug, chapterUrl, chapterTitle, mangaTitle)
    }
    fun getLastRead(slug: String) = db.getLastRead(slug)
    fun getAllHistory() = db.getAllHistory()
}
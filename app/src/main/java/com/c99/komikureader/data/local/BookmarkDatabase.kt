package com.c99.komikureader.data.local

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

class BookmarkDatabase(context: Context) : SQLiteOpenHelper(context, "komiku_bookmarks.db", null, 3) {

    companion object {
        const val TABLE_BOOKMARKS = "bookmarks"
        const val TABLE_HISTORY = "history"
        private const val BOOKMARK_COLUMNS = "slug, title, thumbnail_url, type, status, added_at, last_chapter_url, has_update"
        private const val HISTORY_COLUMNS = "slug, chapter_url, chapter_title, manga_title, read_at, last_page"
    }

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL("""
            CREATE TABLE $TABLE_BOOKMARKS (
                slug TEXT PRIMARY KEY,
                title TEXT NOT NULL,
                thumbnail_url TEXT DEFAULT '',
                type TEXT DEFAULT '',
                status TEXT DEFAULT '',
                added_at INTEGER NOT NULL,
                last_chapter_url TEXT DEFAULT '',
                has_update INTEGER DEFAULT 0
            )
        """)

        db.execSQL("""
            CREATE TABLE $TABLE_HISTORY (
                slug TEXT NOT NULL,
                chapter_url TEXT NOT NULL,
                chapter_title TEXT NOT NULL,
                manga_title TEXT NOT NULL,
                read_at INTEGER NOT NULL,
                last_page INTEGER DEFAULT 0,
                PRIMARY KEY (slug, chapter_url)
            )
        """)
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        // The pre-release v1 schema is rebuilt; every later version must migrate in place.
        if (oldVersion < 2) {
            db.execSQL("DROP TABLE IF EXISTS $TABLE_BOOKMARKS")
            db.execSQL("DROP TABLE IF EXISTS $TABLE_HISTORY")
            onCreate(db)
            return
        }
        if (oldVersion < 3) {
            db.execSQL("ALTER TABLE $TABLE_BOOKMARKS ADD COLUMN last_chapter_url TEXT DEFAULT ''")
            db.execSQL("ALTER TABLE $TABLE_BOOKMARKS ADD COLUMN has_update INTEGER DEFAULT 0")
            db.execSQL("ALTER TABLE $TABLE_HISTORY ADD COLUMN last_page INTEGER DEFAULT 0")
        }
    }

    // --- Bookmarks ---

    fun addBookmark(
        slug: String, title: String, thumbnailUrl: String = "", type: String = "",
        status: String = "", lastChapterUrl: String = ""
    ) {
        writableDatabase.execSQL("""
            INSERT OR REPLACE INTO $TABLE_BOOKMARKS ($BOOKMARK_COLUMNS)
            VALUES (?, ?, ?, ?, ?, ?, ?, 0)
        """, arrayOf(slug, title, thumbnailUrl, type, status, System.currentTimeMillis(), lastChapterUrl))
    }

    fun removeBookmark(slug: String) {
        writableDatabase.execSQL("DELETE FROM $TABLE_BOOKMARKS WHERE slug = ?", arrayOf(slug))
    }

    fun isBookmarked(slug: String): Boolean {
        val cursor = readableDatabase.rawQuery(
            "SELECT 1 FROM $TABLE_BOOKMARKS WHERE slug = ?", arrayOf(slug)
        )
        return cursor.use { it.moveToFirst() }
    }

    fun getAllBookmarks(): List<BookmarkEntry> {
        val cursor = readableDatabase.rawQuery(
            "SELECT $BOOKMARK_COLUMNS FROM $TABLE_BOOKMARKS ORDER BY has_update DESC, added_at DESC", null
        )
        val results = mutableListOf<BookmarkEntry>()
        cursor.use {
            while (it.moveToNext()) {
                results.add(
                    BookmarkEntry(
                        slug = it.getString(0),
                        title = it.getString(1),
                        thumbnailUrl = it.getString(2) ?: "",
                        type = it.getString(3) ?: "",
                        status = it.getString(4) ?: "",
                        addedAt = it.getLong(5),
                        lastChapterUrl = it.getString(6) ?: "",
                        hasUpdate = it.getInt(7) != 0
                    )
                )
            }
        }
        return results
    }

    /** Records the newest known chapter; [hasUpdate] flags it as unseen in the library. */
    fun updateBookmarkLatest(slug: String, latestChapterUrl: String, hasUpdate: Boolean) {
        val values = ContentValues().apply {
            put("last_chapter_url", latestChapterUrl)
            put("has_update", if (hasUpdate) 1 else 0)
        }
        writableDatabase.update(TABLE_BOOKMARKS, values, "slug = ?", arrayOf(slug))
    }

    // --- History ---

    fun addHistory(slug: String, chapterUrl: String, chapterTitle: String, mangaTitle: String) {
        val db = writableDatabase
        val values = ContentValues().apply {
            put("chapter_title", chapterTitle)
            put("manga_title", mangaTitle)
            put("read_at", System.currentTimeMillis())
        }
        // Update-then-insert keeps last_page; SQLite UPSERT needs API 30+.
        val updated = db.update(TABLE_HISTORY, values, "slug = ? AND chapter_url = ?", arrayOf(slug, chapterUrl))
        if (updated == 0) {
            values.put("slug", slug)
            values.put("chapter_url", chapterUrl)
            values.put("last_page", 0)
            db.insert(TABLE_HISTORY, null, values)
        }
    }

    fun updateLastPage(slug: String, chapterUrl: String, page: Int) {
        val values = ContentValues().apply { put("last_page", page) }
        writableDatabase.update(TABLE_HISTORY, values, "slug = ? AND chapter_url = ?", arrayOf(slug, chapterUrl))
    }

    fun getLastPage(slug: String, chapterUrl: String): Int {
        val cursor = readableDatabase.rawQuery(
            "SELECT last_page FROM $TABLE_HISTORY WHERE slug = ? AND chapter_url = ?",
            arrayOf(slug, chapterUrl)
        )
        return cursor.use { if (it.moveToFirst()) it.getInt(0) else 0 }
    }

    fun getReadChapterUrls(slug: String): Set<String> {
        val cursor = readableDatabase.rawQuery(
            "SELECT chapter_url FROM $TABLE_HISTORY WHERE slug = ?", arrayOf(slug)
        )
        val urls = mutableSetOf<String>()
        cursor.use { while (it.moveToNext()) urls.add(it.getString(0)) }
        return urls
    }

    fun getLastRead(slug: String): HistoryEntry? {
        val cursor = readableDatabase.rawQuery(
            "SELECT $HISTORY_COLUMNS FROM $TABLE_HISTORY WHERE slug = ? ORDER BY read_at DESC LIMIT 1",
            arrayOf(slug)
        )
        return cursor.use { if (it.moveToFirst()) it.toHistoryEntry() else null }
    }

    /** Latest chapter read per manga, newest first. */
    fun getAllHistory(limit: Int = 50): List<HistoryEntry> {
        // SQLite returns the row holding MAX() for bare columns, so each manga yields its latest chapter.
        val cursor = readableDatabase.rawQuery(
            "SELECT slug, chapter_url, chapter_title, manga_title, MAX(read_at) AS read_at, last_page " +
            "FROM $TABLE_HISTORY GROUP BY slug ORDER BY read_at DESC LIMIT $limit", null
        )
        val results = mutableListOf<HistoryEntry>()
        cursor.use { while (it.moveToNext()) results.add(it.toHistoryEntry()) }
        return results
    }

    private fun android.database.Cursor.toHistoryEntry() = HistoryEntry(
        slug = getString(0),
        chapterUrl = getString(1),
        chapterTitle = getString(2),
        mangaTitle = getString(3),
        readAt = getLong(4),
        lastPage = getInt(5)
    )
}

data class BookmarkEntry(
    val slug: String,
    val title: String,
    val thumbnailUrl: String = "",
    val type: String = "",
    val status: String = "",
    val addedAt: Long = 0,
    val lastChapterUrl: String = "",
    val hasUpdate: Boolean = false
)

data class HistoryEntry(
    val slug: String,
    val chapterUrl: String,
    val chapterTitle: String,
    val mangaTitle: String,
    val readAt: Long = 0,
    val lastPage: Int = 0
)

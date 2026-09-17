package com.c99.komikureader.data.local

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

class BookmarkDatabase(context: Context) : SQLiteOpenHelper(context, "komiku_bookmarks.db", null, 2) {

    companion object {
        const val TABLE_BOOKMARKS = "bookmarks"
        const val TABLE_HISTORY = "history"
    }

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL("""
            CREATE TABLE $TABLE_BOOKMARKS (
                slug TEXT PRIMARY KEY,
                title TEXT NOT NULL,
                thumbnail_url TEXT DEFAULT '',
                type TEXT DEFAULT '',
                status TEXT DEFAULT '',
                added_at INTEGER NOT NULL
            )
        """)

        db.execSQL("""
            CREATE TABLE $TABLE_HISTORY (
                slug TEXT NOT NULL,
                chapter_url TEXT NOT NULL,
                chapter_title TEXT NOT NULL,
                manga_title TEXT NOT NULL,
                read_at INTEGER NOT NULL,
                PRIMARY KEY (slug, chapter_url)
            )
        """)
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        db.execSQL("DROP TABLE IF EXISTS $TABLE_BOOKMARKS")
        db.execSQL("DROP TABLE IF EXISTS $TABLE_HISTORY")
        onCreate(db)
    }

    // --- Bookmarks ---

    fun addBookmark(slug: String, title: String, thumbnailUrl: String = "", type: String = "", status: String = "") {
        val db = writableDatabase
        db.execSQL("""
            INSERT OR REPLACE INTO $TABLE_BOOKMARKS (slug, title, thumbnail_url, type, status, added_at)
            VALUES (?, ?, ?, ?, ?, ?)
        """, arrayOf(slug, title, thumbnailUrl, type, status, System.currentTimeMillis()))
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
            "SELECT * FROM $TABLE_BOOKMARKS ORDER BY added_at DESC", null
        )
        val results = mutableListOf<BookmarkEntry>()
        cursor.use {
            while (it.moveToNext()) {
                results.add(
                    BookmarkEntry(
                        slug = it.getString(0),
                        title = it.getString(1),
                        thumbnailUrl = it.getString(2),
                        type = it.getString(3),
                        status = it.getString(4),
                        addedAt = it.getLong(5)
                    )
                )
            }
        }
        return results
    }

    // --- History ---

    fun addHistory(slug: String, chapterUrl: String, chapterTitle: String, mangaTitle: String) {
        val db = writableDatabase
        db.execSQL("""
            INSERT OR REPLACE INTO $TABLE_HISTORY (slug, chapter_url, chapter_title, manga_title, read_at)
            VALUES (?, ?, ?, ?, ?)
        """, arrayOf(slug, chapterUrl, chapterTitle, mangaTitle, System.currentTimeMillis()))
    }

    fun getLastRead(slug: String): HistoryEntry? {
        val cursor = readableDatabase.rawQuery(
            "SELECT * FROM $TABLE_HISTORY WHERE slug = ? ORDER BY read_at DESC LIMIT 1",
            arrayOf(slug)
        )
        return cursor.use {
            if (it.moveToFirst()) {
                HistoryEntry(
                    slug = it.getString(0),
                    chapterUrl = it.getString(1),
                    chapterTitle = it.getString(2),
                    mangaTitle = it.getString(3),
                    readAt = it.getLong(4)
                )
            } else null
        }
    }

    fun getAllHistory(): List<HistoryEntry> {
        val cursor = readableDatabase.rawQuery(
            "SELECT DISTINCT slug, chapter_url, chapter_title, manga_title, MAX(read_at) as read_at " +
            "FROM $TABLE_HISTORY GROUP BY slug ORDER BY read_at DESC LIMIT 50", null
        )
        val results = mutableListOf<HistoryEntry>()
        cursor.use {
            while (it.moveToNext()) {
                results.add(
                    HistoryEntry(
                        slug = it.getString(0),
                        chapterUrl = it.getString(1),
                        chapterTitle = it.getString(2),
                        mangaTitle = it.getString(3),
                        readAt = it.getLong(4)
                    )
                )
            }
        }
        return results
    }
}

data class BookmarkEntry(
    val slug: String,
    val title: String,
    val thumbnailUrl: String = "",
    val type: String = "",
    val status: String = "",
    val addedAt: Long = 0
)

data class HistoryEntry(
    val slug: String,
    val chapterUrl: String,
    val chapterTitle: String,
    val mangaTitle: String,
    val readAt: Long = 0
)
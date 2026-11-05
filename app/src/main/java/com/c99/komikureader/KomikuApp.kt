package com.c99.komikureader

import android.app.Application
import com.c99.komikureader.data.local.BookmarkDatabase
import com.c99.komikureader.data.remote.KomikuApi
import com.c99.komikureader.data.repository.MangaRepository
import com.c99.komikureader.work.ChapterUpdateWorker

class KomikuApp : Application() {

    lateinit var repository: MangaRepository
        private set

    override fun onCreate() {
        super.onCreate()
        val api = KomikuApi()
        val db = BookmarkDatabase(this)
        repository = MangaRepository(api, db)

        ChapterUpdateWorker.createChannel(this)
        ChapterUpdateWorker.schedule(this)
    }
}

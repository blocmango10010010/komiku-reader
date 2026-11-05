package com.c99.komikureader.work

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.work.*
import com.c99.komikureader.KomikuApp
import com.c99.komikureader.MainActivity
import com.c99.komikureader.R
import kotlinx.coroutines.delay
import java.util.concurrent.TimeUnit

/** Periodically checks bookmarked titles for new chapters and notifies about them. */
class ChapterUpdateWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val repository = (applicationContext as KomikuApp).repository
        val updated = mutableListOf<Pair<String, String>>() // slug to title

        for (bookmark in repository.getAllBookmarks()) {
            val detail = repository.getMangaDetail(bookmark.slug, force = true) ?: continue
            val latest = detail.chapters.firstOrNull()?.url ?: continue
            if (latest != bookmark.lastChapterUrl) {
                // An empty stored value means a bookmark from before tracking existed: just record it.
                val isNew = bookmark.lastChapterUrl.isNotEmpty()
                repository.updateBookmarkLatest(bookmark.slug, latest, hasUpdate = isNew || bookmark.hasUpdate)
                if (isNew) updated += bookmark.slug to detail.title
            }
            delay(REQUEST_SPACING_MS) // be gentle with the site's DDoS protection
        }

        if (updated.isNotEmpty()) notify(updated)
        return Result.success()
    }

    private fun notify(updated: List<Pair<String, String>>) {
        val manager = NotificationManagerCompat.from(applicationContext)
        if (!manager.areNotificationsEnabled()) return

        val (slug, title) = updated.first()
        val intent = Intent(applicationContext, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            // A single update opens that title; several open the library.
            if (updated.size == 1) putExtra(MainActivity.EXTRA_OPEN_SLUG, slug)
            else putExtra(MainActivity.EXTRA_OPEN_LIBRARY, true)
        }
        val pending = PendingIntent.getActivity(
            applicationContext, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val text = if (updated.size == 1) "$title punya chapter baru"
        else "${updated.size} komik punya chapter baru: " + updated.joinToString { it.second }

        val notification = NotificationCompat.Builder(applicationContext, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("Chapter baru tersedia")
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setContentIntent(pending)
            .setAutoCancel(true)
            .build()
        try {
            manager.notify(NOTIFICATION_ID, notification)
        } catch (e: SecurityException) {
            // Permission revoked between the check and the call.
        }
    }

    companion object {
        const val CHANNEL_ID = "chapter_updates"
        private const val NOTIFICATION_ID = 1001
        private const val WORK_NAME = "chapter_update_check"
        private const val REQUEST_SPACING_MS = 1500L

        fun createChannel(context: Context) {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
            val channel = NotificationChannel(CHANNEL_ID, "Chapter baru", NotificationManager.IMPORTANCE_DEFAULT).apply {
                description = "Pemberitahuan chapter baru untuk komik yang di-bookmark"
            }
            context.getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }

        fun schedule(context: Context) {
            val request = PeriodicWorkRequestBuilder<ChapterUpdateWorker>(6, TimeUnit.HOURS)
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.MINUTES)
                .build()
            WorkManager.getInstance(context)
                .enqueueUniquePeriodicWork(WORK_NAME, ExistingPeriodicWorkPolicy.KEEP, request)
        }
    }
}

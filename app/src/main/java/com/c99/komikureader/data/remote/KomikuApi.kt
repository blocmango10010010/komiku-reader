package com.c99.komikureader.data.remote

import com.c99.komikureader.data.model.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import java.util.concurrent.TimeUnit

class KomikuApi {

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .addInterceptor { chain ->
            val request = chain.request().newBuilder()
                .header("User-Agent", "Mozilla/5.0 (Linux; Android 13; Pixel 7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36")
                .build()
            chain.proceed(request)
        }
        .build()

    private val baseUrl = "https://komiku.org"

    private suspend fun fetchDoc(url: String): Document = withContext(Dispatchers.IO) {
        val request = Request.Builder().url(url).build()
        val response = client.newCall(request).execute()
        Jsoup.parse(response.body?.string() ?: "", url)
    }

    // ==================== HOME ====================

    suspend fun getHomeRanking(): List<MangaItem> = withContext(Dispatchers.IO) {
        val doc = fetchDoc(baseUrl)
        val items = mutableListOf<MangaItem>()

        // Parse ranking section (ls4 articles)
        val rankingSection = doc.select("section#Rekomendasi_Komik .ls4")
        for (article in rankingSection) {
            val link = article.select("a[href*=/manga/]").first() ?: continue
            val href = link.attr("href")
            val slug = extractSlug(href)
            val title = link.select("h4").text().ifEmpty { link.attr("title") }
            val thumb = article.select("img").first()?.attr("data-src")
                ?: article.select("img").first()?.attr("src")
                ?: ""

            if (title.isNotEmpty() && slug.isNotEmpty()) {
                items.add(MangaItem(title = title, slug = slug, thumbnailUrl = thumb))
            }
        }
        items
    }

    suspend fun getHomeLatest(): List<MangaItem> = withContext(Dispatchers.IO) {
        val doc = fetchDoc(baseUrl)
        val items = mutableListOf<MangaItem>()

        // Latest updates - find all manga links with thumbnails on homepage
        // Usually in sections after ranking
        val allArticles = doc.select("article.ls4, div.ls4")
        val seen = mutableSetOf<String>()

        for (article in allArticles) {
            val link = article.select("a[href*=/manga/]").first() ?: continue
            val href = link.attr("href")
            val slug = extractSlug(href)
            if (slug in seen) continue
            seen.add(slug)

            val title = link.select("h4, h3").text().ifEmpty { link.attr("title") }
            val thumb = article.select("img").first()?.attr("data-src")
                ?: article.select("img").first()?.attr("src")
                ?: ""

            if (title.isNotEmpty() && slug.isNotEmpty()) {
                items.add(MangaItem(title = title, slug = slug, thumbnailUrl = thumb))
            }
        }
        items
    }

    // ==================== MANGA LISTING ====================

    suspend fun getMangaList(page: Int = 1): List<MangaItem> = withContext(Dispatchers.IO) {
        val url = if (page <= 1) "$baseUrl/daftar-komik/"
        else "$baseUrl/daftar-komik/page/$page/"

        val doc = fetchDoc(url)
        val items = mutableListOf<MangaItem>()

        val mangaBlocks = doc.select("div.bge")
        if (mangaBlocks.isEmpty()) {
            // Fallback: parse h4 links directly
            val h4Links = doc.select("h4 a[href*=/manga/]")
            for (link in h4Links) {
                val href = link.attr("href")
                val slug = extractSlug(href)
                val title = link.text().trim()
                // Find thumbnail in parent
                val thumb = link.parents().select("img").first()?.attr("data-src")
                    ?: link.parents().select("img").first()?.attr("src")
                    ?: ""
                if (title.isNotEmpty()) {
                    items.add(MangaItem(title = title, slug = slug, thumbnailUrl = thumb))
                }
            }
        } else {
            for (block in mangaBlocks) {
                val link = block.select("a[href*=/manga/]").first() ?: continue
                val href = link.attr("href")
                val slug = extractSlug(href)
                val title = block.select("h4, h3").text().trim().ifEmpty { link.attr("title") }
                val thumb = block.select("img").first()?.attr("data-src")
                    ?: block.select("img").first()?.attr("src")
                    ?: ""

                if (title.isNotEmpty() && slug.isNotEmpty()) {
                    items.add(MangaItem(title = title, slug = slug, thumbnailUrl = thumb))
                }
            }
        }
        items
    }

    // ==================== SEARCH ====================

    suspend fun search(query: String): List<SearchResult> = withContext(Dispatchers.IO) {
        val encoded = java.net.URLEncoder.encode(query, "UTF-8")
        val url = "$baseUrl/?s=$encoded&post_type=manga"
        val doc = fetchDoc(url)

        val results = mutableListOf<SearchResult>()
        val blocks = doc.select("div.bge")

        for (block in blocks) {
            val kanDiv = block.select("div.kan").first() ?: continue
            val chapterLink = block.select("a[href*=-chapter-]").first() ?: continue
            val chapterHref = chapterLink.attr("href")

            // Extract manga slug from chapter URL: /{slug}-chapter-{num}/
            val slug = extractMangaSlugFromChapter(chapterHref)
            if (slug.isEmpty()) continue

            // Get title (often "Untitled" in search results, use slug for display)
            val title = kanDiv.select("h3").text().trim()
                .let { if (it == "Untitled" || it.isEmpty()) slugToTitle(slug) else it }

            val firstChapter = block.select("div.new1").first()?.select("a")?.first()?.attr("href")
            val latestChapter = block.select("div.new1").last()?.select("a")?.last()?.attr("href")
            val updated = kanDiv.select("p").first()?.text()?.trim() ?: ""

            results.add(
                SearchResult(
                    mangaSlug = slug,
                    displayTitle = title,
                    firstChapterUrl = firstChapter,
                    latestChapterUrl = latestChapter,
                    updated = updated
                )
            )
        }
        // Deduplicate by slug
        results.distinctBy { it.mangaSlug }
    }

    // ==================== MANGA DETAIL ====================

    suspend fun getMangaDetail(slug: String): MangaDetail = withContext(Dispatchers.IO) {
        val url = "$baseUrl/manga/$slug/"
        val doc = fetchDoc(url)

        val title = doc.select("h1").text().trim().ifEmpty {
            doc.select("title").text().replace(" - Komiku", "").trim()
        }

        // Parse info table
        val infoTable = doc.select("table.inftable")
        var alternativeTitle = ""
        var type = ""
        var genre = ""
        var author = ""
        var status = ""
        var rating = ""

        for (row in infoTable.select("tr")) {
            val cells = row.select("td")
            if (cells.size >= 2) {
                val label = cells[0].text().trim().lowercase()
                val value = cells[1].text().trim()
                when {
                    label.contains("alternatif") || label.contains("alternative") -> alternativeTitle = value
                    label.contains("tipe") -> type = value
                    label.contains("genre") -> genre = value
                    label.contains("author") || label.contains("pengarang") -> author = value
                    label.contains("status") -> status = value
                    label.contains("rating") -> rating = value
                }
            }
        }

        // Synopsis
        val synopsis = doc.select("section#Sinopsis p").text()
            .ifEmpty { doc.select("article p, .desc p").text() }

        // Thumbnail
        val thumbnail = doc.select("meta[property=og:image]").attr("content")
            .ifEmpty {
                doc.select("img[src*=/uploads/manga/]").first()?.attr("src")
                    ?: doc.select("img[data-src*=/uploads/manga/]").first()?.attr("data-src")
                    ?: ""
            }

        // Views
        val pembaca = doc.select("td:contains(Minggu ini)").text()
        val totalViews = pembaca.substringBefore("views,").replace("Total:", "").trim()
        val weeklyViews = pembaca.substringAfter("Minggu ini:").replace("views", "").trim()

        // Chapter list
        val chapters = mutableListOf<ChapterItem>()
        val chapterTable = doc.select("table#Daftar_Chapter")
        for (row in chapterTable.select("tr[itemprop=itemListElement]")) {
            val link = row.select("a[itemprop=url]").first() ?: continue
            val chapTitle = link.select("span[itemprop=name]").text().trim()
                .ifEmpty { link.text().trim() }
            val chapUrl = link.attr("href")
            val date = row.select("td.tanggalseries").text().trim()

            chapters.add(ChapterItem(title = chapTitle, url = chapUrl, date = date))
        }

        MangaDetail(
            title = title,
            slug = slug,
            alternativeTitle = alternativeTitle,
            type = type,
            genre = genre,
            author = author,
            status = status,
            rating = rating,
            synopsis = synopsis,
            thumbnailUrl = thumbnail,
            totalViews = totalViews,
            weeklyViews = weeklyViews,
            chapters = chapters
        )
    }

    // ==================== CHAPTER READER ====================

    suspend fun getChapterImages(chapterUrl: String): ChapterPage = withContext(Dispatchers.IO) {
        val url = if (chapterUrl.startsWith("http")) chapterUrl else "$baseUrl$chapterUrl"
        val doc = fetchDoc(url)

        val chapterTitle = doc.select("h1").text().trim()
            .ifEmpty { doc.select("title").text().replace(" - Komiku", "").trim() }

        // Extract manga title from og:title or breadcrumbs
        val ogTitle = doc.select("meta[property=og:title]").attr("content")
        val mangaTitle = ogTitle.substringBefore(" Chapter").trim()
            .ifEmpty { doc.select(".breadcrumb a, nav a[href*=/manga/]").last()?.text()?.trim() ?: "" }

        // Find all manga images
        val imageUrls = mutableListOf<String>()
        val allImages = doc.select("img")
        for (img in allImages) {
            val src = img.attr("src")
            if (src.isNotEmpty() && isMangaImage(src)) {
                imageUrls.add(src)
            }
        }

        // Navigation
        val prevLink = doc.select("a:containsOwn(Prev), a:containsOwn(Sebelumnya), a.rel-prev").first()
        val nextLink = doc.select("a:containsOwn(Next), a:containsOwn(Selanjutnya), a.rel-next").first()

        ChapterPage(
            mangaTitle = mangaTitle,
            chapterTitle = chapterTitle,
            imageUrls = imageUrls,
            prevChapterUrl = prevLink?.attr("href"),
            nextChapterUrl = nextLink?.attr("href")
        )
    }

    // ==================== HELPERS ====================

    private fun extractSlug(href: String): String {
        // /manga/{slug}/ -> {slug}
        return href.removePrefix("/").removeSuffix("/")
            .removePrefix("manga/")
            .split("/").firstOrNull() ?: ""
    }

    private fun extractMangaSlugFromChapter(chapterHref: String): String {
        // /{slug}-chapter-{num}/ -> {slug}
        val path = chapterHref.removePrefix("/").removeSuffix("/")
        val idx = path.lastIndexOf("-chapter-")
        if (idx < 0) return ""
        // Try with -chapter-{num} pattern
        val match = Regex("^(.+)-chapter-\\d+").find(path)
        return match?.groupValues?.get(1) ?: ""
    }

    private fun slugToTitle(slug: String): String {
        return slug.split("-")
            .joinToString(" ") { word ->
                word.replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }
            }
    }

    private fun isMangaImage(src: String): Boolean {
        val lower = src.lowercase()
        // Match komiku image patterns
        return (lower.contains("komiku.to/upload") ||
                lower.contains("komiku.org/upload") ||
                lower.contains("img.komiku")) &&
                (lower.endsWith(".webp") || lower.endsWith(".jpg") ||
                 lower.endsWith(".jpeg") || lower.endsWith(".png"))
    }
}
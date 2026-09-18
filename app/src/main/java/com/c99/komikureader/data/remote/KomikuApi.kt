package com.c99.komikureader.data.remote

import com.c99.komikureader.data.model.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

class KomikuApi {

    private val client by lazy {
        OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .addInterceptor { chain ->
                val request = chain.request().newBuilder()
                    .header("User-Agent", "Mozilla/5.0 (Linux; Android 13; Pixel 7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36")
                    .build()
                chain.proceed(request)
            }
            .build()
    }

    private val baseUrl = "https://komiku.org"

    private suspend fun fetchDoc(url: String): Document? = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder().url(url).build()
            val response = client.newCall(request).execute()
            val body = response.body?.string() ?: return@withContext null
            Jsoup.parse(body, url)
        } catch (e: Exception) {
            null
        }
    }

    // ==================== HOME ====================

    suspend fun getHomeRanking(): List<MangaItem> = withContext(Dispatchers.IO) {
        val doc = fetchDoc(baseUrl) ?: return@withContext emptyList()
        val items = mutableListOf<MangaItem>()
        val seen = mutableSetOf<String>()

        val candidates = doc.select(
            "section#Rekomendasi_Komik .ls4 a[href*=/manga/], " +
            ".rank-panel a[href*=/manga/], " +
            "article.ls4 a[href*=/manga/]"
        )

        for (link in candidates) {
            val href = link.attr("href").trim()
            val slug = extractSlug(href)
            if (slug.isEmpty() || slug in seen) continue
            seen.add(slug)

            val title = link.select("h4, h3, span").text().trim()
                .ifEmpty { link.attr("title").trim() }
                .ifEmpty { link.parents().select("h4, h3").text().trim() }
            if (title.isEmpty()) continue

            val parent = link.parent()?.parent() ?: link.parent()
            val thumb = parent?.select("img")?.firstOrNull()?.let {
                it.attr("data-src").ifEmpty { it.attr("src") }
            } ?: ""

            items.add(MangaItem(title = title, slug = slug, thumbnailUrl = thumb))
        }

        // Fallback: any manga link with image on home page
        if (items.isEmpty()) {
            val allManga = doc.select("a[href*=/manga/]")
            for (link in allManga) {
                val href = link.attr("href").trim()
                val slug = extractSlug(href)
                if (slug.isEmpty() || slug in seen) continue
                seen.add(slug)

                val title = link.select("h4,h3").text().trim()
                    .ifEmpty { link.text().trim() }
                if (title.isEmpty() || title.length > 100) continue

                val thumb = link.select("img").firstOrNull()?.let {
                    it.attr("data-src").ifEmpty { it.attr("src") }
                } ?: ""

                items.add(MangaItem(title = title, slug = slug, thumbnailUrl = thumb))
                if (items.size >= 20) break
            }
        }
        items
    }

    suspend fun getHomeLatest(): List<MangaItem> = withContext(Dispatchers.IO) {
        val doc = fetchDoc(baseUrl) ?: return@withContext emptyList()
        val items = mutableListOf<MangaItem>()
        val seen = mutableSetOf<String>()

        val allSections = doc.select("section")
        for (section in allSections) {
            val links = section.select("a[href*=/manga/]")
            for (link in links) {
                val href = link.attr("href").trim()
                val slug = extractSlug(href)
                if (slug.isEmpty() || slug in seen) continue
                seen.add(slug)

                val title = link.select("h4,h3").text().trim()
                    .ifEmpty { link.text().trim() }
                if (title.isEmpty() || title.length > 100) continue

                val thumb = link.select("img").firstOrNull()?.let {
                    it.attr("data-src").ifEmpty { it.attr("src") }
                } ?: ""

                items.add(MangaItem(title = title, slug = slug, thumbnailUrl = thumb))
            }
        }
        items
    }

    // ==================== MANGA LISTING ====================

    suspend fun getMangaList(page: Int = 1, type: String = ""): List<MangaItem> = withContext(Dispatchers.IO) {
        val url = buildString {
            append(baseUrl)
            if (type.isNotEmpty()) {
                append("/pustaka/?tipe=$type")
                if (page > 1) append("&page=$page")
            } else {
                if (page <= 1) append("/daftar-komik/")
                else append("/daftar-komik/page/$page/")
            }
        }

        val doc = fetchDoc(url) ?: return@withContext emptyList()
        val items = mutableListOf<MangaItem>()
        val seen = mutableSetOf<String>()

        val h4Links = doc.select("h4 a[href*=/manga/]")
        if (h4Links.isNotEmpty()) {
            for (link in h4Links) {
                val href = link.attr("href").trim()
                val slug = extractSlug(href)
                if (slug.isEmpty() || slug in seen) continue
                seen.add(slug)

                val title = link.text().trim()
                if (title.isEmpty()) continue

                val thumb = link.parents().select("img[data-src]").firstOrNull()
                    ?.attr("data-src")
                    ?: link.parents().select("img[src]").firstOrNull()
                        ?.attr("src")
                    ?: ""

                items.add(MangaItem(title = title, slug = slug, thumbnailUrl = thumb))
            }
        } else {
            val blocks = doc.select("div.bge, article")
            for (block in blocks) {
                val link = block.select("a[href*=/manga/]").firstOrNull() ?: continue
                val href = link.attr("href").trim()
                val slug = extractSlug(href)
                if (slug.isEmpty() || slug in seen) continue
                seen.add(slug)

                val title = block.select("h4, h3").text().trim()
                    .ifEmpty { link.text().trim() }
                if (title.isEmpty()) continue

                val thumb = block.select("img").firstOrNull()?.let {
                    it.attr("data-src").ifEmpty { it.attr("src") }
                } ?: ""

                items.add(MangaItem(title = title, slug = slug, thumbnailUrl = thumb))
            }
        }

        if (items.isEmpty()) {
            val allLinks = doc.select("a[href*=/manga/]")
            for (link in allLinks) {
                val href = link.attr("href").trim()
                val slug = extractSlug(href)
                if (slug.isEmpty() || slug in seen) continue
                seen.add(slug)

                val title = link.text().trim()
                if (title.isEmpty() || title.length > 100) continue

                items.add(MangaItem(title = title, slug = slug, thumbnailUrl = ""))
                if (items.size >= 50) break
            }
        }
        items
    }

    // ==================== SEARCH ====================

    suspend fun search(query: String): List<SearchResult> = withContext(Dispatchers.IO) {
        val encoded = URLEncoder.encode(query, "UTF-8")
        val url = "$baseUrl/?s=$encoded&post_type=manga"
        val doc = fetchDoc(url) ?: return@withContext emptyList()
        val results = mutableListOf<SearchResult>()
        val seenSlugs = mutableSetOf<String>()

        val blocks = doc.select("div.bge")
        for (block in blocks) {
            val chapterLink = block.select("a[href*=-chapter-]").firstOrNull() ?: continue
            val chapterHref = chapterLink.attr("href")
            val slug = extractMangaSlugFromChapter(chapterHref)
            if (slug.isEmpty() || slug in seenSlugs) continue
            seenSlugs.add(slug)

            val kanDiv = block.select("div.kan").firstOrNull()
            val title = kanDiv?.select("h3")?.text()?.trim()?.let {
                if (it == "Untitled" || it.isEmpty()) slugToTitle(slug) else it
            } ?: slugToTitle(slug)

            val firstChapter = block.select("div.new1").firstOrNull()?.select("a")?.lastOrNull()?.attr("href")
            val latestChapter = block.select("div.new1").lastOrNull()?.select("a")?.lastOrNull()?.attr("href")
            val updated = kanDiv?.select("p")?.firstOrNull()?.text()?.trim() ?: ""

            results.add(SearchResult(
                mangaSlug = slug, displayTitle = title,
                firstChapterUrl = firstChapter, latestChapterUrl = latestChapter,
                updated = updated
            ))
        }
        results
    }

    // ==================== MANGA DETAIL ====================

    suspend fun getMangaDetail(slug: String): MangaDetail? = withContext(Dispatchers.IO) {
        val url = "$baseUrl/manga/$slug/"
        val doc = fetchDoc(url) ?: return@withContext null

        val title = doc.select("h1").text().trim().ifEmpty {
            doc.select("title").text().replace(" - Komiku", "").trim()
        }

        val infoTable = doc.select("table.inftable")
        var alternativeTitle = ""
        var mangaType = ""
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
                    label.contains("alternatif") -> alternativeTitle = value
                    label.contains("tipe") -> mangaType = value
                    label.contains("genre") -> genre = value
                    label.contains("author") || label.contains("pengarang") -> author = value
                    label.contains("status") -> status = value
                    label.contains("rating") -> rating = value
                }
            }
        }

        val synopsis = doc.select("section#Sinopsis p, article p").text()
            .ifEmpty { doc.select("meta[name=description]").attr("content") }

        val thumbnail = doc.select("meta[property=og:image]").attr("content")
            .ifEmpty {
                doc.select("img[src*=/uploads/manga/]").firstOrNull()?.attr("src")
                    ?: doc.select("img[data-src*=/uploads/manga/]").firstOrNull()?.attr("data-src")
                    ?: doc.select("img[src*=/thumbnail/]").firstOrNull()?.attr("src")
                    ?: ""
            }

        val chapters = mutableListOf<ChapterItem>()
        val chapterTable = doc.select("table#Daftar_Chapter")
        for (row in chapterTable.select("tr[itemprop=itemListElement]")) {
            val link = row.select("a[itemprop=url]").firstOrNull() ?: continue
            val chapTitle = link.select("span[itemprop=name]").text().trim()
                .ifEmpty { link.text().trim() }
            val chapUrl = link.attr("href").trim()
            val date = row.select("td.tanggalseries").text().trim()
            if (chapTitle.isNotEmpty()) {
                chapters.add(ChapterItem(title = chapTitle, url = chapUrl, date = date))
            }
        }

        MangaDetail(
            title = title, slug = slug, alternativeTitle = alternativeTitle,
            type = mangaType, genre = genre, author = author,
            status = status, rating = rating, synopsis = synopsis,
            thumbnailUrl = thumbnail, chapters = chapters
        )
    }

    // ==================== CHAPTER READER ====================

    suspend fun getChapterImages(chapterUrl: String): ChapterPage? = withContext(Dispatchers.IO) {
        val url = if (chapterUrl.startsWith("http")) chapterUrl else "$baseUrl$chapterUrl"
        val doc = fetchDoc(url) ?: return@withContext null

        val chapterTitle = doc.select("h1").text().trim()
            .ifEmpty { doc.select("title").text().replace(" - Komiku", "").trim() }

        val ogTitle = doc.select("meta[property=og:title]").attr("content")
        val mangaTitle = ogTitle.substringBefore(" Chapter").trim()
            .ifEmpty { doc.select("a[href*=/manga/]").lastOrNull()?.text()?.trim() ?: "" }

        val imageUrls = mutableListOf<String>()
        for (img in doc.select("img")) {
            val src = img.attr("src").trim()
            if (src.isNotEmpty() && isMangaImage(src)) {
                imageUrls.add(src)
            }
        }

        val prevLink = doc.select("a[rel=prev], a:contains(Prev), a:contains(Sebelumnya)").firstOrNull()
        val nextLink = doc.select("a[rel=next], a:contains(Next), a:contains(Selanjutnya)").firstOrNull()

        ChapterPage(
            mangaTitle = mangaTitle, chapterTitle = chapterTitle,
            imageUrls = imageUrls,
            prevChapterUrl = prevLink?.attr("href")?.trim(),
            nextChapterUrl = nextLink?.attr("href")?.trim()
        )
    }

    // ==================== HELPERS ====================

    private fun extractSlug(href: String): String {
        return href.removePrefix("/").removeSuffix("/")
            .removePrefix("manga/")
            .split("/").firstOrNull()?.trim() ?: ""
    }

    private fun extractMangaSlugFromChapter(chapterHref: String): String {
        val path = chapterHref.removePrefix("/").removeSuffix("/")
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
        return (lower.contains("komiku.to/upload") ||
                lower.contains("komiku.org/upload") ||
                lower.contains("img.komiku")) &&
                (lower.endsWith(".webp") || lower.endsWith(".jpg") ||
                 lower.endsWith(".jpeg") || lower.endsWith(".png"))
    }
}
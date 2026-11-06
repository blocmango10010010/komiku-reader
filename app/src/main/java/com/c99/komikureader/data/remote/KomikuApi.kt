package com.c99.komikureader.data.remote

import com.c99.komikureader.data.model.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.*
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

class KomikuApi {

    private val cookieJar = object : CookieJar {
        private val cookieStore = mutableMapOf<String, MutableList<Cookie>>()

        // OkHttp calls these from multiple threads concurrently.
        @Synchronized
        override fun loadForRequest(url: HttpUrl): List<Cookie> {
            return cookieStore[url.host]?.toList() ?: emptyList()
        }

        @Synchronized
        override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
            cookieStore.getOrPut(url.host) { mutableListOf() }.let { stored ->
                cookies.forEach { newCookie ->
                    stored.removeAll { it.name == newCookie.name }
                    stored.add(newCookie)
                }
            }
        }
    }

    private val client by lazy {
        OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .followRedirects(true)
            .followSslRedirects(true)
            .cookieJar(cookieJar)
            .addInterceptor { chain ->
                val request = chain.request().newBuilder()
                    .header("User-Agent", "Mozilla/5.0 (Linux; Android 13; Pixel 7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36")
                    .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
                    .header("Accept-Language", "id-ID,id;q=0.9,en;q=0.8")
                    .build()
                chain.proceed(request)
            }
            .build()
    }

    private val baseUrl = "https://komiku.org"
    private val apiUrl = "https://api.komiku.org"

    data class FetchResult(val doc: Document?, val error: String?)

    private suspend fun fetchDoc(url: String, retries: Int = 2): FetchResult = withContext(Dispatchers.IO) {
        var lastError: String? = null
        for (attempt in 1..retries) {
            try {
                val request = Request.Builder().url(url).build()
                val response = client.newCall(request).execute()
                val body = response.body?.string() ?: ""
                
                // Check for DDoS-Guard block
                if (response.code == 503 || body.contains("DDoS-Guard") || body.contains("ddos-guard")) {
                    // DDoS-Guard sets cookies; retry with them
                    if (attempt < retries) {
                        kotlinx.coroutines.delay(1000L * attempt)
                        continue
                    }
                    return@withContext FetchResult(null, "Situs sedang sibuk (DDoS protection). Coba lagi nanti.")
                }
                
                if (body.isBlank()) {
                    return@withContext FetchResult(null, "Respons kosong dari server.")
                }
                
                return@withContext FetchResult(Jsoup.parse(body, url), null)
            } catch (e: java.net.SocketTimeoutException) {
                lastError = "Koneksi timeout. Periksa internet."
                if (attempt < retries) kotlinx.coroutines.delay(1000L * attempt)
            } catch (e: java.net.UnknownHostException) {
                return@withContext FetchResult(null, "Tidak bisa terhubung ke komiku.org. Periksa internet.")
            } catch (e: Exception) {
                lastError = e.message ?: "Gagal memuat data"
                if (attempt < retries) kotlinx.coroutines.delay(1000L * attempt)
            }
        }
        FetchResult(null, lastError ?: "Gagal memuat data setelah $retries percobaan.")
    }

    // ==================== HOME ====================

    suspend fun getHomeRanking(): List<MangaItem> = withContext(Dispatchers.IO) {
        val (doc, _) = fetchDoc(baseUrl)
        doc?.let { parseHomeRanking(it) } ?: emptyList()
    }

    /** The home page's "Baca Komik Terbaru" feed, newest update first. */
    suspend fun getHomeLatest(): List<MangaItem> = withContext(Dispatchers.IO) {
        val (doc, _) = fetchDoc(baseUrl)
        doc?.let { parseHomeLatest(it) } ?: emptyList()
    }

    // ==================== MANGA LISTING ====================

    suspend fun getMangaList(page: Int = 1, type: String = ""): List<MangaItem> = withContext(Dispatchers.IO) {
        // /pustaka/ fills its list via JavaScript, so the type filter uses the API listing instead.
        val url = if (type.isNotEmpty()) {
            "$apiUrl/manga/${if (page > 1) "page/$page/" else ""}?tipe=$type"
        } else {
            if (page <= 1) "$baseUrl/daftar-komik/"
            else "$baseUrl/daftar-komik/page/$page/"
        }

        // Past the last page the site returns 404; an empty list ends pagination.
        val (doc, _) = fetchDoc(url)
        doc?.let { parseMangaFromDoc(it) } ?: emptyList()
    }

    // ==================== SEARCH ====================

    suspend fun search(query: String): List<SearchResult> = withContext(Dispatchers.IO) {
        val encoded = URLEncoder.encode(query, "UTF-8")
        
        // Try API first (more reliable, bypasses DDoS-Guard)
        val (apiDoc, _) = fetchDoc("$apiUrl/?s=$encoded")
        if (apiDoc != null) {
            val results = parseSearchResults(apiDoc)
            if (results.isNotEmpty()) return@withContext results
        }

        // Fallback: direct search
        val (doc, _) = fetchDoc("$baseUrl/?s=$encoded&post_type=manga")
        if (doc != null) {
            val results = parseSearchResults(doc)
            if (results.isNotEmpty()) return@withContext results
        }
        
        emptyList()
    }

    // ==================== MANGA DETAIL ====================

    suspend fun getMangaDetail(slug: String): MangaDetail? = withContext(Dispatchers.IO) {
        val (doc, _) = fetchDoc("$baseUrl/manga/$slug/")
        doc?.let { parseMangaDetail(it, slug) }
    }

    // ==================== CHAPTER READER ====================

    suspend fun getChapterImages(chapterUrl: String): ChapterPage? = withContext(Dispatchers.IO) {
        val url = if (chapterUrl.startsWith("http")) chapterUrl else "$baseUrl$chapterUrl"
        val (doc, _) = fetchDoc(url)
        doc?.let { parseChapterPage(it) }
    }

    // ==================== PARSING (internal for unit tests) ====================

    internal fun parseMangaDetail(doc: Document, slug: String): MangaDetail? {
        var title = doc.select("h1").text().trim().removePrefix("Komik ").trim().ifEmpty {
            doc.select("title").text().replace(" - Komiku", "").removePrefix("Komik ").trim()
        }
        if (title.isEmpty()) return null

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
                    label == "judul" -> if (value.isNotEmpty()) title = value
                    label.contains("alternatif") -> alternativeTitle = value
                    label.contains("tipe") -> mangaType = value
                    label.contains("genre") -> genre = value
                    label.contains("author") || label.contains("pengarang") -> author = value
                    label.contains("status") -> status = value
                    label.contains("rating") -> rating = value
                }
            }
        }

        val synopsis = doc.select("p.desc").text().trim()
            .ifEmpty { doc.select("section#Sinopsis p").text().trim() }
            .ifEmpty { doc.select("meta[name=description]").attr("content").trim() }

        val thumbnail = doc.select("meta[property=og:image]").attr("content")
            .ifEmpty {
                doc.select("img[src*=/uploads/manga/]").firstOrNull()?.attr("src")
                    ?: doc.select("img[data-src*=/uploads/manga/]").firstOrNull()?.attr("data-src")
                    ?: doc.select("img[src*=/thumbnail/]").firstOrNull()?.attr("src")
                    ?: ""
            }

        val chapters = mutableListOf<ChapterItem>()
        doc.select("table#Daftar_Chapter tr[itemprop=itemListElement]").forEach { row ->
            val link = row.select("a[itemprop=url]").firstOrNull() ?: return@forEach
            val chapTitle = link.select("span[itemprop=name]").text().trim()
                .ifEmpty { link.text().trim() }
            val chapUrl = toPath(link.attr("href"))
            val date = row.select("td.tanggalseries").text().trim()
            if (chapTitle.isNotEmpty()) {
                chapters.add(ChapterItem(title = chapTitle, url = chapUrl, date = date))
            }
        }

        return MangaDetail(
            title = title, slug = slug, alternativeTitle = alternativeTitle,
            type = mangaType, genre = genre, author = author,
            status = status, rating = rating, synopsis = synopsis,
            thumbnailUrl = thumbnail, chapters = chapters.distinctBy { it.url }
        )
    }

    internal fun parseChapterPage(doc: Document): ChapterPage {
        val chapterTitle = doc.select("h1").text().trim()
            .ifEmpty { doc.select("title").text().replace(" - Komiku", "").trim() }

        val ogTitle = doc.select("meta[property=og:title]").attr("content")
        val mangaTitle = ogTitle.substringBefore(" Chapter").trim()
            .ifEmpty { doc.select("a[href*=/manga/]").lastOrNull()?.text()?.trim() ?: "" }

        val imageUrls = mutableListOf<String>()
        val pageImages = doc.select("#Baca_Komik img").ifEmpty { doc.select("img") }
        pageImages.forEach { img ->
            val src = img.attr("src").trim()
            if (src.isNotEmpty() && isMangaImage(src)) {
                imageUrls.add(src)
            }
        }

        // Komiku's nav buttons are icon-only, identified by aria-label rather than text.
        val prevLink = firstMatch(doc, "a[aria-label=Prev]", "a[rel=prev]", "a:contains(Sebelumnya)")
        val nextLink = firstMatch(doc, "a[aria-label=Next]", "a[rel=next]", "a:contains(Selanjutnya)")

        return ChapterPage(
            mangaTitle = mangaTitle, chapterTitle = chapterTitle,
            imageUrls = imageUrls,
            prevChapterUrl = prevLink?.attr("href")?.let(::toPath)?.takeIf { it.isNotEmpty() },
            nextChapterUrl = nextLink?.attr("href")?.let(::toPath)?.takeIf { it.isNotEmpty() }
        )
    }

    // A comma-joined selector returns matches in document order; this tries selectors by priority instead.
    private fun firstMatch(doc: Document, vararg selectors: String): org.jsoup.nodes.Element? =
        selectors.firstNotNullOfOrNull { doc.selectFirst(it) }

    internal fun parseHomeRanking(doc: Document): List<MangaItem> {
        val els = doc.select("#rank-harian .ls4").ifEmpty { doc.select("#Rekomendasi_Komik .ls4") }
        return els.mapNotNull { homeCard(it, "h4 a", "a.ls24") }.distinctBy { it.slug }
    }

    internal fun parseHomeLatest(doc: Document): List<MangaItem> =
        doc.select("#Terbaru article").mapNotNull { homeCard(it, "h3 a", "a.ls2l") }
            .distinctBy { it.slug }

    private fun homeCard(el: org.jsoup.nodes.Element, titleSel: String, chapterSel: String): MangaItem? {
        val link = el.selectFirst(titleSel) ?: return null
        val slug = extractSlug(link.attr("href"))
        val title = link.text().trim()
        if (slug.isEmpty() || title.isEmpty()) return null
        val img = el.selectFirst("img")
        val chapter = el.selectFirst(chapterSel)?.text()?.trim().orEmpty()
        val updated = el.selectFirst(".ls2t")?.text()?.substringAfter("·", "")?.trim().orEmpty()
        return MangaItem(
            title = title,
            slug = slug,
            thumbnailUrl = img?.attr("data-src")?.ifEmpty { null } ?: img?.attr("src").orEmpty(),
            latestChapter = listOf(chapter, updated).filter { it.isNotEmpty() }.joinToString(" · ")
        )
    }

    internal fun parseMangaFromDoc(doc: Document): List<MangaItem> {
        val items = mutableListOf<MangaItem>()
        val seen = mutableSetOf<String>()

        // Strategy 1: h4 links (daftar-komik format)
        doc.select("h4 a[href*=/manga/], h3 a[href*=/manga/]").forEach { link ->
            addIfNew(link, seen, items)
        }

        // Strategy 2: div.bge blocks (search/list format)
        if (items.isEmpty()) {
            doc.select("div.bge, article").forEach { block ->
                // Prefer the title link; the first link wraps the cover and an "Up N" badge.
                val link = block.selectFirst("a[href*=/manga/]:has(h3), a[href*=/manga/]:has(h4)")
                    ?: block.selectFirst("a[href*=/manga/]")
                link?.let { addIfNew(it, seen, items, type = block.select(".tpe1_inf b").text().trim()) }
            }
        }

        // Strategy 3: ls4 articles (home ranking format)
        if (items.isEmpty()) {
            doc.select(".ls4 a[href*=/manga/], .rank-panel a[href*=/manga/]").forEach { link ->
                addIfNew(link, seen, items)
            }
        }

        // Strategy 4: any manga link (last resort)
        if (items.isEmpty()) {
            doc.select("a[href*=/manga/]").forEach { link ->
                val href = link.attr("href").trim()
                val slug = extractSlug(href)
                if (slug.isEmpty() || slug in seen) return@forEach
                seen.add(slug)
                val title = link.text().trim()
                if (title.isNotEmpty() && title.length < 100) {
                    items.add(MangaItem(title = title, slug = slug, thumbnailUrl = ""))
                }
            }
        }

        return items
    }

    private fun addIfNew(link: org.jsoup.nodes.Element, seen: MutableSet<String>, items: MutableList<MangaItem>, type: String = "") {
        val href = link.attr("href").trim()
        val slug = extractSlug(href)
        if (slug.isEmpty() || slug in seen) return
        seen.add(slug)

        val title = link.select("h4,h3").text().trim()
            .ifEmpty { link.ownText().trim() }
            .ifEmpty { link.attr("title").trim().removePrefix("Baca ").removePrefix("Komik ").trim() }
            .ifEmpty { link.text().trim() }
        if (title.isEmpty() || title.length > 100) return

        // Find thumbnail in parent hierarchy
        var parent = link.parent()
        var thumb = ""
        for (i in 1..5) {
            parent?.select("img")?.firstOrNull()?.let { img ->
                thumb = img.attr("data-src").ifEmpty { img.attr("src") }
            }
            if (thumb.isNotEmpty()) break
            parent = parent?.parent()
        }

        items.add(MangaItem(title = title, slug = slug, thumbnailUrl = thumb, type = type))
    }

    internal fun parseSearchResults(doc: Document): List<SearchResult> {
        val results = mutableListOf<SearchResult>()
        val seenSlugs = mutableSetOf<String>()

        doc.select("div.bge").forEach { block ->
            val chapterLink = block.select("a[href*=-chapter-]").firstOrNull() ?: return@forEach
            val chapterHref = chapterLink.attr("href")
            val slug = extractMangaSlugFromChapter(chapterHref)
            if (slug.isEmpty() || slug in seenSlugs) return@forEach
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
        return results
    }

    // ==================== HELPERS ====================

    // Keeps chapter URLs relative ("/x-chapter-1/") so history keys match the detail page's links.
    private fun toPath(href: String): String =
        href.trim().replace(Regex("^https?://[^/]+"), "")

    private fun extractSlug(href: String): String =
        Regex("/manga/([^/?#]+)").find(href)?.groupValues?.get(1)?.trim() ?: ""

    private fun extractMangaSlugFromChapter(chapterHref: String): String {
        val path = toPath(chapterHref).removePrefix("/").removeSuffix("/")
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
        return lower.contains("komiku.") && lower.contains("/upload") &&
                (lower.endsWith(".webp") || lower.endsWith(".jpg") ||
                 lower.endsWith(".jpeg") || lower.endsWith(".png"))
    }
}
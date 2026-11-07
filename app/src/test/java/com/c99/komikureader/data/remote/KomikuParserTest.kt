package com.c99.komikureader.data.remote

import org.jsoup.Jsoup
import org.junit.Assert.*
import org.junit.Test

/**
 * Fixtures mirror the markup komiku.org served in Nov 2026. If the site changes its
 * layout these fail in CI instead of users seeing empty screens.
 */
class KomikuParserTest {

    private val api = KomikuApi()
    private fun doc(html: String) = Jsoup.parse(html, "https://komiku.org/")

    @Test
    fun `home list uses link text, not the title attribute`() {
        val items = api.parseMangaFromDoc(doc("""
            <div class="ls4"><div class="ls4v"><a href="/manga/ao-ashi/"><img src="https://thumbnail.komiku.to/ao.jpg"></a></div>
            <div class="ls4j"><h4><a href="/manga/ao-ashi/" title="Baca Komik Ao Ashi">Ao Ashi</a></h4>
            <a href="/ao-ashi-chapter-410/" class="ls24">Chapter 410</a></div></div>
        """))
        assertEquals(1, items.size)
        assertEquals("Ao Ashi", items[0].title)
        assertEquals("ao-ashi", items[0].slug)
        assertEquals("https://thumbnail.komiku.to/ao.jpg", items[0].thumbnailUrl)
    }

    @Test
    fun `type listing takes the h3 title and type, not the Up badge`() {
        val items = api.parseMangaFromDoc(doc("""
            <div class="bge"><div class="bgei"><a href="https://komiku.org/manga/fork-knife/">
              <img src="https://thumbnail.komiku.org/fk.png"><div class="tpe1_inf"><b>Manhwa</b> Cooking</div>
              <span class="up">Up 19</span></a></div>
            <div class="kan"><a href="https://komiku.org/manga/fork-knife/"><h3>Fork &amp; Knife</h3></a></div></div>
        """))
        assertEquals(1, items.size)
        assertEquals("Fork & Knife", items[0].title)
        assertEquals("fork-knife", items[0].slug)
        assertEquals("Manhwa", items[0].type)
    }

    @Test
    fun `search results resolve the manga slug from chapter links`() {
        val results = api.parseSearchResults(doc("""
            <div class="bge"><div class="kan"><h3>One Piece</h3><p>Update 7 hari lalu.</p>
            <div class="new1"><a href="/one-piece-chapter-1/"><span>Awal: </span><span>Chapter 1</span></a></div>
            <div class="new1"><a href="/one-piece-chapter-1197/"><span>Terbaru: </span><span>Chapter 1197</span></a></div>
            </div></div>
        """))
        assertEquals(1, results.size)
        assertEquals("one-piece", results[0].mangaSlug)
        assertEquals("One Piece", results[0].displayTitle)
        assertEquals("/one-piece-chapter-1197/", results[0].latestChapterUrl)
    }

    @Test
    fun `detail reads info table, synopsis and chapters`() {
        val detail = api.parseMangaDetail(doc("""
            <head><meta property="og:image" content="https://thumbnail.komiku.to/ao-cover.jpg"></head>
            <h1>Komik Ao Ashi</h1>
            <table class="inftable">
              <tr><td>Judul</td><td>Ao Ashi</td></tr>
              <tr><td>Judul Alternatif</td><td>Ao Ashi: Pemain Muda</td></tr>
              <tr><td>Tipe</td><td>Manga</td></tr>
              <tr><td>Status</td><td>Ongoing</td></tr>
            </table>
            <p class="desc">Ashito Aoi bermimpi jadi pemain bola.</p>
            <article><p>Paragraf SEO yang tidak boleh ikut.</p></article>
            <table id="Daftar_Chapter">
              <tr itemprop="itemListElement"><td><a itemprop="url" href="https://komiku.org/ao-ashi-chapter-2/"><span itemprop="name">Chapter 2</span></a></td><td class="tanggalseries">02/11/2026</td></tr>
              <tr itemprop="itemListElement"><td><a itemprop="url" href="/ao-ashi-chapter-1/"><span itemprop="name">Chapter 1</span></a></td><td class="tanggalseries">01/11/2026</td></tr>
              <tr itemprop="itemListElement"><td><a itemprop="url" href="/ao-ashi-chapter-1/"><span itemprop="name">Chapter 1</span></a></td><td class="tanggalseries">01/11/2026</td></tr>
            </table>
        """), "ao-ashi")!!
        assertEquals("Ao Ashi", detail.title)
        assertEquals("Ao Ashi: Pemain Muda", detail.alternativeTitle)
        assertEquals("Manga", detail.type)
        assertEquals("Ongoing", detail.status)
        assertEquals("Ashito Aoi bermimpi jadi pemain bola.", detail.synopsis)
        assertEquals("https://thumbnail.komiku.to/ao-cover.jpg", detail.thumbnailUrl)
        // Absolute links are normalised to paths and duplicates dropped.
        assertEquals(listOf("/ao-ashi-chapter-2/", "/ao-ashi-chapter-1/"), detail.chapters.map { it.url })
        assertEquals("02/11/2026", detail.chapters[0].date)
    }

    @Test
    fun `chapter page skips promo banners and finds icon-only prev and next`() {
        val page = api.parseChapterPage(doc("""
            <head><meta property="og:title" content="Ao Ashi Chapter 2"></head>
            <h1>Ao Ashi Chapter 2</h1>
            <a href="#">Next</a>
            <div id="Baca_Komik">
              <div><img src="https://img.komiku.org/komiku-promosi.webp"></div>
              <div id="1"><img src="https://img.komiku.org/uploads2/2558985-1.jpg"></div>
              <div id="2"><img src="https://img.komiku.org/uploads2/2558985-2.jpg"></div>
            </div>
            <a href="https://komiku.org/ao-ashi-chapter-1/" class="btn" aria-label="Prev"><svg></svg></a>
            <a href="https://komiku.org/ao-ashi-chapter-3/" class="btn" aria-label="Next"><svg></svg></a>
            <img src="/asset/img/komikuplus2.jpg">
        """))
        assertEquals("Ao Ashi", page.mangaTitle)
        assertEquals("Ao Ashi Chapter 2", page.chapterTitle)
        assertEquals(
            listOf("https://img.komiku.org/uploads2/2558985-1.jpg", "https://img.komiku.org/uploads2/2558985-2.jpg"),
            page.imageUrls
        )
        assertEquals("/ao-ashi-chapter-1/", page.prevChapterUrl)
        assertEquals("/ao-ashi-chapter-3/", page.nextChapterUrl)
    }

    @Test
    fun `first chapter has no prev link`() {
        val page = api.parseChapterPage(doc("""
            <div id="Baca_Komik"><img src="https://img.komiku.org/uploads2/1-1.jpg"></div>
            <a href="https://komiku.org/x-chapter-2/" aria-label="Next"></a>
        """))
        assertNull(page.prevChapterUrl)
        assertEquals("/x-chapter-2/", page.nextChapterUrl)
    }

    @Test
    fun `home latest feed comes from the Terbaru section with chapter and time`() {
        val home = doc("""
            <section id="Rekomendasi_Komik"><div id="rank-harian">
              <div class="ls4"><img data-src="https://t/op.jpg"><div class="ls4j"><h4><a href="/manga/komik-one-piece-indo/">One Piece</a></h4>
              <span class="ls4s">Fantasi · 18,188 views</span><a href="/one-piece-chapter-1198/" class="ls24">Chapter 1198</a></div></div>
            </div><div id="rank-mingguan">
              <div class="ls4"><div class="ls4j"><h4><a href="/manga/ao-ashi/">Ao Ashi</a></h4></div></div>
            </div></section>
            <section id="Terbaru">
              <article class="ls2"><div class="ls2v"><a href="/manga/guard-pass/"><img src="lazy.jpg" data-src="https://t/gp.png"></a></div>
              <div class="ls2j"><h3><a href="/manga/guard-pass/" title="Baca Manhwa Guard Pass">Guard Pass</a></h3>
              <span class="ls2t">Olahraga · 3 menit lalu</span><a href="/guard-pass-chapter-40-2/" class="ls2l">Chapter 40.2</a></div></article>
            </section>
            <section id="Komik_Isekai"><h3><a href="/manga/isekai-x/">Isekai X</a></h3></section>
        """)
        val ranking = api.parseHomeRanking(home)
        assertEquals(listOf("komik-one-piece-indo"), ranking.map { it.slug })

        val latest = api.parseHomeLatest(home)
        assertEquals(1, latest.size)
        assertEquals("Guard Pass", latest[0].title)
        assertEquals("https://t/gp.png", latest[0].thumbnailUrl)
        assertEquals("Chapter 40.2 · 3 menit lalu", latest[0].latestChapter)
    }

    @Test
    fun `title search uses the manga link slug, not the chapter url`() {
        val results = api.parseSearchResults(doc("""
            <div class="bge"><div class="bgei"><a href="/manga/komik-one-piece-indo/"><img src="https://t/op.jpg"></a></div>
            <div class="kan"><a href="/manga/komik-one-piece-indo/"><h3>One Piece</h3></a><p>Update 2 hari lalu.</p>
            <div class="new1"><a href="/one-piece-chapter-1/"><span>Awal: </span><span>Chapter 1</span></a></div>
            <div class="new1"><a href="/one-piece-chapter-1198/"><span>Terbaru: </span><span>Chapter 1198</span></a></div>
            </div></div>
        """))
        assertEquals("komik-one-piece-indo", results.single().mangaSlug)
        assertEquals("One Piece", results.single().displayTitle)
    }
}

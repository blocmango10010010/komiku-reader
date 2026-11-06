// Server-side scraper for komiku.org, ported from the Android app's KomikuApi.kt.
// Runs only on the server: the site doesn't allow cross-origin requests from browsers.
import "server-only";
import * as cheerio from "cheerio";
import type { CheerioAPI } from "cheerio";
import type { AnyNode, Element } from "domhandler";

const BASE = "https://komiku.org";
const API = "https://api.komiku.org";
const UA =
  "Mozilla/5.0 (Linux; Android 13; Pixel 7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36";

export type MangaItem = { title: string; slug: string; thumbnailUrl: string; type: string };
export type ChapterItem = { title: string; url: string; date: string };
export type MangaDetail = {
  title: string;
  slug: string;
  alternativeTitle: string;
  type: string;
  genre: string;
  author: string;
  status: string;
  synopsis: string;
  thumbnailUrl: string;
  chapters: ChapterItem[];
};
export type ChapterPage = {
  mangaSlug: string;
  mangaTitle: string;
  chapterTitle: string;
  imageUrls: string[];
  prevChapterUrl: string | null;
  nextChapterUrl: string | null;
};
export type SearchResult = {
  mangaSlug: string;
  displayTitle: string;
  latestChapterUrl: string | null;
  updated: string;
};

export const MANGA_TYPES = ["manga", "manhwa", "manhua"] as const;
const SAFE_SEGMENT = /^[a-z0-9][a-z0-9-]*$/i;

const sleep = (ms: number) => new Promise((r) => setTimeout(r, ms));

async function fetchDoc(url: string, revalidate: number): Promise<CheerioAPI | null> {
  for (let attempt = 1; attempt <= 2; attempt++) {
    try {
      const res = await fetch(url, {
        headers: {
          "User-Agent": UA,
          Accept: "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8",
          "Accept-Language": "id-ID,id;q=0.9,en;q=0.8",
        },
        next: { revalidate },
      });
      if (res.status === 404) return null;
      const body = await res.text();
      if (res.status === 503 || /ddos-guard/i.test(body)) {
        await sleep(1000 * attempt);
        continue;
      }
      if (!res.ok || !body.trim()) return null;
      return cheerio.load(body, { baseURI: url });
    } catch {
      if (attempt < 2) await sleep(1000 * attempt);
    }
  }
  return null;
}

// ---------- helpers ----------

/** Keeps chapter URLs relative ("/x-chapter-1/") so history keys match across pages. */
export function toPath(href: string): string {
  return href.trim().replace(/^https?:\/\/[^/]+/, "");
}

function extractSlug(href: string): string {
  return /\/manga\/([^/?#]+)/.exec(href)?.[1]?.trim() ?? "";
}

export function mangaSlugFromChapter(chapterHref: string): string {
  const path = toPath(chapterHref).replace(/^\/+|\/+$/g, "");
  return /^(.+)-chapter-\d+/.exec(path)?.[1] ?? "";
}

/** "/ao-ashi-chapter-2/" -> "ao-ashi-chapter-2", the reader route segment. */
export function chapterSegment(chapterUrl: string): string {
  return toPath(chapterUrl).replace(/^\/+|\/+$/g, "");
}

function slugToTitle(slug: string): string {
  return slug
    .split("-")
    .map((w) => (w ? w[0].toUpperCase() + w.slice(1) : w))
    .join(" ");
}

function isMangaImage(src: string): boolean {
  const lower = src.toLowerCase();
  return (
    lower.includes("komiku.") &&
    lower.includes("/upload") &&
    /\.(webp|jpe?g|png)$/.test(lower)
  );
}

function ownText($: CheerioAPI, el: AnyNode): string {
  return $(el)
    .contents()
    .filter((_, n) => n.type === "text")
    .text()
    .trim();
}

// ---------- parsers (exported for tests) ----------

export function parseMangaList($: CheerioAPI): MangaItem[] {
  const items: MangaItem[] = [];
  const seen = new Set<string>();

  const add = (link: Element, type = "") => {
    const $l = $(link);
    const slug = extractSlug($l.attr("href") ?? "");
    if (!slug || seen.has(slug)) return;

    const title =
      $l.find("h4,h3").text().trim() ||
      ownText($, link) ||
      ($l.attr("title") ?? "").trim().replace(/^Baca /, "").replace(/^Komik /, "").trim() ||
      $l.text().trim();
    if (!title || title.length > 100) return;
    seen.add(slug);

    // The cover image sits in a sibling of the title link, a few levels up.
    let thumb = "";
    let parent = $l.parent();
    for (let i = 0; i < 5 && parent.length && !thumb; i++) {
      const img = parent.find("img").first();
      thumb = img.attr("data-src") || img.attr("src") || "";
      parent = parent.parent();
    }
    items.push({ title, slug, thumbnailUrl: thumb, type });
  };

  // Home and /daftar-komik/: title links inside headings.
  $("h4 a[href*='/manga/'], h3 a[href*='/manga/']").each((_, el) => add(el));

  // API listings and search: div.bge cards. The first link wraps the cover and an "Up N" badge.
  if (!items.length) {
    $("div.bge, article").each((_, block) => {
      const $b = $(block);
      let link = $b.find("a[href*='/manga/']:has(h3), a[href*='/manga/']:has(h4)").first();
      if (!link.length) link = $b.find("a[href*='/manga/']").first();
      if (link.length) add(link[0], $b.find(".tpe1_inf b").text().trim());
    });
  }

  if (!items.length) {
    $(".ls4 a[href*='/manga/'], .rank-panel a[href*='/manga/']").each((_, el) => add(el));
  }
  return items;
}

export function parseSearchResults($: CheerioAPI): SearchResult[] {
  const results: SearchResult[] = [];
  const seen = new Set<string>();
  $("div.bge").each((_, block) => {
    const $b = $(block);
    const chapterHref = $b.find("a[href*='-chapter-']").first().attr("href");
    if (!chapterHref) return;
    const slug = mangaSlugFromChapter(chapterHref);
    if (!slug || seen.has(slug)) return;
    seen.add(slug);

    const kan = $b.find("div.kan").first();
    const h3 = kan.find("h3").text().trim();
    const title = !h3 || h3 === "Untitled" ? slugToTitle(slug) : h3;
    const latest = $b.find("div.new1").last().find("a").last().attr("href");
    results.push({
      mangaSlug: slug,
      displayTitle: title,
      latestChapterUrl: latest ? toPath(latest) : null,
      updated: kan.find("p").first().text().trim(),
    });
  });
  return results;
}

export function parseMangaDetail($: CheerioAPI, slug: string): MangaDetail | null {
  const strip = (s: string) => s.replace(" - Komiku", "").replace(/^Komik /, "").trim();
  let title = strip($("h1").first().text()) || strip($("title").text());
  if (!title) return null;

  const info: Record<string, string> = {};
  $("table.inftable tr").each((_, row) => {
    const cells = $(row).find("td");
    if (cells.length >= 2) {
      info[$(cells[0]).text().trim().toLowerCase()] = $(cells[1]).text().trim();
    }
  });
  const pick = (pred: (label: string) => boolean) =>
    Object.entries(info).find(([k]) => pred(k))?.[1] ?? "";
  if (info["judul"]) title = info["judul"];

  const synopsis =
    $("p.desc").text().trim() ||
    $("section#Sinopsis p").text().trim() ||
    ($("meta[name=description]").attr("content") ?? "").trim();

  const thumbnail =
    $("meta[property='og:image']").attr("content") ||
    $("img[src*='/uploads/manga/']").first().attr("src") ||
    "";

  const chapters: ChapterItem[] = [];
  const seen = new Set<string>();
  $("table#Daftar_Chapter tr[itemprop=itemListElement]").each((_, row) => {
    const link = $(row).find("a[itemprop=url]").first();
    if (!link.length) return;
    const chapTitle = link.find("span[itemprop=name]").text().trim() || link.text().trim();
    const url = toPath(link.attr("href") ?? "");
    if (!chapTitle || !url || seen.has(url)) return;
    seen.add(url);
    chapters.push({ title: chapTitle, url, date: $(row).find("td.tanggalseries").text().trim() });
  });

  return {
    title,
    slug,
    alternativeTitle: pick((l) => l.includes("alternatif")),
    type: pick((l) => l.includes("tipe")),
    genre: pick((l) => l.includes("genre")),
    author: pick((l) => l.includes("author") || l.includes("pengarang")),
    status: pick((l) => l.includes("status")),
    synopsis,
    thumbnailUrl: thumbnail,
    chapters,
  };
}

export function parseChapterPage($: CheerioAPI, chapterUrl: string): ChapterPage {
  const chapterTitle =
    $("h1").first().text().trim() || $("title").text().replace(" - Komiku", "").trim();
  const ogTitle = $("meta[property='og:title']").attr("content") ?? "";
  const mangaTitle = ogTitle.split(" Chapter")[0].trim();

  let pageImages = $("#Baca_Komik img");
  if (!pageImages.length) pageImages = $("img");
  const imageUrls: string[] = [];
  pageImages.each((_, img) => {
    const src = ($(img).attr("src") ?? "").trim();
    if (src && isMangaImage(src)) imageUrls.push(src);
  });

  // Nav buttons are icon-only, identified by aria-label. Selectors are tried in priority
  // order because a comma-joined selector returns matches in document order.
  const firstHref = (...selectors: string[]) => {
    for (const s of selectors) {
      const href = $(s).first().attr("href");
      if (href) return toPath(href);
    }
    return null;
  };

  return {
    mangaSlug: mangaSlugFromChapter(chapterUrl),
    mangaTitle,
    chapterTitle,
    imageUrls,
    prevChapterUrl: firstHref("a[aria-label=Prev]", "a[rel=prev]", "a:contains(Sebelumnya)"),
    nextChapterUrl: firstHref("a[aria-label=Next]", "a[rel=next]", "a:contains(Selanjutnya)"),
  };
}

// ---------- data access ----------

const LIST_TTL = 600;
const DETAIL_TTL = 300;
const CHAPTER_TTL = 1800;

export async function getHome(): Promise<{ ranking: MangaItem[]; latest: MangaItem[] } | null> {
  const [home, daftar] = await Promise.all([
    fetchDoc(`${BASE}/`, LIST_TTL),
    fetchDoc(`${BASE}/daftar-komik/`, LIST_TTL),
  ]);
  if (!home && !daftar) return null;
  return {
    ranking: home ? parseMangaList(home).slice(0, 15) : [],
    latest: daftar ? parseMangaList(daftar).slice(0, 30) : [],
  };
}

/** Returns null when the site is unreachable, [] past the last page. */
export async function getMangaList(page: number, type: string): Promise<MangaItem[] | null> {
  // /pustaka/ fills its list via JavaScript, so the type filter uses the API listing.
  const url = (MANGA_TYPES as readonly string[]).includes(type)
    ? `${API}/manga/${page > 1 ? `page/${page}/` : ""}?tipe=${type}`
    : page > 1
      ? `${BASE}/daftar-komik/page/${page}/`
      : `${BASE}/daftar-komik/`;
  const $ = await fetchDoc(url, LIST_TTL);
  return $ ? parseMangaList($) : page > 1 ? [] : null;
}

export async function search(query: string): Promise<SearchResult[]> {
  const q = encodeURIComponent(query);
  for (const url of [`${API}/?s=${q}`, `${BASE}/?s=${q}&post_type=manga`]) {
    const $ = await fetchDoc(url, DETAIL_TTL);
    const results = $ ? parseSearchResults($) : [];
    if (results.length) return results;
  }
  return [];
}

export async function getMangaDetail(slug: string): Promise<MangaDetail | null> {
  if (!SAFE_SEGMENT.test(slug)) return null;
  const $ = await fetchDoc(`${BASE}/manga/${slug}/`, DETAIL_TTL);
  return $ ? parseMangaDetail($, slug) : null;
}

export async function getChapter(segment: string): Promise<ChapterPage | null> {
  if (!SAFE_SEGMENT.test(segment)) return null;
  const path = `/${segment}/`;
  const $ = await fetchDoc(`${BASE}${path}`, CHAPTER_TTL);
  return $ ? parseChapterPage($, path) : null;
}

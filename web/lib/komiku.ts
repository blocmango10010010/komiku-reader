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

export type ChapterRef = { title: string; url: string };
export type MangaItem = {
  title: string;
  slug: string;
  thumbnailUrl: string;
  type: string;
  latestChapter?: ChapterRef;
  /** Relative time from the site, e.g. "3 menit lalu". */
  updated?: string;
};
export type ChapterItem = { title: string; url: string; date: string };
export type MangaDetail = {
  title: string;
  slug: string;
  alternativeTitle: string;
  type: string;
  genre: string;
  genres: string[];
  author: string;
  status: string;
  synopsis: string;
  thumbnailUrl: string;
  chapters: ChapterItem[];
};
export type ChapterPage = {
  /** Decoded path, e.g. "/ao-ashi-chapter-2/"; matches ChapterItem.url for history lookups. */
  url: string;
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
  thumbnailUrl: string;
  type: string;
  latestChapterUrl: string | null;
  latestChapterTitle: string;
  updated: string;
};

export const MANGA_TYPES = ["manga", "manhwa", "manhua"] as const;
export const SORTS = ["update", "az"] as const;
export type Sort = (typeof SORTS)[number];

function safeDecode(s: string): string {
  try {
    return decodeURIComponent(s);
  } catch {
    return s;
  }
}

/**
 * Slugs are kept decoded internally (some contain non-ASCII, e.g. "tokyo卍revengers") and
 * encoded only when requesting komiku. Anything that could change the request path is rejected.
 */
function encodeSegment(segment: string): string | null {
  const s = safeDecode(segment).trim();
  if (!s || s.startsWith(".") || /[\/\\?#\s]/.test(s)) return null;
  return encodeURIComponent(s);
}

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
  return safeDecode(href.trim().replace(/^https?:\/\/[^/]+/, ""));
}

function extractSlug(href: string): string {
  return safeDecode(/\/manga\/([^/?#]+)/.exec(href)?.[1]?.trim() ?? "");
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

  const add = (link: Element, type = "", extra: Pick<MangaItem, "latestChapter" | "updated"> = {}) => {
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
    items.push({ title, slug, thumbnailUrl: thumb, type, ...extra });
  };

  // Home and /daftar-komik/: title links inside headings.
  $("h4 a[href*='/manga/'], h3 a[href*='/manga/']").each((_, el) => add(el));

  // API listings and search: div.bge cards. The first link wraps the cover and an "Up N" badge.
  if (!items.length) {
    $("div.bge, article").each((_, block) => {
      const $b = $(block);
      let link = $b.find("a[href*='/manga/']:has(h3), a[href*='/manga/']:has(h4)").first();
      if (!link.length) link = $b.find("a[href*='/manga/']").first();
      const latest = $b.find("div.new1").last().find("a").last();
      const href = latest.attr("href");
      const updated = $b.find(".judul2").text().split("|")[1]?.trim();
      if (link.length)
        add(link[0], $b.find(".tpe1_inf b").text().trim(), {
          latestChapter: href
            ? { title: latest.find("span").last().text().trim() || latest.text().trim(), url: toPath(href) }
            : undefined,
          updated: updated || undefined,
        });
    });
  }

  if (!items.length) {
    $(".ls4 a[href*='/manga/'], .rank-panel a[href*='/manga/']").each((_, el) => add(el));
  }
  return items;
}

/** Home page: today's ranking and the "Baca Komik Terbaru" (latest updates) feed. */
export function parseHome($: CheerioAPI): { ranking: MangaItem[]; latest: MangaItem[] } {
  const card = (el: Element, titleSel: string, chapterSel: string, metaSel: string, sep: string): MangaItem | null => {
    const $e = $(el);
    const link = $e.find(titleSel).first();
    const slug = extractSlug(link.attr("href") ?? "");
    const title = link.text().trim();
    if (!slug || !title) return null;
    const img = $e.find("img").first();
    const chapter = $e.find(chapterSel).first();
    const href = chapter.attr("href");
    return {
      title,
      slug,
      thumbnailUrl: img.attr("data-src") || img.attr("src") || "",
      type: "",
      latestChapter: href ? { title: chapter.text().trim(), url: toPath(href) } : undefined,
      updated: $e.find(metaSel).text().split(sep)[1]?.trim() || undefined,
    };
  };
  const unique = (items: (MangaItem | null)[]) => {
    const seen = new Set<string>();
    return items.filter((i): i is MangaItem => !!i && !seen.has(i.slug) && !!seen.add(i.slug));
  };

  let rankingEls = $("#rank-harian .ls4").toArray();
  if (!rankingEls.length) rankingEls = $("#Rekomendasi_Komik .ls4").toArray();
  return {
    ranking: unique(rankingEls.map((el) => card(el, "h4 a", "a.ls24", ".ls4s", "·"))),
    latest: unique($("#Terbaru article").toArray().map((el) => card(el, "h3 a", "a.ls2l", ".ls2t", "·"))),
  };
}

export function parseSearchResults($: CheerioAPI): SearchResult[] {
  const results: SearchResult[] = [];
  const seen = new Set<string>();
  $("div.bge").each((_, block) => {
    const $b = $(block);
    // Title search links to /manga/<slug>/; the generic search only links chapters.
    const mangaHref = $b.find("a[href*='/manga/']").first().attr("href");
    const chapterHref = $b.find("a[href*='-chapter-']").first().attr("href");
    const slug = mangaHref ? extractSlug(mangaHref) : chapterHref ? mangaSlugFromChapter(chapterHref) : "";
    if (!slug || seen.has(slug)) return;
    seen.add(slug);

    const kan = $b.find("div.kan").first();
    const h3 = kan.find("h3").text().trim();
    const title = !h3 || h3 === "Untitled" ? slugToTitle(slug) : h3;
    const latest = $b.find("div.new1").last().find("a").last();
    const latestHref = latest.attr("href");
    const img = $b.find("img").first();
    results.push({
      mangaSlug: slug,
      displayTitle: title,
      thumbnailUrl: img.attr("data-src") || img.attr("src") || "",
      type: $b.find(".tpe1_inf b").text().trim(),
      latestChapterUrl: latestHref ? toPath(latestHref) : null,
      latestChapterTitle: latest.find("span").last().text().trim(),
      updated: kan.find("p").first().text().trim().replace(/\.$/, ""),
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
    genres: $("table.inftable li.genre").map((_, el) => $(el).text().trim()).get().filter(Boolean),
    author: pick((l) => l.includes("author") || l.includes("pengarang")),
    status: pick((l) => l.includes("status")),
    synopsis,
    thumbnailUrl: thumbnail,
    chapters,
  };
}

/**
 * The manga's real slug, read from the chapter page's links to its detail page. Guessing it
 * from the chapter URL breaks for titles like One Piece ("/one-piece-chapter-1/" belongs to
 * "/manga/komik-one-piece-indo/").
 */
export function mangaSlugFromChapterPage($: CheerioAPI): string {
  const counts = new Map<string, number>();
  $("a[href*='/manga/']").each((_, a) => {
    const slug = extractSlug($(a).attr("href") ?? "");
    if (slug) counts.set(slug, (counts.get(slug) ?? 0) + 1);
  });
  let best = "";
  for (const [slug, n] of counts) if (n > (counts.get(best) ?? 0)) best = slug;
  return best;
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
    url: toPath(chapterUrl),
    mangaSlug: mangaSlugFromChapterPage($) || mangaSlugFromChapter(chapterUrl),
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

// The home feed changes every few minutes, so it is refreshed more often than other lists.
const HOME_TTL = 120;

export async function getHome(): Promise<{ ranking: MangaItem[]; latest: MangaItem[] } | null> {
  const $ = await fetchDoc(`${BASE}/`, HOME_TTL);
  return $ ? parseHome($) : null;
}

/** Returns null when the site is unreachable, [] past the last page. */
export async function getMangaList(page: number, type: string, sort: Sort = "update"): Promise<MangaItem[] | null> {
  const hasType = (MANGA_TYPES as readonly string[]).includes(type);
  const pagePath = page > 1 ? `page/${page}/` : "";
  // /pustaka/ fills its list via JavaScript, so sorting and type filters use the API listing.
  // A–Z without a type keeps /daftar-komik/, which has 50 titles per page instead of 10.
  let url: string;
  if (sort === "az" && !hasType) {
    url = `${BASE}/daftar-komik/${pagePath}`;
  } else {
    const q = new URLSearchParams(sort === "az" ? { orderby: "title", order: "asc" } : { orderby: "modified" });
    if (hasType) q.set("tipe", type);
    url = `${API}/manga/${pagePath}?${q}`;
  }
  const $ = await fetchDoc(url, sort === "update" ? HOME_TTL : LIST_TTL);
  return $ ? parseMangaList($) : page > 1 ? [] : null;
}

export async function search(query: string): Promise<SearchResult[]> {
  const q = encodeURIComponent(query);
  // Title search returns real slugs, covers and types. The generic search returns chapters,
  // so it is only a fallback, with each slug resolved from the chapter page.
  const [titles, general] = await Promise.all(
    [`${API}/?post_type=manga&s=${q}`, `${API}/?s=${q}`].map(async (url) => {
      const $ = await fetchDoc(url, DETAIL_TTL);
      return $ ? parseSearchResults($) : [];
    }),
  );
  if (titles.length) return titles;

  const resolved = await Promise.all(
    general.slice(0, 6).map(async (r) => {
      if (!r.latestChapterUrl) return null;
      const segment = chapterSegment(r.latestChapterUrl);
      const $ = await fetchDoc(`${BASE}/${encodeURIComponent(segment)}/`, CHAPTER_TTL);
      const slug = $ ? mangaSlugFromChapterPage($) : "";
      return slug ? { ...r, mangaSlug: slug } : null;
    }),
  );
  const seen = new Set<string>();
  return resolved.filter((r): r is SearchResult => !!r && !seen.has(r.mangaSlug) && !!seen.add(r.mangaSlug));
}

export async function getMangaDetail(slug: string): Promise<MangaDetail | null> {
  const encoded = encodeSegment(slug);
  if (!encoded) return null;
  const $ = await fetchDoc(`${BASE}/manga/${encoded}/`, DETAIL_TTL);
  return $ ? parseMangaDetail($, safeDecode(slug)) : null;
}

export async function getChapter(segment: string): Promise<ChapterPage | null> {
  const encoded = encodeSegment(segment);
  if (!encoded) return null;
  const $ = await fetchDoc(`${BASE}/${encoded}/`, CHAPTER_TTL);
  return $ ? parseChapterPage($, `/${safeDecode(segment)}/`) : null;
}

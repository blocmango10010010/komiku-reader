// Server-side scraper for mangaluv.com (Indonesian manga/manhwa/manhua, updated several times a day).
import "server-only";
import type { CheerioAPI } from "cheerio";
import { timeAgo } from "./format";
import { fetchDoc } from "./http";
import { parseRelative } from "./time";
import type { ChapterItem, ChapterPage, MangaDetail, MangaItem, SearchResult } from "./types";

const BASE = "https://www.mangaluv.com";

/** App-level refs: "ml~<slug>" for a series, "ml~<slug>~chapter-<id>" for a chapter. */
export const ML_PREFIX = "ml~";
const SEP = "~";
const SAFE = /^[a-z0-9][a-z0-9-]*$/i;

export const mlMangaRef = (slug: string) => `${ML_PREFIX}${slug}`;
export const mlChapterUrl = (slug: string, chapter: string) => `/${ML_PREFIX}${slug}${SEP}${chapter}/`;

/** "ml~slug~chapter-1" -> { slug, chapter }. Rejects anything that could change the request path. */
export function parseMlChapterRef(ref: string): { slug: string; chapter: string } | null {
  const [slug, chapter] = ref.slice(ML_PREFIX.length).split(SEP);
  return slug && chapter && SAFE.test(slug) && /^chapter-[a-z0-9-]+$/i.test(chapter) ? { slug, chapter } : null;
}

function seriesPath(href: string): { slug: string; chapter?: string } | null {
  const m = /\/series\/([^/?#]+)(?:\/(chapter-[^/?#]+))?/.exec(href);
  return m && SAFE.test(m[1]) ? { slug: m[1], chapter: m[2] } : null;
}

const TYPE_BY_FLAG: Record<string, string> = { KR: "Manhwa", JP: "Manga", CN: "Manhua" };

/** "9 jam" / "9 hours ago" -> "9 jam lalu", plus a sortable timestamp. */
function relative(raw: string): { updated?: string; updatedAt?: number } {
  const updatedAt = parseRelative(raw);
  return updatedAt ? { updated: timeAgo(updatedAt), updatedAt } : {};
}

// ---------- parsers (exported for tests) ----------

/** Series cards on /series, the home page and search results. */
export function parseMlList($: CheerioAPI): MangaItem[] {
  const items: MangaItem[] = [];
  const seen = new Set<string>();
  $("article").each((_, el) => {
    const $a = $(el);
    const link = $a
      .find("a[href*='/series/']")
      .filter((_, a) => !/\/chapter-/.test($(a).attr("href") ?? ""))
      .first();
    const ref = seriesPath(link.attr("href") ?? "");
    if (!ref || seen.has(ref.slug)) return;

    const img = $a.find("img").first();
    const title = (
      $a.find("h3").first().text() || (img.attr("alt") ?? "").replace(/^cover komik\s*/i, "")
    ).trim();
    if (!title) return;
    seen.add(ref.slug);

    // Search cards can list the first chapter before the latest; take the highest-numbered one.
    const chapters = $a.find("a[href*='/chapter-']");
    const chapterNumber = (i: number) => {
      const c = chapters.eq(i);
      return Number(/chapter\s*([\d.]+)/i.exec(c.attr("title") || c.text())?.[1] ?? -1);
    };
    let best = 0;
    for (let i = 1; i < chapters.length; i++) if (chapterNumber(i) > chapterNumber(best)) best = i;
    const chapter = chapters.eq(best);
    const chapterRef = seriesPath(chapter.attr("href") ?? "");
    const chapterTitle = (chapter.attr("title") || chapter.find("span").first().text()).trim();
    const flag = $a.find("[title=KR],[title=JP],[title=CN]").first().attr("title") ?? "";

    items.push({
      title,
      slug: mlMangaRef(ref.slug),
      thumbnailUrl: img.attr("src") || img.attr("data-src") || "",
      type: TYPE_BY_FLAG[flag] ?? "",
      latestChapter: chapterRef?.chapter
        ? { title: chapterTitle || "Chapter", url: mlChapterUrl(ref.slug, chapterRef.chapter) }
        : undefined,
      ...relative(chapter.find("span").last().text().trim()),
      source: "mangaluv",
    });
  });
  return items;
}

type ComicSeriesLd = {
  "@type"?: string;
  name?: string;
  description?: string;
  image?: string;
  genre?: string[] | string;
  alternateName?: string | string[];
  author?: { name?: string } | { name?: string }[];
};

function readComicSeries($: CheerioAPI): ComicSeriesLd | undefined {
  for (const el of $("script[type='application/ld+json']").toArray()) {
    try {
      const json = JSON.parse($(el).text());
      const nodes: ComicSeriesLd[] = Array.isArray(json) ? json : json["@graph"] ?? [json];
      const series = nodes.find((n) => n["@type"] === "ComicSeries");
      if (series) return series;
    } catch {
      // Malformed JSON-LD; fall back to the markup.
    }
  }
  return undefined;
}

export function parseMlDetail($: CheerioAPI, slug: string): MangaDetail | null {
  const ld = readComicSeries($);
  const title = $("h1").first().text().trim() || ld?.name?.trim() || "";
  if (!title) return null;

  const fact = (label: string) =>
    $("dt")
      .filter((_, e) => $(e).text().trim().toLowerCase() === label)
      .first()
      .next("dd")
      .text()
      .trim();
  const ldAuthor = Array.isArray(ld?.author) ? ld?.author[0]?.name : ld?.author?.name;
  const genres = ld?.genre
    ? [ld.genre].flat()
    : $("a[href*='/genre/']").map((_, a) => $(a).text().trim()).get().filter(Boolean);

  // Each chapter appears once in the list; "first/latest chapter" buttons reuse the same URLs.
  const byUrl = new Map<string, ChapterItem>();
  $(`a[href*='/series/${slug}/chapter-']`).each((_, a) => {
    const ref = seriesPath($(a).attr("href") ?? "");
    if (!ref?.chapter) return;
    const spans = $(a).find("span");
    const chapterTitle = spans.first().text().trim() || $(a).text().trim();
    if (!/chapter/i.test(chapterTitle)) return;
    const url = mlChapterUrl(slug, ref.chapter);
    if (byUrl.has(url)) return;
    const raw = spans.length > 1 ? spans.last().text().trim() : "";
    const ts = parseRelative(raw);
    byUrl.set(url, { title: chapterTitle, url, date: ts ? timeAgo(ts) : raw });
  });

  return {
    title,
    slug: mlMangaRef(slug),
    alternativeTitle: [ld?.alternateName ?? []].flat()[0] ?? "",
    type: fact("tipe"),
    genre: genres.join(", "),
    genres,
    author: fact("author") || ldAuthor || "",
    status: fact("status"),
    synopsis: ld?.description?.trim() || ($("meta[property='og:description']").attr("content") ?? "").trim(),
    thumbnailUrl: $("meta[property='og:image']").attr("content") || ld?.image || "",
    chapters: [...byUrl.values()],
    source: "mangaluv",
  };
}

export function parseMlChapter($: CheerioAPI, slug: string, chapter: string): ChapterPage {
  const heading = $("h1").first().text().replace(/\s+/g, " ").trim();
  const navHref = (pattern: RegExp) => {
    const a = $("a[href*='/chapter-']")
      .filter((_, el) => pattern.test($(el).text()))
      .first();
    const ref = seriesPath(a.attr("href") ?? "");
    return ref?.chapter ? mlChapterUrl(ref.slug, ref.chapter) : null;
  };
  return {
    url: mlChapterUrl(slug, chapter),
    mangaSlug: mlMangaRef(slug),
    mangaTitle: heading.replace(/\s*chapter\s+[\w.-]+\s*$/i, "").trim(),
    chapterTitle: heading,
    imageUrls: $(`img[src*='img.mangaluv.com/series/${slug}/']`)
      .map((_, img) => $(img).attr("src")?.trim())
      .get()
      .filter(Boolean),
    prevChapterUrl: navHref(/sebelumnya|prev/i),
    nextChapterUrl: navHref(/selanjutnya|berikutnya|next/i),
  };
}

// ---------- data access ----------

const LIST_TTL = 120;
const DETAIL_TTL = 300;
const CHAPTER_TTL = 1800;

/** /series is ordered by latest update; `category` filters by manga/manhwa/manhua. */
export async function mlList(page: number, type: string, sort: "update" | "az" = "update"): Promise<MangaItem[] | null> {
  const q = new URLSearchParams();
  if (type) q.set("category", type);
  if (sort === "az") q.set("sort", "title");
  if (page > 1) q.set("page", String(page));
  const qs = q.toString();
  const $ = await fetchDoc(`${BASE}/series${qs ? `?${qs}` : ""}`, LIST_TTL);
  if (!$) return page > 1 ? [] : null;
  const items = parseMlList($);
  // A list item knows its type only when the request was filtered by it.
  return type ? items.map((i) => ({ ...i, type: i.type || type[0].toUpperCase() + type.slice(1) })) : items;
}

export async function mlSearch(query: string): Promise<SearchResult[]> {
  const $ = await fetchDoc(`${BASE}/search?q=${encodeURIComponent(query)}`, DETAIL_TTL);
  if (!$) return [];
  return parseMlList($).map((i) => ({
    mangaSlug: i.slug,
    displayTitle: i.title,
    thumbnailUrl: i.thumbnailUrl,
    type: i.type,
    latestChapterUrl: i.latestChapter?.url ?? null,
    latestChapterTitle: i.latestChapter?.title ?? "",
    updated: i.updated ?? "",
    source: "mangaluv",
  }));
}

export async function mlDetail(ref: string): Promise<MangaDetail | null> {
  const slug = ref.slice(ML_PREFIX.length);
  if (!SAFE.test(slug)) return null;
  const $ = await fetchDoc(`${BASE}/series/${slug}`, DETAIL_TTL);
  return $ ? parseMlDetail($, slug) : null;
}

export async function mlChapter(ref: string): Promise<ChapterPage | null> {
  const parsed = parseMlChapterRef(ref);
  if (!parsed) return null;
  const $ = await fetchDoc(`${BASE}/series/${parsed.slug}/${parsed.chapter}`, CHAPTER_TTL);
  return $ ? parseMlChapter($, parsed.slug, parsed.chapter) : null;
}

// Combines all sources: dispatches by ref and merges lists so each title appears once.
import "server-only";
import * as komiku from "./komiku";
import { ML_PREFIX, mlChapter, mlDetail, mlList, mlSearch } from "./mangaluv";
import { parseRelative } from "./time";
import { titleKey } from "./titles";
import type { ChapterPage, MangaDetail, MangaItem, SearchResult, SourceId, SourceLink } from "./types";

function safeDecode(s: string): string {
  try {
    return decodeURIComponent(s);
  } catch {
    return s;
  }
}

export function sourceOf(ref: string): SourceId {
  return safeDecode(ref).startsWith(ML_PREFIX) ? "mangaluv" : "komiku";
}

const tagKomiku = <T extends MangaItem>(items: T[]): T[] =>
  items.map((i) => ({ ...i, source: "komiku" as const, updatedAt: i.updatedAt ?? parseRelative(i.updated) }));

/**
 * One entry per title. The most recently updated copy wins; the others are kept in `sources`
 * so the UI can offer them. Items without a time keep their original order.
 */
export function mergeItems(lists: MangaItem[][], sortByUpdate = true): MangaItem[] {
  const byKey = new Map<string, MangaItem>();
  for (const item of lists.flat()) {
    const key = titleKey(item.title);
    const link: SourceLink = { source: item.source ?? "komiku", slug: item.slug, latestChapter: item.latestChapter?.title };
    const existing = byKey.get(key);
    if (!existing) {
      byKey.set(key, { ...item, sources: [link] });
      continue;
    }
    const newer = (item.updatedAt ?? 0) > (existing.updatedAt ?? 0);
    const winner = newer ? item : existing;
    const other = newer ? existing : item;
    byKey.set(key, {
      ...winner,
      type: winner.type || other.type,
      thumbnailUrl: winner.thumbnailUrl || other.thumbnailUrl,
      sources: newer ? [link, ...(existing.sources ?? [])] : [...(existing.sources ?? []), link],
    });
  }
  const merged = [...byKey.values()];
  return sortByUpdate ? merged.sort((a, b) => (b.updatedAt ?? 0) - (a.updatedAt ?? 0)) : merged;
}

// ---------- lists ----------

export async function getHome(): Promise<{ ranking: MangaItem[]; latest: MangaItem[] } | null> {
  const [home, ml] = await Promise.all([komiku.getHome(), mlList(1, "")]);
  if (!home && !ml) return null;
  return {
    ranking: tagKomiku(home?.ranking ?? []),
    latest: mergeItems([tagKomiku(home?.latest ?? []), ml ?? []]),
  };
}

/** Latest updates across sources (page N of each), or komiku's A–Z list. */
export async function getMangaList(page: number, type: string, sort: komiku.Sort): Promise<MangaItem[] | null> {
  if (sort === "az") {
    const items = await komiku.getMangaList(page, type, "az");
    return items && tagKomiku(items).map((i) => ({ ...i, sources: [{ source: "komiku" as const, slug: i.slug }] }));
  }
  const [k, ml] = await Promise.all([komiku.getMangaList(page, type, "update"), mlList(page, type)]);
  if (k === null && ml === null) return null;
  return mergeItems([tagKomiku(k ?? []), ml ?? []]);
}

export async function search(query: string): Promise<SearchResult[]> {
  const [k, ml] = await Promise.all([komiku.search(query), mlSearch(query)]);
  const byKey = new Map<string, SearchResult>();
  for (const r of [...k.map((r) => ({ ...r, source: "komiku" as const })), ...ml]) {
    const key = titleKey(r.displayTitle);
    const link: SourceLink = { source: r.source ?? "komiku", slug: r.mangaSlug, latestChapter: r.latestChapterTitle };
    const existing = byKey.get(key);
    if (existing) {
      existing.sources = [...(existing.sources ?? []), link];
      existing.thumbnailUrl ||= r.thumbnailUrl;
      existing.type ||= r.type;
    } else {
      byKey.set(key, { ...r, sources: [link] });
    }
  }
  return [...byKey.values()];
}

// ---------- detail & chapter ----------

export async function getMangaDetail(ref: string): Promise<MangaDetail | null> {
  if (sourceOf(ref) === "mangaluv") return mlDetail(safeDecode(ref));
  const detail = await komiku.getMangaDetail(ref);
  return detail && { ...detail, source: "komiku" };
}

export async function getChapter(ref: string): Promise<ChapterPage | null> {
  return sourceOf(ref) === "mangaluv" ? mlChapter(safeDecode(ref)) : komiku.getChapter(ref);
}

/** The same title on the other sources, matched by normalised title or alternative title. */
export async function findAlternatives(detail: MangaDetail): Promise<SourceLink[]> {
  const keys = new Set(
    [detail.title, ...detail.alternativeTitle.split(/[,;]/)].map((t) => titleKey(t)).filter(Boolean),
  );
  const others: Promise<SearchResult[]>[] = [];
  if (detail.source !== "komiku") others.push(komiku.search(detail.title).then((r) => r.map((x) => ({ ...x, source: "komiku" as const }))));
  if (detail.source !== "mangaluv") others.push(mlSearch(detail.title));
  const results = (await Promise.all(others.map((p) => p.catch(() => [] as SearchResult[])))).flat();
  return results
    .filter((r) => keys.has(titleKey(r.displayTitle)))
    .map((r) => ({ source: r.source ?? "komiku", slug: r.mangaSlug, latestChapter: r.latestChapterTitle }));
}

// Shared data shapes for every source.

export type SourceId = "komiku" | "mangaluv";

/** Where else the same title can be read. `slug` is the app-level manga ref. */
export type SourceLink = { source: SourceId; slug: string; latestChapter?: string };

export type ChapterRef = { title: string; url: string };
export type MangaItem = {
  title: string;
  /** App-level manga ref: a bare komiku slug, or a prefixed ref for other sources. */
  slug: string;
  thumbnailUrl: string;
  type: string;
  latestChapter?: ChapterRef;
  /** Human-readable time of the latest update, e.g. "3 menit lalu". */
  updated?: string;
  updatedAt?: number;
  source?: SourceId;
  sources?: SourceLink[];
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
  source?: SourceId;
};
export type ChapterPage = {
  /** Path-style chapter ref, e.g. "/ao-ashi-chapter-2/"; matches ChapterItem.url for history lookups. */
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
  source?: SourceId;
  sources?: SourceLink[];
};

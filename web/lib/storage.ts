"use client";

// Bookmarks and reading history live in localStorage, mirroring the Android app's SQLite tables.
import { useSyncExternalStore } from "react";
import { titleKey } from "./titles";

export type Bookmark = {
  slug: string;
  title: string;
  thumbnailUrl: string;
  type: string;
  status: string;
  addedAt: number;
};

export type HistoryEntry = {
  slug: string;
  chapterUrl: string;
  chapterTitle: string;
  mangaTitle: string;
  readAt: number;
  lastPage: number;
};

const BOOKMARKS_KEY = "komiku:bookmarks";
const HISTORY_KEY = "komiku:history";
const COVERS_KEY = "komiku:covers";
const MAX_COVERS = 500;
const CHANGE_EVENT = "komiku:storage";
const MAX_HISTORY = 2000;

// useSyncExternalStore needs a stable snapshot, so parsed values are cached per raw string.
const cache = new Map<string, { raw: string | null; value: unknown }>();

function read<T>(key: string, fallback: T): T {
  if (typeof window === "undefined") return fallback;
  const raw = window.localStorage.getItem(key);
  const hit = cache.get(key);
  if (hit && hit.raw === raw) return hit.value as T;
  let value: T = fallback;
  try {
    if (raw) value = JSON.parse(raw) as T;
  } catch {
    // Corrupt entry: fall back rather than break the page.
  }
  cache.set(key, { raw, value });
  return value;
}

function write(key: string, value: unknown) {
  window.localStorage.setItem(key, JSON.stringify(value));
  window.dispatchEvent(new Event(CHANGE_EVENT));
}

function subscribe(callback: () => void) {
  window.addEventListener("storage", callback);
  window.addEventListener(CHANGE_EVENT, callback);
  return () => {
    window.removeEventListener("storage", callback);
    window.removeEventListener(CHANGE_EVENT, callback);
  };
}

const EMPTY_BOOKMARKS: Record<string, Bookmark> = {};
const EMPTY_HISTORY: Record<string, HistoryEntry> = {};
const EMPTY_COVERS: Record<string, string> = {};

const historyKey = (slug: string, chapterUrl: string) => `${slug}|${chapterUrl}`;

export function useBookmarks(): Record<string, Bookmark> {
  return useSyncExternalStore(
    subscribe,
    () => read(BOOKMARKS_KEY, EMPTY_BOOKMARKS),
    () => EMPTY_BOOKMARKS,
  );
}

export function useHistory(): Record<string, HistoryEntry> {
  return useSyncExternalStore(
    subscribe,
    () => read(HISTORY_KEY, EMPTY_HISTORY),
    () => EMPTY_HISTORY,
  );
}

/** Latest chapter read per title, newest first. The same title read on two sources counts once. */
export function latestPerManga(history: Record<string, HistoryEntry>): HistoryEntry[] {
  const byTitle = new Map<string, HistoryEntry>();
  for (const entry of Object.values(history)) {
    const key = entry.mangaTitle ? titleKey(entry.mangaTitle) : entry.slug;
    const prev = byTitle.get(key);
    if (!prev || entry.readAt > prev.readAt) byTitle.set(key, entry);
  }
  return [...byTitle.values()].sort((a, b) => b.readAt - a.readAt);
}

export function toggleBookmark(entry: Omit<Bookmark, "addedAt">) {
  const all = { ...read(BOOKMARKS_KEY, EMPTY_BOOKMARKS) };
  if (all[entry.slug]) delete all[entry.slug];
  else all[entry.slug] = { ...entry, addedAt: Date.now() };
  write(BOOKMARKS_KEY, all);
}

export function removeBookmark(slug: string) {
  const all = { ...read(BOOKMARKS_KEY, EMPTY_BOOKMARKS) };
  delete all[slug];
  write(BOOKMARKS_KEY, all);
}

export function addHistory(slug: string, chapterUrl: string, chapterTitle: string, mangaTitle: string) {
  const all = { ...read(HISTORY_KEY, EMPTY_HISTORY) };
  const key = historyKey(slug, chapterUrl);
  all[key] = {
    slug,
    chapterUrl,
    chapterTitle,
    mangaTitle,
    readAt: Date.now(),
    lastPage: all[key]?.lastPage ?? 0,
  };
  // Keep storage bounded; drop the oldest reads first.
  const entries = Object.entries(all);
  if (entries.length > MAX_HISTORY) {
    entries.sort(([, a], [, b]) => b.readAt - a.readAt);
    write(HISTORY_KEY, Object.fromEntries(entries.slice(0, MAX_HISTORY)));
  } else {
    write(HISTORY_KEY, all);
  }
}

export function setLastPage(slug: string, chapterUrl: string, page: number) {
  const all = read(HISTORY_KEY, EMPTY_HISTORY);
  const key = historyKey(slug, chapterUrl);
  if (!all[key] || all[key].lastPage === page) return;
  write(HISTORY_KEY, { ...all, [key]: { ...all[key], lastPage: page } });
}

export function getLastPage(slug: string, chapterUrl: string): number {
  return read(HISTORY_KEY, EMPTY_HISTORY)[historyKey(slug, chapterUrl)]?.lastPage ?? 0;
}

export function clearHistory() {
  write(HISTORY_KEY, {});
}

export function useIsBookmarked(slug: string): boolean {
  const bookmarks = useBookmarks();
  return Boolean(bookmarks[slug]);
}

/** Covers by slug, so history cards can show artwork (the reader page has no cover). */
export function useCovers(): Record<string, string> {
  return useSyncExternalStore(
    subscribe,
    () => read(COVERS_KEY, EMPTY_COVERS),
    () => EMPTY_COVERS,
  );
}

export function rememberCover(slug: string, url: string) {
  if (!url) return;
  const all = read(COVERS_KEY, EMPTY_COVERS);
  if (all[slug] === url) return;
  const next = { ...all, [slug]: url };
  const keys = Object.keys(next);
  // Insertion order approximates age; drop the oldest beyond the cap.
  for (const k of keys.slice(0, Math.max(0, keys.length - MAX_COVERS))) delete next[k];
  write(COVERS_KEY, next);
}

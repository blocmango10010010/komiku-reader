"use client";

import Link from "next/link";
import { useState } from "react";
import type { MangaDetail } from "@/lib/komiku";
import { toggleBookmark, useHistory, useIsBookmarked } from "@/lib/storage";

const chapterHref = (url: string) => `/read/${url.replace(/^\/+|\/+$/g, "")}`;

export function BookmarkButton({ detail }: { detail: MangaDetail }) {
  const bookmarked = useIsBookmarked(detail.slug);
  return (
    <button
      className={`btn ${bookmarked ? "primary" : ""}`}
      onClick={() =>
        toggleBookmark({
          slug: detail.slug,
          title: detail.title,
          thumbnailUrl: detail.thumbnailUrl,
          type: detail.type,
          status: detail.status,
        })
      }
    >
      {bookmarked ? "★ Di-bookmark" : "☆ Bookmark"}
    </button>
  );
}

export function ChapterSection({ detail }: { detail: MangaDetail }) {
  const history = useHistory();
  const [query, setQuery] = useState("");

  const reads = Object.values(history).filter((h) => h.slug === detail.slug);
  const readUrls = new Set(reads.map((h) => h.chapterUrl));
  const lastRead = reads.sort((a, b) => b.readAt - a.readAt)[0];
  const first = detail.chapters[detail.chapters.length - 1];

  const resume = lastRead
    ? { href: chapterHref(lastRead.chapterUrl), label: `Lanjutkan ${lastRead.chapterTitle || "membaca"}` }
    : first
      ? { href: chapterHref(first.url), label: `Mulai Baca ${first.title}` }
      : null;

  const filtered = query
    ? detail.chapters.filter((c) => c.title.toLowerCase().includes(query.toLowerCase()))
    : detail.chapters;
  const readCount = detail.chapters.filter((c) => readUrls.has(c.url)).length;

  return (
    <>
      {resume && (
        <Link href={resume.href} className="btn primary wide">
          ▶ {resume.label}
        </Link>
      )}

      <div className="chapter-header">
        <h2>{detail.chapters.length} Chapter</h2>
        {readCount > 0 && <span className="accent small">{readCount} dibaca</span>}
      </div>
      {detail.chapters.length > 20 && (
        <input
          className="input"
          type="search"
          placeholder="Cari chapter, mis. 120"
          value={query}
          onChange={(e) => setQuery(e.target.value)}
        />
      )}

      <ul className="chapter-list">
        {filtered.map((c) => {
          const isLast = c.url === lastRead?.chapterUrl;
          const isRead = readUrls.has(c.url);
          return (
            <li key={c.url}>
              <Link
                href={chapterHref(c.url)}
                className={`chapter ${isRead && !isLast ? "read" : ""} ${isLast ? "last" : ""}`}
                prefetch={false}
              >
                <span>
                  {isLast ? "🔖 " : isRead ? "✓ " : ""}
                  {c.title}
                </span>
                <span className="muted small">{c.date}</span>
              </Link>
            </li>
          );
        })}
      </ul>
    </>
  );
}

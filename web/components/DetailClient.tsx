"use client";

import Link from "next/link";
import { useEffect, useMemo, useState } from "react";
import type { MangaDetail } from "@/lib/komiku";
import { readHref, shortChapter } from "@/lib/format";
import { rememberCover, toggleBookmark, useHistory, useIsBookmarked } from "@/lib/storage";
import Icon from "./Icons";

function useReading(slug: string) {
  const history = useHistory();
  return useMemo(() => {
    const reads = Object.values(history).filter((h) => h.slug === slug);
    const lastRead = reads.reduce<(typeof reads)[number] | undefined>(
      (best, h) => (!best || h.readAt > best.readAt ? h : best),
      undefined,
    );
    return { readUrls: new Set(reads.map((h) => h.chapterUrl)), lastRead };
  }, [history, slug]);
}

/** Primary "start/continue" button plus bookmark toggle. */
export function ActionBar({ detail }: { detail: MangaDetail }) {
  const bookmarked = useIsBookmarked(detail.slug);
  const { lastRead } = useReading(detail.slug);
  const first = detail.chapters[detail.chapters.length - 1];

  useEffect(() => rememberCover(detail.slug, detail.thumbnailUrl), [detail.slug, detail.thumbnailUrl]);

  const resume = lastRead
    ? { href: readHref(lastRead.chapterUrl), label: `Lanjutkan ${lastRead.chapterTitle ? shortChapter(lastRead.chapterTitle) : "membaca"}` }
    : first
      ? { href: readHref(first.url), label: `Mulai Baca ${shortChapter(first.title)}` }
      : null;

  return (
    <div className="action-bar">
      {resume && (
        <Link href={resume.href} className="btn btn-primary btn-lg grow">
          <Icon name="play" size={18} /> {resume.label}
        </Link>
      )}
      <button
        type="button"
        className={`btn btn-lg btn-icon ${bookmarked ? "btn-active" : ""}`}
        aria-pressed={bookmarked}
        aria-label={bookmarked ? "Hapus dari koleksi" : "Simpan ke koleksi"}
        title={bookmarked ? "Hapus dari koleksi" : "Simpan ke koleksi"}
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
        <Icon name="bookmark" size={20} filled={bookmarked} />
      </button>
    </div>
  );
}

export function Synopsis({ text }: { text: string }) {
  const [open, setOpen] = useState(false);
  return (
    <section className="synopsis">
      <p className={open ? "" : "clamp-3"}>{text}</p>
      {text.length > 160 && (
        <button type="button" className="link-btn" onClick={() => setOpen((o) => !o)} aria-expanded={open}>
          {open ? "Tutup" : "Selengkapnya"}
        </button>
      )}
    </section>
  );
}

export function ChapterList({ detail }: { detail: MangaDetail }) {
  const { readUrls, lastRead } = useReading(detail.slug);
  const [query, setQuery] = useState("");
  const [newestFirst, setNewestFirst] = useState(true);

  const total = detail.chapters.length;
  const readCount = detail.chapters.filter((c) => readUrls.has(c.url)).length;

  const visible = useMemo(() => {
    const q = query.trim().toLowerCase();
    const list = q ? detail.chapters.filter((c) => c.title.toLowerCase().includes(q)) : detail.chapters;
    return newestFirst ? list : [...list].reverse();
  }, [detail.chapters, query, newestFirst]);

  return (
    <section className="chapters">
      <div className="chapters-head">
        <h2>
          Chapter <span className="count">{total}</span>
        </h2>
        {readCount > 0 && (
          <span className="muted small">
            {readCount}/{total} dibaca
          </span>
        )}
      </div>
      {readCount > 0 && (
        <div className="progress" aria-hidden="true">
          <span style={{ width: `${Math.max(2, (readCount / total) * 100)}%` }} />
        </div>
      )}

      <div className="chapters-tools">
        <label className="input-icon">
          <Icon name="search" size={16} />
          <input
            type="search"
            inputMode="numeric"
            placeholder="Cari nomor chapter"
            value={query}
            onChange={(e) => setQuery(e.target.value)}
            aria-label="Cari chapter"
          />
        </label>
        <button
          type="button"
          className="btn btn-sm"
          onClick={() => setNewestFirst((v) => !v)}
          aria-label={newestFirst ? "Urutkan dari chapter pertama" : "Urutkan dari chapter terbaru"}
        >
          <Icon name="sort" size={16} /> {newestFirst ? "Terbaru" : "Terlama"}
        </button>
      </div>

      {visible.length === 0 ? (
        <p className="muted center">Chapter tidak ditemukan.</p>
      ) : (
        <ul className="chapter-list">
          {visible.map((c) => {
            const isLast = c.url === lastRead?.chapterUrl;
            const isRead = readUrls.has(c.url);
            return (
              <li key={c.url}>
                <Link
                  href={readHref(c.url)}
                  className={`chapter${isRead && !isLast ? " is-read" : ""}${isLast ? " is-last" : ""}`}
                  prefetch={false}
                >
                  <span className="chapter-title">{c.title}</span>
                  {isLast ? (
                    <span className="badge">Terakhir dibaca</span>
                  ) : isRead ? (
                    <Icon name="check" size={16} className="muted" />
                  ) : (
                    <span className="muted small">{c.date}</span>
                  )}
                </Link>
              </li>
            );
          })}
        </ul>
      )}
    </section>
  );
}

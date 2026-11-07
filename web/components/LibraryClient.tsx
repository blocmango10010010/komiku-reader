"use client";

import Link from "next/link";
import { useState } from "react";
import { readHref, shortChapter, timeAgo } from "@/lib/format";
import {
  clearHistory,
  latestPerManga,
  removeBookmark,
  useBookmarks,
  useCovers,
  useHistory,
  type Bookmark,
} from "@/lib/storage";
import Cover from "./Cover";
import Icon from "./Icons";

export function ContinueReading() {
  const history = latestPerManga(useHistory()).slice(0, 10);
  const covers = useCovers();
  if (!history.length) return null;
  return (
    <section>
      <div className="section-head">
        <h2>
          <Icon name="history" size={20} className="accent" /> Lanjutkan Membaca
        </h2>
        <Link href="/library?tab=history" className="link-more">
          Riwayat <Icon name="chevron-right" size={16} />
        </Link>
      </div>
      <div className="row-scroll">
        {history.map((h) => (
          <Link key={h.slug} href={readHref(h.chapterUrl)} className="continue">
            <Cover src={covers[h.slug] ?? ""} title={h.mangaTitle || h.slug} className="continue-cover" />
            <div className="continue-info">
              <strong className="clamp-2">{h.mangaTitle || h.slug}</strong>
              <span className="accent small">
                <Icon name="play" size={12} /> {shortChapter(h.chapterTitle) || "Lanjutkan"}
              </span>
              <span className="muted xsmall">{timeAgo(h.readAt)}</span>
            </div>
          </Link>
        ))}
      </div>
    </section>
  );
}

function BookmarkGrid({ bookmarks }: { bookmarks: Bookmark[] }) {
  if (!bookmarks.length) {
    return (
      <div className="state-box">
        <span className="state-icon">
          <Icon name="bookmark" size={28} />
        </span>
        <p>Belum ada komik di koleksi.</p>
        <p className="muted small">Ketuk ikon bookmark di halaman detail komik untuk menyimpannya di sini.</p>
        <Link href="/browse" className="btn btn-primary">
          Jelajahi Komik
        </Link>
      </div>
    );
  }
  return (
    <div className="grid">
      {bookmarks.map((b) => (
        <article key={b.slug} className="card">
          <Link href={`/manga/${b.slug}`} className="card-link">
            <div className="card-cover">
              <Cover src={b.thumbnailUrl} title={b.title} />
              {b.type && <span className={`type-tag type-${b.type.toLowerCase()}`}>{b.type}</span>}
            </div>
            <h3 className="card-title">{b.title}</h3>
          </Link>
          <button
            type="button"
            className="card-remove"
            aria-label={`Hapus ${b.title} dari koleksi`}
            onClick={() => removeBookmark(b.slug)}
          >
            <Icon name="x" size={14} />
          </button>
        </article>
      ))}
    </div>
  );
}

export function Library({ initialTab }: { initialTab: "bookmarks" | "history" }) {
  const [tab, setTab] = useState(initialTab);
  const bookmarkMap = useBookmarks();
  const bookmarks = Object.values(bookmarkMap).sort((a, b) => b.addedAt - a.addedAt);
  const history = latestPerManga(useHistory());
  const covers = useCovers();

  return (
    <div className="stack">
      <div className="segmented full" role="tablist">
        <button role="tab" aria-selected={tab === "bookmarks"} onClick={() => setTab("bookmarks")}>
          Koleksi <span className="count">{bookmarks.length}</span>
        </button>
        <button role="tab" aria-selected={tab === "history"} onClick={() => setTab("history")}>
          Riwayat <span className="count">{history.length}</span>
        </button>
      </div>

      {tab === "bookmarks" && <BookmarkGrid bookmarks={bookmarks} />}

      {tab === "history" &&
        (history.length === 0 ? (
          <div className="state-box">
            <span className="state-icon">
              <Icon name="history" size={28} />
            </span>
            <p>Belum ada riwayat baca.</p>
          </div>
        ) : (
          <>
            <ul className="history-list">
              {history.map((h) => (
                <li key={h.slug} className="history-row">
                  <Link href={`/manga/${h.slug}`} className="history-main">
                    <Cover src={covers[h.slug] ?? bookmarkMap[h.slug]?.thumbnailUrl ?? ""} title={h.mangaTitle || h.slug} className="history-cover" />
                    <div>
                      <strong className="clamp-1">{h.mangaTitle || h.slug}</strong>
                      <span className="muted small block">
                        {shortChapter(h.chapterTitle)} · {timeAgo(h.readAt)}
                      </span>
                    </div>
                  </Link>
                  <Link href={readHref(h.chapterUrl)} className="btn btn-soft btn-sm" aria-label={`Lanjutkan ${h.mangaTitle}`}>
                    <Icon name="play" size={14} /> Lanjut
                  </Link>
                </li>
              ))}
            </ul>
            <button
              type="button"
              className="btn btn-ghost-danger"
              onClick={() => {
                if (confirm("Hapus semua riwayat baca?")) clearHistory();
              }}
            >
              <Icon name="trash" size={16} /> Hapus Riwayat
            </button>
          </>
        ))}
    </div>
  );
}

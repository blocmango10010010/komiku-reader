"use client";

import Link from "next/link";
import { useState } from "react";
import {
  clearHistory,
  latestPerManga,
  removeBookmark,
  useBookmarks,
  useHistory,
  type HistoryEntry,
} from "@/lib/storage";

const chapterHref = (url: string) => `/read/${url.replace(/^\/+|\/+$/g, "")}`;

export function ContinueReading() {
  const recent = latestPerManga(useHistory()).slice(0, 10);
  if (!recent.length) return null;
  return (
    <section>
      <h2 className="section-title">📖 Lanjutkan Membaca</h2>
      <div className="row-scroll">
        {recent.map((h) => (
          <ContinueCard key={h.slug} entry={h} />
        ))}
      </div>
    </section>
  );
}

function ContinueCard({ entry }: { entry: HistoryEntry }) {
  return (
    <Link href={chapterHref(entry.chapterUrl)} className="continue-card">
      <strong className="ellipsis">{entry.mangaTitle || entry.slug}</strong>
      <span className="accent small ellipsis">▶ {entry.chapterTitle || "Lanjutkan"}</span>
    </Link>
  );
}

export function Library() {
  const [tab, setTab] = useState<"bookmarks" | "history">("bookmarks");
  const bookmarks = Object.values(useBookmarks()).sort((a, b) => b.addedAt - a.addedAt);
  const history = latestPerManga(useHistory());

  return (
    <>
      <div className="tabs" role="tablist">
        <button role="tab" aria-selected={tab === "bookmarks"} onClick={() => setTab("bookmarks")}>
          Bookmark ({bookmarks.length})
        </button>
        <button role="tab" aria-selected={tab === "history"} onClick={() => setTab("history")}>
          Riwayat ({history.length})
        </button>
      </div>

      {tab === "bookmarks" &&
        (bookmarks.length === 0 ? (
          <p className="empty">Belum ada bookmark. Tambahkan dari halaman detail komik.</p>
        ) : (
          <ul className="list">
            {bookmarks.map((b) => (
              <li key={b.slug} className="list-row">
                <Link href={`/manga/${b.slug}`} className="list-main">
                  {/* eslint-disable-next-line @next/next/no-img-element */}
                  <img src={b.thumbnailUrl} alt="" className="thumb-sm" referrerPolicy="no-referrer" />
                  <span>
                    <strong>{b.title}</strong>
                    <span className="muted small block">{[b.type, b.status].filter(Boolean).join(" · ")}</span>
                  </span>
                </Link>
                <button className="icon-btn" aria-label={`Hapus ${b.title}`} onClick={() => removeBookmark(b.slug)}>
                  ✕
                </button>
              </li>
            ))}
          </ul>
        ))}

      {tab === "history" &&
        (history.length === 0 ? (
          <p className="empty">Belum ada riwayat baca.</p>
        ) : (
          <>
            <ul className="list">
              {history.map((h) => (
                <li key={h.slug} className="list-row">
                  <Link href={`/manga/${h.slug}`} className="list-main">
                    <span>
                      <strong>{h.mangaTitle || h.slug}</strong>
                      <span className="muted small block">{new Date(h.readAt).toLocaleString("id-ID")}</span>
                    </span>
                  </Link>
                  <Link href={chapterHref(h.chapterUrl)} className="btn small-btn">
                    {h.chapterTitle || "Lanjutkan"}
                  </Link>
                </li>
              ))}
            </ul>
            <button
              className="btn wide"
              onClick={() => {
                if (confirm("Hapus semua riwayat baca?")) clearHistory();
              }}
            >
              Hapus Riwayat
            </button>
          </>
        ))}
    </>
  );
}

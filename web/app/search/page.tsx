import type { Metadata } from "next";
import Link from "next/link";
import Cover from "@/components/Cover";
import Icon from "@/components/Icons";
import { readHref, shortChapter } from "@/lib/format";
import { search } from "@/lib/sources";
import { SOURCE_NAMES } from "@/lib/titles";

export const metadata: Metadata = { title: "Cari" };

const SUGGESTIONS = ["One Piece", "Solo Leveling", "Jujutsu Kaisen", "Blue Lock", "Black Clover", "Omniscient Reader"];

export default async function SearchPage({ searchParams }: { searchParams: Promise<{ q?: string }> }) {
  const q = ((await searchParams).q ?? "").trim().slice(0, 100);
  const results = q ? await search(q) : [];

  return (
    <div className="stack">
      <form action="/search" className="searchbox" role="search">
        <Icon name="search" size={20} className="searchbox-icon" />
        <input
          type="search"
          name="q"
          defaultValue={q}
          placeholder="Cari judul komik…"
          autoFocus={!q}
          aria-label="Judul komik"
          enterKeyHint="search"
        />
        <button className="btn btn-primary" type="submit">
          Cari
        </button>
      </form>

      {!q && (
        <section className="stack-sm">
          <h2 className="label">Pencarian populer</h2>
          <div className="pills wrap">
            {SUGGESTIONS.map((s) => (
              <Link key={s} href={`/search?q=${encodeURIComponent(s)}`} className="pill">
                {s}
              </Link>
            ))}
          </div>
        </section>
      )}

      {q && (
        <p className="muted">
          {results.length > 0 ? `${results.length} hasil untuk “${q}”` : `Tidak ada hasil untuk “${q}”. Coba kata kunci lain.`}
        </p>
      )}

      <ul className="result-list">
        {results.map((r) => (
          <li key={r.mangaSlug} className="result">
            <Link href={`/manga/${r.mangaSlug}`} className="result-main" prefetch={false}>
              <Cover src={r.thumbnailUrl} title={r.displayTitle} className="result-cover" />
              <div className="result-info">
                <h3>{r.displayTitle}</h3>
                <p className="muted small">{[r.type, r.updated].filter(Boolean).join(" · ")}</p>
                {r.sources && (
                  <p className="source-line">{r.sources.map((s) => SOURCE_NAMES[s.source]).join(" · ")}</p>
                )}
              </div>
            </Link>
            {r.latestChapterUrl && (
              <Link href={readHref(r.latestChapterUrl)} className="btn btn-soft btn-sm" prefetch={false}>
                {r.latestChapterTitle ? shortChapter(r.latestChapterTitle) : "Baca"}
              </Link>
            )}
          </li>
        ))}
      </ul>
    </div>
  );
}

import type { Metadata } from "next";
import Link from "next/link";
import { search } from "@/lib/komiku";

export const metadata: Metadata = { title: "Cari" };

export default async function SearchPage({ searchParams }: { searchParams: Promise<{ q?: string }> }) {
  const q = ((await searchParams).q ?? "").trim().slice(0, 100);
  const results = q ? await search(q) : [];

  return (
    <>
      <form action="/search" className="search-form" role="search">
        <input
          className="input"
          type="search"
          name="q"
          defaultValue={q}
          placeholder="Cari manga, manhwa, atau manhua..."
          autoFocus={!q}
          aria-label="Kata kunci"
        />
        <button className="btn primary" type="submit">Cari</button>
      </form>

      {q && results.length === 0 && <p className="empty">Tidak ada hasil untuk &ldquo;{q}&rdquo;.</p>}

      <ul className="list">
        {results.map((r) => (
          <li key={r.mangaSlug}>
            <Link href={`/manga/${r.mangaSlug}`} className="list-row result">
              <span>
                <strong>{r.displayTitle}</strong>
                {r.updated && <span className="muted small block">{r.updated}</span>}
              </span>
              <span className="accent">›</span>
            </Link>
          </li>
        ))}
      </ul>
    </>
  );
}

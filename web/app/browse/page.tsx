import type { Metadata } from "next";
import Link from "next/link";
import ErrorNotice from "@/components/ErrorNotice";
import MangaCard from "@/components/MangaCard";
import { getMangaList, MANGA_TYPES } from "@/lib/komiku";

export const metadata: Metadata = { title: "Semua Komik" };

const FILTERS = [
  { type: "", label: "Semua" },
  { type: "manga", label: "🇯🇵 Manga" },
  { type: "manhwa", label: "🇰🇷 Manhwa" },
  { type: "manhua", label: "🇨🇳 Manhua" },
];

export default async function BrowsePage({
  searchParams,
}: {
  searchParams: Promise<{ type?: string; page?: string }>;
}) {
  const params = await searchParams;
  const type = (MANGA_TYPES as readonly string[]).includes(params.type ?? "") ? params.type! : "";
  const page = Math.max(1, Number.parseInt(params.page ?? "1", 10) || 1);
  const items = await getMangaList(page, type);

  const href = (p: number) => {
    const q = new URLSearchParams();
    if (type) q.set("type", type);
    if (p > 1) q.set("page", String(p));
    const s = q.toString();
    return s ? `/browse?${s}` : "/browse";
  };

  return (
    <>
      <h1 className="page-title">Semua Komik</h1>
      <div className="chips">
        {FILTERS.map((f) => (
          <Link key={f.type} href={f.type ? `/browse?type=${f.type}` : "/browse"} className={`chip ${f.type === type ? "active" : ""}`}>
            {f.label}
          </Link>
        ))}
      </div>

      {items === null ? (
        <ErrorNotice message="Gagal memuat daftar komik." retryHref={href(page)} />
      ) : items.length === 0 ? (
        <p className="empty">Tidak ada komik lagi di halaman ini.</p>
      ) : (
        <div className="grid">
          {items.map((m) => (
            <MangaCard key={m.slug} item={m} />
          ))}
        </div>
      )}

      <nav className="pager">
        {page > 1 ? <Link href={href(page - 1)} className="btn">← Sebelumnya</Link> : <span />}
        <span className="muted">Halaman {page}</span>
        {items && items.length > 0 ? <Link href={href(page + 1)} className="btn">Berikutnya →</Link> : <span />}
      </nav>
    </>
  );
}

import type { Metadata } from "next";
import Link from "next/link";
import ErrorNotice from "@/components/ErrorNotice";
import MangaCard from "@/components/MangaCard";
import { getMangaList, MANGA_TYPES, SORTS, type Sort } from "@/lib/komiku";

export const metadata: Metadata = { title: "Jelajah" };

const FILTERS = [
  { type: "", label: "Semua" },
  { type: "manga", label: "🇯🇵 Manga" },
  { type: "manhwa", label: "🇰🇷 Manhwa" },
  { type: "manhua", label: "🇨🇳 Manhua" },
];

export default async function BrowsePage({
  searchParams,
}: {
  searchParams: Promise<{ type?: string; page?: string; sort?: string }>;
}) {
  const params = await searchParams;
  const type = (MANGA_TYPES as readonly string[]).includes(params.type ?? "") ? params.type! : "";
  const sort: Sort = (SORTS as readonly string[]).includes(params.sort ?? "") ? (params.sort as Sort) : "update";
  const page = Math.max(1, Number.parseInt(params.page ?? "1", 10) || 1);
  const items = await getMangaList(page, type, sort);

  const href = (p: number, t = type, s: Sort = sort) => {
    const q = new URLSearchParams();
    if (t) q.set("type", t);
    if (s !== "update") q.set("sort", s);
    if (p > 1) q.set("page", String(p));
    const str = q.toString();
    return str ? `/browse?${str}` : "/browse";
  };

  return (
    <>
      <h1 className="page-title">{sort === "update" ? "Update Terbaru" : "Komik A–Z"}</h1>
      <div className="chips">
        <Link href={href(1, type, "update")} className={`chip ${sort === "update" ? "active" : ""}`}>
          🆕 Terbaru
        </Link>
        <Link href={href(1, type, "az")} className={`chip ${sort === "az" ? "active" : ""}`}>
          🔤 A–Z
        </Link>
      </div>
      <div className="chips">
        {FILTERS.map((f) => (
          <Link key={f.type} href={href(1, f.type)} className={`chip ${f.type === type ? "active" : ""}`}>
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

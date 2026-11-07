import type { Metadata } from "next";
import Link from "next/link";
import ErrorNotice from "@/components/ErrorNotice";
import Icon from "@/components/Icons";
import MangaCard from "@/components/MangaCard";
import { getMangaList, MANGA_TYPES, SORTS, type Sort } from "@/lib/komiku";

export const metadata: Metadata = { title: "Jelajah" };

const TYPES = [
  { type: "", label: "Semua" },
  { type: "manga", label: "Manga" },
  { type: "manhwa", label: "Manhwa" },
  { type: "manhua", label: "Manhua" },
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
    <div className="stack">
      <header className="page-head">
        <h1>Jelajah</h1>
        <div className="segmented" role="tablist" aria-label="Urutkan">
          <Link href={href(1, type, "update")} role="tab" aria-selected={sort === "update"}>
            Terbaru
          </Link>
          <Link href={href(1, type, "az")} role="tab" aria-selected={sort === "az"}>
            A–Z
          </Link>
        </div>
      </header>

      <div className="pills" role="tablist" aria-label="Tipe">
        {TYPES.map((t) => (
          <Link key={t.type} href={href(1, t.type)} role="tab" aria-selected={t.type === type} className="pill">
            {t.label}
          </Link>
        ))}
      </div>

      {items === null ? (
        <ErrorNotice message="Gagal memuat daftar komik." retryHref={href(page)} />
      ) : items.length === 0 ? (
        <div className="state-box">
          <p>Tidak ada komik lagi di halaman ini.</p>
          <Link href={href(1)} className="btn">Ke halaman pertama</Link>
        </div>
      ) : (
        <div className="grid">
          {items.map((m, i) => (
            <MangaCard key={m.slug} item={m} eager={i < 6} />
          ))}
        </div>
      )}

      <nav className="pager" aria-label="Halaman">
        {page > 1 ? (
          <Link href={href(page - 1)} className="btn" aria-label="Halaman sebelumnya">
            <Icon name="chevron-left" size={18} /> Sebelumnya
          </Link>
        ) : (
          <span />
        )}
        <span className="pager-page">Hal. {page}</span>
        {items && items.length > 0 ? (
          <Link href={href(page + 1)} className="btn" aria-label="Halaman berikutnya">
            Berikutnya <Icon name="chevron-right" size={18} />
          </Link>
        ) : (
          <span />
        )}
      </nav>
    </div>
  );
}

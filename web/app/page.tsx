import Link from "next/link";
import ErrorNotice from "@/components/ErrorNotice";
import Hero from "@/components/Hero";
import Icon from "@/components/Icons";
import { ContinueReading } from "@/components/LibraryClient";
import MangaCard from "@/components/MangaCard";
import { getHome } from "@/lib/sources";

const CATEGORIES = [
  { type: "manga", label: "Manga", sub: "Jepang" },
  { type: "manhwa", label: "Manhwa", sub: "Korea" },
  { type: "manhua", label: "Manhua", sub: "China" },
];

export default async function HomePage() {
  const data = await getHome();

  if (!data) {
    return <ErrorNotice message="Gagal memuat data dari komiku.org. Coba lagi sebentar lagi." retryHref="/" />;
  }

  return (
    <div className="stack-lg">
      <Hero items={data.ranking.slice(0, 6)} />

      <nav className="categories" aria-label="Kategori">
        {CATEGORIES.map((c) => (
          <Link key={c.type} href={`/browse?type=${c.type}`} className={`category category-${c.type}`}>
            <strong>{c.label}</strong>
            <span>{c.sub}</span>
          </Link>
        ))}
      </nav>

      <ContinueReading />

      {data.latest.length > 0 && (
        <section>
          <div className="section-head">
            <h2>
              <Icon name="sparkles" size={20} className="accent" /> Update Terbaru
            </h2>
            <Link href="/browse" className="link-more">
              Lihat semua <Icon name="chevron-right" size={16} />
            </Link>
          </div>
          <div className="grid">
            {data.latest.map((m, i) => (
              <MangaCard key={m.slug} item={m} eager={i < 6} />
            ))}
          </div>
        </section>
      )}
    </div>
  );
}

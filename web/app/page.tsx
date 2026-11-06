import Link from "next/link";
import ErrorNotice from "@/components/ErrorNotice";
import { ContinueReading } from "@/components/LibraryClient";
import MangaCard from "@/components/MangaCard";
import { getHome } from "@/lib/komiku";

export default async function HomePage() {
  const data = await getHome();

  return (
    <>
      <div className="chips">
        <Link href="/browse" className="chip active">🆕 Update Terbaru</Link>
        <Link href="/browse?sort=az" className="chip">📚 A–Z</Link>
        <Link href="/browse?type=manga" className="chip">🇯🇵 Manga</Link>
        <Link href="/browse?type=manhwa" className="chip">🇰🇷 Manhwa</Link>
        <Link href="/browse?type=manhua" className="chip">🇨🇳 Manhua</Link>
      </div>

      <ContinueReading />

      {!data ? (
        <ErrorNotice message="Gagal memuat data dari komiku.org. Coba lagi sebentar lagi." retryHref="/" />
      ) : (
        <>
          {data.ranking.length > 0 && (
            <section>
              <h2 className="section-title">🏆 Populer Hari Ini</h2>
              <div className="row-scroll">
                {data.ranking.map((m) => (
                  <MangaCard key={m.slug} item={m} />
                ))}
              </div>
            </section>
          )}
          {data.latest.length > 0 && (
            <section>
              <div className="section-head">
                <h2 className="section-title">🆕 Update Terbaru</h2>
                <Link href="/browse" className="accent small">Lihat Semua</Link>
              </div>
              <div className="grid">
                {data.latest.map((m) => (
                  <MangaCard key={m.slug} item={m} />
                ))}
              </div>
            </section>
          )}
        </>
      )}
    </>
  );
}

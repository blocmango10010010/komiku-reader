import Link from "next/link";
import type { MangaItem } from "@/lib/komiku";

export default function MangaCard({ item }: { item: MangaItem }) {
  return (
    <Link href={`/manga/${item.slug}`} className="card" prefetch={false}>
      <div className="cover">
        {item.thumbnailUrl ? (
          // eslint-disable-next-line @next/next/no-img-element -- images are served by the source CDN
          <img src={item.thumbnailUrl} alt="" loading="lazy" referrerPolicy="no-referrer" />
        ) : (
          <span className="cover-fallback">📖</span>
        )}
      </div>
      <div className="card-body">
        <span className="card-title">{item.title}</span>
        {item.type && <span className="accent small">{item.type}</span>}
      </div>
    </Link>
  );
}

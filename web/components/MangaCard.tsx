import Link from "next/link";
import type { MangaItem } from "@/lib/komiku";
import { readHref, shortChapter } from "@/lib/format";
import Cover from "./Cover";

export default function MangaCard({ item, eager = false }: { item: MangaItem; eager?: boolean }) {
  const chapter = item.latestChapter;
  return (
    <article className="card">
      <Link href={`/manga/${item.slug}`} className="card-link" prefetch={false}>
        <div className="card-cover">
          <Cover src={item.thumbnailUrl} title={item.title} eager={eager} />
          {item.type && <span className={`type-tag type-${item.type.toLowerCase()}`}>{item.type}</span>}
          {item.updated && <span className="time-tag">{item.updated}</span>}
        </div>
        <h3 className="card-title">{item.title}</h3>
      </Link>
      {chapter && (
        <Link href={readHref(chapter.url)} className="card-chapter" prefetch={false}>
          {shortChapter(chapter.title)}
        </Link>
      )}
    </article>
  );
}

import Link from "next/link";
import type { MangaItem } from "@/lib/komiku";
import { coverUrl, readHref, shortChapter } from "@/lib/format";
import Cover from "./Cover";
import Icon from "./Icons";

/** Swipeable spotlight of today's most popular titles (CSS scroll-snap, no JS). */
export default function Hero({ items }: { items: MangaItem[] }) {
  if (!items.length) return null;
  return (
    <section className="hero" aria-label="Populer hari ini">
      <div className="hero-track">
        {items.map((m, i) => (
          <article key={m.slug} className="hero-slide">
            {/* eslint-disable-next-line @next/next/no-img-element -- decorative backdrop */}
            <img className="hero-backdrop" src={coverUrl(m.thumbnailUrl, 300, 400)} alt="" referrerPolicy="no-referrer" />
            <div className="hero-body">
              <span className="hero-rank">
                <Icon name="flame" size={14} /> #{i + 1} Populer Hari Ini
              </span>
              <h2 className="hero-title">{m.title}</h2>
              {m.updated && <p className="hero-meta">{m.updated}</p>}
              <div className="hero-actions">
                {m.latestChapter && (
                  <Link href={readHref(m.latestChapter.url)} className="btn btn-primary" prefetch={false}>
                    <Icon name="play" size={16} /> Baca {shortChapter(m.latestChapter.title)}
                  </Link>
                )}
                <Link href={`/manga/${m.slug}`} className="btn btn-glass" prefetch={false}>
                  Detail
                </Link>
              </div>
            </div>
            <Link href={`/manga/${m.slug}`} className="hero-cover" tabIndex={-1} aria-hidden="true" prefetch={false}>
              <Cover src={m.thumbnailUrl} title={m.title} eager={i === 0} />
            </Link>
          </article>
        ))}
      </div>
    </section>
  );
}

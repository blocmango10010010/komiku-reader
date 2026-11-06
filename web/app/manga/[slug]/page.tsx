import type { Metadata } from "next";
import { notFound } from "next/navigation";
import { cache } from "react";
import { BookmarkButton, ChapterSection } from "@/components/DetailClient";
import { getMangaDetail } from "@/lib/komiku";

// Shared between generateMetadata and the page so the site is fetched once per request.
const loadDetail = cache(getMangaDetail);

type Props = { params: Promise<{ slug: string }> };

export async function generateMetadata({ params }: Props): Promise<Metadata> {
  const detail = await loadDetail((await params).slug);
  return { title: detail?.title ?? "Komik tidak ditemukan" };
}

export default async function MangaPage({ params }: Props) {
  const detail = await loadDetail((await params).slug);
  if (!detail) notFound();

  const info = [
    ["Tipe", detail.type],
    ["Genre", detail.genre],
    ["Author", detail.author],
    ["Status", detail.status],
  ].filter(([, v]) => v);

  return (
    <>
      <div className="detail-head">
        {/* eslint-disable-next-line @next/next/no-img-element -- images are served by the source CDN */}
        <img src={detail.thumbnailUrl} alt={detail.title} className="detail-cover" referrerPolicy="no-referrer" />
        <div className="detail-info">
          <h1>{detail.title}</h1>
          {detail.alternativeTitle && <p className="muted small">{detail.alternativeTitle}</p>}
          <dl>
            {info.map(([k, v]) => (
              <div key={k}>
                <dt>{k}</dt>
                <dd>{v}</dd>
              </div>
            ))}
          </dl>
          <BookmarkButton detail={detail} />
        </div>
      </div>

      {detail.synopsis && (
        <details className="synopsis">
          <summary>
            <strong>Sinopsis</strong>
            <span className="synopsis-preview">{detail.synopsis}</span>
          </summary>
          <p>{detail.synopsis}</p>
        </details>
      )}

      <ChapterSection detail={detail} />
    </>
  );
}

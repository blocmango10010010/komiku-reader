import type { Metadata } from "next";
import Link from "next/link";
import { notFound } from "next/navigation";
import { cache, Suspense } from "react";
import Cover from "@/components/Cover";
import { ActionBar, ChapterList, Synopsis } from "@/components/DetailClient";
import { coverUrl, shortChapter } from "@/lib/format";
import { findAlternatives, getMangaDetail } from "@/lib/sources";
import { SOURCE_NAMES } from "@/lib/titles";
import type { MangaDetail } from "@/lib/types";

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

  const genres = detail.genres.length ? detail.genres : detail.genre ? [detail.genre] : [];

  return (
    <div className="detail">
      {/* eslint-disable-next-line @next/next/no-img-element -- decorative backdrop */}
      <img className="detail-backdrop" src={coverUrl(detail.thumbnailUrl, 300, 400)} alt="" referrerPolicy="no-referrer" />

      <header className="detail-head">
        <Cover src={detail.thumbnailUrl} title={detail.title} className="detail-cover" eager />
        <div className="detail-info">
          <div className="tags">
            {detail.type && <span className={`tag type-${detail.type.toLowerCase()}`}>{detail.type}</span>}
            {detail.status && (
              <span className={`tag ${/ongoing/i.test(detail.status) ? "tag-live" : ""}`}>{detail.status}</span>
            )}
          </div>
          <h1>{detail.title}</h1>
          {detail.alternativeTitle && <p className="muted small">{detail.alternativeTitle}</p>}
          {detail.author && <p className="small">oleh {detail.author}</p>}
        </div>
      </header>

      <ActionBar detail={detail} />

      <Suspense fallback={<SourceRow detail={detail} />}>
        <Alternatives detail={detail} />
      </Suspense>

      {genres.length > 0 && (
        <ul className="genres" aria-label="Genre">
          {genres.map((g) => (
            <li key={g}>{g}</li>
          ))}
        </ul>
      )}

      {detail.synopsis && <Synopsis text={detail.synopsis} />}

      <ChapterList detail={detail} />
    </div>
  );
}

function SourceRow({ detail, alternatives = [] }: { detail: MangaDetail; alternatives?: { source: keyof typeof SOURCE_NAMES; slug: string; latestChapter?: string }[] }) {
  const latest = detail.chapters[0]?.title;
  return (
    <nav className="sources" aria-label="Sumber">
      <span className="label">Sumber</span>
      <span className="source-pill active" aria-current="page">
        {SOURCE_NAMES[detail.source ?? "komiku"]}
        {latest && <small>{shortChapter(latest)}</small>}
      </span>
      {alternatives.map((a) => (
        <Link key={a.slug} href={`/manga/${a.slug}`} className="source-pill" prefetch={false}>
          {SOURCE_NAMES[a.source]}
          {a.latestChapter && <small>{shortChapter(a.latestChapter)}</small>}
        </Link>
      ))}
    </nav>
  );
}

/** Streams in after the page renders, since it searches the other sources. */
async function Alternatives({ detail }: { detail: MangaDetail }) {
  const alternatives = await findAlternatives(detail);
  return <SourceRow detail={detail} alternatives={alternatives} />;
}

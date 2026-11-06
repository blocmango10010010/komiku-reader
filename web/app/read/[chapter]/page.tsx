import type { Metadata } from "next";
import { notFound } from "next/navigation";
import { cache } from "react";
import Reader from "@/components/Reader";
import { chapterSegment, getChapter } from "@/lib/komiku";

const loadChapter = cache(getChapter);

type Props = { params: Promise<{ chapter: string }> };

export async function generateMetadata({ params }: Props): Promise<Metadata> {
  const page = await loadChapter((await params).chapter);
  return { title: page?.chapterTitle || "Baca" };
}

export default async function ReadPage({ params }: Props) {
  const page = await loadChapter((await params).chapter);
  if (!page) notFound();

  const href = (url: string | null) => (url ? `/read/${chapterSegment(url)}` : null);

  return (
    <Reader
      mangaSlug={page.mangaSlug}
      mangaTitle={page.mangaTitle}
      chapterTitle={page.chapterTitle}
      chapterUrl={page.url}
      imageUrls={page.imageUrls}
      prevHref={href(page.prevChapterUrl)}
      nextHref={href(page.nextChapterUrl)}
    />
  );
}

"use client";

import Link from "next/link";
import { useRouter } from "next/navigation";
import { useEffect, useRef, useState } from "react";
import { addHistory, getLastPage, setLastPage } from "@/lib/storage";

type Props = {
  mangaSlug: string;
  mangaTitle: string;
  chapterTitle: string;
  chapterUrl: string;
  imageUrls: string[];
  prevHref: string | null;
  nextHref: string | null;
};

export default function Reader(props: Props) {
  const { mangaSlug, mangaTitle, chapterTitle, chapterUrl, imageUrls, prevHref, nextHref } = props;
  const router = useRouter();
  const [showBars, setShowBars] = useState(true);
  const [page, setPage] = useState(1);
  const pageRefs = useRef<(HTMLDivElement | null)[]>([]);

  // Record the read and jump back to the saved page.
  useEffect(() => {
    addHistory(mangaSlug, chapterUrl, chapterTitle, mangaTitle);
    const saved = getLastPage(mangaSlug, chapterUrl);
    if (saved > 0 && saved < imageUrls.length) {
      pageRefs.current[saved]?.scrollIntoView({ block: "start" });
    } else {
      window.scrollTo(0, 0);
    }
  }, [mangaSlug, chapterUrl, chapterTitle, mangaTitle, imageUrls.length]);

  // Track the current page and persist it (debounced).
  useEffect(() => {
    let timer: ReturnType<typeof setTimeout> | undefined;
    const observer = new IntersectionObserver(
      (entries) => {
        for (const entry of entries) {
          if (!entry.isIntersecting) continue;
          const index = Number((entry.target as HTMLElement).dataset.index);
          setPage(index + 1);
          clearTimeout(timer);
          timer = setTimeout(() => setLastPage(mangaSlug, chapterUrl, index), 800);
        }
      },
      // A page counts as current once it crosses the upper third of the screen.
      { rootMargin: "-30% 0px -69% 0px" },
    );
    pageRefs.current.forEach((el) => el && observer.observe(el));
    return () => {
      observer.disconnect();
      clearTimeout(timer);
    };
  }, [mangaSlug, chapterUrl, imageUrls.length]);

  // Keyboard navigation on desktop.
  useEffect(() => {
    const onKey = (e: KeyboardEvent) => {
      if (e.key === "ArrowRight" && nextHref) router.push(nextHref);
      if (e.key === "ArrowLeft" && prevHref) router.push(prevHref);
    };
    window.addEventListener("keydown", onKey);
    return () => window.removeEventListener("keydown", onKey);
  }, [router, nextHref, prevHref]);

  return (
    <div className="reader">
      <header className={`reader-bar top ${showBars ? "" : "hidden"}`}>
        <Link href={`/manga/${mangaSlug}`} className="icon-btn" aria-label="Kembali ke detail">
          ←
        </Link>
        <div className="reader-titles">
          <span className="muted small">{mangaTitle}</span>
          <strong>{chapterTitle}</strong>
        </div>
      </header>

      {imageUrls.length === 0 ? (
        <p className="center muted pad">Tidak ada gambar di chapter ini.</p>
      ) : (
        <div className="pages" onClick={() => setShowBars((s) => !s)}>
          {imageUrls.map((url, i) => (
            <div
              key={url}
              className="page"
              data-index={i}
              ref={(el) => {
                pageRefs.current[i] = el;
              }}
            >
              {/* eslint-disable-next-line @next/next/no-img-element -- images are served by the source CDN */}
              <img
                src={url}
                alt={`Halaman ${i + 1}`}
                loading={i < 3 ? "eager" : "lazy"}
                referrerPolicy="no-referrer"
                onLoad={(e) => e.currentTarget.parentElement?.classList.add("loaded")}
              />
            </div>
          ))}
        </div>
      )}

      <div className="end-of-chapter">
        <p>Akhir chapter</p>
        {nextHref ? (
          <Link href={nextHref} className="btn primary wide">
            Chapter Selanjutnya →
          </Link>
        ) : (
          <>
            <p className="muted small">Ini chapter terbaru</p>
            <Link href={`/manga/${mangaSlug}`} className="btn wide">
              Kembali ke Detail
            </Link>
          </>
        )}
      </div>

      <footer className={`reader-bar bottom ${showBars ? "" : "hidden"}`}>
        {prevHref ? (
          <Link href={prevHref} className="icon-btn" aria-label="Chapter sebelumnya">
            ⏮
          </Link>
        ) : (
          <span className="icon-btn disabled">⏮</span>
        )}
        <span className="page-indicator">
          {Math.min(page, imageUrls.length)} / {imageUrls.length}
        </span>
        {nextHref ? (
          <Link href={nextHref} className="icon-btn" aria-label="Chapter selanjutnya">
            ⏭
          </Link>
        ) : (
          <span className="icon-btn disabled">⏭</span>
        )}
      </footer>
    </div>
  );
}

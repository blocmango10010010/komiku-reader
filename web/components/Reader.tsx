"use client";

import Link from "next/link";
import { useRouter } from "next/navigation";
import { useEffect, useRef, useState } from "react";
import { shortChapter } from "@/lib/format";
import { addHistory, getLastPage, setLastPage } from "@/lib/storage";
import Icon from "./Icons";

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
  const total = imageUrls.length;

  // Record the read and jump back to the saved page.
  useEffect(() => {
    addHistory(mangaSlug, chapterUrl, chapterTitle, mangaTitle);
    const saved = getLastPage(mangaSlug, chapterUrl);
    if (saved > 0 && saved < total) {
      pageRefs.current[saved]?.scrollIntoView({ block: "start" });
    } else {
      window.scrollTo(0, 0);
    }
  }, [mangaSlug, chapterUrl, chapterTitle, mangaTitle, total]);

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
  }, [mangaSlug, chapterUrl, total]);

  // Keyboard navigation on desktop.
  useEffect(() => {
    const onKey = (e: KeyboardEvent) => {
      if (e.key === "ArrowRight" && nextHref) router.push(nextHref);
      if (e.key === "ArrowLeft" && prevHref) router.push(prevHref);
    };
    window.addEventListener("keydown", onKey);
    return () => window.removeEventListener("keydown", onKey);
  }, [router, nextHref, prevHref]);

  const progress = total ? (Math.min(page, total) / total) * 100 : 0;
  const detailHref = `/manga/${mangaSlug}`;

  return (
    <div className="reader">
      <header className={`reader-top ${showBars ? "" : "is-hidden"}`}>
        <Link href={detailHref} className="icon-btn" aria-label="Kembali ke detail">
          <Icon name="arrow-left" />
        </Link>
        <div className="reader-titles">
          <span>{mangaTitle}</span>
          <strong>{shortChapter(chapterTitle)}</strong>
        </div>
        <Link href={detailHref} className="icon-btn" aria-label="Daftar chapter">
          <Icon name="list" />
        </Link>
        <div className="reader-progress" aria-hidden="true">
          <span style={{ width: `${progress}%` }} />
        </div>
      </header>

      {total === 0 ? (
        <div className="state-box">
          <p>Tidak ada gambar di chapter ini.</p>
        </div>
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
              {/* eslint-disable-next-line @next/next/no-img-element -- pages are served by the source CDN */}
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

      <section className="chapter-end">
        <span className="label">Selesai</span>
        <h2>{shortChapter(chapterTitle)}</h2>
        {nextHref ? (
          <Link href={nextHref} className="btn btn-primary btn-lg wide">
            Chapter Selanjutnya <Icon name="chevron-right" size={18} />
          </Link>
        ) : (
          <p className="muted">Kamu sudah di chapter terbaru.</p>
        )}
        <Link href={detailHref} className="btn btn-lg wide">
          Kembali ke Detail
        </Link>
      </section>

      <nav className={`reader-dock ${showBars ? "" : "is-hidden"}`} aria-label="Navigasi chapter">
        {prevHref ? (
          <Link href={prevHref} className="dock-btn" aria-label="Chapter sebelumnya">
            <Icon name="chevron-left" size={22} />
          </Link>
        ) : (
          <span className="dock-btn is-disabled" aria-hidden="true">
            <Icon name="chevron-left" size={22} />
          </span>
        )}
        <span className="dock-page">
          {Math.min(page, total)} <span>/ {total}</span>
        </span>
        {nextHref ? (
          <Link href={nextHref} className="dock-btn" aria-label="Chapter selanjutnya">
            <Icon name="chevron-right" size={22} />
          </Link>
        ) : (
          <span className="dock-btn is-disabled" aria-hidden="true">
            <Icon name="chevron-right" size={22} />
          </span>
        )}
      </nav>
    </div>
  );
}

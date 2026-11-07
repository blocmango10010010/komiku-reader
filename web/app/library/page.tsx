import type { Metadata } from "next";
import { Library } from "@/components/LibraryClient";

export const metadata: Metadata = { title: "Koleksi" };

export default async function LibraryPage({ searchParams }: { searchParams: Promise<{ tab?: string }> }) {
  const tab = (await searchParams).tab === "history" ? "history" : "bookmarks";
  return (
    <div className="stack">
      <header className="page-head">
        <h1>Koleksi</h1>
        <span className="muted small">Tersimpan di browser ini</span>
      </header>
      <Library key={tab} initialTab={tab} />
    </div>
  );
}

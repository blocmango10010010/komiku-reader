import type { Metadata } from "next";
import { Library } from "@/components/LibraryClient";

export const metadata: Metadata = { title: "Koleksi" };

export default function LibraryPage() {
  return (
    <>
      <h1 className="page-title">Koleksi Saya</h1>
      <p className="muted small">Bookmark dan riwayat disimpan di browser ini.</p>
      <Library />
    </>
  );
}

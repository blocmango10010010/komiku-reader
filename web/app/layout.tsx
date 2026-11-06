import type { Metadata, Viewport } from "next";
import Link from "next/link";
import "./globals.css";

export const metadata: Metadata = {
  title: { default: "Komiku Reader", template: "%s · Komiku Reader" },
  description: "Baca manga, manhwa, dan manhua Bahasa Indonesia.",
};

export const viewport: Viewport = {
  themeColor: "#121212",
  width: "device-width",
  initialScale: 1,
};

export default function RootLayout({ children }: { children: React.ReactNode }) {
  return (
    <html lang="id">
      <body>
        <header className="topbar">
          <Link href="/" className="brand">
            Komiku Reader
          </Link>
          <nav>
            <Link href="/browse">Jelajah</Link>
            <Link href="/search">Cari</Link>
            <Link href="/library">Koleksi</Link>
          </nav>
        </header>
        <main className="container">{children}</main>
      </body>
    </html>
  );
}

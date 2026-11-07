"use client";

import Link from "next/link";
import { usePathname } from "next/navigation";
import Icon, { type IconName } from "./Icons";

const TABS: { href: string; label: string; icon: IconName }[] = [
  { href: "/", label: "Beranda", icon: "home" },
  { href: "/browse", label: "Jelajah", icon: "compass" },
  { href: "/search", label: "Cari", icon: "search" },
  { href: "/library", label: "Koleksi", icon: "library" },
];

const isActive = (pathname: string, href: string) =>
  href === "/" ? pathname === "/" : pathname === href || pathname.startsWith(`${href}/`);

export function TopBar() {
  const pathname = usePathname();
  // The reader has its own full-screen chrome.
  if (pathname.startsWith("/read/")) return null;
  return (
    <header className="topbar">
      <div className="topbar-inner">
        <Link href="/" className="brand" aria-label="Komiku Reader, beranda">
          <span className="brand-mark">
            <Icon name="book" size={18} />
          </span>
          <span className="brand-name">
            Komiku<span>Reader</span>
          </span>
        </Link>
        <nav className="topnav" aria-label="Navigasi utama">
          {TABS.map((t) => (
            <Link key={t.href} href={t.href} className={isActive(pathname, t.href) ? "active" : ""}>
              <Icon name={t.icon} size={18} />
              {t.label}
            </Link>
          ))}
        </nav>
        <Link href="/search" className="icon-btn topbar-search" aria-label="Cari komik">
          <Icon name="search" />
        </Link>
      </div>
    </header>
  );
}

export function BottomNav() {
  const pathname = usePathname();
  if (pathname.startsWith("/read/")) return null;
  return (
    <nav className="bottomnav" aria-label="Navigasi utama">
      {TABS.map((t) => {
        const active = isActive(pathname, t.href);
        return (
          <Link key={t.href} href={t.href} className={active ? "active" : ""} aria-current={active ? "page" : undefined}>
            <Icon name={t.icon} size={22} filled={active && t.icon === "library"} />
            <span>{t.label}</span>
          </Link>
        );
      })}
    </nav>
  );
}

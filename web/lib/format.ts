// Formatting helpers shared by server and client components.

/** komiku's thumbnail CDN crops on `?resize=W,H`; this gives every cover the same portrait ratio. */
export function coverUrl(url: string, width = 300, height = 400): string {
  if (!url) return "";
  try {
    const u = new URL(url);
    if (!/(^|\.)komiku\.(org|to)$/.test(u.hostname) || !u.hostname.startsWith("thumbnail.")) return url;
    u.search = `?resize=${width},${height}`;
    return u.toString();
  } catch {
    return url;
  }
}

/** "/ao-ashi-chapter-2/" -> "/read/ao-ashi-chapter-2" */
export function readHref(chapterUrl: string): string {
  return `/read/${chapterUrl.replace(/^\/+|\/+$/g, "")}`;
}

export function timeAgo(ts: number, now = Date.now()): string {
  const s = Math.max(0, Math.round((now - ts) / 1000));
  if (s < 60) return "baru saja";
  const m = Math.round(s / 60);
  if (m < 60) return `${m} menit lalu`;
  const h = Math.round(m / 60);
  if (h < 24) return `${h} jam lalu`;
  const d = Math.round(h / 24);
  if (d < 30) return `${d} hari lalu`;
  return new Date(ts).toLocaleDateString("id-ID", { day: "numeric", month: "short", year: "numeric" });
}

/** "Chapter 1198" or "Ao Ashi Chapter 12" -> "Ch. 1198" / "Ch. 12" for compact buttons. */
export function shortChapter(title: string): string {
  const m = /chapter\s*([\w.-]+)/i.exec(title);
  return m ? `Ch. ${m[1]}` : title.trim();
}

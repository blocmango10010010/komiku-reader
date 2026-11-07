import "server-only";
import * as cheerio from "cheerio";
import type { CheerioAPI } from "cheerio";

const UA =
  "Mozilla/5.0 (Linux; Android 13; Pixel 7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36";

const sleep = (ms: number) => new Promise((r) => setTimeout(r, ms));

/** Fetches and parses an HTML page, cached by Next for `revalidate` seconds. Null on 404 or failure. */
export async function fetchDoc(url: string, revalidate: number): Promise<CheerioAPI | null> {
  for (let attempt = 1; attempt <= 2; attempt++) {
    try {
      const res = await fetch(url, {
        headers: {
          "User-Agent": UA,
          Accept: "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8",
          "Accept-Language": "id-ID,id;q=0.9,en;q=0.8",
        },
        next: { revalidate },
      });
      if (res.status === 404) return null;
      const body = await res.text();
      if (res.status === 503 || /ddos-guard/i.test(body)) {
        await sleep(1000 * attempt);
        continue;
      }
      if (!res.ok || !body.trim()) return null;
      return cheerio.load(body, { baseURI: url });
    } catch {
      if (attempt < 2) await sleep(1000 * attempt);
    }
  }
  return null;
}

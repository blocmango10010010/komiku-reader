# Komiku Reader (web)

Web version of the Android app. Next.js App Router. Pages are rendered on the server, which
fetches and parses komiku.org (browsers can't fetch the site directly because of CORS).
Bookmarks and reading history are stored in the browser's `localStorage`.

## Run locally

```bash
cd web
npm install
npm run dev        # http://localhost:3000
```

## Deploy to Vercel

1. Import this repository at https://vercel.com/new.
2. Set **Root Directory** to `web`. The framework is detected as Next.js automatically.
3. Deploy. No environment variables are needed.

Responses from komiku.org are cached on the server (lists 10 min, detail 5 min, chapters 30 min)
to keep request volume low. If the site's DDoS protection blocks the host's IP, pages show a
"Gagal memuat" message.

## Sources

Titles come from **komiku.org** and **mangaluv.com**. Lists, search and reading history are
merged by normalised title (`lib/titles.ts`), so a title available on both appears once. The
detail page offers the other source under "Sumber". Refs for non-komiku sources are prefixed
(`ml~<slug>`, `ml~<slug>~chapter-<id>`); bare komiku slugs keep working.

To add a source, write a scraper like `lib/mangaluv.ts` and wire it into `lib/sources.ts`.
Sites behind Cloudflare challenges or locked APIs are deliberately not supported.

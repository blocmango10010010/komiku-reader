// Turns the relative times sources print ("3 menit lalu", "9 jam", "1 year ago") into timestamps,
// so updates from different sites can be sorted together.

const UNITS: [RegExp, number][] = [
  // Order matters: "minggu"/"month" must be checked before "menit"/"min".
  [/^(detik|seconds?|secs?)$/, 1e3],
  [/^(minggu|weeks?)$/, 7 * 864e5],
  [/^(bulan|bln|months?)$/, 30 * 864e5],
  [/^(tahun|thn|years?)$/, 365 * 864e5],
  [/^(menit|mnt|minutes?|mins?)$/, 6e4],
  [/^(jam|hours?|hrs?)$/, 36e5],
  [/^(hari|days?)$/, 864e5],
];

export function parseRelative(input: string | undefined, now = Date.now()): number | undefined {
  if (!input) return undefined;
  const s = input
    .toLowerCase()
    .replace(/\b(an?|se)\s+(?=(hour|day|minute|month|year|week|second|jam|hari|menit|bulan|tahun|minggu|detik))/g, "1 ");
  if (/baru saja|just now/.test(s)) return now;
  if (/kemarin|yesterday/.test(s)) return now - 864e5;
  for (const m of s.matchAll(/(\d+)\s*([a-z]+)/g)) {
    const unit = UNITS.find(([re]) => re.test(m[2]));
    if (unit) return now - Number(m[1]) * unit[1];
  }
  const abs = Date.parse(input);
  return Number.isNaN(abs) ? undefined : abs;
}

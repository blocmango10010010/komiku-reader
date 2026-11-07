// Client-safe helpers for recognising the same title across sources.
import type { SourceId } from "./types";

export const SOURCE_NAMES: Record<SourceId, string> = {
  komiku: "Komiku",
  mangaluv: "MangaLuv",
};

/**
 * Normalised title used to detect duplicates: "One Punch-Man" and "One Punch Man",
 * or "Komik Ao Ashi" and "Ao Ashi", produce the same key.
 */
export function titleKey(title: string): string {
  return title
    .normalize("NFKD")
    .replace(/[\u0300-\u036f]/g, "")
    .toLowerCase()
    .replace(/&/g, " and ")
    .replace(/['’`]/g, "")
    .replace(/[^\p{L}\p{N}]+/gu, " ")
    .trim()
    .replace(/^(komik|manga|manhwa|manhua)\s+/, "")
    .replace(/\s+(bahasa indonesia|sub indo|indo)$/, "")
    .replace(/\s+/g, " ");
}

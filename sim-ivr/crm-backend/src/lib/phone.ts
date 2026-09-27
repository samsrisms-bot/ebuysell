/** Normalizes a phone number to digits + a leading "+" (if present) so lookups/dedup are consistent regardless of formatting. */
export function normalizePhone(raw: string): string {
  const trimmed = raw.trim();
  const plus = trimmed.startsWith('+') ? '+' : '';
  return plus + trimmed.replace(/[^\d]/g, '');
}

/** Builds the idempotency key a CallEvent is deduped on. */
export function buildDedupeKey(params: { number: string; campaignId: string | null; startedAt: number }): string {
  return `${normalizePhone(params.number)}|${params.campaignId ?? 'none'}|${params.startedAt}`;
}

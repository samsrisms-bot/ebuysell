import { parse } from 'csv-parse/sync';
import { prisma } from '../lib/prisma';
import { normalizePhone } from '../lib/phone';

export interface CsvImportResult {
  imported: number;
  skippedOptedOut: number;
  skippedInvalid: number;
}

/**
 * Parses a "name,phone,...custom columns" CSV (same shape the Android app's own CSV importer
 * expects) and bulk-inserts contacts for [campaignId], skipping any number already opted out.
 */
export async function importContactsCsv(campaignId: string, csvText: string): Promise<CsvImportResult> {
  const rows: Record<string, string>[] = parse(csvText, {
    columns: (header: string[]) => header.map((h) => h.trim().toLowerCase()),
    skip_empty_lines: true,
    trim: true
  });

  const optOuts = await prisma.optOut.findMany({ select: { phone: true } });
  const optedOutPhones = new Set(optOuts.map((o) => o.phone));

  let skippedInvalid = 0;
  let skippedOptedOut = 0;
  const toInsert: { campaignId: string; name: string; phone: string; vars: Record<string, string> }[] = [];

  for (const row of rows) {
    const rawPhone = row.phone;
    if (!rawPhone || rawPhone.trim().length < 3) {
      skippedInvalid++;
      continue;
    }
    const phone = normalizePhone(rawPhone);
    if (optedOutPhones.has(phone)) {
      skippedOptedOut++;
      continue;
    }
    const vars: Record<string, string> = {};
    for (const [key, value] of Object.entries(row)) {
      if (key !== 'name' && key !== 'phone') vars[key] = value;
    }
    toInsert.push({ campaignId, name: row.name ?? '', phone, vars });
  }

  if (toInsert.length > 0) {
    await prisma.contact.createMany({
      data: toInsert.map((c) => ({ ...c, vars: c.vars }))
    });
  }

  return { imported: toInsert.length, skippedOptedOut, skippedInvalid };
}

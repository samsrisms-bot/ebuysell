import { prisma } from '../lib/prisma';
import { buildDedupeKey, normalizePhone } from '../lib/phone';
import { CallEventInput } from '../lib/schemas';
import { logger, maskPhone } from '../lib/logger';

export interface ProcessedEvent {
  callEventId: string;
  wasDuplicate: boolean;
  leadCreated: boolean;
  optedOut: boolean;
}

/**
 * Records one CallCrmEvent from the SIM IVR app. Idempotent: the app retries failed posts from
 * its offline outbox, so the same (number, campaignId, startedAt) triple can arrive more than
 * once — it's upserted onto one row, never duplicated, and a callback_request's Lead is only
 * ever created once per source event.
 */
export async function processIvrEvent(input: CallEventInput): Promise<ProcessedEvent> {
  const number = normalizePhone(input.number);
  const dedupeKey = buildDedupeKey({ number, campaignId: input.campaignId, startedAt: input.startedAt });

  const existing = await prisma.callEvent.findUnique({ where: { dedupeKey } });
  const wasDuplicate = existing !== null;

  const callEvent = await prisma.callEvent.upsert({
    where: { dedupeKey },
    create: {
      dedupeKey,
      event: input.event,
      direction: input.direction,
      number,
      sim: input.sim,
      flow: input.flow,
      digits: input.digits,
      durationMs: input.durationMs,
      startedAt: new Date(input.startedAt),
      endedAt: new Date(input.endedAt),
      campaignId: input.campaignId,
      vars: input.vars
    },
    update: {
      digits: input.digits,
      durationMs: input.durationMs,
      endedAt: new Date(input.endedAt),
      vars: input.vars
    }
  });

  let optedOut = false;
  if (input.campaignId && input.digits.includes('9')) {
    optedOut = true;
    await applyOptOut(number, input.campaignId);
  }

  let leadCreated = false;
  if (input.event === 'callback_request') {
    leadCreated = await createLeadIfMissing(callEvent.id, number, input.vars, input.campaignId);
  }

  logger.info(
    { event: input.event, direction: input.direction, number: maskPhone(number), campaignId: input.campaignId, wasDuplicate, optedOut, leadCreated },
    'processed ivr event'
  );

  return { callEventId: callEvent.id, wasDuplicate, leadCreated, optedOut };
}

async function applyOptOut(number: string, campaignId: string): Promise<void> {
  await prisma.optOut.upsert({
    where: { phone: number },
    create: { phone: number, reason: 'pressed 9 to opt out' },
    update: {}
  });
  await prisma.contact.updateMany({
    where: { campaignId, phone: number },
    data: { status: 'OPTED_OUT' }
  });
}

async function createLeadIfMissing(
  callEventId: string,
  number: string,
  vars: Record<string, string>,
  campaignId: string | null
): Promise<boolean> {
  const existingLead = await prisma.lead.findUnique({ where: { sourceCallEventId: callEventId } });
  if (existingLead) return false;

  await prisma.lead.create({
    data: {
      number,
      name: vars.name ?? null,
      campaignId,
      sourceCallEventId: callEventId
    }
  });
  return true;
}

/** Contacts for a campaign, with anyone on the opt-out list filtered out — used by GET /api/ivr/campaigns/:id/contacts. */
export async function getDialableContacts(campaignId: string) {
  const [contacts, optOuts] = await Promise.all([
    prisma.contact.findMany({ where: { campaignId }, orderBy: { createdAt: 'asc' } }),
    prisma.optOut.findMany({ select: { phone: true } })
  ]);
  const optedOutPhones = new Set(optOuts.map((o) => o.phone));
  return contacts
    .filter((c) => c.status !== 'OPTED_OUT' && !optedOutPhones.has(normalizePhone(c.phone)))
    .map((c) => ({ name: c.name, phone: c.phone, vars: c.vars as Record<string, string> }));
}

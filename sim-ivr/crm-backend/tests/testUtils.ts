import { prisma } from '../src/lib/prisma';

/** Wipes all tables between tests, in FK-safe order. */
export async function resetDb() {
  await prisma.lead.deleteMany();
  await prisma.callEvent.deleteMany();
  await prisma.contact.deleteMany();
  await prisma.optOut.deleteMany();
  await prisma.campaign.deleteMany();
}

export function validEventPayload(overrides: Partial<Record<string, unknown>> = {}) {
  return {
    event: 'call_result',
    direction: 'incoming',
    number: '+919000000001',
    sim: 0,
    flow: 'sample-incoming',
    digits: ['1'],
    durationMs: 18234,
    startedAt: 1732600000000,
    endedAt: 1732600018234,
    campaignId: null,
    vars: { name: 'Asha' },
    ...overrides
  };
}

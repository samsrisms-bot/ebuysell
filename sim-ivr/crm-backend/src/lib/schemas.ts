import { z } from 'zod';

// Mirrors the SIM IVR Android app's CallCrmEvent exactly (see sim-ivr/app .../sync/model/CrmModels.kt).
export const callEventSchema = z.object({
  event: z.enum(['call_result', 'callback_request']),
  direction: z.enum(['incoming', 'outgoing']),
  number: z.string().min(3),
  sim: z.number().int().min(0).max(1),
  flow: z.string().min(1),
  digits: z.array(z.string()).default([]),
  durationMs: z.number().int().min(0),
  startedAt: z.number().int().positive(),
  endedAt: z.number().int().positive(),
  campaignId: z.string().uuid().nullable().optional().default(null),
  vars: z.record(z.string()).default({})
});
export type CallEventInput = z.infer<typeof callEventSchema>;

export const createCampaignSchema = z.object({
  name: z.string().min(1).max(200)
});

export const updateLeadSchema = z.object({
  status: z.enum(['NEW', 'CONTACTED', 'CLOSED'])
});

export const contactRowSchema = z.object({
  name: z.string().default(''),
  phone: z.string().min(3),
  vars: z.record(z.string()).default({})
});

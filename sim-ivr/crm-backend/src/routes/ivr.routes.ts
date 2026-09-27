import { Router } from 'express';
import { requireAppApiKey } from '../middleware/auth';
import { ivrRateLimiter } from '../middleware/rateLimiter';
import { validateBody } from '../middleware/validate';
import { asyncHandler } from '../middleware/errorHandler';
import { callEventSchema } from '../lib/schemas';
import { getDialableContacts, processIvrEvent } from '../services/ivrEvents.service';
import { prisma } from '../lib/prisma';

export const ivrRouter = Router();

ivrRouter.use(ivrRateLimiter, requireAppApiKey);

// POST /api/ivr/events — every call result + every callback_request/opt-out from the app.
ivrRouter.post(
  '/events',
  validateBody(callEventSchema),
  asyncHandler(async (req, res) => {
    const result = await processIvrEvent(req.body);
    res.status(200).json({ ok: true, ...result });
  })
);

// GET /api/ivr/campaigns/:id/contacts — contact list to dial, opt-outs excluded.
ivrRouter.get(
  '/campaigns/:id/contacts',
  asyncHandler(async (req, res) => {
    const campaign = await prisma.campaign.findUnique({ where: { id: req.params.id } });
    if (!campaign) return res.status(404).json({ error: 'campaign not found' });
    const contacts = await getDialableContacts(campaign.id);
    res.status(200).json(contacts);
  })
);

import { Router } from 'express';
import { requireAdminToken } from '../middleware/auth';
import { adminRateLimiter } from '../middleware/rateLimiter';
import { asyncHandler } from '../middleware/errorHandler';
import { prisma } from '../lib/prisma';
import { Prisma } from '@prisma/client';

export const eventsRouter = Router();
eventsRouter.use(adminRateLimiter, requireAdminToken);

eventsRouter.get(
  '/',
  asyncHandler(async (req, res) => {
    const campaignId = req.query.campaignId as string | undefined;
    const limit = Math.min(Number(req.query.limit) || 50, 200);
    const where: Prisma.CallEventWhereInput = campaignId ? { campaignId } : {};
    const events = await prisma.callEvent.findMany({
      where,
      orderBy: { receivedAt: 'desc' },
      take: limit
    });
    res.json(events);
  })
);

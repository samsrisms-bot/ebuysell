import { Router } from 'express';
import { requireAdminToken } from '../middleware/auth';
import { adminRateLimiter } from '../middleware/rateLimiter';
import { validateBody } from '../middleware/validate';
import { asyncHandler } from '../middleware/errorHandler';
import { updateLeadSchema } from '../lib/schemas';
import { prisma } from '../lib/prisma';
import { LeadStatus, Prisma } from '@prisma/client';

export const leadsRouter = Router();
leadsRouter.use(adminRateLimiter, requireAdminToken);

leadsRouter.get(
  '/',
  asyncHandler(async (req, res) => {
    const statusParam = req.query.status as string | undefined;
    const where: Prisma.LeadWhereInput = {};
    if (statusParam) {
      if (!Object.values(LeadStatus).includes(statusParam as LeadStatus)) {
        return res.status(400).json({ error: `invalid status, expected one of ${Object.values(LeadStatus).join(', ')}` });
      }
      where.status = statusParam as LeadStatus;
    }
    const leads = await prisma.lead.findMany({
      where,
      orderBy: { createdAt: 'desc' },
      include: { sourceCallEvent: true, campaign: { select: { name: true } } }
    });
    res.json(leads);
  })
);

leadsRouter.patch(
  '/:id',
  validateBody(updateLeadSchema),
  asyncHandler(async (req, res) => {
    const lead = await prisma.lead.findUnique({ where: { id: req.params.id } });
    if (!lead) return res.status(404).json({ error: 'lead not found' });
    const updated = await prisma.lead.update({ where: { id: lead.id }, data: { status: req.body.status } });
    res.json(updated);
  })
);

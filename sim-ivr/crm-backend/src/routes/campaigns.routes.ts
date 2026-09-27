import { Router } from 'express';
import multer from 'multer';
import { requireAdminToken } from '../middleware/auth';
import { adminRateLimiter } from '../middleware/rateLimiter';
import { validateBody } from '../middleware/validate';
import { asyncHandler } from '../middleware/errorHandler';
import { createCampaignSchema } from '../lib/schemas';
import { prisma } from '../lib/prisma';
import { importContactsCsv } from '../services/csvImport.service';

const upload = multer({ storage: multer.memoryStorage(), limits: { fileSize: 5 * 1024 * 1024 } });

export const campaignsRouter = Router();
campaignsRouter.use(adminRateLimiter, requireAdminToken);

campaignsRouter.get(
  '/',
  asyncHandler(async (_req, res) => {
    const campaigns = await prisma.campaign.findMany({
      orderBy: { createdAt: 'desc' },
      include: { _count: { select: { contacts: true, leads: true } } }
    });
    res.json(campaigns);
  })
);

campaignsRouter.post(
  '/',
  validateBody(createCampaignSchema),
  asyncHandler(async (req, res) => {
    const campaign = await prisma.campaign.create({ data: { name: req.body.name } });
    res.status(201).json(campaign);
  })
);

campaignsRouter.get(
  '/:id',
  asyncHandler(async (req, res) => {
    const campaign = await prisma.campaign.findUnique({ where: { id: req.params.id } });
    if (!campaign) return res.status(404).json({ error: 'campaign not found' });

    const [total, pending, dialed, optedOut, leadCount] = await Promise.all([
      prisma.contact.count({ where: { campaignId: campaign.id } }),
      prisma.contact.count({ where: { campaignId: campaign.id, status: 'PENDING' } }),
      prisma.contact.count({ where: { campaignId: campaign.id, status: 'DIALED' } }),
      prisma.contact.count({ where: { campaignId: campaign.id, status: 'OPTED_OUT' } }),
      prisma.lead.count({ where: { campaignId: campaign.id } })
    ]);

    res.json({ ...campaign, contactCounts: { total, pending, dialed, optedOut }, leadCount });
  })
);

campaignsRouter.post(
  '/:id/contacts/csv',
  upload.single('file'),
  asyncHandler(async (req, res) => {
    const campaign = await prisma.campaign.findUnique({ where: { id: req.params.id } });
    if (!campaign) return res.status(404).json({ error: 'campaign not found' });
    if (!req.file) return res.status(400).json({ error: 'missing "file" field (multipart CSV upload)' });

    const result = await importContactsCsv(campaign.id, req.file.buffer.toString('utf-8'));
    res.status(200).json(result);
  })
);

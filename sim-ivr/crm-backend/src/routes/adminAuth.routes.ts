import { Router } from 'express';
import { requireAdminToken } from '../middleware/auth';
import { adminRateLimiter } from '../middleware/rateLimiter';

export const adminAuthRouter = Router();

// The dashboard's login form POSTs the token here just to confirm it's valid before storing it
// client-side; requireAdminToken does the actual check.
adminAuthRouter.post('/login', adminRateLimiter, requireAdminToken, (_req, res) => {
  res.status(200).json({ ok: true });
});

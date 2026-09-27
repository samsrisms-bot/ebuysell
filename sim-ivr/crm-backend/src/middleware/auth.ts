import { NextFunction, Request, Response } from 'express';

function extractBearer(header: string | undefined): string | null {
  if (!header) return null;
  return header.startsWith('Bearer ') ? header.slice(7) : null;
}

/** Protects the SIM IVR app's own endpoints (/api/ivr/*). Accepts either header, matching the app's Settings screen. */
export function requireAppApiKey(req: Request, res: Response, next: NextFunction) {
  const expected = process.env.API_KEY;
  if (!expected) {
    return res.status(500).json({ error: 'server misconfigured: API_KEY is not set' });
  }
  const bearer = extractBearer(req.get('authorization') ?? undefined);
  const apiKeyHeader = req.get('x-api-key');
  if (bearer === expected || apiKeyHeader === expected) return next();
  return res.status(401).json({ error: 'invalid or missing API key' });
}

/** Protects the human-facing admin/dashboard endpoints (/api/campaigns, /api/leads, /api/events). */
export function requireAdminToken(req: Request, res: Response, next: NextFunction) {
  const expected = process.env.ADMIN_TOKEN;
  if (!expected) {
    return res.status(500).json({ error: 'server misconfigured: ADMIN_TOKEN is not set' });
  }
  const bearer = extractBearer(req.get('authorization') ?? undefined);
  if (bearer === expected) return next();
  return res.status(401).json({ error: 'invalid or missing admin token' });
}

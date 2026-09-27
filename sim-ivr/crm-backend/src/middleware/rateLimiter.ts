import rateLimit from 'express-rate-limit';

// The /api/ivr/* endpoints are reachable from the internet by a phone with the API key —
// generous enough for real campaign traffic, tight enough to blunt abuse of a leaked key.
export const ivrRateLimiter = rateLimit({
  windowMs: 60 * 1000,
  limit: 120,
  standardHeaders: true,
  legacyHeaders: false,
  message: { error: 'rate limit exceeded, slow down' }
});

export const adminRateLimiter = rateLimit({
  windowMs: 60 * 1000,
  limit: 300,
  standardHeaders: true,
  legacyHeaders: false,
  message: { error: 'rate limit exceeded, slow down' }
});

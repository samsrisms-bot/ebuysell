import pino from 'pino';

// Redact the API key / admin token and full phone numbers from every log line by default —
// full detail (including un-masked numbers) is only ever logged at debug level, deliberately,
// via logger.debug() call sites, never at info/warn/error.
export const logger = pino({
  level: process.env.LOG_LEVEL || 'info',
  redact: {
    paths: [
      'req.headers.authorization',
      'req.headers["x-api-key"]',
      '*.apiKey',
      '*.adminToken'
    ],
    censor: '[redacted]'
  }
});

/** Masks a phone number to its last 4 digits for safe logging at info level, e.g. "+91******0001". */
export function maskPhone(phone: string): string {
  if (phone.length <= 4) return '*'.repeat(phone.length);
  return '*'.repeat(phone.length - 4) + phone.slice(-4);
}

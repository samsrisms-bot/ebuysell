import { NextFunction, Request, Response } from 'express';
import { logger } from '../lib/logger';

type AsyncHandler = (req: Request, res: Response, next: NextFunction) => Promise<unknown>;

/** Wraps an async route handler so a rejected promise reaches Express's error handler instead of crashing the process. */
export function asyncHandler(handler: AsyncHandler) {
  return (req: Request, res: Response, next: NextFunction) => {
    handler(req, res, next).catch(next);
  };
}

export function notFoundHandler(req: Request, res: Response) {
  res.status(404).json({ error: `no such route: ${req.method} ${req.path}` });
}

// eslint-disable-next-line @typescript-eslint/no-unused-vars
export function errorHandler(err: unknown, req: Request, res: Response, next: NextFunction) {
  logger.error({ err }, 'unhandled error');
  if (res.headersSent) return next(err);
  res.status(500).json({ error: 'internal server error' });
}

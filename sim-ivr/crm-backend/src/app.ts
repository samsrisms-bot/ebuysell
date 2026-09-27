import express, { Express } from 'express';
import cors from 'cors';
import path from 'path';
import pinoHttp from 'pino-http';
import { logger } from './lib/logger';
import { ivrRouter } from './routes/ivr.routes';
import { campaignsRouter } from './routes/campaigns.routes';
import { leadsRouter } from './routes/leads.routes';
import { eventsRouter } from './routes/events.routes';
import { healthRouter } from './routes/health.routes';
import { adminAuthRouter } from './routes/adminAuth.routes';
import { errorHandler, notFoundHandler } from './middleware/errorHandler';

export function createApp(): Express {
  const app = express();

  app.disable('x-powered-by');
  app.use(cors());
  app.use(express.json({ limit: '1mb' }));
  app.use(pinoHttp({ logger, autoLogging: process.env.NODE_ENV !== 'test' }));

  app.use('/health', healthRouter);
  app.use('/api/ivr', ivrRouter);
  app.use('/api/campaigns', campaignsRouter);
  app.use('/api/leads', leadsRouter);
  app.use('/api/events', eventsRouter);
  app.use('/api/admin', adminAuthRouter);

  app.use(express.static(path.join(__dirname, 'public')));

  app.use(notFoundHandler);
  app.use(errorHandler);

  return app;
}

import { PrismaClient } from '@prisma/client';

// A single shared PrismaClient per process (the standard pattern — creating one per request
// exhausts the connection pool under load).
export const prisma = new PrismaClient({
  log: process.env.NODE_ENV === 'development' ? ['warn', 'error'] : ['error']
});

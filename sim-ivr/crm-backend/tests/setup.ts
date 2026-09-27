import dotenv from 'dotenv';
import path from 'path';

// Must run (and finish mutating process.env) before any test file imports src/lib/prisma.ts,
// which reads DATABASE_URL at construction time — vitest runs setupFiles before the test
// file's own top-level code, so this ordering holds.
dotenv.config({ path: path.resolve(__dirname, '..', '.env.test'), override: true });

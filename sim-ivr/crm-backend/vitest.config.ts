import { defineConfig } from 'vitest/config';

export default defineConfig({
  test: {
    environment: 'node',
    setupFiles: ['./tests/setup.ts'],
    testTimeout: 20000,
    hookTimeout: 20000,
    // Run test files serially — they share one Postgres database and reset it between files.
    fileParallelism: false
  }
});

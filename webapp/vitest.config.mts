import { defineConfig } from 'vitest/config';
import react from '@vitejs/plugin-react';
import tsconfigPaths from 'vite-tsconfig-paths';

// Setup follows Next's own Vitest guide. Note its caveat: Vitest cannot render ASYNC Server
// Components, so those are covered by E2E tests instead (see docs/PRACTICE.md, track F).
// tsconfigPaths is what makes the "@/..." alias resolve inside tests.
export default defineConfig({
  plugins: [tsconfigPaths(), react()],
  test: {
    environment: 'jsdom',
    globals: true,
    setupFiles: ['./tests/setup.ts'],
  },
});

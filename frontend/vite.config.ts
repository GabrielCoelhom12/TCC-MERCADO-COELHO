import { defineConfig } from 'vitest/config';
import react from '@vitejs/plugin-react';

export default defineConfig({
  plugins: [react()],
  server: {
    port: 5173,
    proxy: {
      '/api': 'http://localhost:8080',
      // WebSocket do chat
      '/ws': { target: 'ws://localhost:8080', ws: true },
    },
  },
  build: {
    // O build vai direto para o Spring Boot servir tudo em uma única aplicação (piloto/shadow mode).
    outDir: '../backend/src/main/resources/static',
    emptyOutDir: true,
  },
  test: {
    environment: 'jsdom',
    setupFiles: ['./src/test/setup.ts'],
    coverage: {
      provider: 'v8',
      reporter: ['text-summary', 'lcov'],
      include: ['src/**/*.{ts,tsx}'],
      exclude: ['src/main.tsx', 'src/types.ts', 'src/test/**', 'src/**/*.test.{ts,tsx}'],
      // Cobertura mínima exigida pela linha Web Apps (frontend).
      thresholds: { lines: 25, statements: 25 },
    },
  },
});

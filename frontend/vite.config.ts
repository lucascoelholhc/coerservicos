import { readFileSync } from 'node:fs';
import react from '@vitejs/plugin-react';
import { defineConfig } from 'vitest/config';

/**
 * Os mesmos cabeçalhos de segurança do backend (CSP e Permissions-Policy), lidos de um arquivo só.
 * Valem no `vite preview` (o E2E pega violação de CSP); no `vite dev` não há CSP, porque o HMR
 * precisa de script inline.
 */
const cabecalhosDeSeguranca = JSON.parse(
  readFileSync(new URL('./cabecalhos-seguranca.json', import.meta.url), 'utf-8'),
) as Record<string, string>;

export default defineConfig({
  plugins: [react()],
  server: {
    port: 5173,
    strictPort: true,
    proxy: { '/api': 'http://localhost:8081' },
  },
  preview: {
    port: 4173,
    strictPort: true,
    headers: cabecalhosDeSeguranca,
  },
  test: {
    environment: 'jsdom',
    setupFiles: ['./src/teste/setup.ts'],
    include: ['src/**/*.test.{ts,tsx}'],
    coverage: {
      provider: 'v8',
      include: ['src/**/*.{ts,tsx}'],
      exclude: ['src/**/*.test.{ts,tsx}', 'src/teste/**', 'src/main.tsx', 'src/vite-env.d.ts'],
      thresholds: {
        lines: 80,
        functions: 80,
        branches: 80,
        statements: 80,
        'src/componentes/StatusDiaria/**': { lines: 100, functions: 100, branches: 100, statements: 100 },
      },
    },
  },
});

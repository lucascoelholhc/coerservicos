import { defineConfig, devices } from '@playwright/test';

/**
 * E2E contra o build de produção servido pelo `vite preview` (com a CSP e a Permissions-Policy de
 * cabecalhos-seguranca.json), no celular de 360 px e numa tela larga.
 */
export default defineConfig({
  testDir: 'e2e',
  testIgnore: 'comparacao/**',
  // Fora de test-results/comparacao/, que o Playwright não deve apagar
  outputDir: 'test-results/e2e',
  fullyParallel: true,
  forbidOnly: Boolean(process.env.CI),
  retries: process.env.CI ? 1 : 0,
  reporter: 'list',
  use: {
    baseURL: 'http://localhost:4173',
    trace: 'retain-on-failure',
  },
  projects: [
    { name: 'celular', use: { ...devices['Desktop Chrome'], viewport: { width: 360, height: 740 } } },
    { name: 'largo', use: { ...devices['Desktop Chrome'], viewport: { width: 1280, height: 800 } } },
  ],
  webServer: {
    command: 'npm run build && npm run preview',
    url: 'http://localhost:4173',
    reuseExistingServer: !process.env.CI,
    timeout: 120_000,
  },
});

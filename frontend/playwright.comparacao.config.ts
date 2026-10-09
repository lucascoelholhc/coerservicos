import { defineConfig, devices } from '@playwright/test';

/**
 * `npm run comparar`: captura o Início do protótipo (docs/prototipo, servido por HTTP na 4174) e o do
 * React (vite preview na 4173) em 360x740 e 1280x800, lado a lado, em test-results/comparacao/.
 * Não faz parte do `test:e2e` nem do build.
 */
export default defineConfig({
  testDir: 'e2e/comparacao',
  testMatch: '*.comparacao.ts',
  outputDir: 'test-results/comparacao-artefatos',
  reporter: 'list',
  use: { ...devices['Desktop Chrome'] },
  webServer: [
    {
      command: 'npm run build && npm run preview',
      url: 'http://localhost:4173',
      reuseExistingServer: true,
      timeout: 120_000,
    },
    {
      command: 'npm run prototipo',
      url: 'http://localhost:4174',
      reuseExistingServer: true,
    },
  ],
});

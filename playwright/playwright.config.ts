import { defineConfig, devices } from '@playwright/test';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

const playwrightRoot = path.dirname(fileURLToPath(import.meta.url));
const repositoryRoot = path.resolve(playwrightRoot, '..');
const profile = process.env.DCRIVELLA_PLAYWRIGHT_PROFILE ?? 'mock';

// The client home page intentionally renders tokens. Never copy its DOM into failure artifacts.
process.env.PLAYWRIGHT_NO_COPY_PROMPT = '1';

if (profile !== 'mock' && profile !== 'real') {
  throw new Error(`Unsupported DCRIVELLA_PLAYWRIGHT_PROFILE: ${profile}`);
}

const isMock = profile === 'mock';
const clientServerUrl =
  process.env.DCRIVELLA_PLAYWRIGHT_CLIENT_URL ??
  (isMock ? 'http://localhost:18080' : 'http://localhost:8080');
const authServerUrl =
  process.env.DCRIVELLA_PLAYWRIGHT_AUTH_SERVER_URL ??
  (isMock ? 'http://localhost:19000' : 'http://localhost:9000');
const resourceServerUrl =
  process.env.DCRIVELLA_PLAYWRIGHT_RESOURCE_SERVER_URL ??
  (isMock ? 'http://localhost:19001' : 'http://localhost:8081');
const playwrightUsername = process.env.DCRIVELLA_PLAYWRIGHT_USERNAME ?? 'playwright';
const playwrightPassword = process.env.DCRIVELLA_PLAYWRIGHT_PASSWORD ?? 'playwright-pass';
const outputDir = process.env.DCRIVELLA_PLAYWRIGHT_OUTPUT_DIR ?? './test-results';
const htmlReport = process.env.DCRIVELLA_PLAYWRIGHT_HTML_REPORT ?? 'playwright-report';

process.env.DCRIVELLA_PLAYWRIGHT_CLIENT_URL = clientServerUrl;
process.env.DCRIVELLA_PLAYWRIGHT_AUTH_SERVER_URL = authServerUrl;
process.env.DCRIVELLA_PLAYWRIGHT_RESOURCE_SERVER_URL = resourceServerUrl;
process.env.DCRIVELLA_PLAYWRIGHT_USERNAME = playwrightUsername;
process.env.DCRIVELLA_PLAYWRIGHT_PASSWORD = playwrightPassword;

export default defineConfig({
  testDir: './tests',
  globalSetup: './global-setup.ts',
  fullyParallel: false,
  forbidOnly: Boolean(process.env.CI),
  retries: process.env.CI ? 1 : 0,
  workers: 1,
  timeout: 45_000,
  expect: {
    timeout: 15_000,
  },
  outputDir,
  reporter: process.env.CI
    ? [
        ['line'],
        ['html', { open: 'never', outputFolder: htmlReport }],
        [
          'junit',
          {
            outputFile: path.join(playwrightRoot, 'test-results', 'playwright-junit.xml'),
            includeRetries: true,
          },
        ],
      ]
    : [
        ['list'],
        ['html', { open: 'never', outputFolder: htmlReport }],
      ],
  use: {
    baseURL: clientServerUrl,
    screenshot: 'off',
    trace: 'off',
    video: 'off',
  },
  projects: isMock
    ? [
        {
          name: 'mock',
          testMatch: /.*\/mock\/.*\.spec\.ts/,
          use: { ...devices['Desktop Chrome'] },
        },
      ]
    : [
        {
          name: 'real',
          testMatch: /.*\/real\/.*\.spec\.ts/,
          use: { ...devices['Desktop Chrome'] },
        },
      ],
  webServer: isMock
    ? {
        command: 'node scripts/mock-stack.mjs',
        cwd: playwrightRoot,
        url: `${clientServerUrl}/`,
        reuseExistingServer: false,
        timeout: 240_000,
        stdout: 'pipe',
        stderr: 'pipe',
        env: {
          DCRIVELLA_REPOSITORY_ROOT: repositoryRoot,
          DCRIVELLA_PLAYWRIGHT_CLIENT_URL: clientServerUrl,
          DCRIVELLA_PLAYWRIGHT_AUTH_SERVER_URL: authServerUrl,
          DCRIVELLA_PLAYWRIGHT_RESOURCE_SERVER_URL: resourceServerUrl,
          DCRIVELLA_PLAYWRIGHT_USERNAME: playwrightUsername,
          DCRIVELLA_PLAYWRIGHT_PASSWORD: playwrightPassword,
        },
      }
    : undefined,
});

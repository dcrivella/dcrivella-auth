const profile = process.env.DCRIVELLA_PLAYWRIGHT_PROFILE ?? 'mock';

if (profile !== 'mock' && profile !== 'real') {
  throw new Error(`Unsupported DCRIVELLA_PLAYWRIGHT_PROFILE: ${profile}`);
}

const isMock = profile === 'mock';

export const runtime = {
  profile,
  clientServerUrl: new URL(
    process.env.DCRIVELLA_PLAYWRIGHT_CLIENT_URL ??
      (isMock ? 'http://localhost:18080' : 'http://localhost:8080'),
  ),
  authServerUrl: new URL(
    process.env.DCRIVELLA_PLAYWRIGHT_AUTH_SERVER_URL ??
      (isMock ? 'http://localhost:19000' : 'http://localhost:9000'),
  ),
  resourceServerUrl: new URL(
    process.env.DCRIVELLA_PLAYWRIGHT_RESOURCE_SERVER_URL ??
      (isMock ? 'http://localhost:19001' : 'http://localhost:8081'),
  ),
  username: process.env.DCRIVELLA_PLAYWRIGHT_USERNAME ?? 'playwright',
  password: process.env.DCRIVELLA_PLAYWRIGHT_PASSWORD ?? 'playwright-pass',
};

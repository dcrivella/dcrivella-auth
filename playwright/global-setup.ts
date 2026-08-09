import type { FullConfig } from '@playwright/test';
import { runtime } from './tests/support/runtime';

const REQUEST_TIMEOUT_MS = 10_000;

async function fetchChecked(url: string, init?: RequestInit): Promise<Response> {
  try {
    return await fetch(url, {
      ...init,
      signal: AbortSignal.timeout(REQUEST_TIMEOUT_MS),
    });
  } catch (error) {
    throw new Error(`Playwright real preflight could not reach ${new URL(url).origin}`, {
      cause: error,
    });
  }
}

export default async function globalSetup(_config: FullConfig): Promise<void> {
  if (runtime.profile === 'mock') {
    return;
  }

  const discoveryResponse = await fetchChecked(
    new URL('/.well-known/openid-configuration', runtime.authServerUrl).toString(),
  );
  if (!discoveryResponse.ok) {
    throw new Error(
      `Playwright real preflight expected OIDC discovery HTTP 200, received ${discoveryResponse.status}`,
    );
  }

  const discovery = (await discoveryResponse.json()) as { issuer?: string };
  const expectedIssuer = runtime.authServerUrl.toString().replace(/\/$/, '');
  if (discovery.issuer !== expectedIssuer) {
    throw new Error(
      `Playwright real preflight expected issuer ${expectedIssuer}, received ${discovery.issuer ?? '<missing>'}`,
    );
  }

  const clientResponse = await fetchChecked(runtime.clientServerUrl.toString(), {
    redirect: 'manual',
  });
  if (clientResponse.status < 200 || clientResponse.status >= 400) {
    throw new Error(
      `Playwright real preflight expected the client server to be reachable, received HTTP ${clientResponse.status}`,
    );
  }

  const tasksResponse = await fetchChecked(
    new URL('/tasks', runtime.resourceServerUrl).toString(),
    { redirect: 'manual' },
  );
  if (tasksResponse.status !== 401) {
    throw new Error(
      `Playwright real preflight expected unauthenticated /tasks to return HTTP 401, received ${tasksResponse.status}`,
    );
  }

  console.log(
    `Playwright real preflight passed for ${process.env.DCRIVELLA_PLAYWRIGHT_RUNTIME ?? 'custom'} runtime (${expectedIssuer})`,
  );
}

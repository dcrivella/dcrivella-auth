import { expect, type Locator, type Page } from '@playwright/test';
import { createHash, randomBytes } from 'node:crypto';
import { runtime } from './runtime';

const PLAYWRIGHT_CONSENT_SCOPE = 'playwright.consent';
const PLAYWRIGHT_DENIAL_CLIENT_ID = 'playwright-consent-denial';
const CLIENT_REGISTRATION_ID = 'client-server-pkce-oidc';

type LoginOptions = {
  excludedScopes?: readonly string[];
};

export async function beginLogin(page: Page, options: LoginOptions = {}): Promise<void> {
  const authorizationResponse = await page.request.get(
    new URL(
      `/oauth2/authorization/${CLIENT_REGISTRATION_ID}`,
      runtime.clientServerUrl,
    ).toString(),
    { maxRedirects: 0 },
  );
  expect(authorizationResponse.status()).toBe(302);

  const authorizationLocation = authorizationResponse.headers().location;
  expect(Boolean(authorizationLocation)).toBe(true);
  const authorizationUrl = new URL(authorizationLocation, runtime.clientServerUrl);
  expect(authorizationUrl.origin).toBe(runtime.authServerUrl.origin);
  expect(authorizationUrl.pathname).toBe('/oauth2/authorize');
  authorizationUrl.searchParams.set('prompt', 'consent');
  if (runtime.profile === 'real') {
    const scopes = new Set((authorizationUrl.searchParams.get('scope') ?? '').split(' '));
    for (const scope of options.excludedScopes ?? []) {
      scopes.delete(scope);
    }
    scopes.add(PLAYWRIGHT_CONSENT_SCOPE);
    authorizationUrl.searchParams.set('scope', [...scopes].filter(Boolean).join(' '));
  }

  await page.goto(authorizationUrl.toString());
  await signIn(page);
  if (runtime.profile === 'real') {
    const consentMarker = page.getByLabel(PLAYWRIGHT_CONSENT_SCOPE, { exact: true });
    await expect(consentMarker).toBeVisible();
    await expect(consentMarker).toBeEnabled();
    await expect(consentMarker).not.toBeChecked();
  }
}

export async function beginConsentDenial(page: Page): Promise<void> {
  const verifier = randomBytes(32).toString('base64url');
  const authorizationUrl = new URL('/oauth2/authorize', runtime.authServerUrl);
  authorizationUrl.searchParams.set('response_type', 'code');
  authorizationUrl.searchParams.set('client_id', PLAYWRIGHT_DENIAL_CLIENT_ID);
  authorizationUrl.searchParams.set('scope', 'openid api.read');
  authorizationUrl.searchParams.set('state', randomBytes(32).toString('base64url'));
  authorizationUrl.searchParams.set('nonce', randomBytes(32).toString('base64url'));
  authorizationUrl.searchParams.set('redirect_uri', new URL('/login', runtime.clientServerUrl).toString());
  authorizationUrl.searchParams.set(
    'code_challenge',
    createHash('sha256').update(verifier).digest('base64url'),
  );
  authorizationUrl.searchParams.set('code_challenge_method', 'S256');

  await page.goto(authorizationUrl.toString());
  await signIn(page);
}

async function signIn(page: Page): Promise<void> {
  await expect(page).toHaveURL(
    (url) => url.origin === runtime.authServerUrl.origin && url.pathname === '/login',
  );
  await expect(page.getByRole('heading', { name: 'Please sign in' })).toBeVisible();
  await expect(page.getByPlaceholder('Username')).toBeVisible();
  await expect(page.getByPlaceholder('Password')).toBeVisible();

  await page.getByPlaceholder('Username').fill(runtime.username);
  await page.getByPlaceholder('Password').fill(runtime.password);
  await page.getByRole('button', { name: 'Sign in' }).click();

  await expect(page.getByRole('heading', { name: 'Consent required' })).toBeVisible();
}

export async function grantScope(page: Page, scope: string): Promise<void> {
  const checkbox = page.getByLabel(scope, { exact: true });
  await expect(checkbox).toBeVisible();

  if (await checkbox.isEnabled()) {
    await checkbox.check();
  } else {
    await expect(checkbox).toBeChecked();
  }
}

export async function submitConsent(page: Page): Promise<void> {
  await page.getByRole('button', { name: 'Submit Consent' }).click();
}

export async function expectNoTasks(page: Page): Promise<void> {
  await expect(page).toHaveURL(
    (url) => url.origin === runtime.clientServerUrl.origin && url.pathname === '/home',
  );
  await expect(
    page.getByText('Forbidden (grant api.read on the consent screen and sign in again)'),
  ).toBeVisible();
  await expect(page.getByRole('heading', { name: 'Tasks for user:' })).toHaveCount(0);
  await expect(page.getByRole('listitem')).toHaveCount(0);
}

export async function expectConsentRejected(page: Page): Promise<void> {
  const cancel = page
    .getByRole('button', { name: 'Cancel' })
    .or(page.getByRole('link', { name: 'Cancel' }));
  await cancel.click();
  await expect(page).toHaveURL(
    (url) =>
      url.origin === runtime.clientServerUrl.origin &&
      url.pathname === '/login' &&
      url.searchParams.get('error') === 'access_denied',
  );
  await expect(page.getByRole('heading', { name: 'Consent not granted' })).toBeVisible();
  await expect(page.getByText('No information was shared')).toBeVisible();
  await expect(page.getByRole('link', { name: 'Try sign-in again' })).toHaveAttribute(
    'href',
    `/oauth2/authorization/${CLIENT_REGISTRATION_ID}`,
  );
  await expect(page.getByRole('heading', { name: 'Tasks for user:' })).toHaveCount(0);
}

export async function expectTasks(page: Page): Promise<void> {
  await expect(page).toHaveURL(
    (url) => url.origin === runtime.clientServerUrl.origin && url.pathname === '/home',
  );
  const userHeading = page.locator('#page-title');
  await expect(userHeading).toBeVisible();
  expect([runtime.username, 'User']).toContain((await userHeading.textContent())?.trim());
  await expect(
    page.getByRole('heading', { name: `Tasks for ${runtime.username}:` }),
  ).toBeVisible();

  const tasks = page.getByRole('listitem');
  await expect(tasks).toHaveCount(3);
  await expectTask(tasks.nth(0), 'Task 1');
  await expectTask(tasks.nth(1), 'Task 2');
  await expectTask(tasks.nth(2), 'Task 3');
}

export async function expectResponsiveHome(page: Page): Promise<void> {
  await page.setViewportSize({ width: 1920, height: 1080 });
  const fullHd = await homeLayoutMetrics(page);
  expect(fullHd.horizontalOverflow).toBeLessThanOrEqual(1);
  expect(fullHd.verticalOverflow).toBeLessThanOrEqual(1);
  expect(fullHd.cardCount).toBe(3);
  expect(fullHd.cardTopSpread).toBeLessThanOrEqual(1);

  await page.setViewportSize({ width: 390, height: 844 });
  const mobile = await homeLayoutMetrics(page);
  expect(mobile.horizontalOverflow).toBeLessThanOrEqual(1);
  expect(mobile.cardCount).toBe(3);
  expect(mobile.cardsInsideViewport).toBe(true);
  expect(mobile.cardTopsAscending).toBe(true);

  await page.setViewportSize({ width: 1920, height: 1080 });
}

export async function refreshTasks(page: Page): Promise<void> {
  const refreshButton = page.getByRole('button', { name: 'Refresh tasks' });
  await expect(refreshButton).toBeVisible();
  await expect(refreshButton).toBeEnabled();

  const refreshResponsePromise = page.waitForResponse((response) => {
    const responseUrl = new URL(response.url());
    return (
      response.request().method() === 'GET' &&
      responseUrl.origin === runtime.clientServerUrl.origin &&
      responseUrl.pathname === '/home/tasks'
    );
  });

  await refreshButton.click();
  const refreshResponse = await refreshResponsePromise;
  expect(refreshResponse.status()).toBe(200);
  expect(refreshResponse.headers()['cache-control']).toContain('no-store');
  expect(refreshResponse.request().resourceType()).toBe('fetch');
  expect(new URL(refreshResponse.request().url()).search).toBe('');
  expect(refreshResponse.request().headers().authorization).toBeUndefined();
  await expect(page.getByRole('status')).toHaveText('Tasks refreshed.');
  await expect(refreshButton).toBeEnabled();
  await expectTasks(page);
}

async function expectTask(task: Locator, name: string): Promise<void> {
  await expect(task).toHaveText(name);
}

async function homeLayoutMetrics(page: Page): Promise<{
  horizontalOverflow: number;
  verticalOverflow: number;
  cardCount: number;
  cardTopSpread: number;
  cardsInsideViewport: boolean;
  cardTopsAscending: boolean;
}> {
  return page.evaluate(() => {
    const root = document.documentElement;
    const cards = [...document.querySelectorAll<HTMLElement>('.dashboard > .card')].map((card) =>
      card.getBoundingClientRect(),
    );
    const cardTops = cards.map((card) => card.top);

    return {
      horizontalOverflow: root.scrollWidth - root.clientWidth,
      verticalOverflow: root.scrollHeight - root.clientHeight,
      cardCount: cards.length,
      cardTopSpread:
        cardTops.length > 0 ? Math.max(...cardTops) - Math.min(...cardTops) : Number.POSITIVE_INFINITY,
      cardsInsideViewport: cards.every(
        (card) => card.left >= -1 && card.right <= root.clientWidth + 1,
      ),
      cardTopsAscending: cards.every(
        (card, index) => index === 0 || card.top > cards[index - 1].top,
      ),
    };
  });
}

export async function logout(page: Page): Promise<void> {
  const logoutRequestPromise = page.waitForRequest((request) => {
    const requestUrl = new URL(request.url());
    return (
      requestUrl.origin === runtime.authServerUrl.origin &&
      requestUrl.pathname === '/connect/logout'
    );
  });

  await page.getByRole('button', { name: 'Logout' }).click();
  const logoutRequest = await logoutRequestPromise;
  const logoutUrl = new URL(logoutRequest.url());

  expect(logoutUrl.searchParams.has('id_token_hint')).toBe(true);
  expect(logoutUrl.searchParams.get('post_logout_redirect_uri')).toBe(
    `${runtime.clientServerUrl.origin}/`,
  );

  await expect(page).toHaveURL(
    (url) => url.origin === runtime.authServerUrl.origin && url.pathname === '/login',
  );
  await expect(page.getByRole('heading', { name: 'Please sign in' })).toBeVisible();
}

export async function redactSensitiveHome(page: Page): Promise<void> {
  if (page.isClosed()) {
    return;
  }

  const currentUrl = new URL(page.url());
  if (
    currentUrl.origin === runtime.clientServerUrl.origin &&
    currentUrl.pathname === '/home'
  ) {
    await page.locator('.sensitive-value, pre').evaluateAll((elements) => {
      for (const element of elements) {
        element.textContent = '[redacted]';
      }
    });
  }
}

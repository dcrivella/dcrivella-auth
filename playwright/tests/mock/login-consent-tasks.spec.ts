import { expect, test } from '@playwright/test';
import {
  beginLogin,
  expectConsentRejected,
  expectNoTasks,
  expectResponsiveHome,
  expectTasks,
  grantScope,
  logout,
  redactSensitiveHome,
  refreshTasks,
  submitConsent,
} from '../support/oauth-flow';
import { runtime } from '../support/runtime';

test.describe('mock OAuth browser flow', () => {
  test.afterEach(async ({ page }) => {
    await redactSensitiveHome(page);
  });

  test.beforeEach(async ({ request }) => {
    const response = await request.post(new URL('/__test/reset', runtime.authServerUrl).toString());
    expect(response.ok()).toBe(true);
  });

  test('cancelling consent does not create the client login or expose tasks', async ({ page }) => {
    await beginLogin(page);
    await expectConsentRejected(page);
  });

  test('submitting consent without api.read signs in but the resource server denies the task-list request', async ({ page }) => {
    await beginLogin(page);
    await submitConsent(page);

    await expectNoTasks(page);
  });

  test('granting api.read shows and refreshes exactly three tasks before OIDC logout', async ({ page }) => {
    await beginLogin(page);
    await grantScope(page, 'api.read');
    await submitConsent(page);

    await expectTasks(page);
    await expectResponsiveHome(page);
    await refreshTasks(page);
    await logout(page);
  });
});

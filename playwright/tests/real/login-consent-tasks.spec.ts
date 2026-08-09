import { expect, test } from '@playwright/test';
import {
  beginConsentDenial,
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

test.describe('real OAuth browser flow', () => {
  test.describe.configure({ mode: 'serial' });

  test.afterEach(async ({ page }) => {
    await redactSensitiveHome(page);
  });

  test('cancelling consent does not create the client login or expose tasks', async ({ page }) => {
    await beginConsentDenial(page);
    await expectConsentRejected(page);
  });

  test('submitting consent without api.read signs in but the resource server denies the task-list request', async ({ page }) => {
    await beginLogin(page, { excludedScopes: ['api.read'] });
    await expect(page.getByLabel('api.read', { exact: true })).toHaveCount(0);
    await grantScope(page, 'profile');
    await submitConsent(page);

    await expectNoTasks(page);
    await logout(page);
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

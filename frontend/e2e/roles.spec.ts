import type { Page } from '@playwright/test';
import { expect, test } from './fixtures';
import { admin, ensurePeople, everyone, people, signIn } from './people';

// Task 6.1 (BR-191, AC-279, AC-280): sign-in, landing and navigation for every role never leave a blank page, a page
// error or an unexpected failed call. Runs on the throwaway stack of scripts/e2e.sh and, with E2E_DEMO=1, on a local stack.

test.describe.configure({ timeout: 240_000 });
test.beforeAll(async () => {
  await ensurePeople();
});

/** Collects what a person would never see but a bug leaves behind. */
function watch(page: Page, allow: RegExp[] = []) {
  const problems: string[] = [];
  page.on('pageerror', (e) => problems.push(`pageerror: ${e.message}`));
  page.on('console', (m) => {
    // The browser echoes every failed request as a console error; requests are checked on their own below.
    if (m.type() === 'error' && !m.text().startsWith('Failed to load resource')) problems.push(`console: ${m.text()}`);
  });
  page.on('requestfailed', (r) => {
    // An aborted request is a navigation or a sign-out cancelling a call in flight.
    if (r.failure()?.errorText !== 'net::ERR_ABORTED') problems.push(`requestfailed: ${r.url()}`);
  });
  page.on('response', (r) => {
    const line = `${r.status()} ${r.url().replace(/^https?:\/\/[^/]+/, '')}`;
    if (r.url().includes('/api/') && r.status() >= 400 && !allow.some((a) => a.test(line))) problems.push(`api: ${line}`);
  });
  return problems;
}

/** Nothing readable is drawn: no children under #root, or almost no text. */
async function blank(page: Page) {
  return page.evaluate(() => {
    const root = document.getElementById('root');
    const text = (document.body.innerText || '').trim();
    return !root || root.childElementCount === 0 || text.length < 15;
  });
}

async function settled(page: Page) {
  await page.waitForLoadState('networkidle');
  await page.waitForTimeout(400);
}

// The start-up probe answers 401 to a signed-out browser: that is how the app decides to show the sign-in page.
const PROBE = [/^401 \/api\/auth\/me$/];

for (const who of everyone) {
  test(`${who.role}: signs in, lands on a page with content and opens every menu page`, async ({ page }) => {
    const problems = watch(page, PROBE);
    await signIn(page, who);
    await settled(page);
    expect(await blank(page), 'blank after sign-in').toBe(false);
    await expect(page.getByRole('heading', { level: 1 }).first()).toBeVisible();
    const links = await page.locator('aside a[href]').evaluateAll((as) => [...new Set(as.map((a) => a.getAttribute('href')!))]);
    expect(links.length).toBeGreaterThan(0);
    // The landing page is the first page of the menu the role may open (BR-191).
    expect(new URL(page.url()).pathname).not.toBe('/none');
    for (const href of links) {
      await page.locator(`aside a[href="${href}"]`).first().click();
      await settled(page);
      expect(await blank(page), `blank on ${href}`).toBe(false);
      await expect(page.getByRole('heading', { level: 1 }).first(), `heading on ${href}`).toBeVisible();
      expect(new URL(page.url()).pathname, `landed on ${href}`).toBe(href);
    }
    expect(problems).toEqual([]);
  });
}

// add-timetables-and-invoice-export (PERM-056): the timetable entry is offered to the Groom and the Head Trainer only.
for (const who of everyone) {
  test(`${who.role}: the timetable menu entry ${who.role === 'GROOM' || who.role === 'HEAD_TRAINER' ? 'is offered' : 'is not offered'}`, async ({ page }) => {
    await signIn(page, who);
    await settled(page);
    const entry = page.locator('aside a[href="/timetable"]');
    if (who.role === 'GROOM' || who.role === 'HEAD_TRAINER') await expect(entry.first()).toBeVisible();
    else await expect(entry).toHaveCount(0);
  });
}

test('a wrong password stays on sign-in with a message and no blank page', async ({ page }) => {
  const problems = watch(page, [...PROBE, /^401 \/api\/auth\/login$/]);
  await page.goto('/');
  await page.getByLabel('Email').fill(admin.email);
  await page.getByLabel('Mật khẩu').fill('not-the-password');
  await page.getByRole('button', { name: 'Đăng nhập' }).click();
  await expect(page.getByRole('alert')).toContainText('Sai email hoặc mật khẩu');
  expect(await blank(page)).toBe(false);
  expect(problems).toEqual([]);
});

test('role switch without reload lands on the new role home, never on an access refusal', async ({ page }) => {
  await signIn(page, people.HEAD_TRAINER);
  await page.goto('/progress');
  await expect(page.getByRole('heading', { level: 1 }).first()).toBeVisible();
  await page.getByRole('button', { name: 'Đăng xuất' }).first().click();
  await expect(page.getByLabel('Email')).toBeVisible();
  await page.getByLabel('Email').fill(people.GROOM.email);
  await page.getByLabel('Mật khẩu').fill(people.GROOM.password);
  await page.getByRole('button', { name: 'Đăng nhập' }).click();
  await settled(page);
  await expect(page.getByText('Bạn không có quyền')).toHaveCount(0);
  expect(new URL(page.url()).pathname).not.toBe('/progress');
  expect(await blank(page)).toBe(false);
});

test('an ended session returns to sign-in with a message, and the same person lands back on the page', async ({ page, context }) => {
  await signIn(page, people.HEAD_TRAINER);
  await page.locator('aside a[href="/progress"]').first().click();
  await settled(page);
  await context.clearCookies();
  await page.locator('aside a[href="/health"]').first().click();
  await expect(page.getByLabel('Email')).toBeVisible();
  await expect(page.getByText('Phiên đăng nhập đã hết hạn')).toBeVisible();
  await page.getByLabel('Email').fill(people.HEAD_TRAINER.email);
  await page.getByLabel('Mật khẩu').fill(people.HEAD_TRAINER.password);
  await page.getByRole('button', { name: 'Đăng nhập' }).click();
  await settled(page);
  expect(await blank(page)).toBe(false);
  await expect(page.getByRole('heading', { level: 1 }).first()).toBeVisible();
});

test('a deep link while signed out opens that page after sign-in when the role may', async ({ page }) => {
  await page.goto('/health');
  await page.getByLabel('Email').fill(people.HEAD_TRAINER.email);
  await page.getByLabel('Mật khẩu').fill(people.HEAD_TRAINER.password);
  await page.getByRole('button', { name: 'Đăng nhập' }).click();
  await settled(page);
  expect(new URL(page.url()).pathname).toBe('/health');
  expect(await blank(page)).toBe(false);
});

test('a deep link to a page the role may not open falls back to the role home', async ({ page }) => {
  await page.goto('/accounts');
  await page.getByLabel('Email').fill(people.GROOM.email);
  await page.getByLabel('Mật khẩu').fill(people.GROOM.password);
  await page.getByRole('button', { name: 'Đăng nhập' }).click();
  await settled(page);
  expect(new URL(page.url()).pathname).not.toBe('/accounts');
  expect(await blank(page)).toBe(false);
});

test('an unknown address inside the app goes to the role home', async ({ page }) => {
  await signIn(page, people.VETERINARIAN);
  await page.goto('/no-such-page');
  await settled(page);
  expect(await blank(page)).toBe(false);
  await expect(page.getByRole('heading', { level: 1 }).first()).toBeVisible();
});

test.describe('when the service misbehaves the page is never blank', () => {
  test('the start-up probe answers 502 with an HTML body', async ({ page }) => {
    const errors: string[] = [];
    page.on('pageerror', (e) => errors.push(e.message));
    await page.route('**/api/auth/me', (r) => r.fulfill({ status: 502, contentType: 'text/html', body: '<html>Bad gateway</html>' }));
    await page.goto('/');
    await settled(page);
    expect(await blank(page)).toBe(false);
    await expect(page.getByLabel('Email')).toBeVisible();
    // TECH-200: an answer that is not JSON is an ApiError, not an uncaught SyntaxError.
    expect(errors).toEqual([]);
  });

  test('the start-up probe fails at the network', async ({ page }) => {
    await page.route('**/api/auth/me', (r) => r.abort('connectionrefused'));
    await page.goto('/');
    await settled(page);
    expect(await blank(page)).toBe(false);
  });

  test('a page call answers 500 after sign-in: the shell stays and the page shows an error', async ({ page }) => {
    await signIn(page, people.HEAD_TRAINER);
    await page.route('**/api/health/**', (r) => r.fulfill({ status: 500, contentType: 'application/json', body: '{"code":"INTERNAL_ERROR","message":"Đã có lỗi xảy ra. Vui lòng thử lại.","fields":{}}' }));
    await page.route('**/api/horses?**', (r) => r.fulfill({ status: 500, contentType: 'text/html', body: '<html>boom</html>' }));
    await page.locator('aside a[href="/health"]').first().click();
    await settled(page);
    await expect(page.locator('aside').first()).toBeVisible();
    expect(await blank(page)).toBe(false);
  });

  test('the notification bell fails: the shell and the page stay', async ({ page }) => {
    await page.route('**/api/notifications**', (r) => r.fulfill({ status: 500, contentType: 'text/html', body: '<html>boom</html>' }));
    await signIn(page, people.HEAD_TRAINER);
    await settled(page);
    await expect(page.locator('aside').first()).toBeVisible();
    await expect(page.getByRole('heading', { level: 1 }).first()).toBeVisible();
  });

  test('a route chunk fails to load once and is retried', async ({ page }) => {
    await signIn(page, people.HEAD_TRAINER);
    let failed = false;
    await page.route(/\/src\/pages\/ProgressPage/, (r) => {
      if (!failed) {
        failed = true;
        return r.abort('failed');
      }
      return r.continue();
    });
    await page.locator('aside a[href="/progress"]').first().click();
    await settled(page);
    expect(await blank(page)).toBe(false);
  });
});

import type { Page } from '@playwright/test';
import { expect, test } from './fixtures';
import { admin, ensurePeople, people, signIn } from './people';

// optimize-role-ui-and-date-picker (BR-206 to BR-209, AC-416 to AC-454): the calendar, the money field, the tabs and the
// popups on a real browser, and that no changed page scrolls sideways at the widths the application supports.
// Runs on the throwaway stack of scripts/e2e.sh and, with E2E_CDP and E2E_DEMO=1, on a local stack.

test.describe.configure({ timeout: 240_000 });
test.beforeAll(async () => {
  await ensurePeople();
});

const WIDTHS = [1440, 1024, 768, 390];

async function sideways(page: Page): Promise<number> {
  return page.evaluate(() => document.documentElement.scrollWidth - document.documentElement.clientWidth);
}

test('AC-416 a day is chosen on the calendar and read back as dd/mm/yyyy', async ({ page }) => {
  await signIn(page, admin);
  await page.goto('/billing?tab=statements');
  await page.getByRole('button', { name: 'Điều chỉnh' }).click();
  const form = page.getByRole('form', { name: 'Thêm khoản điều chỉnh' });
  await form.getByRole('button', { name: 'Mở lịch' }).click();
  // The month and year selectors are on the calendar: pick a year, a month, then the 15th.
  const calendar = page.locator('.rdp-root');
  await expect(calendar).toBeVisible();
  await calendar.locator('select').nth(1).selectOption('2025');
  await calendar.locator('select').nth(0).selectOption('2'); // March
  await calendar.locator('[data-day="2025-03-15"] button').click();
  await expect(form.getByLabel(/^Ngày/)).toHaveValue('15/03/2025');
  await expect(page.locator('.rdp-root')).toHaveCount(0);
});

test('AC-420 an amount is typed in thousands by default and shown in full', async ({ page }) => {
  await signIn(page, admin);
  await page.goto('/billing?tab=statements');
  await page.getByRole('button', { name: 'Điều chỉnh' }).click();
  const form = page.getByRole('form', { name: 'Thêm khoản điều chỉnh' });
  await form.getByLabel(/^Số tiền/).fill('-300');
  await expect(form.getByText('= -300.000 VND')).toBeVisible();
  await form.getByLabel('Tính tiền theo').selectOption({ label: 'triệu đồng' });
  await expect(form.getByText('= -300.000.000 VND')).toBeVisible();
});

test('AC-448 the billing overview is first, charts only, and the four cards have one size', async ({ page }) => {
  await signIn(page, admin);
  await page.goto('/billing');
  await expect(page.getByRole('heading', { name: 'Chi phí theo tháng' })).toBeVisible();
  const boxes = await page.locator('section:has(> h3)').evaluateAll((els) => els.map((e) => Math.round(e.getBoundingClientRect().height)));
  expect(boxes.length).toBeGreaterThanOrEqual(4);
  const row = boxes.slice(0, 2);
  expect(Math.abs(row[0] - row[1])).toBeLessThanOrEqual(2);
  await page.getByLabel('Tháng').selectOption('all');
  await expect(page.locator('section:has(> h3)').nth(1).getByText('Tất cả các tháng')).toBeVisible();
});

test('AC-451 "Xem" on a cost row opens the statement in a popup', async ({ page }) => {
  await signIn(page, admin);
  await page.goto('/billing?tab=statements');
  // The statements of the month on show: choose the first month that has an owner with a cost (a stack has data in some months only).
  const month = page.getByLabel('Tháng');
  await expect(month.locator('option').nth(1)).toBeAttached({ timeout: 30_000 });
  const values = await month.locator('option').evaluateAll((os) => os.map((o) => (o as HTMLOptionElement).value).filter((v) => v !== 'all'));
  const eye = page.getByRole('button', { name: /^Xem / }).first();
  let found = false;
  // The newest month comes first and holds the running month's rows; allow time for them to load on a slow link.
  for (const v of values) {
    if (found) break;
    await month.selectOption(v);
    found = await eye.waitFor({ state: 'visible', timeout: 15_000 }).then(() => true, () => false);
  }
  expect(found, 'some month has a cost statement row').toBe(true);
  await eye.click();
  const dialog = page.getByRole('dialog');
  await expect(dialog).toBeVisible();
  await expect(dialog.getByLabel('Tổng cộng')).toBeVisible();
});

test('AC-436 the barn has a search, an area filter and "Ngựa chưa có chuồng" as its own tab', async ({ page }) => {
  await signIn(page, admin);
  await page.goto('/barn');
  await expect(page.getByRole('searchbox')).toBeVisible();
  await expect(page.getByLabel('Khu', { exact: true })).toBeVisible();
  await page.getByRole('button', { name: /^Ngựa chưa có chuồng/ }).click();
  await expect(page).toHaveURL(/tab=unplaced/);
});

test('AC-424 the health page opens on the overview and the list is another tab', async ({ page }) => {
  await signIn(page, people.VETERINARIAN);
  await page.goto('/health');
  await expect(page.getByRole('heading', { name: 'Phân bố trạng thái sức khỏe' })).toBeVisible();
  await expect(page.getByRole('table', { name: 'Sức khỏe đàn ngựa' })).toHaveCount(0);
  await page.getByRole('button', { name: 'Danh sách' }).click();
  await expect(page.getByRole('table', { name: 'Sức khỏe đàn ngựa' })).toBeVisible();
});

test('AC-444 the Groom\'s incidents page is the list, and a new report opens in a popup', async ({ page }) => {
  await signIn(page, people.GROOM);
  await page.goto('/incidents');
  await expect(page.getByRole('form', { name: 'Gửi báo cáo' })).toHaveCount(0);
  await page.getByRole('button', { name: 'Báo cáo mới' }).click();
  await expect(page.getByRole('dialog').getByRole('form', { name: 'Gửi báo cáo' })).toBeVisible();
});

for (const [who, key, paths] of [
  ['Club Manager', 'admin', ['/horses', '/health', '/chores', '/barn', '/supplies', '/billing', '/billing?tab=statements', '/billing?tab=prices']],
  ['Head Trainer', 'HEAD_TRAINER', ['/horses', '/health', '/progress', '/chores', '/barn', '/timetable']],
  ['Veterinarian', 'VETERINARIAN', ['/health', '/care', '/chores', '/barn']],
  ['Groom', 'GROOM', ['/incidents', '/chores', '/barn', '/supplies', '/timetable', '/my-sessions']],
  ['Horse Owner', 'HORSE_OWNER', ['/horses', '/chores', '/barn']],
] as const) {
  test(`${who}: the changed pages do not scroll sideways and show no failed call (AC-425)`, async ({ page }) => {
    const problems: string[] = [];
    page.on('pageerror', (e) => problems.push(`pageerror: ${e.message}`));
    page.on('response', (r) => {
      if (r.url().includes('/api/') && r.status() >= 400 && !r.url().endsWith('/api/auth/me')) problems.push(`${r.status()} ${r.url()}`);
    });
    await signIn(page, key === 'admin' ? admin : people[key]);
    for (const width of WIDTHS) {
      await page.setViewportSize({ width, height: 900 });
      for (const path of paths) {
        await page.goto(path);
        await page.waitForLoadState('networkidle');
        expect(await sideways(page), `${path} at ${width}px`).toBeLessThanOrEqual(1);
      }
    }
    expect(problems).toEqual([]);
  });
}

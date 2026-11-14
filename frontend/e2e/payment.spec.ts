import { request, type APIRequestContext } from '@playwright/test';
import { expect, test } from './fixtures';
import { admin, apiContext, ensurePeople, people, retry, signIn } from './people';

// REQ-041, BR-218..BR-226, AC-469..AC-483: an owner pays a closed invoice by QR, the bank-transfer notice (SePay) marks it
// paid, a transfer that does not match is kept and the Club Manager assigns it. The invoices are made the real way: a
// correction line in a past month and the month closing, so no database access is needed.
// Needs SEPAY_WEBHOOK_API_KEY in the environment (scripts/e2e.sh sets it) and finds its own pair of closable months, so it can run again on the same stack.

const BASE = process.env.BASE_URL ?? 'http://localhost:5173';
const KEY = process.env.SEPAY_WEBHOOK_API_KEY ?? '';

test.describe.configure({ mode: 'serial', timeout: 120_000 });
test.skip(!KEY, 'SEPAY_WEBHOOK_API_KEY is not set');

/** The club month `back` months before the current one, as yyyy-mm (club time is UTC+7). */
function monthBack(back: number): string {
  const now = new Date(Date.now() + 7 * 3600_000);
  const d = new Date(Date.UTC(now.getUTCFullYear(), now.getUTCMonth() - back, 1));
  return `${d.getUTCFullYear()}-${String(d.getUTCMonth() + 1).padStart(2, '0')}`;
}

async function csrfPost(api: APIRequestContext, path: string, data: unknown) {
  const token = (await api.storageState()).cookies.find((c) => c.name === 'XSRF-TOKEN')?.value;
  return retry(() => api.post(`${BASE}${path}`, { data, headers: token ? { 'X-XSRF-TOKEN': decodeURIComponent(token) } : {} }));
}

/** The two months whose invoices this run made: one is paid exactly, the other by an assignment. */
let months: { exact: string; wrong: string } | undefined;
let seq = 0;
async function notice(amount: number, content: string, id = `PW-${Date.now()}-${++seq}`, key: string | null = KEY) {
  const api = await apiContext();
  try {
    const res = await retry(() => api.post(`${BASE}/api/payments/sepay`, {
      data: { id, gateway: 'VietinBank', transactionDate: '2026-11-13 10:00:00', accountNumber: '0000000001', code: null, content, transferType: 'in', transferAmount: amount, referenceCode: 'FT1' },
      headers: key === null ? {} : { Authorization: `Apikey ${key}` },
    }));
    return { status: () => res.status(), text: await res.text() };
  } finally {
    await api.dispose();
  }
}

test.beforeAll(async () => {
  await ensurePeople();
  const api = await apiContext();
  try {
    await retry(() => api.get(`${BASE}/api/auth/csrf`));
    expect((await csrfPost(api, '/api/auth/login', { email: admin.email, password: admin.password })).ok()).toBe(true);
    const found = (await (await retry(() => api.get(`${BASE}/api/accounts?q=${encodeURIComponent(people.HORSE_OWNER.email)}&size=5`))).json()) as { items: { id: number; email: string }[] };
    const ownerId = found.items.find((a) => a.email === people.HORSE_OWNER.email)!.id;
    // Oldest month first: a month can only be closed when the earlier ones are. A stack that an earlier run already
    // used has some months closed, so the months whose correction is refused are skipped; the first two that take
    // the owner's correction and close become the two invoices of this run.
    const made: string[] = [];
    for (let back = 15; back >= 1 && made.length < 2; back--) {
      const month = monthBack(back);
      const amount = made.length === 0 ? 700000 : 1500000;
      const adjusted = await csrfPost(api, '/api/billing/adjustments', { ownerId, date: `${month}-10`, amount, reason: 'Kiểm thử thanh toán' });
      if (!adjusted.ok()) continue;
      expect((await csrfPost(api, `/api/billing/months/${month}/close`, {})).ok()).toBe(true);
      made.push(month);
    }
    if (made.length === 2) months = { wrong: made[0], exact: made[1] };
    expect(months, 'no closable pair of months was found').toBeTruthy();
  } finally {
    await api.dispose();
  }
});

async function openMonth(page: import('@playwright/test').Page, month: string) {
  await signIn(page, people.HORSE_OWNER);
  await page.getByRole('link', { name: 'Chi phí' }).click();
  await page.getByLabel('Tháng').selectOption(month);
}

test('AC-469, AC-470, AC-471, AC-480: the owner pays by QR and the notice marks the invoice paid', async ({ page }) => {
  await openMonth(page, months!.exact);
  await expect(page.getByText('Chưa thanh toán')).toBeVisible();
  await page.getByRole('button', { name: 'Thanh toán' }).click();
  const dialog = page.getByRole('dialog');
  const src = (await dialog.getByRole('img', { name: 'Mã QR chuyển khoản' }).getAttribute('src')) ?? '';
  expect(src).toMatch(/des=SEVQR%20RH\d{8}/);
  const code = src.match(/RH\d{8}/)![0];
  const amount = Number(src.match(/amount=(\d+)/)![1]);
  expect(amount).toBeGreaterThan(0);
  await expect(dialog.getByText(/liên hệ Quản lý CLB/)).toBeVisible();

  expect((await notice(amount, `SEVQR ${code}`, undefined, null)).status()).toBe(401);
  expect((await notice(amount, `SEVQR ${code}`, undefined, 'not-the-key')).status()).toBe(401);
  const id = `PW-PAID-${Date.now()}`;
  const ok = await notice(amount, `sevqr${code.toLowerCase()} chuyen tien`, id);
  expect(ok.status()).toBe(200);
  expect(JSON.parse(ok.text)).toEqual({ success: true });
  await expect(dialog.getByText(/Hóa đơn đã được thanh toán/)).toBeVisible({ timeout: 15_000 });
  expect((await notice(amount, `SEVQR ${code}`, id)).status()).toBe(200); // the same transfer again changes nothing

  await page.keyboard.press('Escape');
  await expect(page.getByText('Đã thanh toán').first()).toBeVisible();
  await expect(page.getByRole('button', { name: 'Thanh toán' })).toHaveCount(0);
});

test('AC-472, AC-476, AC-477, AC-483: a wrong amount is kept unmatched and the Club Manager assigns it', async ({ page, browser }) => {
  await openMonth(page, months!.wrong);
  await page.getByRole('button', { name: 'Thanh toán' }).click();
  const src = (await page.getByRole('dialog').getByRole('img', { name: 'Mã QR chuyển khoản' }).getAttribute('src')) ?? '';
  const code = src.match(/RH\d{8}/)![0];
  const amount = Number(src.match(/amount=(\d+)/)![1]);
  const wrong = await notice(amount - 1000, `SEVQR ${code}`);
  expect(wrong.status()).toBe(200);
  await page.keyboard.press('Escape');
  await expect(page.getByText('Chưa thanh toán')).toBeVisible();

  const ctx = await browser.newContext();
  try {
    const mgr = await ctx.newPage();
    await signIn(mgr, admin);
    await mgr.getByRole('navigation', { name: 'main' }).getByRole('link', { name: 'Thanh toán' }).click();
    await mgr.getByText('Chưa khớp', { exact: true }).first().click();
    const row = mgr.getByRole('row').filter({ hasText: 'số tiền khác tổng' }).filter({ hasText: 'Chưa gán' }).first();
    await expect(row.getByText('mô phỏng')).toBeVisible();
    await row.getByRole('button', { name: /^Gán/ }).click();
    const dialog = mgr.getByRole('dialog');
    await dialog.getByRole('button', { name: 'Gán và đánh dấu đã thanh toán' }).click();
    await expect(dialog.getByText('Bắt buộc nhập lý do.')).toBeVisible();
    const options = dialog.getByRole('combobox').locator('option');
    await expect(options.nth(1)).toBeAttached();
    const value = await options.filter({ hasText: months!.wrong.replace('-', '') }).first().getAttribute('value');
    await dialog.getByRole('combobox').selectOption(value!);
    await dialog.getByLabel('Lý do gán').fill('Chủ ngựa chuyển thiếu, đã nhận phần còn lại');
    await dialog.getByRole('button', { name: 'Gán và đánh dấu đã thanh toán' }).click();
    await expect(dialog).toBeHidden();
  } finally {
    await ctx.close();
  }

  await page.reload();
  await page.getByLabel('Tháng').selectOption(months!.wrong);
  await expect(page.getByText('Đã thanh toán').first()).toBeVisible();
});

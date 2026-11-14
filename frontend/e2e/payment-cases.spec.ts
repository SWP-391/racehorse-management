import type { APIRequestContext } from '@playwright/test';
import { expect, test } from './fixtures';
import { admin, apiContext, people, retry } from './people';

// REQ-041, BR-218..BR-226, AC-469..AC-483: every case of the payment of an invoice by bank transfer, at the API level, so each
// rule is proven on its own. The invoices are made the real way (a correction line for several owners in one past month and the
// month closing), and every transfer carries this run's tag so a run can be repeated on the same stack.
// Needs SEPAY_WEBHOOK_API_KEY and E2E_DEMO=1 (the seeded owners); each run closes one more month.

const BASE = process.env.BASE_URL ?? 'http://localhost:5173';
const KEY = process.env.SEPAY_WEBHOOK_API_KEY ?? '';
const TAG = Date.now().toString(36).toUpperCase();

test.describe.configure({ mode: 'serial', timeout: 180_000 });
test.skip(!KEY || !process.env.E2E_DEMO, 'SEPAY_WEBHOOK_API_KEY or E2E_DEMO is not set');

const OWNERS = ['owner-hoa', 'owner-khanh', 'owner-linh', 'owner-nam', 'owner-phuong'].map((n) => `${n}@racehorse.example`);

function monthBack(back: number): string {
  const now = new Date(Date.now() + 7 * 3600_000);
  const d = new Date(Date.UTC(now.getUTCFullYear(), now.getUTCMonth() - back, 1));
  return `${d.getUTCFullYear()}-${String(d.getUTCMonth() + 1).padStart(2, '0')}`;
}

async function csrfPost(api: APIRequestContext, path: string, data: unknown, headers: Record<string, string> = {}) {
  const token = (await api.storageState()).cookies.find((c) => c.name === 'XSRF-TOKEN')?.value;
  return retry(() => api.post(`${BASE}${path}`, { data, headers: { ...(token ? { 'X-XSRF-TOKEN': decodeURIComponent(token) } : {}), ...headers } }));
}
async function getJson<T = any>(api: APIRequestContext, path: string): Promise<{ status: number; body: T }> {
  const res = await retry(() => api.get(`${BASE}${path}`));
  return { status: res.status(), body: (await res.json().catch(() => null)) as T };
}
async function login(email: string, password: string): Promise<APIRequestContext> {
  const api = await apiContext();
  await retry(() => api.get(`${BASE}/api/auth/csrf`));
  const res = await csrfPost(api, '/api/auth/login', { email, password });
  if (!res.ok()) throw new Error(`sign-in of ${email} failed (${res.status()})`);
  return api;
}

let seq = 0;
/** A transfer notice as the bank-transfer service sends it. `key` null sends none. */
async function notice(fields: Record<string, unknown>, key: string | null = KEY) {
  const api = await apiContext();
  try {
    const data = { id: `PC-${TAG}-${++seq}`, gateway: 'VietinBank', transactionDate: '2026-11-13 10:00:00', accountNumber: '0000000001', code: null, content: '', transferType: 'in', transferAmount: 1000, referenceCode: 'FT1', ...fields };
    const res = await retry(() => api.post(`${BASE}/api/payments/sepay`, { data, headers: key === null ? {} : { Authorization: `Apikey ${key}` } }));
    return { status: res.status(), text: await res.text(), id: data.id as string };
  } finally {
    await api.dispose();
  }
}

interface Inv { id: number; ownerId: number; number: string; total: number; month: string; paymentStatus: string }
const inv: Record<'A' | 'B' | 'C' | 'D' | 'E', Inv> = {} as never;
const amounts = { A: 1_500_000, B: 1_500_000, C: 1_000_000, D: 700_000, E: 900_000 };
const codes: Record<string, string> = {};
let month = '';
let boss: APIRequestContext;
const owners: Record<string, APIRequestContext> = {};

test.beforeAll(async () => {
  boss = await login(admin.email, admin.password);
  const ids: number[] = [];
  for (const email of OWNERS) {
    const found = await getJson<{ items: { id: number; email: string }[] }>(boss, `/api/accounts?q=${encodeURIComponent(email)}&size=5`);
    ids.push(found.body.items.find((a) => a.email === email)!.id);
    owners[email] = await login(email, people.HORSE_OWNER.password);
  }
  // Oldest first: a month that takes the corrections of all five owners and closes gives five invoices at once.
  const keys = Object.keys(amounts) as (keyof typeof amounts)[];
  month = process.env.PC_MONTH ?? ''; // reuse a month a previous run closed (its invoices are still unpaid)
  for (let back = 15; back >= 1 && !month; back--) {
    const m = monthBack(back);
    let allTaken = true;
    for (let i = 0; i < keys.length; i++) {
      const res = await csrfPost(boss, '/api/billing/adjustments', { ownerId: ids[i], date: `${m}-10`, amount: amounts[keys[i]], reason: 'Kiểm thử thanh toán đủ trường hợp' });
      if (!res.ok()) { allTaken = false; break; }
    }
    if (!allTaken) continue;
    const closed = await csrfPost(boss, `/api/billing/months/${m}/close`, {});
    expect(closed.ok(), `close ${m}`).toBe(true);
    month = m;
  }
  expect(month, 'a month that takes five corrections and closes').toBeTruthy();
  const list = await getJson<{ items: Inv[] }>(boss, '/api/billing/invoices?size=100');
  keys.forEach((k, i) => {
    const found = list.body.items.find((x) => x.month === month && x.ownerId === ids[i]);
    expect(found, `invoice ${k}`).toBeTruthy();
    inv[k] = found!;
  });
});

test.afterAll(async () => {
  await boss?.dispose();
  for (const api of Object.values(owners)) await api.dispose();
});

const ownerOf = (k: keyof typeof inv) => owners[OWNERS[(['A', 'B', 'C', 'D', 'E'] as const).indexOf(k)]];
const open = async (k: keyof typeof inv) => {
  const res = await csrfPost(ownerOf(k), `/api/payments/invoices/${inv[k].id}/open`, {});
  return { status: res.status(), body: (await res.json().catch(() => null)) as any };
};
const unmatchedRows = async () => (await getJson<{ items: any[] }>(boss, '/api/payments/unmatched?size=100')).body.items;
const receivedRows = async () => (await getJson<{ items: any[] }>(boss, '/api/payments/received?size=100')).body.items;
const statusOf = async (k: keyof typeof inv) => (await getJson<any>(ownerOf(k), `/api/payments/invoices/${inv[k].id}`)).body;

test('AC-469 the payment shows the full total, a stable code, the bank details, and no part payment', async () => {
  for (const k of ['A', 'B', 'C', 'D', 'E'] as const) {
    const first = await open(k);
    expect(first.status, `open ${k}`).toBe(200);
    expect(first.body.paymentCode).toMatch(/^RH\d{8}$/);
    expect(Number(first.body.total)).toBe(amounts[k]);
    expect(Number(first.body.transfer.amount)).toBe(amounts[k]);
    expect(first.body.transfer.content).toContain(first.body.paymentCode);
    expect(first.body.transfer.accountNumber).toBeTruthy();
    expect(first.body.qrUrl).toContain(`amount=${amounts[k]}`);
    expect(first.body.qrUrl).toContain(first.body.paymentCode);
    codes[k] = first.body.paymentCode;
    const again = await open(k);
    expect(again.body.paymentCode, `the code of ${k} never changes`).toBe(first.body.paymentCode);
  }
});

test('payment codes are unique among invoices', async () => {
  expect(new Set(Object.values(codes)).size).toBe(5);
});

test('AC-481 a month that is not closed offers no payment', async () => {
  const mine = await getJson<{ items: Inv[] }>(owners[OWNERS[0]], '/api/billing/invoices?size=100');
  const open = monthBack(0);
  expect(mine.body.items.some((x) => x.month === open)).toBe(false);
  const none = await csrfPost(owners[OWNERS[0]], '/api/payments/invoices/999999999/open', {});
  expect(none.status()).toBe(404);
});

test('AC-480 a notice without the key or with a wrong key is refused and records nothing', async () => {
  const before = (await receivedRows()).length + (await unmatchedRows()).length;
  expect((await notice({ transferAmount: amounts.A, content: `SEVQR ${codes.A}` }, null)).status).toBe(401);
  expect((await notice({ transferAmount: amounts.A, content: `SEVQR ${codes.A}` }, 'not-the-key')).status).toBe(401);
  expect((await notice({ transferAmount: amounts.A, content: `SEVQR ${codes.A}` }, '')).status).toBe(401);
  expect((await statusOf('A')).status).not.toMatch(/^PAID$/i);
  expect((await receivedRows()).length + (await unmatchedRows()).length).toBe(before);
});

test('a malformed notice is refused with 400 and records nothing', async () => {
  const before = (await receivedRows()).length + (await unmatchedRows()).length;
  expect((await notice({ id: null, transferAmount: 1000, content: 'x' })).status).toBe(400);
  expect((await notice({ id: '', transferAmount: 1000, content: 'x' })).status).toBe(400);
  expect((await notice({ transferAmount: null, content: 'x' })).status).toBe(400);
  expect((await notice({ transferAmount: -5000, content: `SEVQR ${codes.A}` })).status).toBe(400);
  expect((await receivedRows()).length + (await unmatchedRows()).length).toBe(before);
});

test('a notice of money going out is confirmed and ignored', async () => {
  const before = (await receivedRows()).length + (await unmatchedRows()).length;
  const res = await notice({ transferType: 'out', transferAmount: amounts.A, content: `SEVQR ${codes.A}` });
  expect(res.status).toBe(200);
  expect(JSON.parse(res.text)).toEqual({ success: true });
  expect((await receivedRows()).length + (await unmatchedRows()).length).toBe(before);
  expect((await statusOf('A')).status).not.toMatch(/^PAID$/i);
});

test('AC-475 no code or an unknown code is kept unmatched with its reason', async () => {
  const none = await notice({ transferAmount: 123000, content: `chuyen tien an ${TAG}` });
  const unknown = await notice({ transferAmount: 123000, content: `SEVQR RH00000000 ${TAG}` });
  expect(none.status).toBe(200);
  expect(unknown.status).toBe(200);
  const rows = await unmatchedRows();
  expect(rows.find((r) => r.content?.includes(`chuyen tien an ${TAG}`))?.reasonText).toMatch(/không có mã thanh toán/);
  expect(rows.find((r) => r.content?.includes(`RH00000000 ${TAG}`))?.reasonText).toMatch(/mã thanh toán không tồn tại/);
});

test('AC-472 a smaller amount stays unmatched; AC-473 a larger amount stays unmatched; parts are not added up', async () => {
  const less = await notice({ transferAmount: amounts.B - 1000, content: `SEVQR ${codes.B} less ${TAG}` });
  const more = await notice({ transferAmount: amounts.C + 1000, content: `SEVQR ${codes.C} more ${TAG}` });
  // Two halves of invoice D, each below the total: neither pays and they are not added together.
  const half1 = await notice({ transferAmount: amounts.D / 2, content: `SEVQR ${codes.D} half1 ${TAG}` });
  const half2 = await notice({ transferAmount: amounts.D / 2, content: `SEVQR ${codes.D} half2 ${TAG}` });
  for (const r of [less, more, half1, half2]) expect(r.status).toBe(200);
  const rows = await unmatchedRows();
  for (const mark of [`less ${TAG}`, `more ${TAG}`, `half1 ${TAG}`, `half2 ${TAG}`]) {
    const row = rows.find((r) => r.content?.includes(mark));
    expect(row, mark).toBeTruthy();
    expect(row.reasonText).toMatch(/số tiền khác tổng/);
  }
  for (const k of ['B', 'C', 'D'] as const) expect((await statusOf(k)).status, `${k} stays unpaid`).not.toMatch(/^PAID$/i);
});

test('AC-470 a matching transfer pays the invoice (any letter case, with or without the prefix); lines and total do not change', async () => {
  const before = await getJson<any>(boss, `/api/billing/invoices/${inv.A.id}`);
  const res = await notice({ transferAmount: amounts.A, content: `sevqr ${codes.A.toLowerCase()} chuyen tien ${TAG}` });
  expect(res.status).toBe(200);
  expect(JSON.parse(res.text)).toEqual({ success: true });
  const view = await statusOf('A');
  expect(view.status).toMatch(/^PAID$/i);
  expect(view.paidAt).toBeTruthy();
  expect(!view.qrUrl, 'a paid invoice shows no QR (AC-482)').toBe(true);
  const after = await getJson<any>(boss, `/api/billing/invoices/${inv.A.id}`);
  expect(JSON.stringify(after.body)).toBe(JSON.stringify(before.body));
  // The code alone, without the prefix, pays too (invoice E).
  const bare = await notice({ transferAmount: amounts.E, content: `${codes.E} ${TAG}` });
  expect(bare.status).toBe(200);
  expect((await statusOf('E')).status).toMatch(/^PAID$/i);
});

test('AC-471 the same transfer announced twice, also at once, is counted once', async () => {
  const id = `PC-${TAG}-DUP`;
  const countA = async () => (await receivedRows()).filter((r) => r.invoiceId === inv.C.id).length;
  expect(await countA()).toBe(0);
  const results = await Promise.all(
    Array.from({ length: 5 }, () => notice({ id, transferAmount: amounts.C, content: `SEVQR ${codes.C} dup ${TAG}` })),
  );
  for (const r of results) {
    expect(r.status).toBe(200);
    expect(JSON.parse(r.text)).toEqual({ success: true });
  }
  expect(await countA(), 'one payment only').toBe(1);
  expect((await notice({ id, transferAmount: amounts.C, content: `SEVQR ${codes.C} dup ${TAG}` })).status).toBe(200);
  expect(await countA()).toBe(1);
  expect((await statusOf('C')).status).toMatch(/^PAID$/i);
});

test('AC-474 a second transfer for a paid invoice is kept unmatched and changes nothing', async () => {
  const paidAt = (await statusOf('A')).paidAt;
  const res = await notice({ transferAmount: amounts.A, content: `SEVQR ${codes.A} again ${TAG}` });
  expect(res.status).toBe(200);
  const row = (await unmatchedRows()).find((r) => r.content?.includes(`again ${TAG}`));
  expect(row?.reasonText).toMatch(/hóa đơn đã thanh toán/);
  expect((await statusOf('A')).paidAt).toBe(paidAt);
});

test('AC-483 a payment from the test mode is labelled simulated for the Club Manager and the owner', async () => {
  const rows = await receivedRows();
  const row = rows.find((r) => r.invoiceId === inv.A.id);
  expect(row?.simulated).toBe(true);
  expect((await statusOf('A')).simulated).toBe(true);
});

test('AC-477 an assignment without a reason is refused; AC-478 to a paid invoice is refused; AC-476 an assignment pays and is kept', async () => {
  const less = (await unmatchedRows()).find((r) => r.content?.includes(`less ${TAG}`));
  expect(less, 'the smaller transfer of invoice B').toBeTruthy();

  const noReason = await csrfPost(boss, `/api/payments/transfers/${less.id}/assign`, { invoiceId: inv.B.id, reason: '' });
  expect(noReason.status()).toBe(400);
  expect(await noReason.text()).toContain('reason');
  const blank = await csrfPost(boss, `/api/payments/transfers/${less.id}/assign`, { invoiceId: inv.B.id, reason: '   ' });
  expect(blank.status()).toBe(400);

  const toPaid = await csrfPost(boss, `/api/payments/transfers/${less.id}/assign`, { invoiceId: inv.A.id, reason: 'Thử gán vào hóa đơn đã trả' });
  expect(toPaid.status()).toBe(409);
  expect((await statusOf('B')).status).not.toMatch(/^PAID$/i);

  const unpaid = await getJson<any[]>(boss, '/api/payments/unpaid-invoices');
  expect(unpaid.body.some((u) => u.id === inv.B.id)).toBe(true);
  expect(unpaid.body.some((u) => u.id === inv.A.id)).toBe(false);

  const ok = await csrfPost(boss, `/api/payments/transfers/${less.id}/assign`, { invoiceId: inv.B.id, reason: 'Chủ ngựa chuyển thiếu, đã nhận phần còn lại' });
  expect(ok.status(), await ok.text()).toBeLessThan(300);
  expect((await statusOf('B')).status).toMatch(/^PAID$/i);

  // The transfer that was assigned cannot be assigned again, to any invoice.
  const again = await csrfPost(boss, `/api/payments/transfers/${less.id}/assign`, { invoiceId: inv.D.id, reason: 'Gán lần hai' });
  expect(again.status()).toBe(409);
  expect((await statusOf('D')).status).not.toMatch(/^PAID$/i);

  // The assignment works whatever the amount: a larger transfer pays a smaller invoice when the Club Manager decides.
  const half = (await unmatchedRows()).find((r) => r.content?.includes(`half1 ${TAG}`));
  const okHalf = await csrfPost(boss, `/api/payments/transfers/${half.id}/assign`, { invoiceId: inv.D.id, reason: 'Đã xác nhận với chủ ngựa' });
  expect(okHalf.status(), await okHalf.text()).toBeLessThan(300);
  expect((await statusOf('D')).status).toMatch(/^PAID$/i);

  const assigned = (await unmatchedRows()).find((r) => r.id === less.id);
  if (assigned) expect(JSON.stringify(assigned)).toMatch(/ASSIGNED|assigned|invoiceId":\s*\d+/i);
});

test('AC-479 an owner cannot open or read the payment of another owner; other roles are refused', async () => {
  const other = owners[OWNERS[1]];
  const openOther = await csrfPost(other, `/api/payments/invoices/${inv.A.id}/open`, {});
  expect([403, 404]).toContain(openOther.status());
  expect([403, 404]).toContain((await getJson(other, `/api/payments/invoices/${inv.A.id}`)).status);

  for (const path of ['/api/payments/unmatched', '/api/payments/received', '/api/payments/unpaid-invoices']) {
    expect((await getJson(owners[OWNERS[0]], path)).status, `owner ${path}`).toBe(403);
  }
  const assign = await csrfPost(owners[OWNERS[0]], '/api/payments/transfers/1/assign', { invoiceId: inv.A.id, reason: 'x' });
  expect(assign.status()).toBe(403);

  for (const [name, who] of [['Head Trainer', people.HEAD_TRAINER], ['Veterinarian', people.VETERINARIAN], ['Groom', people.GROOM]] as const) {
    const api = await login(who.email, who.password);
    try {
      expect((await csrfPost(api, `/api/payments/invoices/${inv.A.id}/open`, {})).status(), `${name} open`).toBe(403);
      expect((await getJson(api, `/api/payments/invoices/${inv.A.id}`)).status, `${name} status`).toBe(403);
      for (const path of ['/api/payments/unmatched', '/api/payments/received']) expect((await getJson(api, path)).status, `${name} ${path}`).toBe(403);
    } finally {
      await api.dispose();
    }
  }
});

test('the Club Manager reads the payment status of every invoice but is offered no QR; the owner lists only own invoices with status', async () => {
  const all = await getJson<{ items: Inv[] }>(boss, '/api/billing/invoices?size=100');
  for (const k of ['A', 'B', 'C', 'D', 'E'] as const) {
    const row = all.body.items.find((x) => x.id === inv[k].id)!;
    expect(row.paymentStatus, `status of ${k} seen by the Club Manager`).toBe('PAID');
  }
  // The owner's payment page is not the Club Manager's: no QR is offered to the Club Manager for an owner, paid or not.
  expect((await getJson(boss, `/api/payments/invoices/${inv.A.id}`)).status).toBe(403);
  expect((await csrfPost(boss, `/api/payments/invoices/${inv.A.id}/open`, {})).status()).toBe(403);

  const mine = await getJson<{ items: Inv[] }>(owners[OWNERS[0]], '/api/billing/invoices?size=100');
  expect(mine.body.items.length).toBeGreaterThan(0);
  expect(mine.body.items.every((x) => x.ownerId === inv.A.ownerId)).toBe(true);
  expect(mine.body.items.find((x) => x.id === inv.A.id)?.paymentStatus).toBe('PAID');
});

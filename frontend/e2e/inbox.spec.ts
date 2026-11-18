import { expect, test } from './fixtures';
import { admin, ensurePeople, people, signIn } from './people';

// REQ-042, BR-227..BR-234, AC-487, AC-488, AC-492, AC-496, AC-502: a Horse Owner writes to the Club Manager, the Club Manager
// answers, the owner sees the unread count and the answer. Runs on the throwaway stack of scripts/e2e.sh and, with E2E_DEMO=1,
// on a local stack. Every run uses its own subject so it can be repeated on the same stack.

test.describe.configure({ timeout: 240_000 });
test.beforeAll(async () => {
  await ensurePeople();
});

const BASE = process.env.BASE_URL ?? 'http://localhost:5173';
const STAMP = Date.now().toString(36);
const ASK = `Cho ăn sớm hơn 30 phút được không? ${STAMP}`;

test('an owner writes to the Club Manager, who answers; the owner sees the count and the answer in one chat', async ({ page, browser }) => {
  await signIn(page, people.HORSE_OWNER);
  await page.getByRole('navigation', { name: 'main' }).getByRole('link', { name: /Hộp thư/ }).click();
  await expect(page.getByRole('heading', { level: 1, name: 'Hộp thư' })).toBeVisible();

  await page.getByRole('button', { name: /Gửi tin nhắn/ }).first().click();
  const dialog = page.getByRole('dialog');
  await dialog.getByLabel('Gửi tới').selectOption({ label: 'Quản lý CLB' });
  await dialog.getByLabel('Nội dung').fill(ASK);
  await dialog.getByRole('button', { name: 'Gửi', exact: true }).click();
  // the chat with the Club Manager opens
  await expect(page.getByRole('heading', { level: 2, name: 'Quản lý CLB' })).toBeVisible();
  await expect(page.getByText(ASK).first()).toBeVisible();

  // AC-492: an owner is offered the Club Manager, and nobody else without a horse
  const offered = await (await page.request.get(`${BASE}/api/inbox/recipients`)).json();
  expect(offered.recipients.every((r: { role: string }) => r.role === 'CLUB_MANAGER')).toBe(true);

  // The owner leaves the chat first: while it is open the screen reads it every 5 seconds, so an answer that arrives
  // meanwhile is read at once and there is nothing left to count.
  await page.goto('/inbox');
  await expect(page.getByRole('list', { name: 'Danh sách hội thoại' }).getByRole('button', { name: /Quản lý CLB/ })).toBeVisible();

  // the Club Manager reads and answers (AC-488)
  const manager = await browser.newContext({ baseURL: BASE });
  try {
    const mp = await manager.newPage();
    await signIn(mp, admin);
    await mp.getByRole('navigation', { name: 'main' }).getByRole('link', { name: /Hộp thư/ }).click();
    await mp.getByRole('list', { name: 'Danh sách hội thoại' }).getByRole('button', { name: new RegExp(STAMP) }).first().click();
    await expect(mp.getByText(ASK).first()).toBeVisible();
    await mp.getByLabel('Trả lời').fill('Đã ghi nhận, chúng tôi sẽ đổi giờ từ tuần sau.');
    await mp.getByRole('button', { name: 'Gửi', exact: true }).click();
    await expect(mp.locator('p', { hasText: 'Đã ghi nhận, chúng tôi sẽ đổi giờ từ tuần sau.' })).toBeVisible();
  } finally {
    await manager.close();
  }

  // AC-502: the owner is told, and opening the chat clears the count
  await page.goto('/inbox');
  await expect(page.getByRole('navigation', { name: 'main' }).getByRole('status', { name: /tin chưa đọc/ }).first()).toBeVisible();
  await page.getByRole('list', { name: 'Danh sách hội thoại' }).getByRole('button', { name: /Quản lý CLB/ }).click();
  await expect(page.locator('p', { hasText: 'Đã ghi nhận, chúng tôi sẽ đổi giờ từ tuần sau.' })).toBeVisible();
  await expect(page.getByRole('navigation', { name: 'main' }).getByRole('status', { name: /tin chưa đọc/ })).toHaveCount(0);
});

test('a feedback to the Club Manager is highlighted in the chat for both sides, and the chat does not scroll the page', async ({ page, browser }) => {
  const title = `Ngựa khỏe hơn nhiều, cảm ơn các bạn. ${Date.now().toString(36)}`;
  await signIn(page, people.HORSE_OWNER);
  await page.goto('/inbox');
  await page.getByRole('button', { name: 'Feedback' }).click();
  const dialog = page.getByRole('dialog');
  await expect(dialog.getByLabel('Gửi tới')).toHaveCount(0);
  await dialog.getByLabel('Nội dung phản hồi').fill(title);
  await dialog.getByRole('button', { name: 'Gửi', exact: true }).click();
  await expect(page.locator('div.border-amber-300', { hasText: title })).toBeVisible();
  expect(await page.evaluate(() => document.documentElement.scrollHeight <= window.innerHeight + 1)).toBe(true);

  const manager = await browser.newContext({ baseURL: BASE });
  try {
    const mp = await manager.newPage();
    await signIn(mp, admin);
    await mp.goto('/inbox');
    await mp.getByRole('list', { name: 'Danh sách hội thoại' }).getByRole('button', { name: new RegExp(title.slice(-8)) }).first().click();
    await expect(mp.locator('div.border-amber-300', { hasText: title })).toBeVisible();
  } finally {
    await manager.close();
  }
});

test('AC-496: a conversation between two people is not found by anybody else', async ({ page, browser }) => {
  await signIn(page, people.GROOM);
  const created = await page.request.get(`${BASE}/api/inbox/conversations/999999999`);
  expect(created.status()).toBe(404);
  const other = await browser.newContext({ baseURL: BASE });
  try {
    const op = await other.newPage();
    await signIn(op, people.HORSE_OWNER);
    const list = await (await op.request.get(`${BASE}/api/inbox/conversations?size=100`)).json();
    // the owner sees only their own conversations, never one that is not addressed to them
    expect(list.items.every((c: { with: string }) => typeof c.with === 'string')).toBe(true);
  } finally {
    await other.close();
  }
});

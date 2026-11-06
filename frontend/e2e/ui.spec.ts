import { mkdirSync, readFileSync, writeFileSync } from 'node:fs';
import AxeBuilder from '@axe-core/playwright';
import type { Locator, Page } from '@playwright/test';
import { expect, test } from './fixtures';
import { admin } from './people';

/** Chooses from a search select: opens the combobox and clicks the option (BR-186, TECH-178). */
async function pick(page: Page, scope: Page | Locator, label: string | RegExp, option: string | RegExp) {
  await scope.getByRole('combobox', { name: label }).click();
  const wanted = page.getByRole('option', { name: option }).first();
  // A long list shows a search box and only its first page: type the name when the option is not on that page.
  const search = page.getByPlaceholder('Gõ để tìm…');
  await wanted.or(search).first().waitFor();
  if (typeof option === 'string' && !(await wanted.isVisible()) && (await search.isVisible())) await search.fill(option);
  await wanted.click();
}

// Task 3.3 (TECH-081): every page per role at 1440, 1024, 768, 844x390 (phone landscape) and 390 — screenshot, no console errors, no failed
// requests, nothing off-screen, no serious/critical axe finding. Self-contained: makes its own accounts.
const suffix = `ui${process.env.E2E_SUFFIX ?? Date.now().toString(36)}`;
const people = {
  HEAD_TRAINER: { username: `ht-${suffix}`, password: 'trainer-password-1', name: `Trần Văn Hùng ${suffix}` },
  VETERINARIAN: { username: `vet-${suffix}`, password: 'vet-password-12', name: `Nguyễn Thị Mai ${suffix}` },
  GROOM: { username: `groom-${suffix}`, password: 'groom-password-1', name: `Lê Văn Tâm ${suffix}` },
  HORSE_OWNER: { username: `owner-${suffix}`, password: 'owner-password-1', name: `Phạm Quốc Bảo ${suffix}` },
};
const horseName = `Bạch Long ${suffix}`;
const OUT = 'test-results/ui';
const WIDTHS = [
  { name: '1440', width: 1440, height: 900 },
  { name: '1024', width: 1024, height: 768 }, // tablet landscape: the rail appears at 1024
  { name: '768', width: 768, height: 1024 }, // tablet portrait: tables, phone-style navigation
  { name: '844x390', width: 844, height: 390 }, // phone landscape
  { name: '390', width: 390, height: 844 },
];

mkdirSync(OUT, { recursive: true });
// Each role test opens up to six pages at two widths, with axe on each: more than the 60 s default.
test.describe.configure({ timeout: 480_000 });

const FINDINGS = `${OUT}/findings.json`;
/** Merged into the file after every page, so a worker restart after a failure loses nothing. */
function record(key: string, value: unknown) {
  let all: Record<string, unknown> = {};
  try {
    all = JSON.parse(readFileSync(FINDINGS, 'utf8'));
  } catch {
    all = {};
  }
  all[key] = value;
  writeFileSync(FINDINGS, JSON.stringify(all, null, 2));
}

/**
 * Element-level layout detector. A visible element is reported when
 *  - it leaves the viewport and no scroller contains it (off-screen), or
 *  - its nearest `overflow: hidden|clip` ancestor cuts it off (clipped): `hidden` is not an excuse, it is how content
 *    gets silently lost. Scrollers (`auto|scroll`) are legitimate; decorative `aria-hidden` content is ignored.
 */
async function offscreen(page: Page): Promise<string[]> {
  return page.evaluate(() => {
    const vw = document.documentElement.clientWidth;
    const label = (el: Element, kind: string) =>
      `${kind}: ${el.tagName.toLowerCase()} "${(el.textContent ?? '').trim().slice(0, 40)}"`;
    const out: string[] = [];
    for (const el of document.querySelectorAll('body *')) {
      const r = el.getBoundingClientRect();
      if (r.width < 1 || r.height < 1) continue;
      const cs = getComputedStyle(el);
      // opacity 0: Radix's hidden checkbox <input> is not content
      if (cs.visibility === 'hidden' || cs.position === 'fixed' || cs.opacity === '0') continue;
      let clipper: Element | null = null;
      let scroller = false;
      for (let p = el.parentElement; p && p !== document.body; p = p.parentElement) {
        const ox = getComputedStyle(p).overflowX;
        if (ox === 'auto' || ox === 'scroll') {
          scroller = true;
          break;
        }
        if (ox === 'hidden' || ox === 'clip') {
          clipper = p;
          break;
        }
      }
      if (scroller) continue;
      if (clipper) {
        if (el.closest('[aria-hidden="true"]')) continue;
        const c = clipper.getBoundingClientRect();
        if (r.right > c.right + 1 || r.left < c.left - 1) out.push(label(el, 'clipped'));
        continue;
      }
      if (r.right > vw + 1 || r.left < -1) out.push(label(el, 'off-screen'));
    }
    return out;
  });
}

async function signIn(page: Page, username: string, password: string) {
  await page.goto('/');
  await page.getByLabel('Tên đăng nhập').fill(username);
  await page.getByLabel('Mật khẩu').fill(password);
  await page.getByRole('button', { name: 'Đăng nhập' }).click();
  await expect(page.getByTestId('user-menu').first()).toBeAttached();
}

/**
 * The 3D canvas follows its container with a short delay (a scrollbar can appear once the list has loaded, a full-page
 * capture resizes the viewport): wait until it fits its clipping ancestor before the layout is measured.
 */
async function canvasFits(page: Page) {
  await expect
    .poll(
      () =>
        page.evaluate(() => {
          const c = document.querySelector('canvas');
          if (!c) return true;
          for (let p = c.parentElement; p && p !== document.body; p = p.parentElement) {
            const ox = getComputedStyle(p).overflowX;
            if (ox === 'hidden' || ox === 'clip') return c.getBoundingClientRect().right <= p.getBoundingClientRect().right + 1;
          }
          return true;
        }),
      { timeout: 20_000 },
    )
    .toBe(true);
}

/** Opens `path`, waits for content, then runs every check at both widths. */
async function check(page: Page, role: string, label: string, path: string, allow: RegExp[] = []) {
  for (const w of WIDTHS) {
    const errors: string[] = [];
    const failed: string[] = [];
    const onConsole = (m: { type: () => string; text: () => string }) => {
      // The browser echoes every failed request as a console error; requests are checked on their own below.
      if (m.type() === 'error' && !m.text().startsWith('Failed to load resource')) errors.push(m.text());
    };
    const onResponse = (r: { status: () => number; url: () => string }) => {
      const line = `${r.status()} ${r.url()}`;
      if (r.status() >= 400 && !allow.some((a) => a.test(line))) failed.push(line);
    };
    page.on('console', onConsole);
    page.on('response', onResponse);
    await page.setViewportSize({ width: w.width, height: w.height });
    await page.goto(path);
    await expect(page.getByRole('heading', { level: 1 }).first()).toBeVisible();
    if (/\/injuries$|^\/incidents$/.test(path)) {
      // The 3D model is read from a `blob:` request that never reports "finished" when the browser is driven over CDP, so
      // `networkidle` cannot be reached on these pages: wait for the load event and give the scene a fixed moment.
      await page.waitForLoadState('load');
      await page.waitForTimeout(2000);
      await canvasFits(page);
    } else {
      await page.waitForLoadState('networkidle');
    }
    await page.waitForTimeout(450);
    const file = `${OUT}/${role}-${label}-${w.name}.png`;
    await page.screenshot({ path: file, fullPage: true });
    // A full-page capture resizes the viewport for a moment: the canvas has to settle again before it is measured.
    if (/\/injuries$|^\/incidents$/.test(path)) await canvasFits(page);
    const off = await offscreen(page);
    const axe = await new AxeBuilder({ page }).analyze();
    const serious = axe.violations
      .filter((v) => v.impact === 'serious' || v.impact === 'critical')
      .map((v) => `${v.id}: ${v.nodes.length} × ${v.nodes[0]?.target.join(' ')}`);
    page.off('console', onConsole);
    page.off('response', onResponse);
    record(`${role} ${label} @${w.name}`, { errors, failed, off, serious });
    expect.soft(errors, `console errors on ${path} @${w.name}`).toEqual([]);
    expect.soft(failed, `failed requests on ${path} @${w.name}`).toEqual([]);
    expect.soft(off, `off-screen elements on ${path} @${w.name}`).toEqual([]);
    expect.soft(serious, `axe serious/critical on ${path} @${w.name}`).toEqual([]);
  }
}

// Written by the setup test, read by the role tests (a failed test restarts the worker, so no module state).
const HORSE_FILE = `${OUT}/horse-path.txt`;
const horse = () => readFileSync(HORSE_FILE, 'utf8');
const PLAN_FILE = `${OUT}/plan-path.txt`;
const SESSION_FILE = `${OUT}/session-path.txt`;
const plan = () => readFileSync(PLAN_FILE, 'utf8');
const session = () => readFileSync(SESSION_FILE, 'utf8');

// The club's calendar date (Asia/Ho_Chi_Minh), as in smoke.spec.ts.
const clubDate = (offsetDays = 0) =>
  new Date(Date.now() + offsetDays * 864e5).toLocaleDateString('sv-SE', { timeZone: 'Asia/Ho_Chi_Minh' });

test('setup: accounts and one horse for the UI checks', async ({ page }) => {
  await signIn(page, admin.username, admin.password);
  for (const [role, p] of Object.entries(people)) {
    await page.getByRole('link', { name: 'Tài khoản' }).click();
    await page.getByLabel(/^Tên đăng nhập/).fill(p.username);
    await page.getByLabel(/^Họ tên/).fill(p.name);
    await page.getByLabel(/^Mật khẩu/).fill(p.password);
    await page.getByLabel(/^Vai trò/).selectOption(role);
    await page.getByRole('button', { name: 'Tạo tài khoản' }).click();
    // The accounts list is paged: search for the new account instead of hoping it is on the first page.
    await page.getByPlaceholder('Tìm theo họ tên hoặc tên đăng nhập').fill(p.username);
    await expect(page.getByRole('cell', { name: p.username, exact: true })).toBeVisible();
    await page.getByPlaceholder('Tìm theo họ tên hoặc tên đăng nhập').fill('');
  }
  await page.getByRole('link', { name: 'Ngựa' }).click();
  await page.getByRole('link', { name: 'Đăng ký ngựa' }).first().click();
  await page.getByLabel(/^Tên ngựa/).fill(horseName);
  // The registration number is generated and has no field (BR-192).
  await expect(page.getByLabel(/^Số đăng ký/)).toHaveCount(0);
  await page.getByLabel(/^Giới tính/).selectOption('MALE');
  await page.getByLabel(/^Ngày sinh/).fill('2020-03-18');
  await pick(page, page, /^Chủ sở hữu/, people.HORSE_OWNER.name);
  // Every horse has an assigned Head Trainer, the one who plans and starts its sessions below (BR-152).
  await pick(page, page, /^HLV phụ trách/, people.HEAD_TRAINER.name);
  await page.getByRole('button', { name: 'Lưu' }).click();
  await expect(page).toHaveURL(/\/horses\/\d+$/);
  writeFileSync(HORSE_FILE, new URL(page.url()).pathname);
});

test('setup: the horse is cleared, planned and a session is running, assigned to the Groom', async ({ page }) => {
  // Veterinarian: examine the new horse, lift the intake lock, set Đủ điều kiện.
  await signIn(page, people.VETERINARIAN.username, people.VETERINARIAN.password);
  await page.goto(horse());
  // The horse page is a hub of tiles; a tile opens its panel in a dialog.
  await page.getByRole('button', { name: /^Sức khỏe/ }).click();
  const health = page.getByRole('region', { name: 'Sức khỏe' });
  await health.getByLabel('Mức độ quay lại').selectOption('RESUME_AS_IS');
  await health.getByRole('button', { name: 'Gỡ khóa' }).click();
  await expect(health.getByText('Không khóa')).toBeVisible();
  await health.getByLabel('Trạng thái mới').selectOption('ELIGIBLE');
  await health.getByLabel('Lý do').first().fill('Khám nhập đàn, đạt yêu cầu');
  await health.getByRole('button', { name: 'Đổi trạng thái' }).click();
  await expect(health.locator('dd').filter({ hasText: /^Đủ điều kiện$/ })).toBeVisible();

  // Still the Veterinarian: record an examination, then mark an injury on the 3D page, so the examination list and
  // the marks list (status badge, history, status form) exist for the captures.
  await page.keyboard.press('Escape');
  await page.getByRole('button', { name: /^Hồ sơ khám bệnh/ }).click();
  const exams = page.getByRole('form', { name: 'Ghi nhận lần khám' });
  await exams.getByLabel(/^Chẩn đoán/).fill('Viêm khớp bàn trước trái');
  await pick(page, exams, 'Vùng cơ thể', /^Khớp bàn trước trái$/);
  // Once any exam fee type is priced (the demo data has some), the fee type is mandatory (BR-178).
  const feeType = exams.getByLabel(/^Loại phí khám/);
  if (await feeType.count()) await feeType.selectOption({ index: 1 });
  await exams.getByRole('button', { name: 'Ghi nhận lần khám' }).click();
  await expect(page.getByRole('cell', { name: 'Viêm khớp bàn trước trái' })).toBeVisible();
  await page.goto(`${horse()}/injuries`);
  await pick(page, page, 'Vùng cơ thể', /^Khớp bàn trước trái$/);
  await page.getByLabel('Hồ sơ khám').selectOption({ index: 1 });
  await page.getByRole('button', { name: 'Đánh dấu chấn thương' }).click();
  // The 3D page is slow to settle under software WebGL, so this waits longer than the default 5 s.
  await expect(page.getByRole('listitem', { name: 'Khớp bàn trước trái' })).toContainText('Mới', { timeout: 20_000 });
  await page.getByRole('button', { name: 'Đăng xuất' }).click();

  // Head Trainer: plan, activate, schedule today for the Groom, start.
  await signIn(page, people.HEAD_TRAINER.username, people.HEAD_TRAINER.password);
  await page.goto(`${horse()}/plans/new`);
  await page.getByLabel('Tên giáo án').fill('Giáo án mùa thu');
  await page.getByLabel('Tên giai đoạn').fill('Nền tảng');
  await page.getByLabel('Bắt đầu').fill(clubDate());
  await page.getByLabel('Kết thúc').fill(clubDate(30));
  await page.getByLabel('Mục tiêu').fill('Sức bền');
  await page.getByLabel('Cự ly (m)').fill('1600');
  await page.getByLabel('Khối lượng (km/tuần)').fill('12');
  await page.getByLabel('Mặt sân').fill('Cỏ');
  await page.getByRole('button', { name: 'Lưu' }).click();
  await page.getByRole('button', { name: 'Kích hoạt' }).click();
  await expect(page.getByRole('heading', { level: 1 })).toContainText('Đang thực hiện');
  writeFileSync(PLAN_FILE, new URL(page.url()).pathname);

  const form = page.getByRole('form', { name: 'Thêm buổi tập' });
  await form.getByLabel('Ngày').fill(clubDate());
  await form.getByLabel('Giờ bắt đầu').fill('05:00');
  await form.getByLabel('Loại buổi tập').selectOption('GALLOP');
  // The Grooms offered are the ones free for the time range (BR-162), once date and time are set.
  await expect(form.getByRole('combobox', { name: 'Người phụ trách' })).toBeEnabled();
  await pick(page, form, 'Người phụ trách', people.GROOM.name);
  await form.getByRole('button', { name: 'Thêm buổi tập' }).click();
  await page.getByRole('link', { name: clubDate() }).click();
  await page.getByRole('button', { name: 'Bắt đầu' }).click();
  await expect(page.getByText('Đang tập', { exact: true })).toBeVisible();
  writeFileSync(SESSION_FILE, new URL(page.url()).pathname);
});

test('stable care: a Groom ticks a chore, the Club Manager sets a stall and decides a restock', async ({ page }) => {
  const item = `Yến mạch ${suffix}`;
  // Groom: tick feeding for the horse, then tick it away again with a reason.
  await signIn(page, people.GROOM.username, people.GROOM.password);
  await page.getByRole('link', { name: 'Việc hằng ngày' }).click();
  // The chores list is paged: narrow it to this run's horse.
  await page.getByPlaceholder(/^Tìm theo tên ngựa/).fill(horseName);
  await page.getByRole('button', { name: `Đánh dấu Cho ăn cho ${horseName}` }).click();
  await expect(page.getByRole('button', { name: `Hủy đánh dấu Cho ăn của ${horseName}` })).toBeVisible();
  await page.getByRole('button', { name: `Đánh dấu Vệ sinh chuồng cho ${horseName}` }).click();
  await expect(page.getByRole('button', { name: `Hủy đánh dấu Vệ sinh chuồng của ${horseName}` })).toBeVisible();
  await page.getByRole('button', { name: `Hủy đánh dấu Vệ sinh chuồng của ${horseName}` }).click();
  await page.getByLabel('Lý do hủy').fill('Nhầm ngựa');
  await page.getByRole('button', { name: 'Hủy đánh dấu', exact: true }).last().click();
  await expect(page.getByRole('button', { name: `Đánh dấu Vệ sinh chuồng cho ${horseName}` })).toBeVisible();
  await page.getByRole('button', { name: 'Đăng xuất' }).click();

  // Club Manager: stall for the horse, one supply item.
  await signIn(page, admin.username, admin.password);
  const areaName = `Khu ${suffix}`;
  await page.goto('/barn');
  await page.getByRole('button', { name: 'Thêm khu' }).click();
  await page.getByLabel('Tên khu').fill(areaName);
  await page.getByLabel('Sức chứa (số ô)').fill('4');
  await page.getByRole('button', { name: 'Lưu' }).click();
  // The new area shows its four stalls as empty placeholders; the horse is picked from a select (BR-165).
  await page.getByRole('button', { name: `Ô trống 3 của ${areaName}: chọn ngựa để xếp` }).click();
  await pick(page, page, 'Ngựa chưa có chuồng', horseName);
  await page.getByRole('button', { name: 'Lưu' }).click();
  await expect(page.getByRole('list', { name: areaName }).getByText(horseName)).toBeVisible();
  await page.goto('/supplies');
  await page.getByRole('button', { name: 'Thêm vật tư' }).click();
  // The page filter and the form both carry the label "Loại": the form lives in the dialog.
  const supplyForm = page.getByRole('dialog');
  await supplyForm.getByLabel('Tên vật tư', { exact: true }).fill(item);
  await supplyForm.getByLabel('Loại', { exact: true }).selectOption({ label: 'Thức ăn' });
  await supplyForm.getByLabel('Đơn vị', { exact: true }).selectOption('kg');
  await supplyForm.getByLabel('Giá (VND)', { exact: true }).fill('14000');
  await supplyForm.getByLabel('Tồn đầu', { exact: true }).fill('120');
  await supplyForm.getByRole('button', { name: 'Lưu' }).click();
  // The catalog is paged: search for the new item instead of hoping it is on the first page.
  await page.getByPlaceholder('Tìm vật tư theo tên').fill(item);
  await expect(page.getByRole('cell', { name: item, exact: true })).toBeVisible();
  await page.getByRole('button', { name: 'Đăng xuất' }).click();

  // Groom proposes a restock; Club Manager approves it.
  await signIn(page, people.GROOM.username, people.GROOM.password);
  await page.goto('/supplies');
  await page.getByPlaceholder('Tìm vật tư theo tên').fill(item);
  await page.getByRole('button', { name: `Đề xuất bổ sung ${item}` }).click();
  await page.getByLabel(/^Số lượng cần bổ sung/).fill('200');
  await page.getByRole('button', { name: 'Gửi đề xuất' }).click();
  await expect(page.getByText('Chờ duyệt').first()).toBeVisible();
  await page.getByRole('button', { name: 'Đăng xuất' }).click();
  await signIn(page, admin.username, admin.password);
  await page.goto('/supplies');
  await page.getByRole('button', { name: `Duyệt đề xuất ${item}` }).click();
  // Approving asks for a confirmation first.
  await page.locator('[role=alertdialog],[role=dialog]').getByRole('button', { name: 'Duyệt', exact: true }).click();
  // Other pending proposals may exist (the demo data has some): only this one must be gone from the pending list.
  await expect(page.getByRole('button', { name: `Duyệt đề xuất ${item}` })).toHaveCount(0);
  await page.getByRole('button', { name: 'Đăng xuất' }).click();
  // The Groom sees the decision on their own proposals.
  await signIn(page, people.GROOM.username, people.GROOM.password);
  await page.goto('/supplies');
  await expect(page.getByText('Đã duyệt').first()).toBeVisible();
});

test('the layout detector catches broken layouts and ignores legitimate ones', async ({ page }) => {
  await page.setViewportSize({ width: 390, height: 844 });
  await page.goto('/');
  await page.evaluate(() => {
    const add = (html: string) => document.body.insertAdjacentHTML('beforeend', html);
    // A: a wide block straight in the page (off-screen).
    add('<div style="width:900px">quá rộng</div>');
    // B: a visible child cut off by an overflow:hidden parent (clipped).
    add('<div style="overflow:hidden;width:200px"><div style="width:500px">bị cắt</div></div>');
    // C: the same child inside a scroller (legitimate).
    add('<div style="overflow-x:auto;width:200px"><div style="width:500px">cuộn được</div></div>');
    // D: decorative aria-hidden content inside an overflow:hidden parent (legitimate).
    add('<div style="overflow:hidden;width:200px"><div aria-hidden="true" style="width:500px">trang trí</div></div>');
  });
  const found = await offscreen(page);
  expect(found).toContainEqual(expect.stringContaining('off-screen: div "quá rộng"'));
  expect(found).toContainEqual(expect.stringContaining('clipped: div "bị cắt"'));
  expect(found.join('\n')).not.toContain('cuộn được');
  expect(found.join('\n')).not.toContain('trang trí');
});

test('signed out: the login page', async ({ page }) => {
  // Signed out, the app asks who is signed in and is told nobody (401): that is how it decides to show the login page.
  await check(page, 'public', 'login', '/', [/^401 .*\/api\/auth\/me$/]);
});

test('Club Manager pages', async ({ page }) => {
  await signIn(page, admin.username, admin.password);
  for (const [label, path] of [
    ['horses', '/horses'],
    ['horse-new', '/horses/new'],
    ['horse-detail', horse()],
    ['horse-edit', `${horse()}/edit`],
    ['health', '/health'],
    ['accounts', '/accounts'],
    ['audit', '/audit'],
    ['settings', '/settings'],
    ['chores', '/chores'],
    ['barn', '/barn'],
    ['supplies', '/supplies'],
    ['billing', '/billing'],
  ]) {
    await check(page, 'manager', label, path);
  }
});

test('Head Trainer pages', async ({ page }) => {
  await signIn(page, people.HEAD_TRAINER.username, people.HEAD_TRAINER.password);
  for (const [label, path] of [
    ['horses', '/horses'],
    ['horse-detail', horse()],
    ['health', '/health'],
    ['progress', '/progress'],
    ['plan-new', `${horse()}/plans/new`],
    ['plan-detail', plan()],
    ['session-live', session()],
    ['chores', '/chores'],
    ['barn', '/barn'],
    ['timetable', '/timetable'],
  ]) {
    await check(page, 'trainer', label, path);
  }
});

// Two tests: eight pages at five widths, one of them the 3D view under software WebGL, do not fit one 480 s budget on a
// stack with the demo data.
test('Veterinarian pages (care and records)', async ({ page }) => {
  await signIn(page, people.VETERINARIAN.username, people.VETERINARIAN.password);
  for (const [label, path] of [
    ['health', '/health'],
    ['horse-detail', horse()],
    ['plan-detail', plan()],
    ['care', '/care'],
  ]) {
    await check(page, 'vet', label, path);
  }
});

test('Veterinarian pages (injuries, incidents, stable)', async ({ page }) => {
  await signIn(page, people.VETERINARIAN.username, people.VETERINARIAN.password);
  for (const [label, path] of [
    ['injuries', `${horse()}/injuries`],
    ['incidents', '/incidents'],
    ['chores', '/chores'],
    ['barn', '/barn'],
  ]) {
    await check(page, 'vet', label, path);
  }
});

test('Groom pages', async ({ page }) => {
  await signIn(page, people.GROOM.username, people.GROOM.password);
  for (const [label, path] of [
    ['incidents', '/incidents'],
    ['my-sessions', '/my-sessions'],
    ['timetable', '/timetable'],
    ['chores', '/chores'],
    ['barn', '/barn'],
    ['supplies', '/supplies'],
  ]) {
    await check(page, 'groom', label, path);
  }
});

test('Horse Owner pages', async ({ page }) => {
  await signIn(page, people.HORSE_OWNER.username, people.HORSE_OWNER.password);
  for (const [label, path] of [
    ['horses', '/horses'],
    ['horse-detail', horse()],
    ['chores', '/chores'],
    ['barn', '/barn'],
    ['billing', '/billing'],
  ]) {
    await check(page, 'owner', label, path);
  }
});

import { readFileSync } from 'node:fs';
import { join } from 'node:path';
import type { Locator, Page } from '@playwright/test';
import { expect, test } from './fixtures';
import { admin, ensurePeople, people } from './people';

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

/** The horse list is paged: search for this run's horse instead of hoping it is on the first page. */
async function openHorse(page: Page) {
  const search = page.getByPlaceholder(/^Tìm/).first();
  if (await search.count()) await search.fill(horseName);
  await page.getByRole('link', { name: horseName }).click();
}

/** The horse page is a hub of tiles; a tile opens its panel in a dialog (HorseDetailPage). */
async function openTile(page: Page, name: string) {
  await page.getByRole('button', { name: new RegExp(`^${name}`) }).click();
}

/** The responsible person is offered among the Grooms free for the time range, once date and time are set (BR-162). */
async function chooseResponsible(form: Locator, name: string) {
  await expect(form.getByRole('combobox', { name: 'Người phụ trách' })).toBeEnabled();
  await pick(form.page(), form, 'Người phụ trách', name);
}

// Task 7.7: AC-001 end to end. The bootstrap Club Manager signs in, creates a Horse Owner account and
// registers a horse for them; the Owner signs in and sees it.
// Fixed per run: Playwright restarts the worker after a failure, which would otherwise change it mid-file.
const suffix = process.env.E2E_SUFFIX ?? Date.now().toString(36);
const owner = { email: `owner-${suffix}@racehorse.example`, password: 'owner-password-1', name: `Chủ ngựa ${suffix}` };
const horseName = `Sao Mai ${suffix}`;
const vet = { email: `vet-${suffix}@racehorse.example`, password: 'vet-password-12', name: `Bác sĩ ${suffix}` };
const groom = { email: `groom-${suffix}@racehorse.example`, password: 'groom-password-1', name: `Groom ${suffix}` };
const trainer = { email: `ht-${suffix}@racehorse.example`, password: 'trainer-password-1', name: `HLV ${suffix}` };

// On failure, print what the user would have seen: far more useful than a locator timeout.
test.afterEach(async ({ page }, info) => {
  if (info.status !== info.expectedStatus) {
    await page.screenshot({ path: `failure-${info.line}.png`, fullPage: true });
    const alerts = await page.getByRole('alert').allInnerTexts();
    console.log(`ALERTS AT FAILURE: ${JSON.stringify(alerts)}`);
    console.log(`PAGE TEXT AT FAILURE (${info.title}):\n${(await page.locator('body').innerText()).slice(-1500)}`);
  }
});

// The club's calendar date (Asia/Ho_Chi_Minh), not UTC: between 17:00 and 24:00 UTC they differ by a day.
function clubDate(offsetDays = 0): string {
  return new Date(Date.now() + offsetDays * 864e5).toLocaleDateString('sv-SE', { timeZone: 'Asia/Ho_Chi_Minh' });
}

async function signIn(page: Page, email: string, password: string) {
  await page.goto('/');
  await page.getByLabel('Email').fill(email);
  await page.getByLabel('Mật khẩu').fill(password);
  await page.getByRole('button', { name: 'Đăng nhập' }).click();
  await page.locator('aside').first().waitFor({ state: 'attached', timeout: 15_000 });
}

async function createAccount(page: Page, a: { email: string; password: string; name: string }, role: string) {
  await page.getByRole('link', { name: 'Tài khoản' }).click();
  await page.getByLabel(/^Email/).fill(a.email);
  await page.getByLabel(/^Họ tên/).fill(a.name);
  await page.getByLabel(/^Mật khẩu/).fill(a.password);
  await page.getByLabel(/^Vai trò/).selectOption(role);
  await page.getByRole('button', { name: 'Tạo tài khoản' }).click();
  await expectAccountListed(page, a.email);
}

/** The accounts list is paged: search for the new account instead of hoping it is on the first page. */
async function expectAccountListed(page: Page, email: string) {
  await page.getByPlaceholder('Tìm theo họ tên hoặc email').fill(email);
  await expect(page.getByRole('cell', { name: email, exact: true })).toBeVisible();
  await page.getByPlaceholder('Tìm theo họ tên hoặc email').fill('');
}

test('AC-001 Club Manager registers a horse and its owner sees it', async ({ page }) => {
  await signIn(page, admin.email, admin.password);

  await page.getByRole('link', { name: 'Tài khoản' }).click();
  await page.getByLabel(/^Email/).fill(owner.email);
  await page.getByLabel(/^Họ tên/).fill(owner.name);
  await page.getByLabel(/^Mật khẩu/).fill(owner.password);
  await page.getByLabel(/^Vai trò/).selectOption('HORSE_OWNER');
  await page.getByRole('button', { name: 'Tạo tài khoản' }).click();
  await expectAccountListed(page, owner.email);
  // Every horse has an assigned Head Trainer, chosen when it is registered (BR-152).
  await createAccount(page, trainer, 'HEAD_TRAINER');

  await page.getByRole('link', { name: 'Ngựa' }).click();
  // The empty list shows a second "Đăng ký ngựa" link next to the header one; either opens the form.
  await page.getByRole('link', { name: 'Đăng ký ngựa' }).first().click();
  await page.getByLabel(/^Tên ngựa/).fill(horseName);
  // The registration number is generated and has no field (BR-192).
  await expect(page.getByLabel(/^Số đăng ký/)).toHaveCount(0);
  await page.getByLabel(/^Giới tính/).selectOption('FEMALE');
  await page.getByLabel(/^Ngày sinh/).fill('2021-04-12');
  await pick(page, page, /^Chủ sở hữu/, owner.name);
  await pick(page, page, /^HLV phụ trách/, trainer.name);
  await page.getByRole('button', { name: 'Lưu' }).click();
  await expect(page.getByRole('heading', { name: horseName })).toBeVisible();

  await page.getByRole('button', { name: 'Đăng xuất' }).click();
  await signIn(page, owner.email, owner.password);
  await expect(page.getByRole('link', { name: horseName })).toBeVisible();
  await expect(page.getByRole('link', { name: 'Tài khoản' })).toHaveCount(0);

  // AC-122 end to end: a newly registered horse carries the intake lock, so its owner sees "Không sẵn sàng".
  const row = page.getByRole('row').filter({ has: page.getByRole('link', { name: horseName }) });
  await expect(row.getByRole('cell', { name: 'Không sẵn sàng' })).toBeVisible();
});

test('AC-039 Veterinarian clears the new horse, Head Trainer plans, schedules, starts and ends a session', async ({ page }) => {
  await signIn(page, admin.email, admin.password);
  await createAccount(page, vet, 'VETERINARIAN');
  // Only an active Groom can be the responsible person of a session (BR-134, DEC-053).
  await createAccount(page, groom, 'GROOM');
  await page.getByRole('button', { name: 'Đăng xuất' }).click();

  // Veterinarian: examine the new horse, set Đủ điều kiện and lift the intake lock.
  await signIn(page, vet.email, vet.password);
  await openHorse(page);
  await openTile(page, 'Sức khỏe');
  const health = page.getByRole('region', { name: 'Sức khỏe' });
  await expect(health.getByText('Ngựa mới nhập đàn – chưa khám sức khỏe')).toBeVisible();
  await health.getByLabel('Mức độ quay lại').selectOption('RESUME_AS_IS');
  await health.getByRole('button', { name: 'Gỡ khóa' }).click();
  await expect(health.getByText('Không khóa')).toBeVisible();
  await health.getByLabel('Trạng thái mới').selectOption('ELIGIBLE');
  await health.getByLabel('Lý do').first().fill('Khám nhập đàn, đạt yêu cầu');
  await health.getByRole('button', { name: 'Đổi trạng thái' }).click();
  // Wait for the final state, not an intermediate one: signing out earlier would abort the status change.
  await expect(health.locator('dd').filter({ hasText: /^Đủ điều kiện$/ })).toBeVisible();
  await expect(health.getByText('Không khóa')).toBeVisible();
  await page.keyboard.press('Escape');
  await page.getByRole('button', { name: 'Đăng xuất' }).click();

  // Head Trainer: plan, activate, schedule today, start, end.
  await signIn(page, trainer.email, trainer.password);
  await openHorse(page);
  await openTile(page, 'Giáo án huấn luyện');
  await page.getByRole('link', { name: 'Lập giáo án mới' }).click();
  await page.getByLabel('Tên giáo án').fill('Giáo án e2e');
  const today = clubDate();
  const later = clubDate(30);
  await page.getByLabel('Tên giai đoạn').fill('Nền tảng');
  await page.getByLabel('Bắt đầu').fill(today);
  await page.getByLabel('Kết thúc').fill(later);
  await page.getByLabel('Mục tiêu').fill('Sức bền');
  await page.getByLabel('Cự ly (m)').fill('1600');
  await page.getByLabel('Khối lượng (km/tuần)').fill('12');
  await page.getByLabel('Mặt sân').fill('Cỏ');
  await page.getByRole('button', { name: 'Lưu' }).click();
  await page.getByRole('button', { name: 'Kích hoạt' }).click();
  await expect(page.getByRole('heading', { level: 1 })).toContainText('Đang thực hiện');

  const form = page.getByRole('form', { name: 'Thêm buổi tập' });
  await form.getByLabel('Ngày').fill(today);
  await form.getByLabel('Giờ bắt đầu').fill('05:00');
  await form.getByLabel('Loại buổi tập').selectOption('GALLOP');
  await chooseResponsible(form, groom.name);
  await form.getByRole('button', { name: 'Thêm buổi tập' }).click();
  await page.getByRole('link', { name: today }).click();
  await page.getByRole('button', { name: 'Bắt đầu' }).click();
  await expect(page.getByText('Đang tập', { exact: true })).toBeVisible();

  // AC-055 end to end: the live view is shown, refreshes every second, and is labelled as simulated.
  const live = page.getByRole('region', { name: 'Theo dõi trực tiếp' });
  await expect(live.getByText('DỮ LIỆU MÔ PHỎNG')).toBeVisible();
  await expect(live.getByText(/Không dùng để ra quyết định thực tế về ngựa/)).toBeVisible();
  const elapsed = async () => Number(await live.locator('dd').first().innerText());
  const t1 = await elapsed();
  await expect.poll(elapsed, { timeout: 8000 }).toBeGreaterThan(t1);

  const indicators = page.getByRole('form', { name: 'Chỉ số buổi tập' });
  await indicators.getByLabel('Cự ly (m)').fill('1500');
  await indicators.getByLabel('Thời lượng (phút)').fill('55');
  await indicators.getByLabel('Mặt sân').fill('Cát');
  await indicators.getByLabel('Cường độ').selectOption('MEDIUM');
  await indicators.getByLabel(/^Phong độ/).fill('4');
  await indicators.getByLabel('Nhận xét chuyên môn').fill('Phản xạ tốt');
  await indicators.getByRole('button', { name: 'Kết thúc buổi tập' }).click();
  await expect(page.getByText('Hoàn thành', { exact: true })).toBeVisible();
  await expect(page.getByText('Phản xạ tốt')).toBeVisible();
});

test('AC-103 Groom reports an incident with a photo; Veterinarian opens and closes it', async ({ page }) => {
  await signIn(page, groom.email, groom.password);
  await expect(page.getByRole('link', { name: 'Ngựa', exact: true })).toHaveCount(0);
  // The page opens on the list; a new report is written in a popup (AC-444).
  await page.getByRole('button', { name: 'Báo cáo mới' }).click();
  await pick(page, page.getByRole('dialog').getByRole('form', { name: 'Gửi báo cáo' }), 'Ngựa', horseName);
  await page.getByLabel(/^Mô tả sự cố/).fill('Ngựa bỏ ăn từ sáng');
  const jpeg = Buffer.concat([Buffer.from([0xff, 0xd8, 0xff]), Buffer.alloc(512)]);
  await page.getByLabel(/^Ảnh/).setInputFiles({ name: 'bua-an.jpg', mimeType: 'image/jpeg', buffer: jpeg });
  await page.getByRole('button', { name: 'Gửi báo cáo' }).click();
  const mine = page.getByRole('row').filter({ hasText: 'Ngựa bỏ ăn từ sáng' }).filter({ hasText: horseName });
  await expect(mine.getByText('Mới', { exact: true })).toBeVisible();
  await page.getByRole('button', { name: 'Đăng xuất' }).click();

  await signIn(page, vet.email, vet.password);
  await page.getByRole('link', { name: 'Sự cố' }).click();
  const row = page.getByRole('row').filter({ hasText: 'Ngựa bỏ ăn từ sáng' }).filter({ hasText: horseName });
  await row.getByRole('button', { name: 'Mở' }).click();
  // Opening a report asks for a confirmation first.
  await page.locator('[role=alertdialog],[role=dialog]').getByRole('button', { name: 'Mở', exact: true }).click();
  await expect(row.getByText('Đã tiếp nhận')).toBeVisible();
  await row.getByRole('button', { name: 'Đóng báo cáo' }).click();
  await page.getByLabel('Ghi chú xử lý').fill('Đã khám, theo dõi 24 giờ');
  await page.getByRole('form', { name: 'Đóng báo cáo' }).getByRole('button', { name: 'Đóng báo cáo' }).click();
  await expect(row.getByText('Đã xử lý')).toBeVisible();
  await page.getByRole('button', { name: 'Đăng xuất' }).click();

  await signIn(page, groom.email, groom.password);
  const after = page.getByRole('row').filter({ hasText: 'Ngựa bỏ ăn từ sáng' }).filter({ hasText: horseName });
  await expect(after.getByText('Đã xử lý')).toBeVisible();
});

// Task 5.1: the 3D injury view with software WebGL (TECH-051). Screenshots are kept for a human to judge the model.
test.describe('injury marks on the 3D model', () => {
  // Software WebGL plus five canvas screenshots with waits: 3 to 57 s depending on what else the machine is doing
  // (ui.spec.ts runs beside this file), so the 60 s default is too tight. No assertion changes.
  test.describe.configure({ timeout: 180_000 });
  test('AC-126 AC-127 AC-135 AC-142 Veterinarian marks a region and follows recovery; Head Trainer reads only', async ({ page }) => {
    await signIn(page, vet.email, vet.password);
    await page.getByRole('link', { name: 'Ngựa' }).click();
    await openHorse(page);
    await openTile(page, 'Hồ sơ khám bệnh');
    const exams = page.getByRole('form', { name: 'Ghi nhận lần khám' });
    await exams.getByLabel(/^Chẩn đoán/).fill('Viêm khớp bàn trước trái');
    // The one body-region catalog (BR-110): the same name as on the 3D view below.
    await pick(page, exams, 'Vùng cơ thể', /^Khớp bàn trước trái$/);
    // Once any exam fee type is priced (the demo data has some), the fee type is mandatory (BR-178).
    const feeType = exams.getByLabel(/^Loại phí khám/);
    if (await feeType.count()) await feeType.selectOption({ index: 1 });
    await exams.getByRole('button', { name: 'Ghi nhận lần khám' }).click();
    await expect(page.getByRole('cell', { name: 'Viêm khớp bàn trước trái' })).toBeVisible();
    await expect(page.getByRole('cell', { name: 'Khớp bàn trước trái', exact: true })).toBeVisible();
    await page.keyboard.press('Escape');

    await page.getByRole('link', { name: 'Dấu chấn thương trên mô hình 3D' }).click();
    const canvas = page.locator('canvas');
    await expect(canvas).toBeVisible({ timeout: 30_000 });
    await expect(page.getByText(/không hiển thị được mô hình 3D|Không khởi động được/)).toHaveCount(0);
    await pick(page, page, 'Vùng cơ thể', /^Khớp bàn trước trái$/);
    await page.getByLabel('Hồ sơ khám').selectOption({ index: 1 });
    await page.getByRole('button', { name: 'Đánh dấu chấn thương' }).click();
    const item = page.getByRole('listitem', { name: 'Khớp bàn trước trái' });
    // The list re-renders after the save while software WebGL holds the main thread: wait longer, same assertion.
    await expect(item).toContainText('Mới', { timeout: 20_000 });

    await page.waitForTimeout(2500);
    await canvas.screenshot({ path: 'injury-all-layers.png' });
    const layers = page.getByRole('group', { name: 'Lớp hiển thị' });
    const toggle = async (name: string) => layers.getByRole('button', { name, exact: true }).click();
    await toggle('Cơ');
    await toggle('Xương');
    await expect(layers.getByRole('button', { name: 'Da', exact: true })).toHaveAttribute('aria-pressed', 'true');
    await page.waitForTimeout(800);
    await canvas.screenshot({ path: 'injury-skin.png' });
    await toggle('Da');
    await toggle('Cơ');
    await page.waitForTimeout(800);
    await canvas.screenshot({ path: 'injury-muscle.png' });
    await toggle('Cơ');
    await toggle('Xương');
    await expect(layers.getByRole('button', { name: 'Da', exact: true })).toHaveAttribute('aria-pressed', 'false');
    await page.waitForTimeout(800);
    await canvas.screenshot({ path: 'injury-skeleton.png' });
    await expect(item).toBeVisible();

    await item.getByLabel('Trạng thái mới').selectOption('RECOVERING');
    await item.getByLabel('Ghi chú').fill('Bắt đầu đi bộ nhẹ');
    await item.getByRole('button', { name: 'Cập nhật trạng thái' }).click();
    await expect(item).toContainText('Đang hồi phục');
    await item.getByText('Lịch sử').click();
    await expect(item).toContainText('Bắt đầu đi bộ nhẹ');

    await page.getByRole('button', { name: 'Đăng xuất' }).click();
    await signIn(page, trainer.email, trainer.password);
    await page.getByRole('link', { name: 'Ngựa' }).click();
    await openHorse(page);
    await page.getByRole('link', { name: 'Dấu chấn thương trên mô hình 3D' }).click();
    await expect(page.getByRole('listitem', { name: 'Khớp bàn trước trái' })).toContainText('Đang hồi phục');
    await expect(page.getByRole('button', { name: 'Đánh dấu chấn thương' })).toHaveCount(0);
    await expect(page.getByLabel('Trạng thái mới')).toHaveCount(0);
  });
});

// Task 4.1 of add-trial-race-videos: AC-147 and AC-160 end to end. The fixture is a 2-second VP9 video in an MP4
// container (Playwright's Chromium has no H.264 decoder), so the browser really decodes what the Owner plays.
test('AC-147 AC-160 Head Trainer uploads a trial-run video; the Owner plays it', async ({ page }) => {
  const video = readFileSync(join(__dirname, 'fixtures', 'chay-thu.mp4'));
  const today = clubDate();

  await signIn(page, trainer.email, trainer.password);
  await page.getByRole('link', { name: 'Ngựa' }).click();
  await openHorse(page);
  await openTile(page, 'Giáo án huấn luyện');
  await page.getByRole('link', { name: /Giáo án e2e/ }).click();
  const form = page.getByRole('form', { name: 'Thêm buổi tập' });
  await form.getByLabel('Ngày').fill(today);
  await form.getByLabel('Giờ bắt đầu').fill('07:00');
  await form.getByLabel('Loại buổi tập').selectOption('TRIAL_RUN');
  await chooseResponsible(form, groom.name);
  await form.getByRole('button', { name: 'Thêm buổi tập' }).click();
  // The new session (07:00) sorts after the 05:00 gallop of the same day (BR-120).
  await expect(page.getByRole('link', { name: today })).toHaveCount(2);
  await page.getByRole('link', { name: today }).last().click();

  const panel = page.getByRole('region', { name: 'Video chạy thử' });
  await expect(panel.getByText('Chỉ tải video lên khi buổi chạy thử đã Hoàn thành.')).toBeVisible();
  await page.getByRole('button', { name: 'Bắt đầu' }).click();
  const indicators = page.getByRole('form', { name: 'Chỉ số buổi tập' });
  await indicators.getByLabel('Cự ly (m)').fill('1200');
  await indicators.getByLabel('Thời lượng (phút)').fill('20');
  await indicators.getByLabel('Mặt sân').fill('Cát');
  await indicators.getByLabel('Cường độ').selectOption('HEAVY');
  await indicators.getByLabel(/^Phong độ/).fill('5');
  await indicators.getByLabel('Nhận xét chuyên môn').fill('Chạy thử tốt');
  await indicators.getByRole('button', { name: 'Kết thúc buổi tập' }).click();
  await expect(page.getByText('Hoàn thành', { exact: true })).toBeVisible();

  await panel.getByLabel(/^Tệp video/).setInputFiles({ name: 'chay-thu.mp4', mimeType: 'video/mp4', buffer: video });
  await panel.getByRole('button', { name: 'Tải video lên' }).click();
  await expect(panel.getByLabel('chay-thu.mp4')).toBeVisible();
  await page.getByRole('button', { name: 'Đăng xuất' }).click();

  await signIn(page, owner.email, owner.password);
  await openHorse(page);
  await openTile(page, 'Giáo án huấn luyện');
  await page.getByRole('button', { name: /Xem video \(1 video\)/ }).click();
  const player = page.getByRole('region', { name: 'Video chạy thử' }).getByLabel('chay-thu.mp4');
  await expect(player).toBeVisible();
  // The browser fetched and decoded the file through the authorised endpoint: it knows the 2-second duration.
  await expect.poll(() => player.evaluate((v: HTMLVideoElement) => v.duration), { timeout: 15000 }).toBeGreaterThan(1.5);
  await expect(page.getByRole('button', { name: 'Xóa video' })).toHaveCount(0);
});

// Task 3.5 of add-region-catalog-cancel-and-groom-sessions: AC-180 end to end (BR-114, DEC-040).
test('AC-180 Head Trainer assigns a session to the Groom; the Groom sees it read-only', async ({ page }) => {
  const tomorrow = clubDate(1);

  await signIn(page, trainer.email, trainer.password);
  await page.getByRole('link', { name: 'Ngựa' }).click();
  await openHorse(page);
  await openTile(page, 'Giáo án huấn luyện');
  await page.getByRole('link', { name: /Giáo án e2e/ }).click();
  const form = page.getByRole('form', { name: 'Thêm buổi tập' });
  await form.getByLabel('Ngày').fill(tomorrow);
  await form.getByLabel('Giờ bắt đầu').fill('20:00');
  await form.getByLabel('Loại buổi tập').selectOption('TROT');
  await chooseResponsible(form, groom.name);
  await form.getByRole('button', { name: 'Thêm buổi tập' }).click();
  await expect(page.getByRole('link', { name: tomorrow })).toBeVisible();
  await page.getByRole('button', { name: 'Đăng xuất' }).click();

  await signIn(page, groom.email, groom.password);
  await page.getByRole('link', { name: 'Buổi tập được giao' }).click();
  // The Groom now also holds today's sessions of the earlier scenarios (BR-134): pick tomorrow's row.
  const row = page.getByRole('row').filter({ hasText: horseName }).filter({ hasText: tomorrow });
  await expect(row).toContainText(tomorrow);
  await expect(row).toContainText('Chạy nước kiệu');
  await expect(row).toContainText('Đã lên lịch');
  // Read-only: no session link and no action other than answering the assignment: "Đã nhận" (BR-118) or
  // "Không nhận được" (BR-159).
  await expect(page.getByRole('main').getByRole('button')).toHaveCount(2);
  await expect(row.getByRole('button', { name: 'Đã nhận' })).toBeVisible();
  await expect(row.getByRole('button', { name: 'Không nhận được' })).toBeVisible();
  await expect(page.getByRole('link', { name: tomorrow })).toHaveCount(0);
});

// Task 3.1 of add-responsible-person-handover: AC-185 and AC-186 end to end (BR-115, BR-116, BR-118, DEC-041).
test('AC-185 AC-186 Head Trainer hands the session over; both Grooms are told; the new one confirms', async ({ page }) => {
  const tomorrow = clubDate(1);
  const groom2 = { email: `groom2-${suffix}@racehorse.example`, password: 'groom2-password-1', name: `Groom hai ${suffix}` };
  await signIn(page, admin.email, admin.password);
  await createAccount(page, groom2, 'GROOM');
  await page.getByRole('button', { name: 'Đăng xuất' }).click();

  await signIn(page, trainer.email, trainer.password);
  await page.getByRole('link', { name: 'Ngựa' }).click();
  await openHorse(page);
  await openTile(page, 'Giáo án huấn luyện');
  await page.getByRole('link', { name: /Giáo án e2e/ }).click();
  await page.getByRole('link', { name: tomorrow }).click();
  const change = page.getByRole('form', { name: 'Đổi người phụ trách' });
  await pick(page, change, /Người phụ trách/, groom2.name);
  await change.getByRole('button', { name: 'Đổi người phụ trách' }).click();
  await expect(page.getByText(`Người phụ trách: ${groom2.name}`)).toBeVisible();
  await expect(page.getByText('(Chưa xác nhận)')).toBeVisible();
  await page.getByRole('button', { name: 'Đăng xuất' }).click();

  // The previous Groom is told and no longer sees the session.
  await signIn(page, groom.email, groom.password);
  await page.getByRole('button', { name: /^Thông báo/ }).click();
  await expect(page.getByText(new RegExp(`Bạn không còn phụ trách buổi tập Chạy nước kiệu của ngựa "${horseName}"`))).toBeVisible();
  await page.getByRole('link', { name: 'Buổi tập được giao' }).click();
  // Only the handed-over session (tomorrow) must be gone: the Groom may hold other sessions of the same horse.
  await expect(page.getByRole('row').filter({ hasText: horseName }).filter({ hasText: tomorrow })).toHaveCount(0);
  await page.getByRole('button', { name: 'Đăng xuất' }).click();

  // The new Groom is told and confirms "Đã nhận".
  await signIn(page, groom2.email, groom2.password);
  await page.getByRole('button', { name: /^Thông báo/ }).click();
  await expect(page.getByText(new RegExp(`Bạn được giao phụ trách buổi tập Chạy nước kiệu của ngựa "${horseName}"`))).toBeVisible();
  await page.getByRole('link', { name: 'Buổi tập được giao' }).click();
  const row = page.getByRole('row').filter({ hasText: horseName });
  await expect(row).toContainText('Chưa xác nhận');
  await row.getByRole('button', { name: 'Đã nhận' }).click();
  await expect(row).toContainText('Đã nhận');
  // "Đã nhận" is gone; the Groom may still refuse later (BR-159), so only the decline button remains.
  await expect(row.getByRole('button', { name: 'Đã nhận' })).toHaveCount(0);
  await expect(row.getByRole('button')).toHaveCount(1);
  await page.getByRole('button', { name: 'Đăng xuất' }).click();

  // The Head Trainer sees "Đã nhận" next to the session.
  await signIn(page, trainer.email, trainer.password);
  await page.getByRole('link', { name: 'Ngựa' }).click();
  await openHorse(page);
  await openTile(page, 'Giáo án huấn luyện');
  await page.getByRole('link', { name: /Giáo án e2e/ }).click();
  const planRow = page.getByRole('row').filter({ has: page.getByRole('link', { name: tomorrow }) });
  await expect(planRow).toContainText(`${groom2.name} (Đã nhận)`);
});

// Task 5.1 of add-session-start-time: AC-197 end to end (BR-121, DEC-045).
test('AC-197 a session overlapping another of the same horse is refused, naming it', async ({ page }) => {
  const tomorrow = clubDate(1);

  await signIn(page, trainer.email, trainer.password);
  await page.getByRole('link', { name: 'Ngựa' }).click();
  await openHorse(page);
  await openTile(page, 'Giáo án huấn luyện');
  await page.getByRole('link', { name: /Giáo án e2e/ }).click();
  await expect(page.getByRole('link', { name: tomorrow })).toHaveCount(1);
  const form = page.getByRole('form', { name: 'Thêm buổi tập' });
  await form.getByLabel('Ngày').fill(tomorrow);
  await form.getByLabel('Giờ bắt đầu').fill('20:30');
  await form.getByLabel('Loại buổi tập').selectOption('GALLOP');
  await chooseResponsible(form, groom.name);
  await form.getByRole('button', { name: 'Thêm buổi tập' }).click();
  await expect(page.getByRole('alert')).toContainText('Trùng giờ với buổi tập Chạy nước kiệu');
  await expect(page.getByRole('alert')).toContainText('lúc 20:00');
  await expect(page.getByRole('link', { name: tomorrow })).toHaveCount(1);
});

// Task 5.1 of add-approved-ration: AC-245, AC-255 and AC-257 end to end (BR-137, BR-142, DEC-054).
test('AC-245 AC-255 AC-257 the Veterinarian composes a ration; the Groom and the Owner read it', async ({ page }) => {
  const oats = `Yến mạch ${suffix}`;

  // The Club Manager adds a feed item to the catalog.
  await signIn(page, admin.email, admin.password);
  await page.goto('/supplies');
  await page.getByRole('button', { name: 'Thêm vật tư' }).click();
  // The page filter and the form both carry the label "Loại": the form lives in the dialog.
  const supplyForm = page.getByRole('dialog');
  await supplyForm.getByLabel('Tên vật tư', { exact: true }).fill(oats);
  await supplyForm.getByLabel('Loại', { exact: true }).selectOption({ label: 'Thức ăn' });
  await supplyForm.getByLabel('Đơn vị', { exact: true }).selectOption('kg');
  await supplyForm.getByLabel('Giá (VND)', { exact: true }).fill('14000');
  await supplyForm.getByLabel('Tồn đầu', { exact: true }).fill('50');
  await supplyForm.getByRole('button', { name: 'Lưu' }).click();
  // The catalog is paged and grows with every run: look the new item up by name instead of hoping for page one.
  await page.getByRole('searchbox').first().fill(oats);
  await expect(page.getByRole('cell', { name: oats, exact: true })).toBeVisible();
  await page.getByRole('button', { name: 'Đăng xuất' }).click();

  // The Veterinarian composes the ration.
  await signIn(page, vet.email, vet.password);
  await page.getByRole('link', { name: 'Ngựa' }).click();
  await openHorse(page);
  await page.getByRole('link', { name: 'Khẩu phần ăn' }).click();
  const form = page.getByRole('form', { name: 'Soạn khẩu phần' });
  await form.getByLabel('Tên bữa').fill('Bữa sáng');
  await form.getByLabel('Giờ ăn').fill('06:00');
  await form.getByLabel('Loại 1.1').selectOption('GRAIN');
  await pick(page, form, 'Vật tư 1.1', oats);
  await form.getByLabel('Số lượng 1.1').fill('2.5');
  await form.getByRole('button', { name: 'Lưu khẩu phần' }).click();
  const current = page.getByRole('list', { name: 'Khẩu phần hiện hành' });
  await expect(current).toContainText('Bữa sáng');
  await expect(current).toContainText(oats);
  await page.getByRole('button', { name: 'Đăng xuất' }).click();

  // The Groom reads it read-only, beside the chore "Cho ăn".
  await signIn(page, groom.email, groom.password);
  await page.getByRole('link', { name: 'Việc hằng ngày' }).click();
  await page.getByPlaceholder(/^Tìm theo tên ngựa/).fill(horseName);
  await expect(page.getByText(new RegExp(`06:00 Bữa sáng: ${oats} 2.5 kg`))).toBeVisible();
  await page.getByRole('button', { name: 'Đăng xuất' }).click();

  // The Owner reads the current ration of the own horse, without an editor.
  await signIn(page, owner.email, owner.password);
  await page.getByRole('link', { name: 'Ngựa' }).click();
  await openHorse(page);
  await page.getByRole('link', { name: 'Khẩu phần ăn' }).click();
  await expect(page.getByRole('list', { name: 'Khẩu phần hiện hành' })).toContainText(oats);
  await expect(page.getByRole('form', { name: 'Soạn khẩu phần' })).toHaveCount(0);
});

// BR-191: an ended session returns the person to sign-in, and sign-in lands on a page the role may open.
test('AC-279 AC-280 AC-282 an ended session returns to sign-in; sign-in lands on a page the role may open', async ({ page }) => {
  const expired = 'Phiên đăng nhập đã hết hạn, vui lòng đăng nhập lại.';
  const form = async (email: string, password: string) => {
    // exact: the accounts table has a sort button labelled "Sắp xếp theo Email" that can still be on screen.
    await page.getByLabel('Email', { exact: true }).fill(email);
    await page.getByLabel('Mật khẩu', { exact: true }).fill(password);
    await page.getByRole('button', { name: 'Đăng nhập' }).click();
  };

  // AC-279 and AC-280: the session cookie is lost while the Club Manager reads the audit log.
  await signIn(page, admin.email, admin.password);
  await page.getByRole('link', { name: 'Tài khoản' }).click();
  await page.context().clearCookies();
  // Either this click or the page's own background refresh is the first call refused; both end the session.
  await page.getByRole('link', { name: 'Nhật ký thao tác' }).click({ timeout: 3000 }).catch(() => undefined);
  await expect(page.getByText(expired)).toBeVisible();
  await expect(page.getByRole('button', { name: 'Đăng xuất' })).toHaveCount(0);
  const left = new URL(page.url()).pathname;
  await form(admin.email, admin.password);
  await page.locator('aside').first().waitFor({ state: 'attached', timeout: 15_000 });
  await expect(page).toHaveURL(new RegExp(`${left}$`));
  await expect(page.getByText(expired)).toHaveCount(0);

  // AC-280: the Owner signs in on the screen the Club Manager left (no reload): the home page, no refusal.
  await page.getByRole('link', { name: 'Tài khoản' }).click();
  await page.getByRole('button', { name: 'Đăng xuất' }).click();
  await form(owner.email, owner.password);
  await page.locator('aside').first().waitFor({ state: 'attached', timeout: 15_000 });
  await expect(page).toHaveURL(/\/horses$/);
  await expect(page.getByText('Bạn không có quyền thực hiện thao tác này.')).toHaveCount(0);

  // AC-282: with no session an old link shows no data, only sign-in.
  await page.context().clearCookies();
  await page.goto('/horses');
  await expect(page.getByLabel('Email', { exact: true })).toBeVisible();
  await expect(page.getByRole('link', { name: horseName })).toHaveCount(0);
});

// BR-178, BR-179, BR-182: the Club Manager prices an examination, the Veterinarian picks it without seeing an amount,
// the Owner reads the charge and the Head Trainer has no cost page. The fee type is deactivated again at the end, so a
// priced type does not make the fee mandatory for the examinations of other tests.
test('AC-352 AC-353 AC-360 the Club Manager prices an examination; the Veterinarian never sees the amount; the Owner reads the charge', async ({ page }) => {
  const typeName = `Khám e2e ${suffix}`;

  await signIn(page, admin.email, admin.password);
  await page.getByRole('link', { name: 'Chi phí' }).click();
  await page.getByRole('button', { name: 'Bảng giá' }).click();
  await page.getByLabel('Tên loại phí').fill(typeName);
  await page.getByRole('button', { name: 'Thêm loại phí khám' }).click();
  const row = page.getByRole('row', { name: new RegExp(typeName) });
  await expect(row).toBeVisible();
  await row.getByRole('button', { name: 'Đặt giá mới' }).click();
  const priceForm = page.getByRole('form', { name: 'Đặt giá mới' });
  await priceForm.getByLabel(/^Số tiền/).fill('200000');
  await priceForm.getByLabel(/^Áp dụng từ/).fill(clubDate());
  await priceForm.getByRole('button', { name: 'Lưu giá' }).click();
  await expect(row).toContainText('200.000');
  await page.getByRole('button', { name: 'Đăng xuất' }).click();

  // The Veterinarian chooses the type by name; no amount is on the page.
  await signIn(page, vet.email, vet.password);
  await page.getByRole('link', { name: 'Ngựa' }).click();
  await openHorse(page);
  await openTile(page, 'Hồ sơ khám bệnh');
  const select = page.getByLabel(/^Loại phí khám/);
  await expect(select).toBeVisible();
  await expect(page.locator('body')).not.toContainText('200.000');
  await page.getByLabel(/^Chẩn đoán/).fill('Khám định kỳ e2e');
  await select.selectOption({ label: typeName });
  await page.getByRole('button', { name: 'Ghi nhận lần khám' }).click();
  await expect(page.getByRole('cell', { name: 'Khám định kỳ e2e' })).toBeVisible();
  await expect(page.locator('body')).not.toContainText('200.000');
  await page.keyboard.press('Escape');
  await page.getByRole('button', { name: 'Đăng xuất' }).click();

  // The Owner reads the charge on the cost page; the Head Trainer has no such page.
  await signIn(page, owner.email, owner.password);
  await page.getByRole('link', { name: 'Chi phí' }).click();
  await expect(page.getByRole('heading', { name: horseName })).toBeVisible();
  await expect(page.getByTestId('statement')).toContainText('Phí khám và điều trị');
  await expect(page.getByTestId('statement')).toContainText('200.000');
  await page.getByRole('button', { name: 'Đăng xuất' }).click();
  await signIn(page, trainer.email, trainer.password);
  await expect(page.getByRole('link', { name: 'Chi phí' })).toHaveCount(0);
  await page.getByRole('button', { name: 'Đăng xuất' }).click();

  // The type is switched off again so later examinations need no fee type.
  await signIn(page, admin.email, admin.password);
  await page.getByRole('link', { name: 'Chi phí' }).click();
  await page.getByRole('button', { name: 'Bảng giá' }).click();
  await page.getByRole('row', { name: new RegExp(typeName) }).getByRole('button', { name: 'Ngừng dùng' }).click();
  await expect(page.getByRole('row', { name: new RegExp(typeName) })).toContainText('Đã ngừng');
});


// add-supply-unit-catalog: AC-388, AC-389, AC-392 end to end. The Club Manager adds a unit, uses it in an item,
// renames it (the item shows the new name) and deactivates it (it is no longer offered for a new item).
test('AC-388 AC-389 AC-392 the Club Manager adds, uses, renames and deactivates a unit', async ({ page }) => {
  // Digits are allowed in a unit name; the whole suffix keeps the name unique across runs (a shortened one collided).
  const letters = suffix;
  const unit = `thùng ${letters}`;
  const renamed = `kiện ${letters}`;
  const item = `Vật tư đơn vị ${suffix}`;
  await signIn(page, admin.email, admin.password);
  await page.goto('/supplies');

  await page.getByRole('button', { name: 'Thêm đơn vị' }).click();
  await page.getByRole('dialog').getByLabel('Tên đơn vị', { exact: true }).fill(`  ${unit}  `);
  await page.getByRole('dialog').getByRole('button', { name: 'Lưu' }).click();
  await expect(page.getByRole('cell', { name: unit, exact: true })).toBeVisible();

  await page.getByRole('button', { name: 'Thêm vật tư' }).click();
  const form = page.getByRole('dialog');
  await form.getByLabel('Tên vật tư', { exact: true }).fill(item);
  await form.getByLabel('Loại', { exact: true }).selectOption({ label: 'Dụng cụ' });
  await form.getByLabel('Đơn vị', { exact: true }).selectOption({ label: unit });
  await form.getByLabel('Giá (VND)', { exact: true }).fill('1000');
  await form.getByRole('button', { name: 'Lưu' }).click();
  // The catalogue is paged and grows with every run: search for this run's item.
  await page.getByPlaceholder(/^Tìm/).first().fill(item);
  await expect(page.getByRole('row', { name: new RegExp(item) })).toContainText(unit);

  await page.getByRole('button', { name: `Đổi tên đơn vị ${unit}` }).click();
  await page.getByRole('dialog').getByLabel('Tên đơn vị', { exact: true }).fill(renamed);
  await page.getByRole('dialog').getByRole('button', { name: 'Lưu' }).click();
  await expect(page.getByRole('row', { name: new RegExp(item) })).toContainText(renamed);

  await page.getByRole('button', { name: `Ngừng dùng đơn vị ${renamed}` }).click();
  await expect(page.getByRole('row', { name: new RegExp(renamed) }).first()).toContainText('Ngừng dùng');
  await page.getByRole('button', { name: 'Thêm vật tư' }).click();
  await expect(page.getByRole('dialog').getByLabel('Đơn vị', { exact: true }).locator('option', { hasText: renamed })).toHaveCount(0);
});

// add-timetables-and-invoice-export: AC-400, AC-401, AC-402 read through the screen with the seeded demo accounts or the
// accounts of the run. The pages are read-only, so nothing is created here; the time-ordered content is covered by the
// service tests (TimetableTest) and the Vitest page tests.
test('AC-400 AC-401 AC-402 the Groom and the Head Trainer open their timetable by day and by week', async ({ page }) => {
  await ensurePeople();
  for (const who of [people.GROOM, people.HEAD_TRAINER]) {
    await signIn(page, who.email, who.password);
    await page.locator('aside a[href="/timetable"]').first().click();
    await expect(page.getByRole('heading', { level: 1, name: 'Thời gian biểu' })).toBeVisible();
    await expect(page.locator('main section h2, main [role="alert"], main p').first()).toBeVisible();
    await page.getByRole('button', { name: 'Tuần' }).click();
    await expect(page.locator('main section h2')).toHaveCount(7);
    expect(await page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth)).toBe(true);
    await page.getByRole('button', { name: 'Đăng xuất' }).first().click();
  }
});

test('AC-400 AC-401 AC-402 a Groom with barn hours and a session sees both today and in the week; the Head Trainer sees the horse sessions', async ({ page }) => {
  // The Club Manager gives this run's Groom barn hours on every weekday (midday, clear of the sessions planned above).
  await signIn(page, admin.email, admin.password);
  const csrf = (await page.context().cookies()).find((c) => c.name === 'XSRF-TOKEN')?.value ?? '';
  const headers = { 'X-XSRF-TOKEN': decodeURIComponent(csrf) };
  const found = await page.request.get(`/api/accounts?q=${encodeURIComponent(groom.email)}`);
  const id = ((await found.json()).items as { id: number; email: string }[]).find((a) => a.email === groom.email)?.id;
  expect(id, 'the run Groom exists').toBeTruthy();
  const ranges = [1, 2, 3, 4, 5, 6, 7].map((weekday) => ({ weekday, start: '12:00', end: '13:00' }));
  const saved = await page.request.put(`/api/accounts/${id}/schedule`, { data: { ranges }, headers });
  expect(saved.ok(), await saved.text()).toBe(true);
  await page.getByRole('button', { name: 'Đăng xuất' }).first().click();

  await signIn(page, groom.email, groom.password);
  await page.locator('aside a[href="/timetable"]').first().click();
  const today = page.locator('main section').filter({ hasText: '(Hôm nay)' });
  await expect(today).toContainText('Giờ ở chuồng: 12:00–13:00');
  await expect(today.getByRole('link').first()).toBeVisible();
  await page.getByRole('button', { name: 'Tuần' }).click();
  await expect(page.getByText('Giờ ở chuồng: 12:00–13:00')).toHaveCount(7);
  expect(await page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth)).toBe(true);
  await page.getByRole('button', { name: 'Đăng xuất' }).first().click();

  await signIn(page, trainer.email, trainer.password);
  await page.locator('aside a[href="/timetable"]').first().click();
  await expect(page.locator('main section').filter({ hasText: '(Hôm nay)' }).getByRole('link').first()).toBeVisible();
});

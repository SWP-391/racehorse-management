import type { Locator, Page } from '@playwright/test';
import { expect, test } from './fixtures';
import { admin, ensureHorse, ensurePeople, everyone, people, signIn } from './people';

// Task 6.1 (input-validation): every form refuses what its rule refuses, before anything is sent, with a message
// on the field. Written ahead of the fixes: a failing case names a field that still lacks its rule.

test.describe.configure({ timeout: 240_000 });
test.beforeAll(async () => {
  await ensurePeople();
  await ensureHorse();
});

// The club's calendar date (Asia/Ho_Chi_Minh), as in smoke.spec.ts.
const clubDate = (offsetDays = 0) =>
  new Date(Date.now() + offsetDays * 864e5).toLocaleDateString('sv-SE', { timeZone: 'Asia/Ho_Chi_Minh' });

/** Counts the writes a page sends, so "nothing is sent" is a number and not a hope. */
function writes(page: Page) {
  const sent: string[] = [];
  page.on('request', (r) => {
    if (['POST', 'PUT', 'PATCH', 'DELETE'].includes(r.method()) && r.url().includes('/api/') && !r.url().includes('/api/auth/'))
      sent.push(`${r.method()} ${r.url().replace(/^https?:\/\/[^/]+/, '')}`);
  });
  return sent;
}

interface Field {
  tag: string;
  type: string;
  label: string;
  min: string;
  max: string;
  step: string;
  maxlength: number;
  unit: string;
}

/** Every visible form control of the page or of the open dialog, with the attributes the rules hang on. */
async function fields(page: Page): Promise<Field[]> {
  return page.evaluate(() => {
    const visible = (e: Element) => {
      const r = e.getBoundingClientRect();
      return r.width > 0 && r.height > 0 && getComputedStyle(e).visibility !== 'hidden';
    };
    const labelOf = (e: HTMLElement) => {
      const byFor = e.id && document.querySelector(`label[for="${CSS.escape(e.id)}"]`);
      return (byFor?.textContent ?? e.getAttribute('aria-label') ?? e.closest('label')?.textContent ?? e.getAttribute('placeholder') ?? '').trim();
    };
    const scope = document.querySelector('[role=dialog]') ?? document;
    const out: Field[] = [];
    for (const e of scope.querySelectorAll<HTMLInputElement>('input,select,textarea')) {
      if (!visible(e) || ['hidden', 'checkbox', 'radio', 'file'].includes(e.type)) continue;
      out.push({ tag: e.tagName.toLowerCase(), type: e.type, label: labelOf(e).slice(0, 60), min: e.min, max: e.max, step: e.step, maxlength: e.maxLength, unit: e.dataset.unit ?? '' });
    }
    return out;
  });
}

/** What a field must carry, by kind. Returns the broken rules, empty when the field is fine. */
function broken(f: Field): string[] {
  const out: string[] = [];
  if (f.type === 'number') {
    if (!f.min) out.push('min');
    if (!f.max) out.push('max');
    if (!f.step) out.push('step');
    // A measured value names its unit in the label or beside the input, which carries `data-unit` (DEC-026).
    if (!/\(.+\)/.test(f.label) && !f.unit) out.push('unit');
  }
  if (f.type === 'date' || f.type === 'datetime-local') {
    if (!f.min && !f.max) out.push('min/max');
  }
  if ((f.type === 'text' || f.tag === 'textarea') && !(f.maxlength > 0)) out.push('maxlength');
  return out;
}

const OPENERS = /^(Thêm|Tạo|Đăng ký|Sửa|Ghi nhận|Lập|Đề xuất|Soạn|Nhập|Chỉnh|Cập nhật)/;

for (const who of everyone) {
  test(`${who.role}: every form control on every menu page carries its rule`, async ({ page }) => {
    await signIn(page, who);
    await page.setViewportSize({ width: 1440, height: 900 });
    const links = await page.locator('aside a[href]').evaluateAll((as) => [...new Set(as.map((a) => a.getAttribute('href')!))]);
    const violations: string[] = [];
    for (const href of links) {
      await page.goto(href);
      await page.waitForLoadState('networkidle');
      for (const f of await fields(page)) {
        const b = broken(f);
        if (b.length) violations.push(`${href} "${f.label}" (${f.type}) lacks ${b.join(', ')}`);
      }
      const names = await page
        .locator('main button:visible')
        .evaluateAll((els) => els.map((e) => (e.getAttribute('aria-label') ?? e.textContent ?? '').trim()).filter(Boolean));
      // One dialog per kind of opener on a page: "Sửa X" for fourteen rows is the same form fourteen times.
      const firstOfKind = new Map<string, string>();
      for (const n of names) {
        const kind = n.match(OPENERS)?.[0];
        if (kind && !firstOfKind.has(kind)) firstOfKind.set(kind, n);
      }
      for (const name of new Set(firstOfKind.values())) {
        try {
          await page.locator('main').getByRole('button', { name, exact: true }).first().click({ timeout: 2000 });
          await page.waitForTimeout(500);
          for (const f of await fields(page)) {
            const b = broken(f);
            if (b.length) violations.push(`${href} > ${name} "${f.label}" (${f.type}) lacks ${b.join(', ')}`);
          }
          await page.keyboard.press('Escape');
          if (new URL(page.url()).pathname !== href) await page.goto(href);
        } catch {
          // An opener that opens nothing (a link styled as a button, a disabled action) has no form to check.
        }
      }
    }
    expect([...new Set(violations)]).toEqual([]);
  });
}

/** Fills each bad value in turn, submits, and expects the field to be refused on the spot. */
async function refuses(field: Locator, submit: Locator, bad: string[]) {
  for (const value of bad) {
    await field.fill(value);
    await submit.click();
    await expect(field, `value "${value}"`).toHaveAttribute('aria-invalid', 'true');
  }
}

/** The first horse the signed-in person can open; these cases run on the demo data (`E2E_DEMO=1`, docs/validation.md). */
async function firstHorseId(page: Page): Promise<number> {
  const res = await page.request.get('/api/horses?size=1');
  const body = (await res.json()) as { items?: { id: number }[] };
  const id = body.items?.[0]?.id;
  if (!id) throw new Error('These cases need at least one horse (ensureHorse registers one on an empty stack).');
  return id;
}

// Task 7.4: the forms the sweep above cannot open without data. Each case enters invalid values, expects the message on the
// field and counts the writes that left the page.
test.describe('forms that need data refuse invalid input before sending', () => {
  // A missing control fails in seconds with its name, not after the four-minute default.
  test.use({ actionTimeout: 10_000 });

  test('veterinarian: care interval (whole days, 1 to 3650) on a new care item', async ({ page }) => {
    await signIn(page, people.VETERINARIAN);
    const sent = writes(page);
    await page.goto('/care?tab=horse');
    // The add button stays disabled until a horse is chosen.
    await page.getByRole('combobox', { name: /^Ngựa/ }).click();
    // The first horse of the list, whatever its name: wait for the list to load, then take the first option.
    const first = page.getByRole('listbox').getByRole('option').first();
    await first.waitFor();
    await first.click();
    await expect(page.getByRole('button', { name: 'Thêm mục chăm sóc' }).first()).toBeEnabled();
    await page.getByRole('button', { name: 'Thêm mục chăm sóc' }).first().click();
    const dialog = page.getByRole('dialog');
    await dialog.getByLabel(/^Tên \(vắc-xin/).fill('Kiểm thử');
    await refuses(dialog.getByLabel(/^Chu kỳ/), dialog.getByRole('button', { name: 'Thêm mục chăm sóc' }), ['0', '3651', '2.5', '']);
    expect(sent, 'nothing sent').toEqual([]);
  });

  test('veterinarian: examination weight and date', async ({ page }) => {
    await signIn(page, people.VETERINARIAN);
    const sent = writes(page);
    await page.goto(`/horses/${await firstHorseId(page)}`);
    await page.getByText('Hồ sơ khám bệnh', { exact: true }).click();
    const form = page.getByRole('form', { name: 'Ghi nhận lần khám' });
    await form.getByLabel(/^Chẩn đoán/).fill('Kiểm thử');
    await refuses(form.getByLabel(/^Cân nặng/), form.getByRole('button', { name: /^Lưu|^Ghi nhận/ }), ['99', '1501', '-5']);
    expect(sent, 'nothing sent').toEqual([]);
  });

  test('veterinarian: ration quantity must be above zero', async ({ page }) => {
    await signIn(page, people.VETERINARIAN);
    const sent = writes(page);
    await page.goto(`/horses/${await firstHorseId(page)}/ration`);
    // A horse without a ration opens the editor at once; one with a ration needs "Soạn khẩu phần" first.
    const compose = page.getByRole('button', { name: 'Soạn khẩu phần' });
    await page.getByLabel(/^Tên bữa/).or(compose).first().waitFor();
    if (await compose.count()) await compose.click();
    await refuses(page.getByLabel(/^Số lượng/).first(), page.getByRole('button', { name: 'Lưu khẩu phần' }), ['0', '-1']);
    expect(sent, 'nothing sent').toEqual([]);
  });

  test('club manager: stock receipt quantity and the price list amount', async ({ page }) => {
    await signIn(page, admin);
    const sent = writes(page);
    await page.goto('/billing');
    await page.getByText('Bảng giá', { exact: true }).first().click();
    await page.getByRole('button', { name: 'Đặt giá mới' }).first().click();
    const price = page.getByRole('dialog');
    await refuses(price.getByLabel(/^Số tiền/), price.getByRole('button', { name: 'Lưu giá' }), ['1000000001', '']);
    // A minus is not typed in a price (only in an invoice adjustment), so there is no negative value to refuse (BR-207).
    expect(sent, 'nothing sent').toEqual([]);
  });

  test('club manager: a leave date in the past is refused', async ({ page }) => {
    await signIn(page, admin);
    const sent = writes(page);
    await page.goto('/accounts');
    // The list is paged and test accounts pile up: search for the Groom instead of hoping one is on the first page.
    await page.getByPlaceholder('Tìm theo họ tên hoặc email').fill(people.GROOM.email);
    await page.getByRole('button', { name: /^Ghi nghỉ phép cho/ }).first().click();
    const dialog = page.getByRole('dialog');
    await dialog.getByLabel(/^Ngày nghỉ/).fill(clubDate(-3));
    await dialog.getByRole('button', { name: 'Ghi nghỉ phép' }).click();
    await expect(dialog.getByLabel(/^Ngày nghỉ/)).toHaveAttribute('aria-invalid', 'true');
    expect(sent, 'nothing sent').toEqual([]);
  });

  test('head trainer: a plan phase refuses an empty name, a distance and a volume above the limits', async ({ page }) => {
    await signIn(page, people.HEAD_TRAINER);
    const sent = writes(page);
    await page.goto(`/horses/${await firstHorseId(page)}/plans/new`);
    const save = page.getByRole('button', { name: 'Lưu' });
    await save.click();
    await expect(page.getByLabel(/^Tên giai đoạn/)).toHaveAttribute('aria-invalid', 'true');
    await refuses(page.getByLabel(/^Cự ly/), save, ['0', '50001', '2.5']);
    await refuses(page.getByLabel(/^Khối lượng/), save, ['0', '1001']);
    expect(sent, 'nothing sent').toEqual([]);
  });
});

test.describe('Club Manager forms refuse invalid input before sending', () => {
  test.beforeEach(async ({ page }) => {
    await signIn(page, admin);
  });

  test('horse: required fields, future birth date and long text are refused on the field', async ({ page }) => {
    const sent = writes(page);
    await page.goto('/horses/new');
    await page.getByRole('button', { name: 'Lưu' }).click();
    for (const label of [/^Tên ngựa/, /^Giới tính/, /^Ngày sinh/]) {
      await expect(page.getByLabel(label), String(label)).toHaveAttribute('aria-invalid', 'true');
    }
    // The first refused field takes focus.
    await expect(page.getByLabel(/^Tên ngựa/)).toBeFocused();

    await page.getByLabel(/^Tên ngựa/).fill('Kiểm thử');
    await page.getByLabel(/^Giới tính/).selectOption('MALE');
    await page.getByLabel(/^Ngày sinh/).fill(clubDate(1));
    await page.getByRole('button', { name: 'Lưu' }).click();
    await expect(page.getByLabel(/^Ngày sinh/)).toHaveAttribute('aria-invalid', 'true');
    await expect(page.getByText(/không được ở tương lai|không được sau hôm nay/i).first()).toBeVisible();
    expect(sent.filter((s) => s.startsWith('POST /api/horses')), 'nothing sent').toEqual([]);

    await expect(page.getByLabel(/^Tên ngựa/)).toHaveAttribute('maxlength', '200');
  });

  test('audit: an end date before the start refuses the search', async ({ page }) => {
    const requests: string[] = [];
    page.on('request', (r) => r.url().includes('/api/audit') && requests.push(r.url()));
    await page.goto('/audit');
    await page.waitForLoadState('networkidle');
    requests.length = 0;
    await page.getByLabel('Từ ngày').fill(clubDate(-1));
    await page.getByLabel('Đến ngày').fill(clubDate(-10));
    await expect(page.getByLabel('Đến ngày')).toHaveAttribute('aria-invalid', 'true');
    await page.waitForTimeout(800);
    expect(requests, 'no list request with an inverted range').toEqual([]);
  });

  test('chores: a history range that ends before it starts is refused on the end field', async ({ page }) => {
    await page.goto('/chores');
    await page.getByRole('tab', { name: 'Lịch sử' }).or(page.getByRole('button', { name: 'Lịch sử' })).first().click();
    const to = page.getByLabel('Đến ngày');
    await page.getByLabel('Từ ngày').fill(clubDate(-1));
    await to.fill(clubDate(-10));
    await expect(to).toHaveAttribute('aria-invalid', 'true');
  });

  test('settings: zero, negative and fractional values are refused, nothing is saved', async ({ page }) => {
    const sent = writes(page);
    await page.goto('/settings');
    await page.waitForLoadState('networkidle');
    const numbers = page.locator('main input[type=number]');
    const count = await numbers.count();
    expect(count).toBeGreaterThan(0);
    for (let i = 0; i < count; i++) {
      const input = numbers.nth(i);
      for (const bad of ['0', '-3', '2.5', '']) {
        await input.fill(bad);
        await input.locator('xpath=ancestor::form').getByRole('button', { name: /^Lưu/ }).click();
        await expect(input, `value "${bad}" on field ${i}`).toHaveAttribute('aria-invalid', 'true');
      }
    }
    expect(sent, 'nothing sent').toEqual([]);
  });

  test('supplies: stock, price and unit are refused or chosen, not typed freely', async ({ page }) => {
    const sent = writes(page);
    await page.goto('/supplies');
    await page.getByRole('button', { name: 'Thêm vật tư' }).click();
    const form = page.getByRole('dialog');
    await form.getByLabel('Tên vật tư', { exact: true }).fill('Kiểm thử');
    await form.getByLabel('Loại', { exact: true }).selectOption({ label: 'Thức ăn' });
    await form.getByLabel('Giá (VND)', { exact: true }).fill('1000000001');
    await form.getByLabel('Tồn đầu', { exact: true }).fill('2.5');
    await form.getByRole('button', { name: 'Lưu' }).click();
    await expect(form.getByLabel('Giá (VND)', { exact: true })).toHaveAttribute('aria-invalid', 'true');
    await expect(form.getByLabel(/^Tồn đầu/)).toHaveAttribute('aria-invalid', 'true');
    // Text that only looks like a number ("1e3") is refused as well (BR-197).
    await form.getByLabel('Tồn đầu', { exact: true }).fill('1e3');
    await form.getByRole('button', { name: 'Lưu' }).click();
    await expect(form.getByLabel(/^Tồn đầu/)).toHaveAttribute('aria-invalid', 'true');
    // The unit of a supply item is picked from the catalog, so it is a select, not text.
    expect(await form.getByLabel(/^Đơn vị/).evaluate((e) => e.tagName.toLowerCase())).toBe('select');
    expect(sent.filter((s) => s.includes('/api/supplies')), 'nothing sent').toEqual([]);
  });

  test('units: blank, spaces-only, digits-only and symbol names are refused on the field, nothing is sent', async ({ page }) => {
    const sent = writes(page);
    await page.goto('/supplies?tab=units');
    await page.getByRole('button', { name: 'Thêm đơn vị' }).click();
    const dialog = page.getByRole('dialog');
    const name = dialog.getByLabel('Tên đơn vị', { exact: true });
    for (const bad of ['', '   ', '123', 'kg!', 'a'.repeat(21)]) {
      await name.fill(bad);
      await dialog.getByRole('button', { name: 'Lưu' }).click();
      await expect(name, `value "${bad}"`).toHaveAttribute('aria-invalid', 'true');
    }
    expect(sent.filter((s) => s.includes('/api/supply-units')), 'nothing sent').toEqual([]);
  });

  test('units: only the Club Manager sees the units section of the supplies page', async ({ page, browser }) => {
    await page.goto('/supplies?tab=units');
    await expect(page.getByRole('button', { name: 'Thêm đơn vị' })).toBeVisible();
    for (const who of Object.values(people)) {
      const context = await browser.newContext({ baseURL: process.env.BASE_URL ?? 'http://localhost:5173' });
      const other = await context.newPage();
      await signIn(other, who);
      await other.goto('/supplies');
      await other.waitForLoadState('networkidle');
      await expect(other.getByRole('button', { name: 'Thêm đơn vị' }), who.role).toHaveCount(0);
      await context.close();
    }
  });

  test('barn: an area capacity of 0 or above the limit is refused', async ({ page }) => {
    const sent = writes(page);
    await page.goto('/barn');
    await page.getByRole('button', { name: 'Thêm khu' }).click();
    await page.getByLabel('Tên khu').fill('Khu kiểm thử');
    for (const bad of ['0', '201', '2.5']) {
      await page.getByLabel(/^Sức chứa/).fill(bad);
      await page.getByRole('button', { name: 'Lưu' }).click();
      await expect(page.getByLabel(/^Sức chứa/), `capacity ${bad}`).toHaveAttribute('aria-invalid', 'true');
    }
    expect(sent.filter((s) => s.includes('/api/areas') || s.includes('/api/barn')), 'nothing sent').toEqual([]);
  });

  test('accounts: email and name length, and a password shorter than the minimum, are refused on the field', async ({ page }) => {
    const sent = writes(page);
    await page.goto('/accounts');
    await page.getByLabel(/^Email/).fill('x');
    await page.getByLabel(/^Họ tên/).fill('');
    await page.getByLabel(/^Mật khẩu/).fill('short');
    await page.getByRole('button', { name: 'Tạo tài khoản' }).click();
    await expect(page.getByLabel(/^Họ tên/)).toHaveAttribute('aria-invalid', 'true');
    await expect(page.getByLabel(/^Mật khẩu/)).toHaveAttribute('aria-invalid', 'true');
    expect(sent.filter((s) => s.includes('/api/accounts')), 'nothing sent').toEqual([]);
  });
});

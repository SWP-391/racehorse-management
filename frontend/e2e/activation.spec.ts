import { expect, test } from './fixtures';
import { admin, ensurePeople, signIn } from './people';

// REQ-043, BR-235..BR-238, AC-505, AC-507, AC-510, AC-512, AC-518, AC-520: the Club Manager invites a person without a
// password, the account waits as "Chờ kích hoạt", cannot sign in, and a wrong code is refused. The throwaway stack of
// scripts/e2e.sh sends no mail, so the activation itself (right code) is covered by the backend tests, which read the
// code from the recording mailer. Every run uses its own email so it can be repeated on the same stack.

test.describe.configure({ timeout: 240_000 });
test.beforeAll(async () => {
  await ensurePeople();
});

const STAMP = Date.now().toString(36);
const EMAIL = `rt-moi-${STAMP}@racehorse.example`;

test('an invited account waits for activation, cannot sign in, and a wrong code is refused', async ({ page, browser }) => {
  await signIn(page, admin);
  await page.getByRole('navigation', { name: 'main' }).getByRole('link', { name: 'Tài khoản' }).click();

  // AC-520: the email is asked twice
  const form = page.getByRole('form', { name: 'Tạo tài khoản' });
  await form.getByLabel('Email', { exact: true }).fill(EMAIL);
  await form.getByLabel('Nhập lại email').fill(`x${EMAIL}`);
  await form.getByLabel('Họ tên').fill(`Người mới ${STAMP}`);
  await form.getByLabel('Vai trò').selectOption('VETERINARIAN');
  await form.getByRole('button', { name: 'Tạo tài khoản' }).click();
  await expect(form.getByText('Hai email chưa giống nhau.')).toBeVisible();

  // AC-505: the default mode sends the code and creates a pending account
  await expect(form.getByRole('radio', { name: /Gửi mã kích hoạt qua email/ })).toBeChecked();
  await form.getByLabel('Nhập lại email').fill(EMAIL);
  await form.getByRole('button', { name: 'Tạo tài khoản' }).click();
  const row = page.getByRole('row', { name: new RegExp(EMAIL) });
  await expect(row.getByText('Chờ kích hoạt')).toBeVisible();

  // AC-518: a pending row has its own actions and no role selector
  await expect(row.getByRole('button', { name: /Gửi lại mã kích hoạt/ })).toBeVisible();
  await expect(row.getByRole('button', { name: /Sửa email/ })).toBeVisible();
  await expect(row.getByRole('button', { name: 'Vô hiệu hóa' })).toBeVisible();
  await expect(row.getByLabel(/Đổi vai trò/)).toHaveCount(0);

  // AC-512: a resend right after the creation is too soon (the creation counts toward the pace)
  await row.getByRole('button', { name: /Gửi lại mã kích hoạt/ }).click();
  await expect(page.getByRole('alert').filter({ hasText: 'Vui lòng thử lại sau' })).toBeVisible();

  // AC-507 and AC-510: nobody signs in to it, and a wrong code is refused on the code field
  const person = await browser.newContext();
  try {
    const p = await person.newPage();
    await p.goto('/');
    await p.getByLabel('Email').fill(EMAIL);
    await p.getByLabel('Mật khẩu').fill('whatever-password-1');
    await p.getByRole('button', { name: 'Đăng nhập' }).click();
    await expect(p.getByRole('alert')).toContainText('Sai email hoặc mật khẩu');

    await p.getByRole('link', { name: 'Kích hoạt tài khoản' }).click();
    await p.getByLabel('Email').fill(EMAIL);
    await p.getByLabel('Mã xác nhận').fill('000000');
    await p.getByLabel('Mật khẩu mới', { exact: true }).fill('chosen-password-1');
    await p.getByLabel('Nhập lại mật khẩu mới').fill('chosen-password-1');
    await p.getByRole('button', { name: 'Kích hoạt tài khoản' }).click();
    await expect(p.getByText('Mã xác nhận không đúng hoặc đã hết hạn.')).toBeVisible();
  } finally {
    await person.close();
  }
});

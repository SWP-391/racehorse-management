import { expect, test } from './fixtures';
import { admin, ensurePeople, people, signIn } from './people';

// REQ-044, BR-239, AC-521, AC-522, AC-526, AC-530: the Club Manager renames another account and their own from the
// accounts page, a blank name is refused on the field, the name in the shell follows a self-rename without a reload, and the audit
// log lists "Đổi tên tài khoản". Names carry a per-run stamp so the spec can be repeated on the same stack, and the
// admin's own name is put back at the end so other specs that read it are not affected.

async function find(page: import('@playwright/test').Page, email: string) {
  await page.getByPlaceholder('Tìm theo họ tên hoặc email').fill(email);
  const row = page.getByRole('row', { name: new RegExp(email) });
  await expect(row).toHaveCount(1);
  return row;
}

test.describe.configure({ timeout: 240_000 });
test.beforeAll(async () => {
  await ensurePeople();
});

const STAMP = Date.now().toString(36);

test('the Club Manager renames another account and their own, and the audit log lists it', async ({ page }) => {
  await signIn(page, admin);
  await page.getByRole('navigation', { name: 'main' }).getByRole('link', { name: 'Tài khoản' }).click();

  // AC-526: a blank name is refused on the field and the popup stays open
  const groomRow = await find(page, people.GROOM.email);
  const groomName = (await groomRow.getByRole('cell').first().innerText()).trim();
  await groomRow.getByRole('button', { name: /^Sửa tên/ }).click();
  let dialog = page.getByRole('dialog');
  await dialog.getByLabel('Họ tên').fill('   ');
  await dialog.getByRole('button', { name: 'Lưu tên' }).click();
  await expect(dialog.getByText('Bắt buộc nhập.')).toBeVisible();

  // AC-521: another account is renamed and the list shows the new name
  const renamed = `Groom Đổi Tên ${STAMP}`;
  await dialog.getByLabel('Họ tên').fill(`  ${renamed.replace(' ', '   ')}  `);
  await dialog.getByRole('button', { name: 'Lưu tên' }).click();
  await expect(dialog).toHaveCount(0);
  await expect(await find(page, people.GROOM.email)).toContainText(renamed);

  // AC-522: the Club Manager's own name changes in the header at once, with no reload
  const ownRow = await find(page, admin.email);
  const ownName = (await ownRow.getByRole('cell').first().innerText()).trim();
  const ownNew = `Quản lý ${STAMP}`;
  await ownRow.getByRole('button', { name: /^Sửa tên/ }).click();
  dialog = page.getByRole('dialog');
  await dialog.getByLabel('Họ tên').fill(ownNew);
  await dialog.getByRole('button', { name: 'Lưu tên' }).click();
  await expect(dialog).toHaveCount(0);
  await expect(page.locator(`[title="${ownNew}"]`).first()).toBeVisible();

  // AC-530: the audit log lists the renames with the old and the new name
  await page.getByRole('link', { name: 'Nhật ký thao tác' }).click();
  await expect(page.getByText('Đổi tên tài khoản').first()).toBeVisible();
  await expect(page.getByText(ownNew).first()).toBeVisible();

  // Put the names back so the other specs see what they expect.
  await page.getByRole('navigation', { name: 'main' }).getByRole('link', { name: 'Tài khoản' }).click();
  for (const [email, name] of [[people.GROOM.email, groomName], [admin.email, ownName]] as const) {
    await (await find(page, email)).getByRole('button', { name: /^Sửa tên/ }).click();
    await page.getByRole('dialog').getByLabel('Họ tên').fill(name);
    await page.getByRole('dialog').getByRole('button', { name: 'Lưu tên' }).click();
    await expect(page.getByRole('dialog')).toHaveCount(0);
  }
});

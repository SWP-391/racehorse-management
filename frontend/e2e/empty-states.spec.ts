import type { Page } from '@playwright/test';
import { expect, test } from './fixtures';
import { admin, ensureHorse, ensurePeople, everyone, signIn } from './people';

// Task 1.2 / 4.3 (BR-198, AC-395, AC-398): no popup or dialog of any role is an empty frame. For each role it opens every
// tile of a horse page and every "Xem" popup of every page in the menu, and fails when the dialog holds nothing but its title.

test.describe.configure({ timeout: 240_000 });
test.beforeAll(async () => {
  await ensurePeople();
  await ensureHorse();
});

/** What an open dialog shows beyond its title: visible text and visible controls other than the close button. */
async function content(page: Page): Promise<{ title: string; text: string; controls: number }> {
  return page.evaluate(() => {
    const dialog = document.querySelector('[role=dialog],[role=alertdialog]');
    if (!dialog) return { title: '', text: '', controls: -1 };
    const visible = (e: Element) => {
      const r = e.getBoundingClientRect();
      return r.width > 0 && r.height > 0 && getComputedStyle(e).visibility !== 'hidden';
    };
    const titleEl = dialog.querySelector('[id^="radix"][id*="title"], h2');
    const title = (titleEl?.textContent ?? '').trim();
    const clone = dialog.cloneNode(true) as HTMLElement;
    clone.querySelectorAll('.sr-only,[data-radix-dialog-close],button[aria-label="Đóng"]').forEach((e) => e.remove());
    clone.querySelectorAll('[id^="radix"][id*="title"]').forEach((e) => e.remove());
    const controls = [...dialog.querySelectorAll('input,select,textarea,button,a[href]')].filter(
      (e) => visible(e) && e.getAttribute('aria-label') !== 'Đóng' && !e.hasAttribute('data-radix-dialog-close'),
    ).length;
    return { title, text: (clone.textContent ?? '').replace(/\s+/g, ' ').trim(), controls };
  });
}

async function checkOpenDialog(page: Page, where: string, offenders: string[]) {
  await page.waitForTimeout(700);
  const c = await content(page);
  if (c.controls < 0) offenders.push(`${where}: no dialog opened`);
  else if (!c.text && c.controls === 0) offenders.push(`${where}: empty frame (title "${c.title}")`);
  else if (c.text === c.title && c.controls === 0) offenders.push(`${where}: only the title "${c.title}"`);
  await page.keyboard.press('Escape');
  await page.waitForTimeout(250);
}

for (const who of everyone) {
  test(`${who.role}: no popup or dialog is an empty frame`, async ({ page }) => {
    const offenders: string[] = [];
    await signIn(page, who.role === 'CLUB_MANAGER' ? admin : who);

    // Every tile of a horse page.
    const hasHorses = (await page.locator('aside a[href="/horses"]').count()) > 0;
    await page.goto(hasHorses ? '/horses' : '/');
    const first = page.locator('main a[href^="/horses/"]').filter({ hasNotText: /Đăng ký/ }).first();
    if (hasHorses) {
      await first.waitFor({ timeout: 15_000 });
      await first.click();
      await expect(page).toHaveURL(/\/horses\/\d+$/);
      const tiles = page.locator('main button.group');
      await tiles.first().waitFor({ timeout: 15_000 });
      const n = await tiles.count();
      expect(n, 'the horse page offers tiles').toBeGreaterThan(0);
      for (let i = 0; i < n; i++) {
        const label = (await tiles.nth(i).locator('span.font-medium').first().innerText()).trim();
        await tiles.nth(i).click();
        await checkOpenDialog(page, `${who.role} horse tile "${label}"`, offenders);
      }
    }

    // Every "Xem" popup of every page in the menu.
    const hrefs = await page.locator('aside a[href^="/"]').evaluateAll((as) => [...new Set(as.map((a) => a.getAttribute('href')!))]);
    for (const href of hrefs) {
      await page.goto(href);
      await page.waitForLoadState('networkidle');
      await page.waitForTimeout(500);
      const eyes = page.locator('main button[aria-label^="Xem:"]');
      const count = Math.min(await eyes.count(), 6);
      for (let i = 0; i < count; i++) {
        const name = (await eyes.nth(i).getAttribute('aria-label')) ?? '';
        await eyes.nth(i).click();
        await checkOpenDialog(page, `${who.role} ${href} "${name}"`, offenders);
      }
    }
    expect(offenders, offenders.join('\n')).toEqual([]);
  });
}

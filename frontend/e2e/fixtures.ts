import { expect, test as base, type Browser, type BrowserType, type LaunchOptions } from '@playwright/test';

export { expect };

/**
 * Same `test` as Playwright's, except that with `E2E_CDP=http://localhost:9222` the specs drive an already running
 * Chrome over CDP instead of launching one. Every test still gets its own isolated context (own cookies and storage),
 * and the person's own tabs are never touched. Without the variable the browser is launched as usual.
 */
export const test = base.extend<object, { browser: Browser; _browserOptions: LaunchOptions }>({
  browser: [
    async ({ playwright, browserName, _browserOptions }, use) => {
      const endpoint = process.env.E2E_CDP;
      if (endpoint) {
        const attached = await playwright.chromium.connectOverCDP(endpoint);
        await use(attached);
        // Only the connection is dropped: `close()` on an attached browser leaves Chrome running.
        await attached.close();
        return;
      }
      const launched = await (playwright[browserName] as BrowserType).launch(_browserOptions);
      await use(launched);
      await launched.close();
    },
    { scope: 'worker' },
  ],
});

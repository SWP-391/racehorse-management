import { defineConfig } from '@playwright/test';

// One suffix for the whole run: Playwright restarts a worker after a failed test, and a suffix taken from the clock when a
// spec loads would change, so the accounts created earlier in the run could no longer sign in ("Sai email").
// The config is loaded once by the runner and its environment is inherited by every worker.
process.env.E2E_SUFFIX ??= Date.now().toString(36);

export default defineConfig({
  testDir: '.',
  timeout: 60_000,
  // A remote stack drops the odd connection; E2E_RETRIES=2 repeats a failed test (0 for a local run).
  retries: Number(process.env.E2E_RETRIES ?? 0),
  reporter: 'list',
  use: {
    baseURL: process.env.BASE_URL ?? 'http://localhost:5173',
    locale: 'vi-VN',
    // Kept for diagnosis: the container is thrown away after the run (the report names the files under test-results).
    screenshot: 'only-on-failure',
    trace: 'retain-on-failure',
    // Software WebGL so the 3D injury view renders in the headless container (TECH-051).
    launchOptions: { args: ['--use-angle=swiftshader', '--enable-unsafe-swiftshader', '--ignore-gpu-blocklist'] },
  },
});

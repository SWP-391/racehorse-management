import { request, type APIRequestContext, type Page } from '@playwright/test';

/**
 * One account per role for the specs that only read the app. On the throwaway stack of `scripts/e2e.sh` they are created
 * through the API by the bootstrap Club Manager; with `E2E_DEMO=1` the seeded demo accounts of a local stack are used.
 */
export type RoleKey = 'CLUB_MANAGER' | 'HEAD_TRAINER' | 'VETERINARIAN' | 'GROOM' | 'HORSE_OWNER';
export interface Person {
  username: string;
  password: string;
  role: RoleKey;
}

const BASE = process.env.BASE_URL ?? 'http://localhost:5173';
const suffix = process.env.E2E_SUFFIX ?? 'local';
const demoPassword = process.env.E2E_DEMO_PASSWORD ?? 'Demo-ZuiebAHj4dxpYWVN';

// The ONLY place that knows the Club Manager login of a run: every spec imports it (a second copy once defaulted to an
// empty password and every sign-in failed). Local stack: admin / racehorse-local-1; scripts/e2e.sh sets BOOTSTRAP_PASSWORD.
export const admin: Person = {
  username: process.env.BOOTSTRAP_USERNAME ?? 'admin',
  password: process.env.BOOTSTRAP_PASSWORD ?? 'racehorse-local-1',
  role: 'CLUB_MANAGER',
};

export const people: Record<Exclude<RoleKey, 'CLUB_MANAGER'>, Person> = process.env.E2E_DEMO
  ? {
      HEAD_TRAINER: { username: 'htl-minh', password: demoPassword, role: 'HEAD_TRAINER' },
      VETERINARIAN: { username: 'bs-lan', password: demoPassword, role: 'VETERINARIAN' },
      GROOM: { username: 'groom-an', password: demoPassword, role: 'GROOM' },
      HORSE_OWNER: { username: 'owner-hoa', password: demoPassword, role: 'HORSE_OWNER' },
    }
  : {
      HEAD_TRAINER: { username: `rt-ht-${suffix}`, password: 'trainer-password-1', role: 'HEAD_TRAINER' },
      VETERINARIAN: { username: `rt-vet-${suffix}`, password: 'vet-password-12', role: 'VETERINARIAN' },
      GROOM: { username: `rt-groom-${suffix}`, password: 'groom-password-1', role: 'GROOM' },
      HORSE_OWNER: { username: `rt-owner-${suffix}`, password: 'owner-password-1', role: 'HORSE_OWNER' },
    };

export const everyone: Person[] = [admin, ...Object.values(people)];

async function post(api: APIRequestContext, path: string, data: unknown) {
  const token = (await api.storageState()).cookies.find((c) => c.name === 'XSRF-TOKEN')?.value;
  return api.post(`${BASE}${path}`, { data, headers: token ? { 'X-XSRF-TOKEN': decodeURIComponent(token) } : {} });
}

/** Creates the role accounts that do not exist yet (a second call, or a demo stack, changes nothing). */
export async function ensurePeople(): Promise<void> {
  if (process.env.E2E_DEMO) return;
  const api = await request.newContext();
  try {
    await api.get(`${BASE}/api/auth/csrf`);
    const login = await post(api, '/api/auth/login', { username: admin.username, password: admin.password });
    if (!login.ok()) throw new Error(`Club Manager sign-in failed (${login.status()}): ${await login.text()}`);
    for (const p of Object.values(people)) {
      const res = await post(api, '/api/accounts', {
        username: p.username,
        displayName: `Kiểm thử ${p.username}`,
        password: p.password,
        roles: [p.role],
      });
      // 409 or 400 "already exists" is fine on a rerun.
      if (!res.ok() && res.status() >= 500) throw new Error(`creating ${p.username} failed: ${res.status()}`);
    }
  } finally {
    await api.dispose();
  }
}

/**
 * A fresh throwaway stack has no horse: the specs that open a horse's forms need one, so the bootstrap Club Manager
 * registers one for the Horse Owner and the Head Trainer of `people`. A stack that already has a horse is left alone.
 */
export async function ensureHorse(): Promise<void> {
  const api = await request.newContext();
  try {
    await api.get(`${BASE}/api/auth/csrf`);
    const login = await post(api, '/api/auth/login', { username: admin.username, password: admin.password });
    if (!login.ok()) throw new Error(`Club Manager sign-in failed (${login.status()}): ${await login.text()}`);
    const existing = (await (await api.get(`${BASE}/api/horses?size=1`)).json()) as { items?: unknown[] };
    if (existing.items?.length) return;
    const idOf = async (username: string): Promise<number> => {
      const found = (await (await api.get(`${BASE}/api/accounts?q=${encodeURIComponent(username)}&size=5`)).json()) as {
        items?: { id: number; username: string }[];
      };
      const id = found.items?.find((a) => a.username === username)?.id;
      if (!id) throw new Error(`account ${username} not found: run ensurePeople first`);
      return id;
    };
    const res = await post(api, '/api/horses', {
      name: `Ngựa kiểm thử ${suffix}`,
      sex: 'MALE',
      birthDate: '2020-01-01',
      ownerId: await idOf(people.HORSE_OWNER.username),
      trainerId: await idOf(people.HEAD_TRAINER.username),
    });
    if (!res.ok()) throw new Error(`registering the test horse failed (${res.status()}): ${await res.text()}`);
  } finally {
    await api.dispose();
  }
}

export async function signIn(page: Page, who: Person): Promise<void> {
  await page.goto('/');
  await page.getByLabel('Tên đăng nhập').fill(who.username);
  await page.getByLabel('Mật khẩu').fill(who.password);
  await page.getByRole('button', { name: 'Đăng nhập' }).click();
  await page.locator('aside').first().waitFor({ state: 'attached', timeout: 15_000 });
}

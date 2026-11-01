import { vi as vitest } from 'vitest';
import { ApiError, request, resetSessionEnded, setSessionEndedHandler } from './client';

const reply = (status: number, body?: unknown) => new Response(body === undefined ? null : JSON.stringify(body), { status });

const handler = vitest.fn();

beforeEach(() => {
  document.cookie = 'XSRF-TOKEN=test-token';
  resetSessionEnded();
  setSessionEndedHandler(handler);
});

afterEach(() => {
  setSessionEndedHandler(null);
  vitest.unstubAllGlobals();
});

describe('an ended session (BR-191, TECH-126)', () => {
  test('AC-279 three parallel refused calls end the session once and still fail each call', async () => {
    vitest.stubGlobal('fetch', vitest.fn().mockImplementation(async () => reply(401, { code: 'UNAUTHENTICATED', message: 'Vui lòng đăng nhập.' })));
    const calls = await Promise.allSettled([
      request('GET', '/api/horses'),
      request('GET', '/api/readiness'),
      request('GET', '/api/notifications'),
    ]);
    expect(calls.every((c) => c.status === 'rejected' && c.reason instanceof ApiError && c.reason.status === 401)).toBe(true);
    expect(handler).toHaveBeenCalledTimes(1);
    expect(handler).toHaveBeenCalledWith(null);
  });

  test('AC-281 a wrong password is a 401 of its own and does not end a session', async () => {
    vitest.stubGlobal('fetch', vitest.fn().mockResolvedValue(reply(401, { code: 'INVALID_CREDENTIALS', message: 'Sai mật khẩu.' })));
    await expect(request('POST', '/api/auth/login', { username: 'x', password: 'y' })).rejects.toMatchObject({ code: 'INVALID_CREDENTIALS' });
    expect(handler).not.toHaveBeenCalled();
  });

  test('the start-up probe of /api/auth/me is not an ended session', async () => {
    vitest.stubGlobal('fetch', vitest.fn().mockResolvedValue(reply(401)));
    await expect(request('GET', '/api/auth/me')).rejects.toBeInstanceOf(ApiError);
    expect(handler).not.toHaveBeenCalled();
  });

  test('a deactivated account passes the service message to the handler', async () => {
    vitest.stubGlobal(
      'fetch',
      vitest.fn().mockResolvedValue(reply(401, { code: 'ACCOUNT_DEACTIVATED', message: 'Tài khoản đã bị vô hiệu hóa.' })),
    );
    await expect(request('GET', '/api/horses')).rejects.toBeInstanceOf(ApiError);
    expect(handler).toHaveBeenCalledWith('Tài khoản đã bị vô hiệu hóa.');
  });

  test('after the next sign-in a later ended session is reported again', async () => {
    vitest.stubGlobal('fetch', vitest.fn().mockImplementation(async () => reply(401)));
    await request('GET', '/api/horses').catch(() => undefined);
    resetSessionEnded();
    await request('GET', '/api/horses').catch(() => undefined);
    expect(handler).toHaveBeenCalledTimes(2);
  });

  test('an answer other than 401 never calls the handler', async () => {
    vitest.stubGlobal('fetch', vitest.fn().mockResolvedValue(reply(403, { code: 'FORBIDDEN', message: 'Không có quyền.' })));
    await expect(request('GET', '/api/horses')).rejects.toMatchObject({ status: 403 });
    expect(handler).not.toHaveBeenCalled();
  });
});

describe('an answer that is not the service\'s JSON (TECH-200)', () => {
  const html = (status: number) => new Response('<html>Bad gateway</html>', { status, headers: { 'Content-Type': 'text/html' } });

  test('an HTML error page is an ApiError with the status, not a SyntaxError', async () => {
    vitest.stubGlobal('fetch', vitest.fn().mockResolvedValue(html(502)));
    await expect(request('GET', '/api/horses')).rejects.toMatchObject({ status: 502, code: 'HTTP_502', message: 'Đã có lỗi xảy ra. Vui lòng thử lại.' });
  });

  test('an HTML page with status 200 is a malformed answer', async () => {
    vitest.stubGlobal('fetch', vitest.fn().mockResolvedValue(html(200)));
    await expect(request('GET', '/api/auth/me')).rejects.toMatchObject({ code: 'MALFORMED_RESPONSE' });
  });

  test('an empty 200 answer is not an error', async () => {
    vitest.stubGlobal('fetch', vitest.fn().mockResolvedValue(new Response('', { status: 200 })));
    await expect(request('GET', '/api/horses')).resolves.toBeUndefined();
  });

  test('a refused 401 with an HTML body still ends the session', async () => {
    vitest.stubGlobal('fetch', vitest.fn().mockResolvedValue(html(401)));
    await expect(request('GET', '/api/horses')).rejects.toBeInstanceOf(ApiError);
    expect(handler).toHaveBeenCalledWith(null);
  });

  test('a network failure is an ApiError with status 0', async () => {
    vitest.stubGlobal('fetch', vitest.fn().mockRejectedValue(new TypeError('Failed to fetch')));
    await expect(request('GET', '/api/horses')).rejects.toMatchObject({ status: 0, code: 'NETWORK_ERROR' });
  });
});

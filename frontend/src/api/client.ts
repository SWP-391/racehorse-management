// Thin fetch wrapper: same-origin session cookie, CSRF header from the XSRF-TOKEN cookie (TECH-006),
// and refusals turned into ApiError with the service's code, Vietnamese message and per-field messages.

export class ApiError extends Error {
  constructor(
    public readonly status: number,
    public readonly code: string,
    message: string,
    public readonly fields: Record<string, string> = {},
  ) {
    super(message);
  }
}

/**
 * Called when the service says nobody is signed in any more (BR-191, TECH-126): with `null` for an ended session,
 * with the server's text when the account was deactivated. It runs once per burst of refused calls (a page fires
 * several at once) until `resetSessionEnded` is called by the next successful sign-in.
 */
type SessionEndedHandler = (message: string | null) => void;

let sessionEnded: SessionEndedHandler | null = null;
let sessionEndedFired = false;

export function setSessionEndedHandler(handler: SessionEndedHandler | null) {
  sessionEnded = handler;
}

export function resetSessionEnded() {
  sessionEndedFired = false;
}

const FALLBACK_MESSAGE = 'Đã có lỗi xảy ra. Vui lòng thử lại.';

/** The sign-in call answers a wrong password with 401 and `/api/auth/me` is the start-up probe: neither is an ended session. */
const NOT_A_SESSION_END = ['/api/auth/login', '/api/auth/me'];

function csrfToken(): string | undefined {
  const match = document.cookie.split('; ').find((c) => c.startsWith('XSRF-TOKEN='));
  return match ? decodeURIComponent(match.substring('XSRF-TOKEN='.length)) : undefined;
}

export async function request<T>(method: string, path: string, body?: unknown): Promise<T> {
  const headers: Record<string, string> = { Accept: 'application/json' };
  const isForm = typeof FormData !== 'undefined' && body instanceof FormData;
  if (body !== undefined && !isForm) headers['Content-Type'] = 'application/json';
  if (method !== 'GET') {
    let token = csrfToken();
    if (!token) {
      await fetch('/api/auth/csrf', { credentials: 'same-origin' });
      token = csrfToken();
    }
    if (token) headers['X-XSRF-TOKEN'] = token;
  }
  let response: Response;
  try {
    response = await fetch(path, {
      method,
      headers,
      credentials: 'same-origin',
      body: body === undefined ? undefined : isForm ? (body as FormData) : JSON.stringify(body),
    });
  } catch {
    // The network is down or the service is unreachable (TECH-200): a refusal like any other, never a bare TypeError.
    throw new ApiError(0, 'NETWORK_ERROR', FALLBACK_MESSAGE);
  }
  if (response.status === 204) return undefined as T;
  const text = await response.text();
  let data: any;
  let malformed = false;
  try {
    data = text ? JSON.parse(text) : undefined;
  } catch {
    // A proxy or gateway answered with HTML or plain text (TECH-200): an ApiError below, not an uncaught SyntaxError.
    malformed = true;
  }
  if (response.status === 401 && !NOT_A_SESSION_END.includes(path) && !sessionEndedFired) {
    sessionEndedFired = true;
    sessionEnded?.(data?.code === 'ACCOUNT_DEACTIVATED' ? (data.message ?? null) : null);
  }
  if (malformed && response.ok) throw new ApiError(response.status, 'MALFORMED_RESPONSE', FALLBACK_MESSAGE);
  if (!response.ok) {
    throw new ApiError(
      response.status,
      data?.code ?? `HTTP_${response.status}`,
      data?.message ?? FALLBACK_MESSAGE,
      data?.fields ?? {},
    );
  }
  return data as T;
}

/**
 * Downloads a file through the signed-in session (the cookie travels, a plain link would not carry a refusal as a
 * message) and returns it with the name the service gave it in Content-Disposition.
 */
export async function download(path: string): Promise<{ blob: Blob; name: string }> {
  let response: Response;
  try {
    response = await fetch(path, { credentials: 'same-origin' });
  } catch {
    throw new ApiError(0, 'NETWORK_ERROR', FALLBACK_MESSAGE);
  }
  if (!response.ok) {
    let data: any;
    try {
      data = await response.json();
    } catch {
      data = undefined;
    }
    throw new ApiError(response.status, data?.code ?? `HTTP_${response.status}`, data?.message ?? FALLBACK_MESSAGE, data?.fields ?? {});
  }
  const disposition = response.headers.get('Content-Disposition') ?? '';
  const match = /filename\*?=(?:UTF-8'')?"?([^";]+)"?/i.exec(disposition);
  return { blob: await response.blob(), name: match ? decodeURIComponent(match[1]) : 'tai-xuong.xlsx' };
}

/** Saves a downloaded file the way a link with `download` would. */
export function saveFile({ blob, name }: { blob: Blob; name: string }) {
  const url = URL.createObjectURL(blob);
  const a = document.createElement('a');
  a.href = url;
  a.download = name;
  document.body.appendChild(a);
  a.click();
  a.remove();
  URL.revokeObjectURL(url);
}

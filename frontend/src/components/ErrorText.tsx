import { ApiError } from '../api/client';
import { vi } from '../i18n/vi';

/** Turns an unknown error into the service's Vietnamese message. */
export function messageOf(e: unknown): string {
  return e instanceof ApiError ? e.message : vi.common.unexpectedError;
}

export function fieldsOf(e: unknown): Record<string, string> {
  return e instanceof ApiError ? e.fields : {};
}

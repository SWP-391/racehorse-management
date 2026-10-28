import { clubMonth, formatVnd } from './utils';

describe('money and month helpers (BR-180)', () => {
  test('formats whole dong with the vi-VN separator', () => {
    expect(formatVnd(150000).replace(/\s| /g, ' ')).toBe('150.000 ₫');
    expect(formatVnd(0).replace(/\s| /g, ' ')).toBe('0 ₫');
  });

  test('the club month follows the club zone and shifts by whole months', () => {
    // 2026-12-31 20:00 UTC is already 2027-01-01 in Ho Chi Minh City.
    const now = new Date('2026-12-31T20:00:00Z');
    expect(clubMonth(0, now)).toBe('2027-01');
    expect(clubMonth(-1, now)).toBe('2026-12');
    expect(clubMonth(-13, now)).toBe('2025-12');
  });
});

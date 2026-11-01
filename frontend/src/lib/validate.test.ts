import { vi as vitest } from 'vitest';
import { collect, focusFirstInvalid, date, dateRange, daysBetween, number, oneOf, required, showDate, today, maxLength, BOUNDS, unitName, normaliseUnitName } from './validate';

// 2026-10-31 12:00 in Ho Chi Minh is 05:00 UTC; 2026-10-31 23:30 there is still the 31st, 16:30 UTC.
const noon = new Date('2026-10-31T05:00:00Z');
const lateEvening = new Date('2026-10-31T16:30:00Z');
const justAfterMidnight = new Date('2026-10-31T17:30:00Z'); // 00:30 on 1 November in the club

describe('today in the club zone', () => {
  test('is the club date, not the UTC date', () => {
    expect(today(0, noon)).toBe('2026-10-31');
    expect(today(0, lateEvening)).toBe('2026-10-31');
    expect(today(0, justAfterMidnight)).toBe('2026-11-01');
    expect(today(1, noon)).toBe('2026-11-01');
    expect(today(-31, noon)).toBe('2026-09-30');
  });
});

describe('required and length', () => {
  test.each([['', true], ['   ', true], [null, true], [undefined, true], ['a', false], [0, false]])('required(%j) refused: %s', (v, refused) => {
    expect(required(v) !== null).toBe(refused);
  });
  test('maxLength', () => {
    expect(maxLength('a'.repeat(200), 200)).toBeNull();
    expect(maxLength('a'.repeat(201), 200)).toBe('Tối đa 200 ký tự.');
    expect(maxLength(undefined, 5)).toBeNull();
  });
});

describe('unitName (same rules as the service, BR-195)', () => {
  test('refuses blank, digits only, symbols, 21 characters', () => {
    expect(unitName('')).toBe('Bắt buộc nhập tên đơn vị.');
    expect(unitName('   ')).toBe('Bắt buộc nhập tên đơn vị.');
    expect(unitName('123')).toBe('Tên đơn vị phải có ít nhất một chữ cái.');
    expect(unitName('kg!')).toBe('Tên đơn vị chỉ gồm chữ, số, dấu cách và dấu "/".');
    expect(unitName('a'.repeat(21))).toBe('Tối đa 20 ký tự.');
  });
  test('accepts letters with accents, digits, space and a slash; spaces are collapsed', () => {
    expect(unitName('lít')).toBeNull();
    expect(unitName('  chai   lớn ')).toBeNull();
    expect(unitName('kg/ngày 2')).toBeNull();
    expect(normaliseUnitName('  chai   lớn ')).toBe('chai lớn');
  });
});

describe('number', () => {
  test('empty passes unless required', () => {
    expect(number('')).toBeNull();
    expect(number('', { required: true })).toBe('Bắt buộc nhập.');
  });
  test('not a number', () => {
    expect(number('abc')).toBe('Hãy nhập một số.');
    expect(number('1e999')).toBe('Hãy nhập một số.');
    expect(number('NaN')).toBe('Hãy nhập một số.');
    for (const text of ['1e3', '0x10', '1e', '--1', '1 2']) expect(number(text)).toBe('Hãy nhập một số.');
  });
  test('whole numbers only', () => {
    expect(number('2.5', { integer: true })).toBe('Hãy nhập số nguyên.');
    expect(number('2', { integer: true })).toBeNull();
    // A decimal point is refused in a whole-number field, even "2.0" (BR-197).
    expect(number('2,0', { integer: true })).toBe('Hãy nhập số nguyên.');
  });
  test('positive means above zero', () => {
    expect(number('0', { positive: true })).toBe('Giá trị phải lớn hơn 0.');
    expect(number('-3', { positive: true })).toBe('Giá trị phải lớn hơn 0.');
    expect(number('0.1', { positive: true })).toBeNull();
  });
  test('min, max and the decimal comma', () => {
    expect(number('0', { min: 0 })).toBeNull();
    expect(number('-1', { min: 0 })).toBe('Giá trị không được âm.');
    expect(number('0', { min: 1 })).toBe('Giá trị tối thiểu là 1.');
    expect(number('301', BOUNDS.heartRateBpm)).toBe('Giá trị tối đa là 300.');
    expect(number('300', BOUNDS.heartRateBpm)).toBeNull();
    expect(number('12,5', { max: 20 })).toBeNull();
  });
  test('form is 1 to 5', () => {
    expect(number('6', { ...BOUNDS.form, integer: true })).toBe('Giá trị tối đa là 5.');
    expect(number('0', { ...BOUNDS.form, integer: true })).toBe('Giá trị tối thiểu là 1.');
    expect(number(3, { ...BOUNDS.form, integer: true })).toBeNull();
  });
});

describe('date', () => {
  test('format and real calendar days', () => {
    expect(date('2026-02-30')).toBe('Ngày không hợp lệ.');
    expect(date('2026-13-01')).toBe('Ngày không hợp lệ.');
    expect(date('31/10/2026')).toBe('Ngày không hợp lệ.');
    expect(date('2024-02-29')).toBeNull();
    expect(date('2026-02-29')).toBe('Ngày không hợp lệ.');
  });
  test('empty passes unless required', () => {
    expect(date('')).toBeNull();
    expect(date('', { required: true })).toBe('Bắt buộc nhập.');
  });
  test('not in the future, by the club day', () => {
    expect(date('2026-10-31', { notFuture: true }, lateEvening)).toBeNull();
    expect(date('2026-11-01', { notFuture: true }, lateEvening)).toBe('Ngày không được ở tương lai.');
    expect(date('2026-11-01', { notFuture: true }, justAfterMidnight)).toBeNull();
  });
  test('not in the past', () => {
    expect(date('2026-10-30', { notPast: true }, noon)).toBe('Ngày không được ở quá khứ.');
    expect(date('2026-10-31', { notPast: true }, noon)).toBeNull();
  });
  test('min and max name the limit as dd/MM/yyyy', () => {
    expect(date('2026-01-01', { min: '2026-06-01' })).toBe('Ngày không được trước 01/06/2026.');
    expect(date('2026-12-01', { max: '2026-06-01' })).toBe('Ngày không được sau 01/06/2026.');
  });
  test('showDate', () => {
    expect(showDate('2026-10-31')).toBe('31/10/2026');
    expect(showDate('bad')).toBe('bad');
  });
});

describe('dateRange', () => {
  test('end before start is refused', () => {
    expect(dateRange('2026-10-10', '2026-10-01')).toBe('Ngày kết thúc không được trước ngày bắt đầu.');
    expect(dateRange('2026-10-10', '2026-10-10')).toBeNull();
  });
  test('an open or incomplete range passes', () => {
    expect(dateRange('', '2026-10-01')).toBeNull();
    expect(dateRange('2026-10-01', '')).toBeNull();
    expect(dateRange('bad', '2026-10-01')).toBeNull();
  });
  test('too long', () => {
    expect(daysBetween('2026-10-01', '2026-10-31')).toBe(30);
    expect(dateRange('2026-10-01', '2026-10-31', 31)).toBeNull();
    expect(dateRange('2026-10-01', '2026-11-02', 31)).toBe('Khoảng ngày tối đa 31 ngày.');
  });
});

describe('oneOf and collect', () => {
  test('oneOf', () => {
    expect(oneOf('MALE', ['MALE', 'FEMALE'])).toBeNull();
    expect(oneOf('X', ['MALE', 'FEMALE'])).toBe('Hãy chọn một giá trị.');
    expect(oneOf('', ['MALE'])).toBe('Bắt buộc nhập.');
  });
  test('collect keeps only refused fields', () => {
    expect(collect({ a: null, b: 'lỗi', c: null, d: 'lỗi khác' })).toEqual({ b: 'lỗi', d: 'lỗi khác' });
  });
});

describe('focusFirstInvalid', () => {
  afterEach(() => {
    vitest.useRealTimers();
    document.body.innerHTML = '';
  });

  test('focuses the first field marked invalid once the errors are drawn', () => {
    vitest.useFakeTimers();
    document.body.innerHTML = '<form><input id="a"><input id="b" aria-invalid="true"><input id="c" aria-invalid="true"></form>';
    focusFirstInvalid(document.querySelector('form'));
    expect(document.activeElement).toBe(document.body);
    vitest.runAllTimers();
    expect(document.activeElement).toBe(document.getElementById('b'));
  });

  test('does nothing when no field is invalid', () => {
    vitest.useFakeTimers();
    document.body.innerHTML = '<form><input id="a"></form>';
    focusFirstInvalid(document.querySelector('form'));
    vitest.runAllTimers();
    expect(document.activeElement).toBe(document.body);
  });
});

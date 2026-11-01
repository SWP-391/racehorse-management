import { fromDong, groupDong, toDong } from './money';

describe('money units (BR-207, DEC-070)', () => {
  it('AC-420 types thousands by default', () => {
    expect(toDong('1500', 1000)).toBe('1500000');
  });

  it('AC-421 reads decimals in million and plain dong', () => {
    expect(toDong('2.5', 1_000_000)).toBe('2500000');
    expect(toDong('2,5', 1_000_000)).toBe('2500000');
    expect(toDong('200000', 1)).toBe('200000');
    expect(toDong('0.001', 1000)).toBe('1');
  });

  it('keeps a fraction of a dong as typed so the whole-number check names the field', () => {
    expect(toDong('1500.5', 1)).toBe('1500.5');
    expect(toDong('1.0005', 1000)).toBe('1000.5');
    expect(groupDong('1500.5')).toBe('');
  });

  it('keeps a deduction negative and gives nothing for unreadable text', () => {
    expect(toDong('-300', 1000)).toBe('-300000');
    expect(toDong('-0', 1000)).toBe('0');
    expect(toDong('', 1000)).toBe('');
    expect(toDong('-', 1000)).toBe('');
    expect(toDong('1e3', 1000)).toBe('');
  });

  it('shows a saved amount in the largest unit that divides it', () => {
    expect(fromDong('150000')).toEqual({ text: '150', unit: 1000 });
    expect(fromDong('2000000')).toEqual({ text: '2', unit: 1_000_000 });
    expect(fromDong('1234')).toEqual({ text: '1234', unit: 1 });
    expect(fromDong('-300000')).toEqual({ text: '-300', unit: 1000 });
    expect(fromDong('')).toEqual({ text: '', unit: 1000 });
    expect(fromDong('0')).toEqual({ text: '0', unit: 1000 });
  });

  it('groups with dots', () => {
    expect(groupDong('1500000')).toBe('1.500.000');
    expect(groupDong('-300000')).toBe('-300.000');
  });
});

import { nextStatuses, parseNodeName, STATUS_COLOURS, anchorName } from './injuryModel';
import { regionLabel } from '../components/BodyRegions';

describe('injury model helpers (task 4.2)', () => {
  test('node names give layer and region code', () => {
    expect(parseNodeName('muscle.LF_FETLOCK')).toEqual({ layer: 'muscle', code: 'LF_FETLOCK' });
    expect(parseNodeName('skin.HEAD')).toEqual({ layer: 'skin', code: 'HEAD' });
    expect(parseNodeName('anchor.HEAD')).toBeNull();
    expect(parseNodeName('horse')).toBeNull();
    expect(parseNodeName(undefined)).toBeNull();
  });

  test('allowed transitions: nothing leaves Đã hồi phục or Đã hủy', () => {
    expect(nextStatuses('NEW')).toEqual(['RECOVERING', 'RECOVERED']);
    expect(nextStatuses('RECOVERING')).toEqual(['RECOVERED']);
    expect(nextStatuses('RECOVERED')).toEqual([]);
    expect(nextStatuses('CANCELLED')).toEqual([]);
  });

  test('each status has its own colour', () => {
    expect(new Set(Object.values(STATUS_COLOURS)).size).toBe(4);
  });

  test('region names come from the one catalog, the code is the fallback', () => {
    const regions = [{ code: 'LF_FETLOCK', vi: 'Khớp bàn trước trái', en: 'Left fore fetlock' }];
    expect(regionLabel(regions, 'LF_FETLOCK')).toBe('Khớp bàn trước trái');
    expect(regionLabel(regions, 'HEAD')).toBe('HEAD');
    expect(anchorName('HEAD')).toBe('anchor.HEAD');
  });
});

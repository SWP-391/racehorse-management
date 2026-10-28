import { render, screen } from '@testing-library/react';
import { SessionEnd, sessionEndText } from './SessionEnd';

describe('session end time (BR-190, AC-380)', () => {
  test('the planned end is start plus duration while upcoming', () => {
    render(<SessionEnd plannedEnd="08:00:00" plannedEndNextDay={false} endedAt={null} />);
    expect(screen.getByText('Kết thúc dự kiến 08:00')).toBeInTheDocument();
  });

  test('once ended, the actual end time replaces it', () => {
    // 2026-10-24T00:52:00Z is 07:52 in the club's time zone.
    expect(sessionEndText({ plannedEnd: '08:00:00', plannedEndNextDay: false, endedAt: '2026-10-24T00:52:00Z' })).toBe('Kết thúc 07:52');
  });

  test('a session ending after midnight says so', () => {
    expect(sessionEndText({ plannedEnd: '00:30:00', plannedEndNextDay: true, endedAt: null })).toBe('Kết thúc dự kiến 00:30 (hôm sau)');
  });

  test('an actual end on a later club day says so (BR-190)', () => {
    // 2026-10-24T21:55:00Z is 04:55 on the 25th in the club's time zone; the session was on the 24th.
    expect(sessionEndText({ plannedEnd: '03:55:00', plannedEndNextDay: true, endedAt: '2026-10-24T21:55:00Z', date: '2026-10-24' })).toBe('Kết thúc 04:55 (hôm sau)');
    expect(sessionEndText({ plannedEnd: '08:00:00', plannedEndNextDay: false, endedAt: '2026-10-24T00:52:00Z', date: '2026-10-24' })).toBe('Kết thúc 07:52');
  });

  test('a session without a start time has no end', () => {
    expect(sessionEndText({ plannedEnd: null, plannedEndNextDay: false, endedAt: null })).toBeNull();
    render(<SessionEnd plannedEnd={null} plannedEndNextDay={false} endedAt={null} />);
    expect(screen.getByText('—')).toBeInTheDocument();
  });
});

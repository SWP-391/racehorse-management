import { screen, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { vi as vitest } from 'vitest';
import TimetablePage from './TimetablePage';
import { renderAs } from '../test/render';
import { api } from '../api/api';
import { ApiError } from '../api/client';
import type { Timetable, TimetableDay } from '../api/types';
import { clubDate } from '../lib/utils';

vitest.mock('../api/api', () => ({ api: { myTimetable: vitest.fn() } }));

const today = clubDate(new Date().toISOString());

const day = (date: string, patch: Partial<TimetableDay> = {}): TimetableDay => ({ date, barnHours: [], leave: null, items: [], ...patch });
const table = (days: TimetableDay[]): Timetable => ({ from: days[0].date, to: days[days.length - 1].date, days });
const item = (id: number, horseName: string, startTime: string | null, patch = {}) => ({
  id, horseId: id, horseName, startTime, plannedEnd: startTime ? '11:00:00' : null, endsNextDay: false, type: 'TROT', state: 'SCHEDULED', groomName: null, ...patch,
});

describe('timetable (BR-200 to BR-202)', () => {
  test('AC-400 a Groom sees barn hours and sessions in time order, the untimed last, a next-day end labelled', async () => {
    vitest.mocked(api.myTimetable).mockResolvedValue(
      table([
        day(today, {
          barnHours: [{ weekday: 1, start: '05:00', end: '09:00' }],
          items: [item(1, 'Sao Mai', '10:00:00'), item(2, 'Gió Bắc', '23:30:00', { plannedEnd: '00:30:00', endsNextDay: true }), item(3, 'Bạch Mã', null)],
        }),
      ]),
    );
    renderAs('GROOM', <TimetablePage />);
    expect(await screen.findByText(/Giờ ở chuồng: 05:00–09:00/)).toBeInTheDocument();
    const rows = screen.getAllByRole('listitem');
    expect(rows[0]).toHaveTextContent('Sao Mai');
    expect(rows[1]).toHaveTextContent('23:30');
    expect(rows[1]).toHaveTextContent('hôm sau');
    expect(rows[2]).toHaveTextContent('chưa có giờ');
    // A Groom's row opens the assigned-sessions page, where acknowledging stays.
    expect(within(rows[0]).getByRole('link', { name: 'Sao Mai' })).toHaveAttribute('href', '/my-sessions');
  });

  test('AC-401 the week view shows every day and a leave day with the late label', async () => {
    const days = Array.from({ length: 7 }, (_, i) => day(`2026-11-0${i + 2}`));
    days[2] = day('2026-11-04', { leave: { late: true, reason: null } });
    vitest.mocked(api.myTimetable).mockResolvedValue(table(days));
    renderAs('GROOM', <TimetablePage />);
    await userEvent.click(await screen.findByRole('button', { name: 'Tuần' }));
    expect(await screen.findByText(/Nghỉ phép \(đột xuất\)/)).toBeInTheDocument();
    expect(screen.getAllByRole('heading', { level: 2 })).toHaveLength(7);
    const [from, to] = vitest.mocked(api.myTimetable).mock.calls.at(-1)!;
    expect((new Date(`${to}T00:00:00Z`).getTime() - new Date(`${from}T00:00:00Z`).getTime()) / 864e5).toBe(6);
    expect(new Date(`${from}T00:00:00Z`).getUTCDay()).toBe(1);
  });

  test('AC-402 a Head Trainer sees the Groom of each session and a link to the session', async () => {
    vitest.mocked(api.myTimetable).mockResolvedValue(
      table([day(today, { items: [item(7, 'Sao Mai', '08:00:00', { groomName: 'Nhân viên An' }), item(8, 'Gió Bắc', '09:00:00')] })]),
    );
    renderAs('HEAD_TRAINER', <TimetablePage />);
    const rows = await screen.findAllByRole('listitem');
    expect(rows[0]).toHaveTextContent('Nhân viên An');
    expect(rows[1]).toHaveTextContent('chưa có người phụ trách');
    expect(within(rows[0]).getByRole('link', { name: 'Sao Mai' })).toHaveAttribute('href', '/sessions/7');
  });

  test('AC-404 a day with nothing says so', async () => {
    vitest.mocked(api.myTimetable).mockResolvedValue(table([day(today)]));
    renderAs('GROOM', <TimetablePage />);
    expect(await screen.findByText('Không có buổi tập nào trong ngày này.')).toBeInTheDocument();
  });

  test('a cancelled session stays, with its state', async () => {
    vitest.mocked(api.myTimetable).mockResolvedValue(table([day(today, { items: [item(1, 'Sao Mai', '10:00:00', { state: 'CANCELLED' })] })]));
    renderAs('GROOM', <TimetablePage />);
    expect(await screen.findByText('Hủy')).toBeInTheDocument();
  });

  test('a failed load shows the error with a retry', async () => {
    vitest.mocked(api.myTimetable).mockRejectedValueOnce(new ApiError(500, 'X', 'Mất kết nối')).mockResolvedValue(table([day(today)]));
    renderAs('GROOM', <TimetablePage />);
    expect(await screen.findByText('Mất kết nối')).toBeInTheDocument();
    await userEvent.click(screen.getByRole('button', { name: 'Thử lại' }));
    expect(await screen.findByText('Không có buổi tập nào trong ngày này.')).toBeInTheDocument();
  });

  test('the next button moves the day and asks the service for it', async () => {
    vitest.mocked(api.myTimetable).mockResolvedValue(table([day(today)]));
    renderAs('GROOM', <TimetablePage />);
    await screen.findByText('Không có buổi tập nào trong ngày này.');
    await userEvent.click(screen.getByRole('button', { name: 'Sau' }));
    await vitest.waitFor(() => {
      const [from] = vitest.mocked(api.myTimetable).mock.calls.at(-1)!;
      expect(from > today).toBe(true);
    });
  });
});

describe('timetable by day, week and month with a calendar (BR-200, BR-209, AC-433, AC-434)', () => {
  const month = (first: string, count: number, withSessions: Record<string, number> = {}): Timetable =>
    table(
      Array.from({ length: count }, (_, i) => {
        const date = `${first.slice(0, 8)}${String(i + 1).padStart(2, '0')}`;
        return day(date, { items: Array.from({ length: withSessions[date] ?? 0 }, (_, k) => item(100 + i * 10 + k, `Ngựa ${i}-${k}`, '06:00:00')) });
      }),
    );

  test('AC-433 a day chosen in the calendar moves the week, and "Hôm nay" comes back', async () => {
    vitest.mocked(api.myTimetable).mockImplementation(async (from, to) =>
      table(Array.from({ length: (Date.parse(to) - Date.parse(from)) / 864e5 + 1 }, (_, i) => day(new Date(Date.parse(from) + i * 864e5).toISOString().slice(0, 10)))),
    );
    renderAs('GROOM', <TimetablePage />);
    await screen.findByText(/Không có buổi tập nào/);
    await userEvent.click(screen.getByRole('button', { name: 'Tuần' }));
    const picker = screen.getByLabelText('Chọn ngày');
    await userEvent.clear(picker);
    await userEvent.type(picker, '15/10/2026'); // a Thursday: the week is 12 to 18 October
    await vitest.waitFor(() => expect(api.myTimetable).toHaveBeenLastCalledWith('2026-10-12', '2026-10-18'));
    await userEvent.click(screen.getByRole('button', { name: 'Hôm nay' }));
    expect(screen.getByLabelText('Chọn ngày')).toHaveValue(today.split('-').reverse().join('/'));
  });

  test('AC-434 the month shows how many sessions each day holds and a click opens that day', async () => {
    const first = `${today.slice(0, 8)}01`;
    const count = new Date(Date.UTC(Number(today.slice(0, 4)), Number(today.slice(5, 7)), 0)).getUTCDate();
    vitest.mocked(api.myTimetable).mockResolvedValue(month(first, count, { [`${today.slice(0, 8)}05`]: 3 }));
    renderAs('GROOM', <TimetablePage />);
    await userEvent.click(await screen.findByRole('button', { name: 'Tháng' }));
    expect(api.myTimetable).toHaveBeenLastCalledWith(first, `${today.slice(0, 8)}${String(count).padStart(2, '0')}`);
    const busy = await screen.findByRole('button', { name: /3 buổi tập/ });
    expect(screen.getByRole('button', { name: new RegExp(`${count}/\\d\\d/\\d{4}: không có buổi tập`) })).toBeInTheDocument();
    await userEvent.click(busy);
    expect(api.myTimetable).toHaveBeenLastCalledWith(`${today.slice(0, 8)}05`, `${today.slice(0, 8)}05`);
    expect(screen.getByRole('button', { name: 'Ngày' })).toHaveAttribute('aria-pressed', 'true');
  });
});

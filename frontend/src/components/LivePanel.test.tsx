import { act, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { vi as vitest } from 'vitest';
import LivePanel from './LivePanel';
import { renderAs } from '../test/render';
import { api } from '../api/api';
import type { LiveView } from '../api/types';

vitest.mock('../api/api', () => ({ api: { live: vitest.fn() } }));

const active = (hr: number, elapsed: number): LiveView => ({
  active: true, sessionId: 4, dataSource: 'SIMULATED', label: 'DỮ LIỆU MÔ PHỎNG', elapsedSeconds: elapsed,
  current: { offsetSecond: elapsed, heartRate: hr, speed: 30 },
  recent: [{ offsetSecond: elapsed - 1, heartRate: hr - 1, speed: 29 }, { offsetSecond: elapsed, heartRate: hr, speed: 30 }],
  alerts: [], thresholds: [], summary: null,
});

afterEach(() => vitest.useRealTimers());

describe('live panel (task 4.1, BR-050, BR-053, NFR-011)', () => {
  test('AC-055 shows the label and refreshes every second', async () => {
    vitest.useFakeTimers({ shouldAdvanceTime: true });
    vitest.mocked(api.live).mockResolvedValueOnce(active(120, 60)).mockResolvedValue(active(125, 61));
    renderAs('HEAD_TRAINER', <LivePanel horseId={9} />);
    expect(await screen.findByText('DỮ LIỆU MÔ PHỎNG')).toBeInTheDocument();
    expect(screen.getByRole('status')).toHaveTextContent('Không dùng để ra quyết định thực tế về ngựa');
    expect(screen.getByTestId('heart-rate')).toHaveTextContent('120');
    await act(async () => {
      await vitest.advanceTimersByTimeAsync(1000);
    });
    expect(api.live).toHaveBeenCalledTimes(2);
    expect(await screen.findByText('125')).toBeInTheDocument();
  });

  test('AC-059 an idle horse shows no simulated data', async () => {
    vitest.mocked(api.live).mockResolvedValue({
      active: false, sessionId: null, dataSource: null, label: null, elapsedSeconds: null, current: null, recent: [], alerts: [], thresholds: [], summary: null,
    });
    renderAs('HEAD_TRAINER', <LivePanel horseId={9} />);
    expect(await screen.findByText('Ngựa đang nghỉ: không có dữ liệu mô phỏng.')).toBeInTheDocument();
    expect(screen.queryByTestId('heart-rate')).not.toBeInTheDocument();
  });

  test('AC-060 the Owner gets no live panel and no request is made', () => {
    renderAs('HORSE_OWNER', <LivePanel horseId={9} />);
    expect(screen.queryByRole('region')).not.toBeInTheDocument();
    expect(api.live).not.toHaveBeenCalled();
  });

  test('a raised alert is listed with the simulated label', async () => {
    vitest.mocked(api.live).mockResolvedValue({
      ...active(160, 80),
      alerts: [{ sessionId: 4, measure: 'HEART_RATE', limit: 150, value: 153, offsetSecond: 70, raisedAt: '', dataSource: 'SIMULATED', label: 'DỮ LIỆU MÔ PHỎNG' }],
    });
    renderAs('HEAD_TRAINER', <LivePanel horseId={9} />);
    expect(await screen.findByRole('list', { name: 'Cảnh báo' })).toHaveTextContent('vượt ngưỡng 150: 153 (DỮ LIỆU MÔ PHỎNG)');
  });
});

describe('live range (BR-189, AC-377 to AC-379)', () => {
  const figures = (current: number) => ({ current, min: 98, max: 171, avg: 142.5 });
  const withRange = (over: Partial<LiveView> = {}): LiveView => ({
    ...active(160, 80),
    thresholds: [{ measure: 'HEART_RATE', limit: 195 }],
    summary: { heartRate: figures(160), speed: { current: 30, min: 0, max: 16.5, avg: 9.2 }, windowSeconds: 60 },
    ...over,
  });

  test('AC-377 threshold line, safe zone, current, min, max and average with units', async () => {
    vitest.mocked(api.live).mockResolvedValue(withRange());
    renderAs('HEAD_TRAINER', <LivePanel horseId={9} />);
    expect(await screen.findByTestId('threshold-line')).toBeInTheDocument();
    expect(screen.getByTestId('safe-zone')).toBeInTheDocument();
    const hr = screen.getByTestId('heart-rate-figures');
    expect(hr).toHaveTextContent('Thấp nhất98 bpm');
    expect(hr).toHaveTextContent('Cao nhất171 bpm');
    expect(hr).toHaveTextContent('Trung bình142.5 bpm');
    expect(screen.getByTestId('speed-figures')).toHaveTextContent('Cao nhất16.5 km/h');
    expect(screen.getByText(/Ngưỡng cảnh báo: 195 bpm/)).toBeInTheDocument();
  });

  test('AC-378 choosing a window asks for it at once', async () => {
    vitest.mocked(api.live).mockResolvedValue(withRange());
    renderAs('HEAD_TRAINER', <LivePanel horseId={9} />);
    await screen.findByTestId('heart-rate-figures');
    await userEvent.click(screen.getByRole('button', { name: '5 phút' }));
    await vitest.waitFor(() => expect(api.live).toHaveBeenLastCalledWith(9, 'FIVE_MINUTES'));
    await userEvent.click(screen.getByRole('button', { name: 'Cả buổi tập' }));
    await vitest.waitFor(() => expect(api.live).toHaveBeenLastCalledWith(9, 'SESSION'));
  });

  test('AC-379 without a threshold there is no line and no zone, the figures stay', async () => {
    vitest.mocked(api.live).mockResolvedValue(withRange({ thresholds: [] }));
    renderAs('HEAD_TRAINER', <LivePanel horseId={9} />);
    await screen.findByTestId('heart-rate-figures');
    expect(screen.queryByTestId('threshold-line')).not.toBeInTheDocument();
    expect(screen.queryByTestId('safe-zone')).not.toBeInTheDocument();
  });
});

describe('live panel failure', () => {
  test('an error offers Retry, which asks again at once', async () => {
    vitest.mocked(api.live).mockReset().mockRejectedValueOnce(new Error('down')).mockResolvedValue(active(120, 60));
    renderAs('HEAD_TRAINER', <LivePanel horseId={9} />);
    const retry = await screen.findByRole('button', { name: 'Thử lại' });
    await userEvent.click(retry);
    expect(await screen.findByText('DỮ LIỆU MÔ PHỎNG')).toBeInTheDocument();
  });
});

describe('live panel structure (axe: definition-list)', () => {
  test('every group of the list holds only a term and a description, and each series has its own line', async () => {
    vitest.mocked(api.live).mockResolvedValue(active(120, 60));
    const { container } = renderAs('HEAD_TRAINER', <LivePanel horseId={9} />);
    await screen.findByTestId('heart-rate');
    const groups = container.querySelectorAll('dl > div');
    expect(groups).toHaveLength(3);
    groups.forEach((g) => expect([...g.children].map((c) => c.tagName)).toEqual(['DT', 'DD']));
    expect(screen.getByTestId('heart-rate').querySelector('svg')).not.toBeNull();
    expect(screen.getByTestId('speed').querySelector('svg')).not.toBeNull();
  });
});

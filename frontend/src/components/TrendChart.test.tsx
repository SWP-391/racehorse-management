import { render, screen, within } from '@testing-library/react';
import TrendChart from './TrendChart';
import type { Session } from '../api/types';

const session = (id: number, form: number, source: 'SIMULATED' | 'MANUAL' | null): Session =>
  ({
    id, date: `2026-10-${10 + id}`, distanceM: 1500 + id, durationMin: 55, intensity: 'MEDIUM', form,
    avgHeartRateSource: source, maxSpeedSource: source,
  }) as unknown as Session;

describe('trend chart (task 4.3, AC-066)', () => {
  test('shows distance, duration, intensity and form, marks simulated sessions, and shows no fitness score', () => {
    render(<TrendChart sessions={[session(1, 3, 'MANUAL'), session(2, 4, 'SIMULATED'), session(3, 5, null)]} />);
    // The chart's hidden table: one series (the form rating) plus the simulated marker column.
    const table = screen.getByRole('table', { name: 'Xu hướng phong độ' });
    const rows = within(table).getAllByRole('row').slice(1);
    expect(rows).toHaveLength(3);
    expect(rows[1]).toHaveTextContent('mô phỏng');
    expect(rows[0]).not.toHaveTextContent('mô phỏng');
    expect(rows[2]).not.toHaveTextContent('mô phỏng');
    expect(rows.map((r) => within(r).getAllByRole('cell')[0].textContent)).toEqual(['3', '4', '5']);
    // The other recorded indicators sit in the details table.
    const details = screen.getByRole('table', { name: 'Chi tiết các buổi tập' });
    const detailRows = within(details).getAllByRole('row').slice(1);
    expect(detailRows[0]).toHaveTextContent('1501');
    expect(detailRows[0]).toHaveTextContent('55');
    expect(detailRows[0]).toHaveTextContent('Vừa');
    expect(detailRows[1]).toHaveTextContent('mô phỏng');
    expect(detailRows[0]).not.toHaveTextContent('mô phỏng');
    // Only recorded indicators are columns; no computed score column exists (BR-042).
    expect(within(details).getAllByRole('columnheader').map((h) => h.textContent)).toEqual([
      'Ngày', 'Cự ly (m)', 'Thời lượng (phút)', 'Cường độ', 'Phong độ (1–5)',
    ]);
    expect(screen.getByText(/không có điểm thể lực tính toán/)).toBeInTheDocument();
  });

  test('shows a placeholder when there are no completed sessions', () => {
    render(<TrendChart sessions={[]} />);
    expect(screen.getByText('—')).toBeInTheDocument();
  });
});

describe('trend chart on the progress board (compact)', () => {
  test('the indicator table is behind a disclosure and still lists every recorded indicator', () => {
    const { container } = render(<TrendChart compact sessions={[session(1, 3, 'MANUAL'), session(2, 4, 'SIMULATED')]} />);
    const details = container.querySelector('details');
    expect(details).not.toBeNull();
    expect(details).not.toHaveAttribute('open');
    expect(details!.querySelector('summary')).toHaveTextContent('Chi tiết các buổi tập');
    const table = within(details as HTMLElement).getByRole('table', { name: 'Chi tiết các buổi tập', hidden: true });
    expect(table).toHaveTextContent('1501');
    expect(table).toHaveTextContent('Vừa');
    expect(table).toHaveTextContent('mô phỏng');
  });

  test('with fewer than three sessions a caption says how many there are', () => {
    render(<TrendChart compact sessions={[session(1, 3, 'MANUAL')]} />);
    expect(screen.getByText('1 buổi tập')).toBeInTheDocument();
  });
});


import { useState } from 'react';
import { render, screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { DateInput, TimeInput } from './typed-inputs';
import { today } from '@/lib/validate';

function Harness({ initial = '', min, max, seen }: { initial?: string; min?: string; max?: string; seen?: string[] }) {
  const [v, setV] = useState(initial);
  return (
    <>
      <label htmlFor="d">Ngày</label>
      <DateInput id="d" value={v} min={min} max={max} onChange={(x) => { setV(x); seen?.push(x); }} />
    </>
  );
}

describe('DateInput (BR-206, DEC-069)', () => {
  test('a typed day is read as dd/mm/yyyy and given as yyyy-MM-dd; yyyy-MM-dd is read too', async () => {
    const seen: string[] = [];
    render(<Harness seen={seen} />);
    await userEvent.type(screen.getByLabelText('Ngày'), '05/03/2026');
    expect(seen.at(-1)).toBe('2026-03-05');
    await userEvent.clear(screen.getByLabelText('Ngày'));
    await userEvent.type(screen.getByLabelText('Ngày'), '2026-04-09');
    expect(seen.at(-1)).toBe('2026-04-09');
    await userEvent.tab();
    expect(screen.getByLabelText('Ngày')).toHaveValue('09/04/2026');
  });

  test('a saved day is shown as dd/mm/yyyy and follows a reset by the page', async () => {
    render(<Harness initial="2026-03-05" />);
    expect(screen.getByLabelText('Ngày')).toHaveValue('05/03/2026');
  });

  test('AC-418 a typed day past the limit is passed on and marked with the limit named', async () => {
    const seen: string[] = [];
    render(<Harness max="2026-11-01" seen={seen} />);
    await userEvent.type(screen.getByLabelText('Ngày'), '02/11/2026');
    expect(seen.at(-1)).toBe('2026-11-02');
    expect(screen.getByLabelText('Ngày')).toHaveAttribute('aria-invalid', 'true');
    expect(screen.getByRole('alert')).toHaveTextContent('Ngày không được sau 01/11/2026.');
  });

  test('a day that does not exist is refused', async () => {
    render(<Harness />);
    await userEvent.type(screen.getByLabelText('Ngày'), '31/02/2026');
    expect(screen.getByRole('alert')).toHaveTextContent('Ngày không hợp lệ');
  });

  test('AC-417 "Hôm nay" chooses today and closes the calendar', async () => {
    const seen: string[] = [];
    render(<Harness seen={seen} />);
    await userEvent.click(screen.getByRole('button', { name: 'Mở lịch' }));
    // The calendar loads on first open; a cold run of the whole suite needs longer than the default second.
    await userEvent.click(await screen.findByRole('button', { name: 'Hôm nay' }, { timeout: 5000 }));
    expect(seen.at(-1)).toBe(today());
    await waitFor(() => expect(screen.queryByRole('button', { name: 'Hôm nay' })).toBeNull());
  });

  test('AC-416 jumps to another year and month, then a day', async () => {
    const seen: string[] = [];
    render(<Harness initial="2026-11-01" seen={seen} />);
    await userEvent.click(screen.getByRole('button', { name: 'Mở lịch' }));
    const grid = await screen.findByRole('grid', {}, { timeout: 5000 });
    const root = grid.closest('.rdp-root') as HTMLElement;
    const [month, year] = Array.from(root.querySelectorAll('select')) as HTMLSelectElement[];
    await userEvent.selectOptions(year, '2024');
    await userEvent.selectOptions(month, '2'); // March
    await userEvent.click(within(root).getByRole('button', { name: /15.*2024/ }));
    expect(seen.at(-1)).toBe('2024-03-15');
    expect(screen.getByLabelText('Ngày')).toHaveValue('15/03/2024');
  });

  test('AC-418 on the calendar a day past the limit is off', async () => {
    render(<Harness initial="2026-11-01" min="2026-10-01" max="2026-11-01" />);
    await userEvent.click(screen.getByRole('button', { name: 'Mở lịch' }));
    const root = (await screen.findByRole('grid', {}, { timeout: 5000 })).closest('.rdp-root') as HTMLElement;
    expect(root.querySelector('[data-day="2026-11-02"] button')).toBeDisabled();
    expect(root.querySelector('[data-day="2026-11-01"] button')).not.toBeDisabled();
  });

  test('"Hôm nay" is off when today is outside the limits', async () => {
    render(<Harness min="2026-01-01" max="2026-02-01" />);
    await userEvent.click(screen.getByRole('button', { name: 'Mở lịch' }));
    expect(await screen.findByRole('button', { name: 'Hôm nay' }, { timeout: 5000 })).toBeDisabled();
  });
});

describe('TimeInput (BR-206)', () => {
  function TimeHarness({ seen }: { seen: string[] }) {
    const [v, setV] = useState('');
    return <TimeInput aria-label="Giờ" value={v} onChange={(x) => { setV(x); seen.push(x); }} />;
  }

  test('AC-419 06:30 is saved however it is typed, and 25:00 is refused', async () => {
    const seen: string[] = [];
    render(<TimeHarness seen={seen} />);
    await userEvent.type(screen.getByLabelText('Giờ'), '0630');
    expect(seen.at(-1)).toBe('06:30');
    expect(screen.getByLabelText('Giờ')).toHaveValue('06:30');
    await userEvent.clear(screen.getByLabelText('Giờ'));
    await userEvent.type(screen.getByLabelText('Giờ'), '25:00');
    expect(seen.at(-1)).toBe('');
    expect(screen.getByRole('alert')).toHaveTextContent('Giờ không hợp lệ');
  });

  test('a time is chosen from the list of quarter-hours', async () => {
    const seen: string[] = [];
    render(<TimeHarness seen={seen} />);
    await userEvent.click(screen.getByRole('button', { name: 'Chọn giờ' }));
    await userEvent.click(await screen.findByRole('button', { name: '14:45' }));
    expect(seen.at(-1)).toBe('14:45');
    expect(screen.getByLabelText('Giờ')).toHaveValue('14:45');
  });
});

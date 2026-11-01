import { useState } from 'react';
import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MoneyInput } from './money-input';

function Harness({ initial = '', allowNegative = false, onValue }: { initial?: string; allowNegative?: boolean; onValue?: (v: string) => void }) {
  const [v, setV] = useState(initial);
  return (
    <>
      <label htmlFor="amt">Số tiền</label>
      <MoneyInput id="amt" value={v} allowNegative={allowNegative} onChange={(x) => { setV(x); onValue?.(x); }} />
      <button type="button" onClick={() => setV('')}>reset</button>
    </>
  );
}

describe('MoneyInput (BR-207, DEC-070)', () => {
  it('AC-420 gives whole dong and shows the full amount in thousands by default', async () => {
    const seen: string[] = [];
    render(<Harness onValue={(v) => seen.push(v)} />);
    await userEvent.type(screen.getByLabelText('Số tiền'), '1500');
    expect(seen.at(-1)).toBe('1500000');
    expect(screen.getByText('= 1.500.000 VND')).toBeInTheDocument();
  });

  it('AC-421 changes with the unit', async () => {
    const seen: string[] = [];
    render(<Harness onValue={(v) => seen.push(v)} />);
    await userEvent.type(screen.getByLabelText('Số tiền'), '2.5');
    await userEvent.selectOptions(screen.getByLabelText('Tính tiền theo'), 'triệu đồng');
    expect(seen.at(-1)).toBe('2500000');
    expect(screen.getByText('= 2.500.000 VND')).toBeInTheDocument();
    await userEvent.selectOptions(screen.getByLabelText('Tính tiền theo'), 'đồng');
    // 2.5 dong is not a whole amount: the field says so instead of rounding.
    expect(screen.getByText('Số tiền phải là số đồng nguyên.')).toBeInTheDocument();
  });

  it('refuses a fraction of a dong in the line under the field', async () => {
    render(<Harness />);
    await userEvent.selectOptions(screen.getByLabelText('Tính tiền theo'), 'đồng');
    await userEvent.type(screen.getByLabelText('Số tiền'), '1500.5');
    expect(screen.getByText('Số tiền phải là số đồng nguyên.')).toBeInTheDocument();
  });

  it('shows a saved amount in a sensible unit', () => {
    render(<Harness initial="150000" />);
    expect(screen.getByLabelText('Số tiền')).toHaveValue('150');
    expect(screen.getByLabelText('Tính tiền theo')).toHaveValue('1000');
    expect(screen.getByText('= 150.000 VND')).toBeInTheDocument();
  });

  it('takes a minus only where a deduction is allowed', async () => {
    const { unmount } = render(<Harness />);
    await userEvent.type(screen.getByLabelText('Số tiền'), '-300');
    expect(screen.getByLabelText('Số tiền')).toHaveValue('300');
    unmount();
    render(<Harness allowNegative />);
    await userEvent.type(screen.getByLabelText('Số tiền'), '-300');
    expect(screen.getByText('= -300.000 VND')).toBeInTheDocument();
  });

  it('follows a reset by the parent', async () => {
    render(<Harness initial="150000" />);
    await userEvent.click(screen.getByText('reset'));
    expect(screen.getByLabelText('Số tiền')).toHaveValue('');
  });
});

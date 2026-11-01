import { act, render, screen, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { useState } from 'react';
import { MemoryRouter } from 'react-router-dom';
import { vi as vitest } from 'vitest';
import { Button } from './button';
import { FormField } from './form-field';
import { Input, Textarea } from './input';
import { DateInput, NumberInput } from './typed-inputs';
import { Select } from './select';
import { Dialog, DialogContent, DialogTitle, DialogTrigger } from './dialog';
import { DataView } from './data-view';
import { DataTable } from './data-table';
import { EmptyState } from './states';
import { useLoad } from '@/hooks/useLoad';
import { Chart } from '../chart/Chart';
import { DetailPopup } from './detail-popup';
import { Inbox } from 'lucide-react';

describe('kit inputs (task 2.3)', () => {
  test('a loading button is disabled and busy, so a second press cannot submit twice', async () => {
    const onClick = vitest.fn();
    render(
      <Button loading onClick={onClick}>
        Lưu
      </Button>,
    );
    const button = screen.getByRole('button', { name: 'Lưu' });
    expect(button).toBeDisabled();
    expect(button).toHaveAttribute('aria-busy', 'true');
    await userEvent.click(button);
    expect(onClick).not.toHaveBeenCalled();
  });

  test('a button never submits its form unless it says so', async () => {
    const onSubmit = vitest.fn((e: Event) => e.preventDefault());
    render(
      <form onSubmit={(e) => onSubmit(e.nativeEvent)}>
        <Button>Thêm dòng</Button>
      </form>,
    );
    await userEvent.click(screen.getByRole('button', { name: 'Thêm dòng' }));
    expect(onSubmit).not.toHaveBeenCalled();
  });

  test('FormField links the label, the description and the error to the control', () => {
    render(
      <FormField label="Tên ngựa" description="Như trên giấy đăng ký" error="Bắt buộc nhập.">
        <Input />
      </FormField>,
    );
    const input = screen.getByLabelText('Tên ngựa');
    expect(input).toHaveAttribute('aria-invalid', 'true');
    expect(input).toHaveAccessibleDescription('Như trên giấy đăng ký Bắt buộc nhập.');
    expect(screen.getByRole('alert')).toHaveTextContent('Bắt buộc nhập.');
  });

  test('the kit Select stays a native select, so selectOptions works (TECH-072)', async () => {
    render(
      <FormField label="Giới tính">
        <Select defaultValue="">
          <option value="">—</option>
          <option value="MALE">Đực</option>
          <option value="FEMALE">Cái</option>
        </Select>
      </FormField>,
    );
    await userEvent.selectOptions(screen.getByLabelText('Giới tính'), 'FEMALE');
    expect(screen.getByLabelText('Giới tính')).toHaveValue('FEMALE');
  });
});

describe('kit overlays (task 2.4)', () => {
  test('a dialog takes focus, Escape closes it and focus returns to the trigger', async () => {
    render(
      <Dialog>
        <DialogTrigger asChild>
          <Button>Mở</Button>
        </DialogTrigger>
        <DialogContent aria-describedby={undefined}>
          <DialogTitle>Xác nhận</DialogTitle>
          <Input aria-label="Lý do" />
        </DialogContent>
      </Dialog>,
    );
    const trigger = screen.getByRole('button', { name: 'Mở' });
    await userEvent.click(trigger);
    const dialog = screen.getByRole('dialog', { name: 'Xác nhận' });
    expect(dialog).toContainElement(document.activeElement as HTMLElement);
    await userEvent.keyboard('{Escape}');
    expect(screen.queryByRole('dialog')).not.toBeInTheDocument();
    expect(trigger).toHaveFocus();
  });
});

function Loaded({ fn }: { fn: () => Promise<string[]> }) {
  const load = useLoad(fn, []);
  return (
    <DataView
      load={load}
      isEmpty={(d) => d.length === 0}
      empty={<EmptyState icon={Inbox} title="Chưa có ngựa nào." />}
    >
      {(items) => (
        <ul>
          {items.map((i) => (
            <li key={i}>{i}</li>
          ))}
        </ul>
      )}
    </DataView>
  );
}

describe('kit data states (task 2.5)', () => {
  afterEach(() => vitest.useRealTimers());

  test('the skeleton appears only after 200 ms, so fast loads never flash', async () => {
    vitest.useFakeTimers();
    render(<Loaded fn={() => new Promise(() => undefined)} />);
    expect(screen.queryByRole('status')).not.toBeInTheDocument();
    act(() => {
      vitest.advanceTimersByTime(199);
    });
    expect(screen.queryByRole('status')).not.toBeInTheDocument();
    act(() => {
      vitest.advanceTimersByTime(2);
    });
    expect(screen.getByRole('status')).toBeInTheDocument();
  });

  test('a failed load shows the message and Retry loads again', async () => {
    const fn = vitest.fn().mockRejectedValueOnce(new Error('x')).mockResolvedValueOnce(['Sao Mai']);
    render(<Loaded fn={fn} />);
    const alert = await screen.findByRole('alert');
    await userEvent.click(within(alert).getByRole('button', { name: 'Thử lại' }));
    expect(await screen.findByText('Sao Mai')).toBeInTheDocument();
    expect(fn).toHaveBeenCalledTimes(2);
  });

  test('an empty answer shows the empty state, not an empty list', async () => {
    render(<Loaded fn={() => Promise.resolve([])} />);
    expect(await screen.findByText('Chưa có ngựa nào.')).toBeInTheDocument();
    expect(screen.queryByRole('list')).not.toBeInTheDocument();
  });

  test('DataTable keeps one semantic table (rows and cells) and sorts on demand', async () => {
    function Table() {
      const [rows] = useState([
        { id: 1, name: 'Sao Mai', age: 5 },
        { id: 2, name: 'Bạch Long', age: 3 },
      ]);
      return (
        <MemoryRouter>
          <DataTable
            caption="Danh sách ngựa"
            data={rows}
            rowKey={(r) => r.id}
            columns={[
              { id: 'name', header: 'Tên ngựa', cell: (r) => r.name, sortValue: (r) => r.name, primary: true },
              { id: 'age', header: 'Tuổi', cell: (r) => r.age, sortValue: (r) => r.age },
            ]}
          />
        </MemoryRouter>
      );
    }
    render(<Table />);
    const table = screen.getByRole('table', { name: 'Danh sách ngựa' });
    expect(within(table).getAllByRole('row')).toHaveLength(3);
    expect(within(table).getByRole('cell', { name: 'Sao Mai' })).toBeInTheDocument();
    await userEvent.click(screen.getByRole('button', { name: 'Sắp xếp theo Tuổi' }));
    const bodyRows = within(table).getAllByRole('row').slice(1);
    expect(bodyRows[0]).toHaveTextContent('Bạch Long');
  });
});

describe('DataTable cells (round 3 finding)', () => {
  test('a cell keeps its DOM node, and what was typed in it, when the page re-renders', async () => {
    function Host() {
      const [n, setN] = useState(0);
      return (
        <>
          <button onClick={() => setN(n + 1)}>lần {n}</button>
          {/* The column function is new on every render, as in every page. */}
          <DataTable
            data={[{ id: 1 }]}
            rowKey={(r) => r.id}
            columns={[{ id: 'note', header: 'Ghi chú', cell: () => <input aria-label="ghi chú" /> }]}
          />
        </>
      );
    }
    render(<Host />);
    const before = screen.getByLabelText('ghi chú');
    await userEvent.type(before, 'đi nước kiệu');
    await userEvent.click(screen.getByRole('button', { name: /lần/ }));
    const after = screen.getByLabelText('ghi chú');
    expect(after).toBe(before);
    expect(after).toHaveValue('đi nước kiệu');
  });
});

describe('DataTable phone-card labels (task 2.5)', () => {
  // The card layout is CSS (below 1280 px each cell shows its own term); jsdom has no layout, so this checks the
  // markup the CSS reads: a data-label per cell, the shorter cardLabel when given, none for the title and actions.
  test('cells carry the term the card shows', () => {
    render(
      <DataTable
        data={[{ id: 1 }]}
        rowKey={(r) => r.id}
        columns={[
          { id: 'name', header: 'Tên ngựa', primary: true, cell: () => 'Sao Mai' },
          { id: 'reg', header: 'Số đăng ký / microchip', cardLabel: 'Số đăng ký', cell: () => 'VN-1' },
          { id: 'sex', header: 'Giới tính', cell: () => 'Cái' },
          { id: 'badge', header: 'Tình trạng', hideLabelOnMobile: true, cell: () => 'Sẵn sàng' },
          { id: 'act', header: 'Thao tác', actions: true, cell: () => <button>Mở</button> },
        ]}
      />,
    );
    const cell = (text: string) => screen.getByText(text).closest('td')!;
    expect(cell('Sao Mai')).not.toHaveAttribute('data-label');
    expect(cell('VN-1')).toHaveAttribute('data-label', 'Số đăng ký');
    expect(cell('Cái')).toHaveAttribute('data-label', 'Giới tính');
    expect(cell('Sẵn sàng')).not.toHaveAttribute('data-label');
    expect(screen.getByRole('button', { name: 'Mở' }).closest('td')).not.toHaveAttribute('data-label');
  });
});

describe('kit chart (task 2.6)', () => {
  test('values are readable from the hidden table, with marked (simulated) points named', () => {
    render(
      <Chart
        kind="area"
        title="Xu hướng phong độ"
        categories={['01/10', '08/10']}
        series={[{ name: 'Phong độ', data: [3, 4], marked: [false, true] }]}
        markedLabel="Mô phỏng"
      />,
    );
    const table = screen.getByRole('table', { name: 'Xu hướng phong độ' });
    const rows = within(table).getAllByRole('row');
    expect(rows[1]).toHaveTextContent('01/10');
    expect(rows[1]).toHaveTextContent('3');
    expect(rows[2]).toHaveTextContent('Mô phỏng');
  });
});

describe('typed inputs (input-validation)', () => {
  test('NumberInput carries its range, step and unit', () => {
    render(<NumberInput aria-label="Cân nặng" min={100} max={1500} unit="kg" value="450" onChange={() => undefined} />);
    const input = screen.getByLabelText('Cân nặng');
    expect(input).toHaveAttribute('type', 'number');
    expect(input).toHaveAttribute('min', '100');
    expect(input).toHaveAttribute('max', '1500');
    expect(input).toHaveAttribute('step', 'any');
    expect(input).toHaveAttribute('data-unit', 'kg');
    expect(screen.getByText('kg')).toBeInTheDocument();
  });

  test('NumberInput with integer takes step 1 and reports the typed text', async () => {
    const onChange = vitest.fn();
    render(<NumberInput aria-label="Khoảng" integer min={1} max={9} value="" onChange={onChange} />);
    const input = screen.getByLabelText('Khoảng');
    expect(input).toHaveAttribute('step', '1');
    await userEvent.type(input, '4');
    expect(onChange).toHaveBeenCalledWith('4');
  });

  test('DateInput has plausible bounds unless the page narrows them', () => {
    render(
      <>
        <DateInput aria-label="Mặc định" value="" onChange={() => undefined} />
        <DateInput aria-label="Ngày sinh" max="2026-10-31" value="" onChange={() => undefined} />
      </>,
    );
    expect(screen.getByLabelText('Mặc định')).toHaveAttribute('data-min', '1990-01-01');
    expect(screen.getByLabelText('Mặc định')).toHaveAttribute('data-max', '2100-12-31');
    expect(screen.getByLabelText('Ngày sinh')).toHaveAttribute('data-max', '2026-10-31');
  });

  test('a text field and a textarea have a length limit unless the page sets the service\'s own', () => {
    render(
      <>
        <Input aria-label="Tên" />
        <Input aria-label="Tên ngựa" maxLength={200} />
        <Textarea aria-label="Mô tả" />
        <Input aria-label="Mật khẩu" type="password" />
      </>,
    );
    expect(screen.getByLabelText('Tên')).toHaveAttribute('maxlength', '255');
    expect(screen.getByLabelText('Tên ngựa')).toHaveAttribute('maxlength', '200');
    expect(screen.getByLabelText('Mô tả')).toHaveAttribute('maxlength', '2000');
    expect(screen.getByLabelText('Mật khẩu')).not.toHaveAttribute('maxlength');
  });
});

describe('detail popup never opens as an empty frame (BR-198, AC-395, AC-398)', () => {
  async function open(props: Partial<React.ComponentProps<typeof DetailPopup>>) {
    render(
      <DetailPopup title="Xu hướng" subject="Sao Mai" {...props}>
        <p>Nội dung</p>
      </DetailPopup>,
    );
    await userEvent.click(screen.getByRole('button', { name: /Xu hướng — Sao Mai/ }));
    return screen.getByRole('dialog');
  }

  test('AC-395 empty: the popup says what is empty', async () => {
    const dialog = await open({ empty: true, emptyText: 'Chưa có dữ liệu xu hướng.' });
    expect(within(dialog).getByText('Chưa có dữ liệu xu hướng.')).toBeInTheDocument();
    expect(within(dialog).queryByText('Nội dung')).not.toBeInTheDocument();
  });

  test('AC-398 loading shows a loading state', async () => {
    const dialog = await open({ loading: true });
    expect(within(dialog).getByLabelText('Đang tải…')).toBeInTheDocument();
    expect(within(dialog).queryByText('Nội dung')).not.toBeInTheDocument();
  });

  test('AC-398 failure shows the error with a retry', async () => {
    const onRetry = vitest.fn();
    const dialog = await open({ error: 'Mất kết nối', onRetry });
    expect(within(dialog).getByText('Mất kết nối')).toBeInTheDocument();
    await userEvent.click(within(dialog).getByRole('button', { name: 'Thử lại' }));
    expect(onRetry).toHaveBeenCalled();
  });

  test('with content the children show', async () => {
    expect(within(await open({})).getByText('Nội dung')).toBeInTheDocument();
  });
});

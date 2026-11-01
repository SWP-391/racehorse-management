import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter, useSearchParams } from 'react-router-dom';
import { useTabParam } from './useTabParam';

const TABS = ['overview', 'list'] as const;

function Probe() {
  const [tab, setTab] = useTabParam(TABS);
  const [params] = useSearchParams();
  return (
    <>
      <p>tab={tab}</p>
      <p>url={params.toString()}</p>
      <button onClick={() => setTab('list')}>list</button>
      <button onClick={() => setTab('overview')}>overview</button>
    </>
  );
}

describe('useTabParam (BR-209, design D7)', () => {
  test('the first tab is the default and leaves no parameter; another tab is kept in the URL next to the filters', async () => {
    render(<MemoryRouter initialEntries={['/health?status=INJURED']}><Probe /></MemoryRouter>);
    expect(screen.getByText('tab=overview')).toBeInTheDocument();
    await userEvent.click(screen.getByText('list'));
    expect(screen.getByText('tab=list')).toBeInTheDocument();
    expect(screen.getByText(/url=.*status=INJURED/)).toBeInTheDocument();
    expect(screen.getByText(/url=.*tab=list/)).toBeInTheDocument();
    await userEvent.click(screen.getByText('overview'));
    expect(screen.getByText(/url=status=INJURED$/)).toBeInTheDocument();
  });

  test('an unknown value gives the first tab', () => {
    render(<MemoryRouter initialEntries={['/health?tab=nope']}><Probe /></MemoryRouter>);
    expect(screen.getByText('tab=overview')).toBeInTheDocument();
  });

  test('a link to a tab opens it', () => {
    render(<MemoryRouter initialEntries={['/health?tab=list']}><Probe /></MemoryRouter>);
    expect(screen.getByText('tab=list')).toBeInTheDocument();
  });
});

import { Pagination } from './ui/data-table';

/** Numbered pages for a list inside a panel or dialog (BR-185); nothing shows while one page holds every row. */
export function PagerBar({ page, size, total, onPage }: { page: number; size: number; total: number; onPage: (page: number) => void }) {
  if (total <= size) return null;
  const pages = Math.ceil(total / size);
  return (
    <div className="mt-3">
      <Pagination page={page + 1} total={pages} onPage={(n) => onPage(n - 1)} />
    </div>
  );
}

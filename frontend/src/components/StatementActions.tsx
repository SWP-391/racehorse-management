import { useState } from 'react';
import { Link } from 'react-router-dom';
import { Download, Printer } from 'lucide-react';
import { saveFile } from '../api/client';
import { vi } from '../i18n/vi';
import { notify } from '../lib/notify';
import { Button } from './ui/button';

/** Downloads a file through the authenticated client; the name comes from the service (BR-204). */
export function ExportButton({ run, label = vi.billing.exportExcel, ariaLabel }: { run: () => Promise<{ blob: Blob; name: string }>; label?: string; ariaLabel?: string }) {
  const [busy, setBusy] = useState(false);
  async function click() {
    setBusy(true);
    try {
      saveFile(await run());
    } catch (e) {
      notify.error(e);
    } finally {
      setBusy(false);
    }
  }
  return (
    <Button size="sm" variant="outline" loading={busy} onClick={() => void click()} aria-label={ariaLabel}>
      <Download aria-hidden />
      {label}
    </Button>
  );
}

/** Opens the print view of an invoice or a statement (D5). */
export function PrintLink({ to, ariaLabel }: { to: string; ariaLabel?: string }) {
  return (
    <Button asChild size="sm" variant="outline">
      <Link to={to} aria-label={ariaLabel}>
        <Printer aria-hidden />
        {vi.billing.print}
      </Link>
    </Button>
  );
}

import { useState, type FormEvent } from 'react';
import { CircleAlert, ShieldOff } from 'lucide-react';
import { api } from '../api/api';
import { useAuth } from '../auth/AuthContext';
import { messageOf } from '../components/ErrorText';
import { vi } from '../i18n/vi';
import { useLoad } from '../hooks/useLoad';
import { notify } from '../lib/notify';
import { Button } from '../components/ui/button';
import { DataView } from '../components/ui/data-view';
import { PageHeader, Section } from '../components/ui/display';
import { FormField } from '../components/ui/form-field';
import { Input } from '../components/ui/input';
import { Callout, DetailSkeleton, EmptyState } from '../components/ui/states';

interface NumberSettingProps {
  title: string;
  description: string;
  label: string;
  value: number;
  step?: string;
  /** Unit shown under the field (phút, giờ). */
  unit: string;
  /** Saves the typed number and returns the value the service kept. */
  save: (n: number) => Promise<number>;
}

/** One setting: a Section with a short description and a one-field form; the service's refusal keeps the saved value. */
function NumberSetting({ title, description, label, value, step, unit, save }: NumberSettingProps) {
  const [text, setText] = useState(String(value));
  const [saved, setSaved] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [pending, setPending] = useState(false);

  async function submit(e: FormEvent) {
    e.preventDefault();
    setError(null);
    setSaved(false);
    setPending(true);
    try {
      // Sent as typed, so 1.5 or 0 is refused by the service and the saved value stays (AC-205).
      setText(String(await save(Number(text))));
      setSaved(true);
      notify.success(vi.common.saved);
    } catch (err) {
      setError(messageOf(err));
    } finally {
      setPending(false);
    }
  }

  return (
    <Section title={title} description={description}>
      <form onSubmit={submit} noValidate aria-label={label} className="grid max-w-md gap-4">
        {error && (
          <Callout tone="danger" icon={CircleAlert} role="alert">
            {error}
          </Callout>
        )}
        <FormField label={label} description={unit}>
          <Input type="number" step={step} value={text} onChange={(e) => setText(e.target.value)} />
        </FormField>
        <div className="flex flex-col gap-3 sm:flex-row sm:items-center">
          <Button type="submit" variant="outline" loading={pending} className="w-full sm:w-auto">
            {vi.common.save}
          </Button>
          {saved && (
            <span role="status" className="text-meta text-status-eligible-ink">
              ✓ {vi.common.saved}
            </span>
          )}
        </div>
      </form>
    </Section>
  );
}

/**
 * Club Manager settings: grace period before a forgotten session is auto-closed (BR-094) and the late-change threshold
 * for responsible persons (BR-123, PERM-034) and the notice a leave needs to count as planned (BR-167, PERM-054) and the days of cover that turn a supply
 * item to "Cần nhập" (BR-175).
 */
export default function SettingsPage() {
  const { can } = useAuth();
  const grace = can('CONFIGURE_GRACE_PERIOD');
  const threshold = can('CONFIGURE_LATE_CHANGE_THRESHOLD');
  const leaveNotice = can('CONFIGURE_THRESHOLDS');
  const graceLoad = useLoad(() => api.gracePeriod(), [], grace);
  const thresholdLoad = useLoad(() => api.lateChangeThreshold(), [], threshold);
  const leaveNoticeLoad = useLoad(() => api.leaveNotice(), [], leaveNotice);
  // The same permission (PERM-054) covers the stock warning threshold (BR-175).
  const stockWarningLoad = useLoad(() => api.stockWarning(), [], leaveNotice);

  if (!grace && !threshold && !leaveNotice) {
    return (
      <div role="alert">
        <EmptyState icon={ShieldOff} title={vi.common.accessDenied} className="mt-8" />
      </div>
    );
  }

  return (
    <>
      <PageHeader title={vi.training.settings} description={vi.settings.subtitle} />
      <div className="grid gap-8">
        {grace && (
          <DataView load={graceLoad} skeleton={<DetailSkeleton />}>
            {(g) => (
              <NumberSetting
                title={vi.settings.graceTitle}
                description={vi.settings.graceHint}
                label={vi.training.gracePeriod}
                value={g.minutes}
                unit={vi.settings.unitMinutes}
                save={async (n) => (await api.setGracePeriod(n)).minutes}
              />
            )}
          </DataView>
        )}
        {threshold && (
          <DataView load={thresholdLoad} skeleton={<DetailSkeleton />}>
            {(t) => (
              <NumberSetting
                title={vi.settings.thresholdTitle}
                description={vi.settings.thresholdHint}
                label={vi.training.lateChangeThreshold}
                value={t.hours}
                step="any"
                unit={vi.settings.unitHours}
                save={async (n) => (await api.setLateChangeThreshold(n)).hours}
              />
            )}
          </DataView>
        )}
        {leaveNotice && (
          <DataView load={leaveNoticeLoad} skeleton={<DetailSkeleton />}>
            {(l) => (
              <NumberSetting
                title={vi.settings.leaveNoticeTitle}
                description={vi.settings.leaveNoticeHint}
                label={vi.settings.leaveNoticeLabel}
                value={l.days}
                unit={vi.settings.unitDays}
                save={async (n) => (await api.setLeaveNotice(n)).days}
              />
            )}
          </DataView>
        )}
        {leaveNotice && (
          <DataView load={stockWarningLoad} skeleton={<DetailSkeleton />}>
            {(w) => (
              <NumberSetting
                title={vi.settings.stockWarningTitle}
                description={vi.settings.stockWarningHint}
                label={vi.settings.stockWarningLabel}
                value={w.days}
                unit={vi.settings.unitDays}
                save={async (n) => (await api.setStockWarning(n)).days}
              />
            )}
          </DataView>
        )}
      </div>
    </>
  );
}

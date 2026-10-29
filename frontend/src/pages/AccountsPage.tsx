import { useState, type FormEvent } from 'react';
import { Ban, CalendarClock, CalendarOff, CircleAlert, CircleCheck, ShieldOff, Users } from 'lucide-react';
import { api } from '../api/api';
import { ApiError } from '../api/client';
import { ROLES, type Account, type Role, type ScheduleRange } from '../api/types';
import { useAuth } from '../auth/AuthContext';
import { vi } from '../i18n/vi';
import { usePagedList } from '../hooks/usePagedList';
import { notify } from '../lib/notify';
import { GroomScheduleDialog } from '../components/GroomScheduleDialog';
import { LeaveDialog } from '../components/LeaveDialog';
import { ScheduleEditor, toServiceRanges } from '../components/ScheduleEditor';
import { Button } from '../components/ui/button';
import { ConfirmButton } from '../components/ui/confirm-button';
import { DataTable, type DataColumn } from '../components/ui/data-table';
import { DataView } from '../components/ui/data-view';
import { Badge, PageHeader, Section } from '../components/ui/display';
import { FormField } from '../components/ui/form-field';
import { Input } from '../components/ui/input';
import { ListFooter, ListToolbar } from '../components/ui/list-toolbar';
import { Select } from '../components/ui/select';
import { Callout, EmptyState } from '../components/ui/states';

const EMPTY_FORM = { username: '', displayName: '', password: '', role: '' as Role | '' };

/**
 * Club Manager: create accounts with exactly one role, change role, deactivate (BR-090); a Groom gets fixed weekly
 * barn hours when created or later, and the Club Manager records leave (BR-166, BR-167).
 */
export default function AccountsPage() {
  const { can, me } = useAuth();
  const allowed = can('MANAGE_ACCOUNTS');
  const list = usePagedList((query) => api.accounts(query), { filters: ['role'], enabled: allowed });
  const [error, setError] = useState<string | null>(null);
  const [fieldErrors, setFieldErrors] = useState<Record<string, string>>({});
  const [form, setForm] = useState(EMPTY_FORM);
  const [pending, setPending] = useState(false);
  const [schedule, setSchedule] = useState<ScheduleRange[]>([]);
  const [scheduleOf, setScheduleOf] = useState<Account | null>(null);
  const [leaveOf, setLeaveOf] = useState<Account | null>(null);

  if (!allowed) {
    return (
      <div role="alert">
        <EmptyState icon={ShieldOff} title={vi.common.accessDenied} className="mt-8" />
      </div>
    );
  }

  function report(e: unknown) {
    if (e instanceof ApiError) {
      setError(e.message);
      setFieldErrors(e.fields);
    } else {
      setError(vi.common.unexpectedError);
    }
  }

  async function create(e: FormEvent) {
    e.preventDefault();
    setError(null);
    setFieldErrors({});
    setPending(true);
    try {
      await api.createAccount({
        username: form.username,
        displayName: form.displayName,
        password: form.password,
        roles: form.role ? [form.role] : [],
        schedule: form.role === 'GROOM' && schedule.length > 0 ? toServiceRanges(schedule) : undefined,
      });
      setForm(EMPTY_FORM);
      setSchedule([]);
      notify.success(vi.common.saved);
      await list.load.reload();
    } catch (err) {
      report(err);
    } finally {
      setPending(false);
    }
  }

  async function changeRole(account: Account, role: Role) {
    setError(null);
    setFieldErrors({});
    try {
      await api.changeRole(account.id, [role]);
      notify.success(vi.common.saved);
      await list.load.reload();
    } catch (err) {
      report(err);
    }
  }

  async function deactivate(account: Account) {
    setError(null);
    setFieldErrors({});
    try {
      await api.deactivate(account.id);
      notify.success(vi.common.saved);
      await list.load.reload();
    } catch (err) {
      report(err);
    }
  }

  const columns: DataColumn<Account>[] = [
    { id: 'name', header: vi.accounts.displayName, primary: true, sortValue: (a) => a.displayName, cell: (a) => a.displayName },
    { id: 'username', fit: true, header: vi.accounts.username, sortValue: (a) => a.username, cell: (a) => a.username },
    {
      id: 'role',
      header: vi.accounts.role,
      hideLabelOnMobile: true,
      cell: (a) =>
        a.role === 'CLUB_MANAGER' ? (
          // The Club Manager is the highest role and is never changed through the system (owner's rule, P-06).
          <>
            <Badge tone="outline">{vi.roles[a.role]}</Badge>
            <span className="mt-1 block text-meta text-muted-foreground">{vi.accounts.managerRoleLocked}</span>
          </>
        ) : a.active ? (
          <>
            {/* The phone card's term sits above the full-width select, so the role is never truncated. */}
            <span className="mb-1 block text-meta text-muted-foreground xl:hidden">{vi.accounts.role}</span>
            <Select
              aria-label={`${vi.accounts.changeRole} ${a.username}`}
              className="w-full xl:w-40"
              value={a.role}
              onChange={(e) => void changeRole(a, e.target.value as Role)}
            >
              {ROLES.map((r) => (
                <option key={r} value={r}>
                  {vi.roles[r]}
                </option>
              ))}
            </Select>
          </>
        ) : (
          <Badge tone="outline">{vi.roles[a.role]}</Badge>
        ),
    },
    {
      id: 'hours', fit: true,
      header: vi.schedule.title,
      cell: (a) =>
        a.role !== 'GROOM' ? null : a.hasSchedule === false ? (
          <Badge tone="watch" icon={<CircleAlert aria-hidden />}>
            {vi.schedule.noneBadge}
          </Badge>
        ) : null,
    },
    {
      id: 'status', fit: true,
      header: vi.accounts.status,
      cell: (a) =>
        a.active ? (
          <Badge tone="eligible" icon={<CircleCheck aria-hidden />}>
            {vi.accounts.active}
          </Badge>
        ) : (
          <Badge tone="outline" icon={<Ban aria-hidden />}>
            {vi.accounts.inactive}
          </Badge>
        ),
    },
    {
      id: 'actions', fit: true,
      header: '',
      actions: true,
      cell: (a) => (
        <>
          {a.role === 'GROOM' && a.active && (
            <>
              <Button type="button" variant="outline" size="sm" aria-label={vi.schedule.editFor(a.displayName)} onClick={() => setScheduleOf(a)}>
                <CalendarClock aria-hidden />
                {vi.schedule.edit}
              </Button>
              <Button type="button" variant="outline" size="sm" aria-label={vi.leave.buttonFor(a.displayName)} onClick={() => setLeaveOf(a)}>
                <CalendarOff aria-hidden />
                {vi.leave.button}
              </Button>
            </>
          )}
          {a.active && a.id !== me?.id && (
            <ConfirmButton
              destructive
              title={vi.accounts.deactivateTitle(a.displayName)}
              description={vi.accounts.deactivateText}
              confirmLabel={vi.accounts.deactivate}
              onConfirm={() => deactivate(a)}
            >
              <Ban aria-hidden />
              {vi.accounts.deactivate}
            </ConfirmButton>
          )}
        </>
      ),
    },
  ];

  // The refusal shows next to the field when the service names one; the banner is for refusals without a field.
  const banner = error && Object.keys(fieldErrors).length === 0;

  return (
    <>
      <PageHeader title={vi.accounts.title} description={vi.accounts.subtitle} />
      <div className="grid gap-8">
        {banner && (
          <Callout tone="danger" icon={CircleAlert} role="alert">
            {error}
          </Callout>
        )}
        <Section title={vi.accounts.create}>
          <form onSubmit={create} noValidate aria-label={vi.accounts.create} className="grid max-w-3xl gap-5">
            <div className="grid gap-5 md:grid-cols-2">
              <FormField label={vi.accounts.username} error={fieldErrors.username}>
                <Input value={form.username} onChange={(e) => setForm({ ...form, username: e.target.value })} />
              </FormField>
              <FormField label={vi.accounts.displayName} error={fieldErrors.displayName}>
                <Input value={form.displayName} onChange={(e) => setForm({ ...form, displayName: e.target.value })} />
              </FormField>
              <FormField label={vi.accounts.password} error={fieldErrors.password}>
                <Input type="password" value={form.password} onChange={(e) => setForm({ ...form, password: e.target.value })} />
              </FormField>
              {/* A single select: one role per person (BR-090). */}
              <FormField label={vi.accounts.role} description={vi.accounts.oneRoleOnly} error={fieldErrors.roles}>
                <Select value={form.role} onChange={(e) => setForm({ ...form, role: e.target.value as Role })}>
                  <option value="">—</option>
                  {ROLES.map((r) => (
                    <option key={r} value={r}>
                      {vi.roles[r]}
                    </option>
                  ))}
                </Select>
              </FormField>
            </div>
            {form.role === 'GROOM' && (
              <fieldset className="grid gap-2">
                <legend className="text-sm font-medium">{vi.accounts.scheduleOfGroom}</legend>
                <p className="text-meta text-muted-foreground">{vi.accounts.scheduleOptional}</p>
                <ScheduleEditor value={schedule} onChange={setSchedule} errors={fieldErrors} />
                {fieldErrors.schedule && (
                  <p role="alert" className="text-meta text-status-injured-ink">
                    {fieldErrors.schedule}
                  </p>
                )}
              </fieldset>
            )}
            <div className="flex sm:justify-end">
              <Button type="submit" loading={pending} className="w-full sm:w-auto">
                {vi.accounts.create}
              </Button>
            </div>
          </form>
        </Section>
        <Section title={vi.accounts.list}>
          <div className="mb-4">
            <ListToolbar
              query={list.query}
              onQuery={list.setQuery}
              placeholder={vi.accounts.searchPlaceholder}
              total={list.load.state.data?.total}
            >
              <Select
                aria-label={vi.accounts.roleFilter}
                value={list.filters.role ?? ''}
                onChange={(e) => list.setFilter('role', e.target.value)}
                className="w-48"
              >
                <option value="">{vi.accounts.anyRole}</option>
                {ROLES.map((r) => (
                  <option key={r} value={r}>
                    {vi.roles[r]}
                  </option>
                ))}
              </Select>
            </ListToolbar>
          </div>
          <DataView
            load={list.load}
            isEmpty={(data) => data.items.length === 0}
            empty={
              list.narrowed ? (
                <EmptyState icon={Users} title={vi.common.noResults} text={vi.common.noResultsHint} />
              ) : (
                <EmptyState icon={Users} title={vi.accounts.empty} text={vi.accounts.emptyHint} />
              )
            }
          >
            {(data) => (
              <div className="grid gap-3">
                <DataTable caption={vi.accounts.list} data={data.items} columns={columns} rowKey={(a) => a.id} />
                <ListFooter data={data} onPage={list.setPage} onSize={list.setSize} />
              </div>
            )}
          </DataView>
        </Section>
      </div>
      <GroomScheduleDialog groom={scheduleOf} onClose={() => setScheduleOf(null)} onSaved={() => void list.load.reload()} />
      <LeaveDialog groom={leaveOf} onClose={() => setLeaveOf(null)} />
    </>
  );
}

import { useState, type FormEvent } from 'react';
import { ScrollText, ShieldOff } from 'lucide-react';
import { api } from '../api/api';
import { useAuth } from '../auth/AuthContext';
import { vi } from '../i18n/vi';
import { useLoad } from '../hooks/useLoad';
import { usePagedList } from '../hooks/usePagedList';
import { Button } from '../components/ui/button';
import { DataTable, type DataColumn } from '../components/ui/data-table';
import { DataView } from '../components/ui/data-view';
import { PageHeader } from '../components/ui/display';
import { FormField } from '../components/ui/form-field';
import { Input } from '../components/ui/input';
import { ListFooter, ListToolbar } from '../components/ui/list-toolbar';
import { SearchSelect, type Chosen } from '../components/ui/search-select';
import { EmptyState } from '../components/ui/states';
import type { AuditEntry } from '../api/types';

// Enum codes in the details (role, lock kind, return level, status, session type) read in Vietnamese through the
// tables the screens already use; anything else is shown as it came.
const CODE_LABELS: Record<string, string>[] = [
  vi.roles,
  vi.health.lockKinds,
  vi.health.returnLevels,
  vi.health.statuses,
  vi.training.types,
  vi.injury.statuses,
  vi.chores.names,
  vi.supplies.categories,
];
const isCode = (v: string) => /^[A-Z][A-Z0-9_]+$/.test(v);
const label = (v: string) => (isCode(v) ? (CODE_LABELS.map((t) => t[v]).find(Boolean) ?? v) : v);
/** Internal record ids (examinationId, horseId, lockId, markId …) mean nothing to a reader: the target column names the object. */
const readable = (key: string) => !/Id$/.test(key);
/** `region` is the stable code; when the entry also carries `regionName` (the catalog name at that time), that is the one shown. */
const shown = (data: Record<string, unknown>) =>
  Object.entries(data).filter(([k]) => readable(k) && !(k === 'region' && 'regionName' in data));

/** Names for the codes and ids the log stores: the user ids of a responsible-person change. */
type Lookup = { user: (id: number) => string };

/** One value of the details JSON as text: a {from,to} pair reads "từ → đến", other objects are flattened. */
function show(v: unknown, lookup: Lookup, userIds = false): string {
  if (v && typeof v === 'object') {
    const o = v as Record<string, unknown>;
    if ('from' in o && 'to' in o) return `${show(o.from, lookup, userIds)} → ${show(o.to, lookup, userIds)}`;
    return shown(o)
      .map(([k, x]) => `${vi.audit.keys[k] ?? k}: ${show(x, lookup)}`)
      .join(', ');
  }
  if (v === null || v === undefined) return vi.common.none;
  if (typeof v === 'boolean') return v ? vi.common.yes : vi.common.no;
  if (userIds && typeof v === 'number') return lookup.user(v);
  return typeof v === 'string' ? label(v) : String(v);
}

/** The details JSON as `key: value` lines; unreadable text is shown as it came. */
function Details({ text, action, lookup }: { text: string; action: string; lookup: Lookup }) {
  let data: unknown;
  try {
    data = JSON.parse(text);
  } catch {
    return <span className="break-words">{text}</span>;
  }
  if (!data || typeof data !== 'object' || Array.isArray(data)) return <span className="break-words">{text}</span>;
  const rows = shown(data as Record<string, unknown>);
  if (rows.length === 0) return <>{vi.common.none}</>;
  return (
    <div className="grid gap-0.5">
      {rows.map(([k, v]) => (
        <div key={k} className="break-words">
          <span className="text-muted-foreground">{vi.audit.keys[k] ?? k}:</span> {show(v, lookup, action === 'SESSION_RESPONSIBLE_CHANGED' && (k === 'from' || k === 'to'))}
        </div>
      ))}
    </div>
  );
}

/** Read-only audit log for the Club Manager, filtered by date range and user (BR-093). No edit controls. */
export default function AuditPage() {
  const { can } = useAuth();
  const allowed = can('VIEW_AUDIT_LOG');
  const list = usePagedList((query) => api.audit(query), { filters: ['from', 'to', 'actorId'], enabled: allowed });
  // The form fields are drafts until "Lọc" is pressed; the applied filters live in the URL.
  const [draft, setDraft] = useState({ from: list.filters.from ?? '', to: list.filters.to ?? '' });
  const [actor, setActor] = useState<Chosen | null>(null);
  // Names for the user ids that the log's details carry (a responsible-person change); a name not found shows its id.
  const users = useLoad(() => api.accounts({ size: 100 }).then((p) => p.items).catch(() => []), [], allowed);
  const lookup: Lookup = {
    user: (id) => users.state.data?.find((u) => u.id === id)?.displayName ?? `#${id}`,
  };
  const actorId = list.filters.actorId ? Number(list.filters.actorId) : null;
  const actorShown: Chosen | null = actorId ? (actor?.id === actorId ? actor : { id: actorId, label: lookup.user(actorId) }) : null;

  if (!allowed) {
    return (
      <div role="alert">
        <EmptyState icon={ShieldOff} title={vi.common.accessDenied} className="mt-8" />
      </div>
    );
  }

  function submit(e: FormEvent) {
    e.preventDefault();
    list.setFilters({ from: draft.from, to: draft.to });
  }

  // No sortable headers: the log is read in the order the service returns it, and has no controls (AC-026).
  const columns: DataColumn<AuditEntry>[] = [
    { id: 'time', header: vi.audit.time, className: 'whitespace-nowrap', cell: (e) => <span className="tabular whitespace-nowrap">{e.occurredAtClub}</span> },
    { id: 'actor', header: vi.audit.actor, cell: (e) => e.actorName },
    {
      id: 'action',
      header: vi.audit.action,
      primary: true,
      cell: (e) => (
        <span className="grid gap-0.5">
          <span className="font-medium">{vi.audit.actions[e.action] ?? e.action}</span>
          {vi.audit.actions[e.action] && <span className="font-mono text-meta text-muted-foreground">{e.action}</span>}
        </span>
      ),
    },
    {
      id: 'target',
      header: vi.audit.target,
      cell: (e) => `${vi.audit.targets[e.targetType] ?? e.targetType}${e.targetId != null ? ` #${e.targetId}` : ''}`,
    },
    { id: 'details', header: vi.audit.details, cell: (e) => <Details text={e.details} action={e.action} lookup={lookup} /> },
  ];

  return (
    <>
      <PageHeader title={vi.audit.title} description={vi.audit.subtitle} />
      <form
        onSubmit={submit}
        aria-label={vi.audit.filter}
        className="mb-6 grid gap-4 sm:grid-cols-2 lg:grid-cols-[repeat(3,minmax(0,1fr))_auto] lg:items-end"
      >
        <FormField label={vi.audit.from}>
          <Input type="date" value={draft.from} onChange={(e) => setDraft({ ...draft, from: e.target.value })} />
        </FormField>
        <FormField label={vi.audit.to}>
          <Input type="date" value={draft.to} onChange={(e) => setDraft({ ...draft, to: e.target.value })} />
        </FormField>
        <FormField label={vi.audit.actor}>
          <SearchSelect
            value={actorShown}
            placeholder={vi.audit.all}
            clearable
            load={(q) => api.options('accounts', { q })}
            onChange={(chosen) => {
              setActor(chosen);
              list.setFilter('actorId', chosen ? String(chosen.id) : '');
            }}
          />
        </FormField>
        <Button type="submit" variant="outline" loading={list.load.state.status === 'loading' && list.load.state.data !== null}>
          {vi.audit.filter}
        </Button>
      </form>
      <div className="mb-4">
        <ListToolbar query={list.query} onQuery={list.setQuery} placeholder={vi.audit.searchPlaceholder} total={list.load.state.data?.total} />
      </div>
      <DataView
        load={list.load}
        isEmpty={(data) => data.items.length === 0}
        empty={
          list.narrowed ? (
            <EmptyState icon={ScrollText} title={vi.common.noResults} text={vi.common.noResultsHint} />
          ) : (
            <EmptyState icon={ScrollText} title={vi.audit.empty} text={vi.audit.emptyHint} />
          )
        }
      >
        {(data) => (
          <div className="grid gap-3">
            <DataTable caption={vi.audit.title} data={data.items} columns={columns} rowKey={(e) => e.id} />
            <ListFooter data={data} onPage={list.setPage} onSize={list.setSize} />
          </div>
        )}
      </DataView>
    </>
  );
}

import { useState, type FormEvent } from 'react';
import { BellRing, CircleAlert, CircleCheck, ClipboardList, Eye, ImageIcon, ShieldOff } from 'lucide-react';
import { api } from '../api/api';
import type { Incident } from '../api/types';
import { useAuth } from '../auth/AuthContext';
import { fieldsOf, messageOf } from '../components/ErrorText';
import { RegionSelect, regionLabel, useBodyRegions } from '../components/BodyRegions';
import { vi } from '../i18n/vi';
import { usePagedList } from '../hooks/usePagedList';
import { notify } from '../lib/notify';
import { Button } from '../components/ui/button';
import { ConfirmButton } from '../components/ui/confirm-button';
import { LongText } from '../components/ui/long-text';
import { Dialog, DialogContent, DialogDescription, DialogHeader, DialogTitle } from '../components/ui/dialog';
import { DataTable, type DataColumn } from '../components/ui/data-table';
import { DataView } from '../components/ui/data-view';
import { Badge, PageHeader, Section } from '../components/ui/display';
import { FormField } from '../components/ui/form-field';
import { FileInput } from '../components/ui/file-input';
import { Input, Textarea } from '../components/ui/input';
import { ListFooter, ListToolbar } from '../components/ui/list-toolbar';
import { Select } from '../components/ui/select';
import { SearchSelect, type Chosen } from '../components/ui/search-select';
import { Callout, EmptyState } from '../components/ui/states';

const STATE_BADGE = {
  NEW: { tone: 'watch', icon: BellRing },
  ACKNOWLEDGED: { tone: 'neutral', icon: Eye },
  HANDLED: { tone: 'eligible', icon: CircleCheck },
} as const;

function StateBadge({ state }: { state: string }) {
  const s = STATE_BADGE[state as keyof typeof STATE_BADGE];
  const label = vi.incidents.states[state] ?? state;
  if (!s) return <Badge>{label}</Badge>;
  const Icon = s.icon;
  return (
    <Badge tone={s.tone} icon={<Icon aria-hidden />}>
      {label}
    </Badge>
  );
}

/**
 * Incident reports. A Groom picks a horse from the minimal list and sends a description, an optional body region from
 * the one catalog (BR-111) and up to 3 photos, and sees
 * the status of their own reports; a Veterinarian opens (acknowledges) and closes them (BR-070, BR-071).
 */
export default function IncidentsPage() {
  const { can } = useAuth();
  const send = can('SEND_INCIDENTS');
  const handle = can('HANDLE_INCIDENTS');
  const allowed = send || handle;
  const incidents = usePagedList((query) => api.incidents(query), { filters: ['state'], enabled: allowed });
  const [horse, setHorse] = useState<Chosen | null>(null);
  const [description, setDescription] = useState('');
  const [bodyRegion, setBodyRegion] = useState('');
  const [photos, setPhotos] = useState<File[]>([]);
  const [fileKey, setFileKey] = useState(0);
  const regions = useBodyRegions(allowed);
  const [closing, setClosing] = useState<{ id: number; note: string }>({ id: 0, note: '' });
  const [error, setError] = useState<string | null>(null);
  const [fieldErrors, setFieldErrors] = useState<Record<string, string>>({});
  const [pending, setPending] = useState<'send' | 'close' | null>(null);

  if (!allowed) {
    return (
      <div role="alert">
        <EmptyState icon={ShieldOff} title={vi.common.accessDenied} className="mt-8" />
      </div>
    );
  }

  async function submit(e: FormEvent) {
    e.preventDefault();
    setError(null);
    setFieldErrors({});
    setPending('send');
    try {
      await api.sendIncident(horse ? horse.id : '', description, photos, bodyRegion);
      setHorse(null);
      setDescription('');
      setBodyRegion('');
      setPhotos([]);
      setFileKey((k) => k + 1);
      notify.success(vi.common.saved);
      await incidents.load.reload();
    } catch (err) {
      setError(messageOf(err));
      setFieldErrors(fieldsOf(err));
    } finally {
      setPending(null);
    }
  }

  async function open(id: number) {
    setError(null);
    try {
      await api.openIncident(id);
      await incidents.load.reload();
    } catch (e) {
      setError(messageOf(e));
    }
  }

  async function close(e: FormEvent) {
    e.preventDefault();
    setError(null);
    setFieldErrors({});
    setPending('close');
    try {
      await api.closeIncident(closing.id, closing.note, null);
      setClosing({ id: 0, note: '' });
      await incidents.load.reload();
    } catch (err) {
      setError(messageOf(err));
      setFieldErrors(fieldsOf(err));
    } finally {
      setPending(null);
    }
  }

  const photoErrors = Object.entries(fieldErrors).filter(([k]) => k.startsWith('photos'));

  const columns: DataColumn<Incident>[] = [
    {
      id: 'horse',
      header: vi.incidents.horse,
      primary: true,
      cell: (r) => <span className="font-display text-[1.0625rem] font-medium">{r.horseName}</span>,
    },
    {
      id: 'description',
      header: vi.incidents.description,
      className: 'md:min-w-64 md:whitespace-normal',
      cell: (r) => (
        <>
          <LongText text={r.description} title={vi.incidents.description} subject={r.horseName} />{' '}
          {r.photoIds.map((p) => (
            <a
              key={p}
              href={`/api/incidents/${r.id}/photos/${p}`}
              target="_blank"
              rel="noreferrer"
              className="mr-3 inline-flex items-center gap-1 py-1 text-sm text-primary underline-offset-4 hover:underline"
            >
              <ImageIcon aria-hidden className="size-3.5" />
              {vi.incidents.photoLink(p)}
            </a>
          ))}
          {r.closingNote && <div className="mt-1 text-sm text-muted-foreground">{r.closingNote}</div>}
        </>
      ),
    },
    { id: 'region', header: vi.regions.label, cell: (r) => (r.bodyRegion ? regionLabel(regions, r.bodyRegion) : vi.common.none) },
    ...(handle ? [{ id: 'groom', header: vi.incidents.sentBy, cell: (r: Incident) => r.groom } satisfies DataColumn<Incident>] : []),
    { id: 'state', header: vi.incidents.status, cell: (r) => <StateBadge state={r.state} /> },
    ...(handle
      ? [
          {
            id: 'actions',
            header: '',
            actions: true,
            cell: (r: Incident) => (
              <>
                {r.state === 'NEW' && (
                  <ConfirmButton title={vi.incidents.open} description={vi.incidents.openText} confirmLabel={vi.incidents.open} onConfirm={() => open(r.id)}>
                    <Eye aria-hidden />
                    {vi.incidents.open}
                  </ConfirmButton>
                )}
                {r.state !== 'HANDLED' && (
                  <Button variant="outline" size="sm" onClick={() => setClosing({ id: r.id, note: '' })}>
                    <CircleCheck aria-hidden />
                    {vi.incidents.close}
                  </Button>
                )}
              </>
            ),
          } satisfies DataColumn<Incident>,
        ]
      : []),
  ];

  return (
    <>
      <PageHeader title={vi.incidents.title} />
      {error && (
        <Callout tone="danger" icon={CircleAlert} role="alert" className="mb-6">
          {error}
        </Callout>
      )}
      <div className="grid gap-10">
        {send && (
          <Section title={vi.incidents.newReport}>
            <form onSubmit={submit} className="grid max-w-2xl gap-5" noValidate aria-label={vi.incidents.send}>
              <div className="grid gap-5 md:grid-cols-2">
                <FormField label={vi.incidents.horse} error={fieldErrors.horseId}>
                  <SearchSelect
                    value={horse}
                    placeholder={vi.incidents.chooseHorse}
                    load={(q) => api.options('horses', { q })}
                    onChange={setHorse}
                  />
                </FormField>
                <RegionSelect regions={regions} value={bodyRegion} onChange={setBodyRegion} error={fieldErrors.bodyRegion} />
              </div>
              <FormField label={vi.incidents.description} error={fieldErrors.description}>
                <Textarea value={description} onChange={(e) => setDescription(e.target.value)} />
              </FormField>
              <FormField label={vi.incidents.photos}>
                <FileInput
                  key={fileKey}
                  buttonLabel={vi.incidents.choosePhoto}
                  accept="image/jpeg,image/png"
                  multiple
                  onChange={(e) => setPhotos(Array.from(e.target.files ?? []))}
                />
              </FormField>
              {photoErrors.map(([k, m]) => (
                <Callout key={k} tone="danger" icon={CircleAlert} role="alert">
                  {m}
                </Callout>
              ))}
              <div className="flex flex-col sm:flex-row sm:justify-end">
                <Button type="submit" loading={pending === 'send'}>
                  {vi.incidents.send}
                </Button>
              </div>
            </form>
          </Section>
        )}

        <Section title={vi.incidents.list}>
          <div className="mb-4">
            <ListToolbar
              query={incidents.query}
              onQuery={incidents.setQuery}
              placeholder={vi.incidents.searchPlaceholder}
              total={incidents.load.state.data?.total}
            >
              <Select
                aria-label={vi.incidents.status}
                value={incidents.filters.state ?? ''}
                onChange={(e) => incidents.setFilter('state', e.target.value)}
                className="w-48"
              >
                <option value="">{vi.incidents.anyState}</option>
                {Object.entries(vi.incidents.states).map(([code, text]) => (
                  <option key={code} value={code}>
                    {text}
                  </option>
                ))}
              </Select>
            </ListToolbar>
          </div>
          <DataView
            load={incidents.load}
            isEmpty={(data) => data.items.length === 0}
            empty={
              incidents.narrowed ? (
                <EmptyState icon={ClipboardList} title={vi.common.noResults} text={vi.common.noResultsHint} />
              ) : (
                <EmptyState icon={ClipboardList} title={vi.incidents.none} text={vi.incidents.noneHint} />
              )
            }
          >
            {(data) => (
              <div className="grid gap-3">
                <DataTable caption={vi.incidents.list} data={data.items} columns={columns} rowKey={(r) => r.id} />
                <ListFooter data={data} onPage={incidents.setPage} onSize={incidents.setSize} />
              </div>
            )}
          </DataView>

          <Dialog open={handle && closing.id !== 0} onOpenChange={(isOpen) => !isOpen && setClosing({ id: 0, note: '' })}>
            <DialogContent className="max-w-lg">
              <DialogHeader>
                <DialogTitle>{vi.incidents.close}</DialogTitle>
                <DialogDescription className="sr-only">{vi.incidents.close}</DialogDescription>
              </DialogHeader>
              <form onSubmit={close} className="grid gap-5" aria-label={vi.incidents.close} noValidate>
                <FormField label={vi.incidents.closingNote} error={fieldErrors.note}>
                  <Input value={closing.note} onChange={(e) => setClosing({ ...closing, note: e.target.value })} />
                </FormField>
                <div className="flex flex-col-reverse gap-2 sm:flex-row sm:justify-end">
                  <Button type="button" variant="outline" onClick={() => setClosing({ id: 0, note: '' })}>
                    {vi.common.cancel}
                  </Button>
                  <Button type="submit" loading={pending === 'close'}>
                    {vi.incidents.close}
                  </Button>
                </div>
              </form>
            </DialogContent>
          </Dialog>
        </Section>
      </div>
    </>
  );
}

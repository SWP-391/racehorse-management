import { useState, type FormEvent } from 'react';
import { CircleAlert, ClipboardList, Scale } from 'lucide-react';
import { api } from '../api/api';
import type { BodyRegion, Examination, ExaminationInput } from '../api/types';
import { useAuth } from '../auth/AuthContext';
import { fieldsOf, messageOf } from './ErrorText';
import { RegionSelect, regionLabel, useBodyRegions } from './BodyRegions';
import { vi } from '../i18n/vi';
import { useLoad } from '../hooks/useLoad';
import { PagerBar } from './PagerBar';
import { Button } from './ui/button';
import { DataTable, type DataColumn } from './ui/data-table';
import { DataView } from './ui/data-view';
import { Section } from './ui/display';
import { FormField } from './ui/form-field';
import { Input } from './ui/input';
import { Select } from './ui/select';
import { Callout, EmptyState } from './ui/states';
import { notify } from '../lib/notify';

const empty = (): ExaminationInput => ({
  diagnosis: '',
  bodyRegion: '',
  bodyDetail: '',
  weightKg: '',
  treatment: '',
  prescription: '',
  feeTypeId: '',
});

/** Catalog name and detail; an examination from before the catalog keeps its own text unchanged (BR-110, AC-171). */
function bodyAreaText(x: Examination, regions: BodyRegion[]): string {
  if (!x.bodyRegion) return x.bodyAreaLegacy ?? vi.common.none;
  const name = regionLabel(regions, x.bodyRegion);
  return x.bodyDetail ? `${name} — ${x.bodyDetail}` : name;
}

/**
 * Examinations on the horse page: the Veterinarian records them, the Head Trainer reads them read-only (PERM-016,
 * PERM-017); a Horse Owner sees only the recorded weights over time (BR-081).
 */
export default function ExaminationPanel({ horseId, archived }: { horseId: number; archived: boolean }) {
  const { can } = useAuth();
  const details = can('VIEW_EXAMINATIONS');
  const ownerView = !details && can('VIEW_READINESS');
  const [examPage, setExamPage] = useState(0);
  const exams = useLoad(() => api.examinations(horseId, examPage), [horseId, examPage], details);
  const weights = useLoad(() => api.weights(horseId), [horseId], ownerView);
  const regions = useBodyRegions(details);
  // The fee types with a price today, names only; the field shows only when the club has priced types (BR-178, BR-182).
  const feeTypes = useLoad(() => api.examFeeTypes(), [], can('RECORD_EXAMINATIONS') && !archived);
  const feeOptions = feeTypes.state.data ?? [];
  const [form, setForm] = useState<ExaminationInput>(empty());
  const [error, setError] = useState<string | null>(null);
  const [fieldErrors, setFieldErrors] = useState<Record<string, string>>({});
  const [pending, setPending] = useState(false);

  if (!details && !ownerView) return null;

  async function submit(e: FormEvent) {
    e.preventDefault();
    setError(null);
    setFieldErrors({});
    setPending(true);
    try {
      await api.recordExamination(horseId, form);
      notify.success(vi.common.saved);
      setForm(empty());
      if (examPage === 0) await exams.reload();
      else setExamPage(0);
    } catch (err) {
      setError(messageOf(err));
      setFieldErrors(fieldsOf(err));
    } finally {
      setPending(false);
    }
  }

  if (ownerView) {
    return (
      <Section title={vi.vet.weights} id="weights-section">
        <DataView
          load={weights}
          isEmpty={(list) => list.length === 0}
          empty={<EmptyState icon={Scale} title={vi.vet.noWeights} text={vi.vet.noWeightsHint} />}
        >
          {(list) => (
            <ul className="grid text-sm md:max-w-xl">
              {list.map((w) => (
                <li key={w.date} className="tabular border-b py-2">
                  {w.date}: {w.weightKg} kg
                </li>
              ))}
            </ul>
          )}
        </DataView>
      </Section>
    );
  }

  const none = vi.common.none;
  const columns: DataColumn<Examination>[] = [
    { id: 'date', header: vi.vet.examDate, primary: true, sortValue: (x) => x.examDate, cell: (x) => <span className="tabular">{x.examDate}</span> },
    { id: 'diagnosis', header: vi.vet.diagnosis, cell: (x) => x.diagnosis },
    { id: 'region', header: vi.regions.label, cell: (x) => bodyAreaText(x, regions) },
    { id: 'weight', header: vi.vet.weight, cell: (x) => <span className="tabular">{x.weightKg ?? none}</span> },
    { id: 'treatment', header: vi.vet.treatment, cell: (x) => x.treatment ?? none },
    { id: 'prescription', header: vi.vet.prescription, cell: (x) => x.prescription ?? none },
    { id: 'by', header: vi.vet.recordedBy, cell: (x) => x.recordedBy },
  ];

  return (
    <Section title={vi.vet.examinations} id="examinations-section">
      {error && (
        <Callout tone="danger" icon={CircleAlert} role="alert">
          {error}
        </Callout>
      )}
      {can('RECORD_EXAMINATIONS') && !archived && (
        <form onSubmit={submit} noValidate aria-label={vi.vet.newExam} className="grid gap-4 md:max-w-3xl md:grid-cols-2">
          <FormField label={`${vi.vet.diagnosis} *`} error={fieldErrors.diagnosis} className="md:col-span-2">
            <Input value={form.diagnosis} onChange={(e) => setForm({ ...form, diagnosis: e.target.value })} />
          </FormField>
          <RegionSelect
            regions={regions}
            value={form.bodyRegion}
            onChange={(bodyRegion) => setForm({ ...form, bodyRegion })}
            error={fieldErrors.bodyRegion}
          />
          <FormField label={vi.vet.bodyDetail}>
            <Input value={form.bodyDetail} onChange={(e) => setForm({ ...form, bodyDetail: e.target.value })} />
          </FormField>
          <FormField label={vi.vet.weight} error={fieldErrors.weightKg}>
            <Input
              type="number"
              step="0.1"
              value={form.weightKg}
              onChange={(e) => setForm({ ...form, weightKg: e.target.value === '' ? '' : Number(e.target.value) })}
            />
          </FormField>
          <FormField label={vi.vet.treatment}>
            <Input value={form.treatment} onChange={(e) => setForm({ ...form, treatment: e.target.value })} />
          </FormField>
          {feeOptions.length > 0 && (
            <FormField label={`${vi.vet.feeType} *`} error={fieldErrors.feeTypeId} description={vi.vet.feeTypeHint}>
              <Select
                value={form.feeTypeId === '' || form.feeTypeId === undefined ? '' : String(form.feeTypeId)}
                onChange={(e) => setForm({ ...form, feeTypeId: e.target.value === '' ? '' : Number(e.target.value) })}
              >
                <option value="">{vi.vet.chooseFeeType}</option>
                {feeOptions.map((t) => (
                  <option key={t.id} value={t.id}>
                    {t.name}
                  </option>
                ))}
              </Select>
            </FormField>
          )}
          <FormField label={vi.vet.prescription} className="md:col-span-2">
            <Input value={form.prescription} onChange={(e) => setForm({ ...form, prescription: e.target.value })} />
          </FormField>
          <div className="md:col-span-2">
            <Button type="submit" loading={pending}>
              {vi.vet.newExam}
            </Button>
          </div>
        </form>
      )}
      <DataView
        load={exams}
        isEmpty={(p) => p.items.length === 0}
        empty={<EmptyState icon={ClipboardList} title={vi.vet.noExams} text={vi.vet.noExamsHint} />}
      >
        {(p) => (
          <>
            <DataTable caption={vi.vet.examinations} data={p.items} columns={columns} rowKey={(x) => x.id} />
            <PagerBar page={p.page} size={p.size} total={p.total} onPage={setExamPage} />
          </>
        )}
      </DataView>
    </Section>
  );
}

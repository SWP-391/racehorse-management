import { useEffect, useState, type FormEvent } from 'react';
import { CircleAlert } from 'lucide-react';
import { api } from '../api/api';
import { ApiError } from '../api/client';
import type { HorseInput } from '../api/types';
import { imageKind } from '../lib/imageSignature';
import { vi } from '../i18n/vi';
import { useUnsavedChanges } from '../hooks/useUnsavedChanges';
import { notify } from '../lib/notify';
import { Button } from './ui/button';
import { FormField } from './ui/form-field';
import { Input } from './ui/input';
import { FileInput } from './ui/file-input';
import { Select } from './ui/select';
import { SearchSelect, type Chosen } from './ui/search-select';
import { HorseAvatar } from './HorseAvatar';
import { Callout, DetailSkeleton } from './ui/states';
import { useDelayedFlag } from '../hooks/useDelayedFlag';

const EMPTY: HorseInput = {
  name: '',
  registrationNumber: '',
  sex: '',
  birthDate: '',
  coatColour: '',
  breed: '',
  ownerId: '',
  trainerId: '',
};

const PHOTO_TYPES = ['image/jpeg', 'image/png'];
const PHOTO_MAX_BYTES = 5 * 1024 * 1024;

interface HorseFormProps {
  /** The horse to edit; omitted to register a new one. */
  horseId?: number;
  /** After a save: the saved horse and whether its photo was refused (the horse itself is saved either way). */
  onSaved: (saved: { id: number }, photoFailed: boolean) => void;
  onCancel: () => void;
  /** Tells the page the horse's name once it is loaded (for a breadcrumb). */
  onName?: (name: string) => void;
}

/** The register / edit form of a horse (Club Manager), on a page or in a popup. Field errors come from the service (BR-002, BR-003). */
export default function HorseForm({ horseId, onSaved, onCancel, onName }: HorseFormProps) {
  const id = horseId;
  const editing = id !== undefined;
  const [form, setForm] = useState<HorseInput>(EMPTY);
  const [loaded, setLoaded] = useState(!editing);
  const [owner, setOwner] = useState<Chosen | null>(null);
  const [trainer, setTrainer] = useState<Chosen | null>(null);
  const [photo, setPhoto] = useState<File | null>(null);
  const [photoError, setPhotoError] = useState<string | null>(null);
  const [photoPreview, setPhotoPreview] = useState<string | null>(null);
  const [current, setCurrent] = useState<{ id: number; name: string; photoVersion: number | null } | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [fieldErrors, setFieldErrors] = useState<Record<string, string>>({});
  const [pending, setPending] = useState(false);
  const dirty = useUnsavedChanges();
  const showSkeleton = useDelayedFlag(!loaded);

  useEffect(() => {
    if (editing) {
      api
        .horse(id)
        .then((h) => {
          onName?.(h.name);
          setForm({
            name: h.name,
            registrationNumber: h.registrationNumber,
            sex: h.sex,
            birthDate: h.birthDate,
            coatColour: h.coatColour ?? '',
            breed: h.breed ?? '',
            ownerId: h.owner.id,
            trainerId: h.mainTrainer?.id ?? '',
          });
          setOwner({ id: h.owner.id, label: h.owner.displayName });
          setTrainer(h.mainTrainer ? { id: h.mainTrainer.id, label: h.mainTrainer.displayName } : null);
          setCurrent({ id: h.id, name: h.name, photoVersion: h.photoVersion });
          setLoaded(true);
        })
        .catch((e) => {
          setError(e instanceof ApiError ? e.message : vi.common.unexpectedError);
          setLoaded(true);
        });
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [editing, id]);

  async function choosePhoto(file: File | null) {
    dirty.current = true;
    if (photoPreview) URL.revokeObjectURL(photoPreview);
    // Checked here before anything is saved or sent; the service checks again from the file content (BR-184).
    // The first bytes must match too, so a renamed text file is refused at once.
    const refusal =
      file === null
        ? null
        : !PHOTO_TYPES.includes(file.type)
          ? vi.horses.photoType
          : file.size > PHOTO_MAX_BYTES
            ? vi.horses.photoTooLarge
            : (await imageKind(file)) === null
              ? vi.horses.photoContent
              : null;
    setPhotoError(refusal);
    if (refusal) {
      setPhoto(null);
      setPhotoPreview(null);
      return;
    }
    setPhoto(file);
    setPhotoPreview(file ? URL.createObjectURL(file) : null);
  }

  const update = (patch: Partial<HorseInput>) => {
    dirty.current = true;
    setForm((f) => ({ ...f, ...patch }));
  };

  async function submit(e: FormEvent) {
    e.preventDefault();
    setError(null);
    setFieldErrors({});
    setPending(true);
    const body = {
      ...form,
      sex: form.sex || null,
      birthDate: form.birthDate || null,
      ownerId: form.ownerId === '' ? null : form.ownerId,
      trainerId: form.trainerId === '' ? null : form.trainerId,
    } as unknown as HorseInput;
    try {
      const saved = editing ? await api.updateHorse(id, body) : await api.registerHorse(body);
      dirty.current = false;
      notify.success(vi.common.saved);
      let photoFailed = false;
      if (photo) {
        // The horse is already saved: a refused photo is reported and does not undo it (TECH-180).
        try {
          await api.uploadHorsePhoto(saved.id, photo);
        } catch (failure) {
          notify.error(failure);
          photoFailed = true;
        }
      }
      onSaved(saved, photoFailed);
    } catch (err) {
      if (err instanceof ApiError) {
        setError(err.message);
        setFieldErrors(err.fields);
      } else {
        setError(vi.common.unexpectedError);
      }
    } finally {
      setPending(false);
    }
  }

  return (
    <>
      {!loaded ? (
        showSkeleton && <DetailSkeleton />
      ) : (
        <form onSubmit={submit} noValidate className="grid max-w-3xl gap-8">
          {error && (
            <Callout tone="danger" icon={CircleAlert}>
              {error}
            </Callout>
          )}
          <fieldset className="grid gap-5 border-0 p-0">
            <legend className="mb-4 font-display text-h2">{vi.horses.identity}</legend>
            <div className="grid gap-5 md:grid-cols-2">
              <FormField label={`${vi.horses.name} *`} error={fieldErrors.name} className="md:col-span-2">
                <Input value={form.name} onChange={(e) => update({ name: e.target.value })} />
              </FormField>
              <FormField label={`${vi.horses.registrationNumber} *`} error={fieldErrors.registrationNumber}>
                <Input value={form.registrationNumber} onChange={(e) => update({ registrationNumber: e.target.value })} />
              </FormField>
              <FormField label={`${vi.horses.sex} *`} error={fieldErrors.sex}>
                <Select value={form.sex} onChange={(e) => update({ sex: e.target.value as HorseInput['sex'] })}>
                  <option value="">—</option>
                  <option value="MALE">{vi.horses.male}</option>
                  <option value="FEMALE">{vi.horses.female}</option>
                </Select>
              </FormField>
              <FormField label={`${vi.horses.birthDate} *`} error={fieldErrors.birthDate}>
                <Input type="date" value={form.birthDate} onChange={(e) => update({ birthDate: e.target.value })} />
              </FormField>
              <FormField label={vi.horses.coatColour}>
                <Input value={form.coatColour} onChange={(e) => update({ coatColour: e.target.value })} />
              </FormField>
              <FormField label={vi.horses.breed} className="md:col-span-2">
                <Input value={form.breed} onChange={(e) => update({ breed: e.target.value })} />
              </FormField>
            </div>
          </fieldset>
          <fieldset className="grid gap-5 border-0 border-t p-0 pt-6">
            <legend className="mb-4 font-display text-h2">{vi.horses.ownership}</legend>
            <div className="grid gap-5 md:grid-cols-2">
              <FormField label={`${vi.horses.owner} *`} error={fieldErrors.ownerId}>
                <SearchSelect
                  value={owner}
                  placeholder={vi.horses.chooseOwner}
                  load={(q) => api.options('owners', { q })}
                  onChange={(chosen) => {
                    setOwner(chosen);
                    update({ ownerId: chosen ? chosen.id : '' });
                  }}
                />
              </FormField>
              <FormField label={`${vi.assign.trainer} *`} error={fieldErrors.trainerId} description={vi.assign.trainerHint}>
                <SearchSelect
                  value={trainer}
                  placeholder={vi.assign.chooseTrainer}
                  load={(q) => api.options('trainers', { q })}
                  onChange={(chosen) => {
                    setTrainer(chosen);
                    update({ trainerId: chosen ? chosen.id : '' });
                  }}
                />
              </FormField>
            </div>
          </fieldset>
          <fieldset className="grid gap-5 border-0 border-t p-0 pt-6">
            <legend className="mb-4 font-display text-h2">{vi.horses.photo}</legend>
            <div className="flex flex-wrap items-center gap-5">
              {photoPreview ? (
                <img src={photoPreview} alt={vi.horses.photoPreview} className="size-24 rounded-full border object-cover" />
              ) : (
                <HorseAvatar
                  id={current?.id ?? 0}
                  name={form.name || '?'}
                  photoVersion={current?.photoVersion ?? null}
                  variant="full"
                  className="size-24 text-2xl"
                />
              )}
              <FormField label={vi.horses.photoFile} description={vi.horses.photoHint} error={photoError} className="min-w-0 flex-1 md:max-w-sm">
                <FileInput accept="image/jpeg,image/png" onChange={(e) => void choosePhoto(e.target.files?.[0] ?? null)} />
              </FormField>
            </div>
          </fieldset>
          <div className="flex flex-col-reverse gap-2 border-t pt-6 sm:flex-row sm:justify-end">
            <Button type="button" variant="outline" onClick={onCancel}>
              {vi.common.cancel}
            </Button>
            <Button type="submit" loading={pending}>
              {vi.common.save}
            </Button>
          </div>
        </form>
      )}
    </>
  );
}

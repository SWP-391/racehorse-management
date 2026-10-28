import { useEffect, useState } from 'react';
import { api } from '../api/api';
import type { BodyRegion } from '../api/types';
import { vi } from '../i18n/vi';
import { notify } from '../lib/notify';
import { FormField } from './ui/form-field';
import { SearchSelect, localOptions } from './ui/search-select';

/**
 * The one body-region catalog on the client (BR-110, TECH-052). Names come only from the server catalog; no screen
 * writes a region name of its own.
 */
export function useBodyRegions(enabled = true): BodyRegion[] {
  const [regions, setRegions] = useState<BodyRegion[]>([]);
  useEffect(() => {
    if (!enabled) return;
    let alive = true;
    api
      .bodyRegions()
      .then((r) => alive && setRegions(r))
      .catch((e) => alive && notify.error(e));
    return () => {
      alive = false;
    };
  }, [enabled]);
  return regions;
}

/** The catalog name of a region; the code itself until the catalog has loaded. */
export function regionLabel(regions: BodyRegion[], code: string): string {
  return regions.find((r) => r.code === code)?.vi ?? code;
}

/** The single select used wherever a body region is chosen (examination, incident report, injury mark). */
export function RegionSelect({
  regions,
  value,
  onChange,
  error,
}: {
  regions: BodyRegion[];
  value: string;
  onChange: (code: string) => void;
  error?: string;
}) {
  const at = regions.findIndex((r) => r.code === value);
  const chosen = at < 0 ? null : { id: at + 1, label: regions[at].vi };
  return (
    <FormField label={vi.regions.label} error={error}>
      {/* 41 regions: a search box over the catalog (BR-186); the control keeps the catalog index, the code is mapped back. */}
      <SearchSelect
        value={chosen}
        clearable
        placeholder={vi.regions.choose}
        load={localOptions(regions.map((r, i) => ({ id: i + 1, label: r.vi, hint: null })))}
        onChange={(c) => onChange(c ? regions[c.id - 1].code : '')}
      />
    </FormField>
  );
}

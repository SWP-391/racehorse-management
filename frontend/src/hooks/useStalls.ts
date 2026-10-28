import { api } from '../api/api';
import { useLoad } from './useLoad';

/** Where a horse stands: the area and stall number, or the old free-text stall that has not been placed yet. */
export interface Stall {
  area: string | null;
  slot: number | null;
  legacy: string | null;
}

/**
 * The stall of every horse the person may see, from the barn answer (the service reduces it to the horses they can
 * see). A list shows "Khu A · Ô 3" next to each horse so nobody has to open the barn page to find one (BR-165).
 * A refused or failed call leaves the map empty: the column then reads "chưa xếp chuồng", the list still works.
 */
export function useStalls(enabled = true): ReadonlyMap<number, Stall> | null {
  const load = useLoad(
    async () => {
      const barn = await api.barn();
      const map = new Map<number, Stall>();
      for (const area of barn.areas) {
        for (const s of area.slots) if (s.horse) map.set(s.horse.horseId, { area: area.name, slot: s.slot, legacy: null });
      }
      for (const h of barn.unplaced) map.set(h.horseId, { area: null, slot: null, legacy: h.legacyStall });
      return map as ReadonlyMap<number, Stall>;
    },
    [],
    enabled,
  );
  if (load.state.status === 'error') return new Map();
  return load.state.data;
}

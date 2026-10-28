import { MapPin } from 'lucide-react';
import type { Stall } from '../hooks/useStalls';
import { Skeleton } from './ui/states';
import { vi } from '@/i18n/vi';

/** "Khu A · Ô 3" with a pin; a skeleton while the barn loads (`stalls` null); a muted note when the horse is not placed. */
export function StallLabel({ stalls, horseId }: { stalls: ReadonlyMap<number, Stall> | null; horseId: number }) {
  if (!stalls) return <Skeleton className="h-5 w-20" />;
  const stall = stalls.get(horseId);
  if (!stall || stall.area === null) {
    return <span className="text-muted-foreground">{stall?.legacy ?? vi.barn.notPlaced}</span>;
  }
  return (
    <span className="inline-flex items-center gap-1.5 whitespace-nowrap">
      <MapPin aria-hidden className="size-3.5 shrink-0 text-primary" />
      {stall.area} · {vi.barn.stallOf(stall.slot ?? 0)}
    </span>
  );
}

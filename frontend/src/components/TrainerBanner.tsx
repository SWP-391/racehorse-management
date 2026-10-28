import { Eye } from 'lucide-react';
import { vi } from '../i18n/vi';
import { Callout } from './ui/states';

/**
 * Shown to a Head Trainer who reads a horse that is not theirs: every trainer sees every horse, only the assigned
 * one writes (BR-153). The text names the trainer who does, and the loan while one lasts (BR-156).
 */
export function TrainerBanner({
  trainer,
  until,
  className,
}: {
  trainer: { displayName: string } | null;
  until?: string | null;
  className?: string;
}) {
  return (
    <Callout tone="info" icon={Eye} role="status" className={className}>
      {trainer ? vi.assign.notAssigned(trainer.displayName) : vi.assign.noTrainer}
      {until && <span className="ml-1">{vi.assign.temporaryUntil(until)}</span>}
    </Callout>
  );
}

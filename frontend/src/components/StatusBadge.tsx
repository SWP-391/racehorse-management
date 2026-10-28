import { Bandage, CircleCheck, Eye, FlaskConical, Lock, ShieldAlert } from 'lucide-react';
import { Badge } from './ui/display';
import { vi } from '../i18n/vi';

const HEALTH = {
  ELIGIBLE: { tone: 'eligible', icon: CircleCheck },
  UNDER_OBSERVATION: { tone: 'watch', icon: Eye },
  INJURED: { tone: 'injured', icon: Bandage },
  QUARANTINED: { tone: 'quarantine', icon: ShieldAlert },
} as const;

/** Health status as wash + ink + icon, so colour is never the only cue (docs/ui-direction.md § Theme). */
export function HealthBadge({ status }: { status: string }) {
  const s = HEALTH[status as keyof typeof HEALTH];
  const label = vi.health.statuses[status] ?? status;
  if (!s) return <Badge>{label}</Badge>;
  const Icon = s.icon;
  return (
    <Badge tone={s.tone} icon={<Icon aria-hidden />}>
      {label}
    </Badge>
  );
}

export function LockBadge({ children }: { children?: string }) {
  return (
    <Badge tone="locked" icon={<Lock aria-hidden />}>
      {children ?? vi.health.lock}
    </Badge>
  );
}

/** The simulated-data label (BR-050): the same text everywhere it is shown. */
export function SimulatedBadge({ children }: { children: string }) {
  return (
    <Badge tone="simulated" icon={<FlaskConical aria-hidden />} className="whitespace-nowrap">
      {children}
    </Badge>
  );
}

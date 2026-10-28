import { CircleCheck, CircleX, Eye } from 'lucide-react';
import type { Readiness } from '../api/types';
import { vi } from '../i18n/vi';
import { Badge } from './ui/display';

const TONE = {
  READY: { tone: 'eligible', icon: CircleCheck },
  UNDER_OBSERVATION: { tone: 'watch', icon: Eye },
  NOT_READY: { tone: 'injured', icon: CircleX },
} as const;

/** Derived readiness (REQ-025) as a status badge with an icon, never colour alone. */
export function ReadinessBadge({ value }: { value: Readiness }) {
  const t = TONE[value as keyof typeof TONE];
  const label = vi.readiness.values[value] ?? value;
  if (!t) return <Badge>{label}</Badge>;
  const Icon = t.icon;
  return (
    <Badge tone={t.tone} icon={<Icon aria-hidden />}>
      {label}
    </Badge>
  );
}

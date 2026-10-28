import { clubDate, clubTime } from '@/lib/utils';
import { hhmm } from '@/i18n/time';
import { vi } from '@/i18n/vi';

interface SessionEndProps {
  plannedEnd: string | null;
  plannedEndNextDay: boolean;
  /** The instant the session ended; null while it has not. */
  endedAt: string | null;
  /** The session's day (`yyyy-mm-dd`); lets an end on a later club day read "(hôm sau)" (BR-190). */
  date?: string;
}

/**
 * The end of a session: "Kết thúc 07:52" once it ended, otherwise the planned end "Kết thúc dự kiến 08:00" (start
 * plus planned duration, BR-190); nothing while the session has no start time.
 */
export function sessionEndText({ plannedEnd, plannedEndNextDay, endedAt, date }: SessionEndProps): string | null {
  if (endedAt) return vi.training.endedAt(clubTime(endedAt), date !== undefined && clubDate(endedAt) > date);
  if (!plannedEnd) return null;
  return vi.training.plannedEnd(hhmm(plannedEnd), plannedEndNextDay);
}

export function SessionEnd(props: SessionEndProps) {
  const text = sessionEndText(props);
  return text ? <span className="tabular">{text}</span> : <span className="text-muted-foreground">{vi.common.none}</span>;
}

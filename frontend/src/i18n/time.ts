/** "07:00:00" -> "07:00"; "—" for a session created before start times existed (BR-120). */
export function hhmm(time: string | null | undefined): string {
  return time ? time.slice(0, 5) : '—';
}

import { DetailPopup } from './detail-popup';

/**
 * Free text in a narrow table column: short text shows as it is; longer text shows its first words and an eye button
 * that opens the whole text in a popup, so one long note never stretches a row.
 */
export function LongText({ text, title, subject, limit = 48 }: { text: string; title: string; subject?: string; limit?: number }) {
  if (text.length <= limit) return <>{text}</>;
  return (
    <span className="inline-flex flex-wrap items-center gap-x-2 gap-y-1">
      <span>{text.slice(0, limit).trimEnd()}…</span>
      <DetailPopup title={title} subject={subject}>
        <p className="whitespace-pre-wrap break-words text-sm">{text}</p>
      </DetailPopup>
    </span>
  );
}

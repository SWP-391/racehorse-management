import { vi } from '../../i18n/vi';

/**
 * Club mark: a hand-drawn horseshoe (lucide has no horse) beside the serif wordmark. Not a link: the rail and the
 * drawer already link every section, and a second link whose name contains "Ngựa" would shadow the Horses nav item.
 */
export function Wordmark() {
  return (
    <div className="flex min-w-0 items-center gap-2.5 text-foreground">
      <svg aria-hidden viewBox="0 0 32 32" className="size-8 shrink-0 text-primary">
        <rect width="32" height="32" rx="7" className="fill-accent" />
        <path
          d="M10 23.5V15a6 6 0 0 1 12 0v8.5"
          fill="none"
          stroke="currentColor"
          strokeWidth="3.2"
          strokeLinecap="round"
        />
        <g className="fill-background">
          <circle cx="10" cy="17" r="0.9" />
          <circle cx="10.6" cy="13.4" r="0.9" />
          <circle cx="22" cy="17" r="0.9" />
          <circle cx="21.4" cy="13.4" r="0.9" />
        </g>
      </svg>
      <span className="grid leading-none">
        <span className="font-display text-[1.25rem] font-semibold tracking-tight">{vi.shell.wordmark}</span>
        <span className="mt-1 text-[0.6875rem] text-muted-foreground">{vi.shell.club}</span>
      </span>
    </div>
  );
}

import { cn } from '@/lib/utils';

const TONES = ['bg-emerald-100 text-emerald-900', 'bg-amber-100 text-amber-900', 'bg-sky-100 text-sky-900', 'bg-rose-100 text-rose-900', 'bg-violet-100 text-violet-900'];

/** The letters a name is known by (the club office is "QL"): the first letter of its last word ("Bà Lý Thị Hoa" gives "H"). */
function initial(name: string): string {
  if (/\bCLB\b/.test(name)) return 'QL';
  const words = name.trim().split(/\s+/).filter(Boolean);
  return (words[words.length - 1]?.[0] ?? '?').toUpperCase();
}

/** A round badge with the initial of a person, in a colour that stays the same for the same name. */
export function Avatar({ name, size = 'md', className }: { name: string; size?: 'sm' | 'md'; className?: string }) {
  let hash = 0;
  for (const ch of name) hash = (hash * 31 + ch.charCodeAt(0)) % 997;
  return (
    <span
      aria-hidden
      className={cn('inline-flex shrink-0 items-center justify-center rounded-full font-semibold', size === 'sm' ? 'size-7 text-xs' : 'size-10 text-sm', TONES[hash % TONES.length], className)}
    >
      {initial(name)}
    </span>
  );
}

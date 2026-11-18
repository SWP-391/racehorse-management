import { useEffect, useState } from 'react';
import { NavLink } from 'react-router-dom';
import type { LucideIcon } from 'lucide-react';
import { BookOpen, CalendarCheck, MessageSquare, CalendarDays, ClipboardCheck, HeartPulse, House, Package, Landmark, Receipt, ScrollText, Settings, Siren, Syringe, TrendingUp, Users } from 'lucide-react';
import type { Permission } from '../../api/types';
import { api } from '../../api/api';
import { useAuth } from '../../auth/AuthContext';
import { INBOX_CHANGED } from '../inbox/events';
import { SECTIONS } from '../../routes/access';
import { vi } from '../../i18n/vi';
import { cn } from '@/lib/utils';

interface NavItem {
  to: string;
  label: string;
  icon: LucideIcon;
  /** Shown when the user holds any of these (BR-091: the menu mirrors the role; the service still checks every call). */
  any: Permission[];
}

const DECOR: Record<string, { label: string; icon: LucideIcon }> = {
  '/horses': { label: vi.nav.horses, icon: BookOpen },
  '/health': { label: vi.nav.health, icon: HeartPulse },
  '/progress': { label: vi.nav.progress, icon: TrendingUp },
  '/care': { label: vi.nav.care, icon: Syringe },
  '/incidents': { label: vi.nav.incidents, icon: Siren },
  '/chores': { label: vi.nav.chores, icon: ClipboardCheck },
  '/barn': { label: vi.nav.barn, icon: House },
  '/supplies': { label: vi.nav.supplies, icon: Package },
  '/billing': { label: vi.nav.billing, icon: Receipt },
  '/payments': { label: vi.nav.payments, icon: Landmark },
  '/timetable': { label: vi.nav.timetable, icon: CalendarDays },
  '/my-sessions': { label: vi.nav.mySessions, icon: CalendarCheck },
  '/accounts': { label: vi.nav.accounts, icon: Users },
  '/audit': { label: vi.nav.audit, icon: ScrollText },
  '/settings': { label: vi.nav.settings, icon: Settings },
  '/inbox': { label: vi.nav.inbox, icon: MessageSquare },
};

/** Built from the same table as the landing check after sign-in, so the menu and the landing cannot drift (TECH-127). */
const ITEMS: NavItem[] = SECTIONS.map((s) => ({ to: s.path, any: s.any, ...DECOR[s.path] }));

/** Sections the signed-in role may open; the first is that role's home. */
export function useNavItems() {
  const { can } = useAuth();
  return ITEMS.filter((item) => item.any.some(can));
}

const UNREAD_POLL_MS = 30_000;

/** Unread messages of the inbox, asked every 30 seconds and again when a conversation was opened (BR-233). */
function useInboxUnread() {
  const [unread, setUnread] = useState<number | null>(null);
  useEffect(() => {
    let alive = true;
    const ask = () => {
      // A failed answer keeps the last count; unknown is not zero.
      Promise.resolve()
        .then(() => api.inboxUnread())
        .then((r) => alive && setUnread(r.unread))
        .catch(() => undefined);
    };
    ask();
    const timer = setInterval(ask, UNREAD_POLL_MS);
    window.addEventListener(INBOX_CHANGED, ask);
    return () => {
      alive = false;
      clearInterval(timer);
      window.removeEventListener(INBOX_CHANGED, ask);
    };
  }, []);
  return unread;
}

/** The ruled list of sections: hairlines between rows, a 2 px green bar on the active one. */
export function NavList({ onNavigate }: { onNavigate?: () => void }) {
  const items = useNavItems();
  const unread = useInboxUnread();
  return (
    <nav aria-label="main">
      <ul className="m-0 grid list-none divide-y divide-border/70 border-y border-border/70 p-0">
        {items.map(({ to, label, icon: Icon }) => (
          <li key={to}>
            <NavLink
              to={to}
              onClick={onNavigate}
              className={({ isActive }) =>
                cn(
                  'relative flex h-11 items-center gap-3 px-3 text-sm text-muted-foreground no-underline transition-colors duration-[var(--dur-fast)] hover:bg-secondary/70 hover:text-foreground lg:h-10',
                  'before:absolute before:inset-y-1.5 before:left-0 before:w-0.5 before:rounded-full before:bg-primary before:opacity-0 before:transition-opacity',
                  isActive && 'font-semibold text-foreground before:opacity-100',
                )
              }
            >
              <Icon aria-hidden className="size-4 shrink-0" />
              {label}
              {to === '/inbox' && unread !== null && unread > 0 && (
                <span
                  role="status"
                  aria-label={vi.inbox.unread(unread)}
                  className="ml-auto grid h-4 min-w-4 place-items-center rounded-full bg-destructive px-1 text-[0.625rem] leading-none font-semibold text-destructive-foreground tabular-nums"
                >
                  {unread > 9 ? '9+' : unread}
                </span>
              )}
            </NavLink>
          </li>
        ))}
      </ul>
    </nav>
  );
}

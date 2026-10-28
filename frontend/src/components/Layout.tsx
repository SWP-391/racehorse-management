import { useEffect, useRef, useState, type RefObject } from 'react';
import { Outlet, useLocation } from 'react-router-dom';
import { LogOut, Menu } from 'lucide-react';
import { Toaster } from 'sonner';
import { useAuth } from '../auth/AuthContext';
import { vi } from '../i18n/vi';
import NotificationBell from './NotificationBell';
import { NavList } from './layout/NavList';
import { Wordmark } from './layout/Wordmark';
import { Button } from './ui/button';
import { Avatar, AvatarFallback, TOPBAR_CRUMBS_ID } from './ui/display';
import { Drawer, DrawerContent, DrawerDescription, DrawerTitle, DrawerTrigger } from './ui/drawer';
import { TooltipProvider } from './ui/menu';

// First and last word: "Trần Văn Hùng" → "TH" (Vietnamese names put the given name last).
const initials = (name: string) => {
  const words = name.split(/\s+/).filter(Boolean);
  const pick = words.length > 1 ? [words[0], words[words.length - 1]] : words;
  return pick.map((w) => w[0]?.toUpperCase()).join('');
};

/** Name, role and sign-out; `data-testid="user-menu"` is the shell contract (ui-kit § Shell). */
function UserBlock() {
  const { me, logout } = useAuth();
  if (!me) return null;
  return (
    <div data-testid="user-menu" className="grid gap-3">
      <div className="flex min-w-0 items-center gap-3">
        <Avatar>
          <AvatarFallback>{initials(me.displayName)}</AvatarFallback>
        </Avatar>
        <div className="grid min-w-0 leading-tight">
          <span className="line-clamp-2 text-sm font-medium break-words" title={me.displayName}>
            {me.displayName}
          </span>
          {/* When the name is the role itself (the bootstrap Club Manager), the username tells the account apart. */}
          <span className="truncate text-meta text-muted-foreground">
            {me.displayName === vi.roles[me.role] ? me.username : vi.roles[me.role]}
          </span>
        </div>
      </div>
      <Button variant="outline" size="sm" className="w-full justify-start" onClick={() => void logout()}>
        <LogOut aria-hidden />
        {vi.auth.signOut}
      </Button>
    </div>
  );
}

/** Toasts sit top-center on phones and bottom-right on desktop (ui-kit § Base). */
function usePhone() {
  const query = '(max-width: 767px)';
  const [phone, setPhone] = useState(() => window.matchMedia?.(query).matches ?? false);
  useEffect(() => {
    const mq = window.matchMedia?.(query);
    if (!mq) return;
    const onChange = () => setPhone(mq.matches);
    mq.addEventListener('change', onChange);
    return () => mq.removeEventListener('change', onChange);
  }, []);
  return phone;
}

/** On every route change: focus the page's <h1> so keyboard and screen-reader users land on the new page (ui-kit contract 5). */
function useFocusHeadingOnRouteChange(main: RefObject<HTMLElement>) {
  const { pathname } = useLocation();
  const first = useRef(true);
  useEffect(() => {
    if (first.current) {
      first.current = false;
      return;
    }
    const timer = window.setTimeout(() => {
      const h1 = main.current?.querySelector<HTMLElement>('h1');
      if (!h1) return;
      if (!h1.hasAttribute('tabindex')) h1.setAttribute('tabindex', '-1');
      h1.focus({ preventScroll: true });
    }, 0);
    window.scrollTo({ top: 0 });
    return () => window.clearTimeout(timer);
  }, [pathname, main]);
}

/**
 * The ledger shell (docs/ui-direction.md § Layout archetype): a ruled rail with the sections the role may open
 * at ≥ lg, a top strip with the bell, and a bottom drawer for the same sections on phones. The service still
 * checks every call (BR-091).
 */
export default function Layout() {
  const { pathname } = useLocation();
  const [drawerOpen, setDrawerOpen] = useState(false);
  const phone = usePhone();
  const main = useRef<HTMLElement>(null);
  useFocusHeadingOnRouteChange(main);

  return (
    <TooltipProvider>
      <a
        href="#main"
        className="sr-only z-[60] rounded-md bg-primary px-4 py-2 text-primary-foreground focus:not-sr-only focus:fixed focus:top-3 focus:left-3"
      >
        {vi.common.skipToContent}
      </a>
      <div className="min-h-dvh lg:grid lg:grid-cols-[15.5rem_minmax(0,1fr)]">
        <aside className="hidden border-r bg-card/60 lg:sticky lg:top-0 lg:flex lg:h-dvh lg:flex-col lg:gap-6 lg:px-4 lg:py-5 [view-transition-name:rail]">
          <div className="px-1">
            <Wordmark />
          </div>
          <div className="-mx-1 flex-1 overflow-y-auto">
            <NavList />
          </div>
          <UserBlock />
        </aside>

        <div className="flex min-w-0 flex-col">
          <header className="sticky top-0 z-40 flex h-14 items-center gap-2 border-b bg-background/90 px-4 backdrop-blur-sm md:px-6 lg:h-14 lg:px-10 [view-transition-name:topbar]">
            <div className="min-w-0 flex-1 lg:hidden">
              <Wordmark />
            </div>
            {/* PageHeader portals the page's breadcrumb here at ≥ lg (it stays in the page column on phones). */}
            <div id={TOPBAR_CRUMBS_ID} className="hidden min-w-0 flex-1 lg:block" />
            <NotificationBell />
            <div className="lg:hidden">
              <Drawer open={drawerOpen} onOpenChange={setDrawerOpen}>
                <DrawerTrigger asChild>
                  <Button variant="ghost" size="icon" aria-label={vi.common.openMenu}>
                    <Menu aria-hidden />
                  </Button>
                </DrawerTrigger>
                <DrawerContent>
                  <DrawerTitle className="sr-only">{vi.common.navigation}</DrawerTitle>
                  <DrawerDescription className="sr-only">{vi.appTitle}</DrawerDescription>
                  <div className="grid gap-5 overflow-y-auto px-4 pb-2">
                    <NavList onNavigate={() => setDrawerOpen(false)} />
                    <UserBlock />
                  </div>
                </DrawerContent>
              </Drawer>
            </div>
          </header>

          <main
            id="main"
            ref={main}
            tabIndex={-1}
            className="mx-auto w-full max-w-[76rem] flex-1 px-4 pt-6 pb-16 outline-none md:px-6 md:pt-8 lg:px-10 lg:pt-10"
          >
            <div key={pathname} className="animate-[page-in_160ms_var(--ease-out)_both]">
              <Outlet />
            </div>
          </main>
        </div>
      </div>
      <Toaster position={phone ? 'top-center' : 'bottom-right'} visibleToasts={3} toastOptions={{ className: 'font-sans' }} />
    </TooltipProvider>
  );
}

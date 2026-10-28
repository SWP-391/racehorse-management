import { createContext, useCallback, useContext, useEffect, useMemo, useRef, useState, type ReactNode } from 'react';
import { api } from '../api/api';
import { ApiError, resetSessionEnded, setSessionEndedHandler } from '../api/client';
import type { Me, Permission } from '../api/types';
import { vi } from '../i18n/vi';
import { canOpen, homeFor } from '../routes/access';

interface AuthState {
  me: Me | null;
  loading: boolean;
  login: (username: string, password: string) => Promise<void>;
  logout: () => Promise<void>;
  can: (permission: Permission) => boolean;
  /** Why the person is back at sign-in without having signed out (BR-191); null when they signed out themselves. */
  endedReason: string | null;
  clearEndedReason: () => void;
  /** The page to open right after a successful sign-in; consumed by `Routed` (TECH-127). */
  landing: string | null;
  consumeLanding: () => void;
}

const AuthContext = createContext<AuthState | null>(null);

const currentPath = () => window.location.pathname + window.location.search;

/**
 * `onSignedOut` runs after a person signs out themselves: the app shell uses it to go back to the root URL and start
 * from a clean tree (no state, no cache of the last person), without reloading the page.
 */
export function AuthProvider({ children, initial, onSignedOut }: { children: ReactNode; initial?: Me | null; onSignedOut?: () => void }) {
  const [me, setMe] = useState<Me | null>(initial ?? null);
  const [loading, setLoading] = useState(initial === undefined);
  const [endedReason, setEndedReason] = useState<string | null>(null);
  const [landing, setLanding] = useState<string | null>(null);
  /** The page the person was on when they left, kept in memory only (a reload falls back to the normal flow). */
  const returnTo = useRef<string | null>(null);

  useEffect(() => {
    if (initial !== undefined) return;
    api
      .me()
      .then((m) => {
        resetSessionEnded();
        setMe(m);
      })
      .catch((e) => {
        if (!(e instanceof ApiError)) throw e;
        setMe(null);
      })
      .finally(() => setLoading(false));
  }, [initial]);

  // Any refused call because nobody is signed in ends the client session at once (TECH-126).
  useEffect(() => {
    setSessionEndedHandler((message) => {
      returnTo.current = currentPath();
      setEndedReason(message ?? vi.auth.sessionExpired);
      setMe(null);
    });
    return () => setSessionEndedHandler(null);
  }, []);

  const login = useCallback(async (username: string, password: string) => {
    const next = await api.login(username, password);
    resetSessionEnded();
    const from = returnTo.current ?? currentPath();
    returnTo.current = null;
    const target = canOpen(next, from) ? from : homeFor(next);
    setEndedReason(null);
    setLanding(target === currentPath() ? null : target);
    setMe(next);
  }, []);

  const logout = useCallback(async () => {
    try {
      await api.logout();
    } catch (e) {
      // An ended session answers 401: the person is signed out either way.
      if (!(e instanceof ApiError)) throw e;
    }
    // A signed-out person leaves nothing behind: no page to return to and no state in memory.
    returnTo.current = null;
    setEndedReason(null);
    setLanding(null);
    setMe(null);
    onSignedOut?.();
  }, [onSignedOut]);

  const clearEndedReason = useCallback(() => setEndedReason(null), []);
  const consumeLanding = useCallback(() => setLanding(null), []);

  const value = useMemo<AuthState>(
    () => ({
      me,
      loading,
      login,
      logout,
      can: (p) => !!me?.permissions.includes(p),
      endedReason,
      clearEndedReason,
      landing,
      consumeLanding,
    }),
    [me, loading, login, logout, endedReason, clearEndedReason, landing, consumeLanding],
  );
  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
}

export function useAuth(): AuthState {
  const ctx = useContext(AuthContext);
  if (!ctx) throw new Error('useAuth outside AuthProvider');
  return ctx;
}

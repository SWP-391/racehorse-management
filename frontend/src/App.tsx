import { lazy, Suspense, useEffect, type ComponentType, type ReactNode } from 'react';
import { Navigate, Route, Routes, useLocation } from 'react-router-dom';
import { ShieldOff } from 'lucide-react';
import { AuthProvider, useAuth } from './auth/AuthContext';
import { homeFor } from './routes/access';
import type { Me } from './api/types';
import Layout from './components/Layout';
import { ErrorBoundary } from './components/ErrorBoundary';
import LoginPage from './pages/LoginPage';
import ForgotPasswordPage from './pages/ForgotPasswordPage';
import { DetailSkeleton, EmptyState } from './components/ui/states';
import { useDelayedFlag } from './hooks/useDelayedFlag';
import { vi } from './i18n/vi';

/** A route chunk that fails to load (the dev server restarted, a deploy replaced it) is tried again once before it errors. */
function lazyRetry<T extends { default: ComponentType<any> }>(load: () => Promise<T>) {
  return lazy(() => load().catch(() => new Promise<T>((resolve) => setTimeout(resolve, 400)).then(load)));
}

// Every workspace route is its own chunk, so the first load carries neither three.js nor ECharts (TECH-081).
const AccountsPage = lazyRetry(() => import('./pages/AccountsPage'));
const AuditPage = lazyRetry(() => import('./pages/AuditPage'));
const HorsesPage = lazyRetry(() => import('./pages/HorsesPage'));
const HorseFormPage = lazyRetry(() => import('./pages/HorseFormPage'));
const HorseDetailPage = lazyRetry(() => import('./pages/HorseDetailPage'));
const HealthPage = lazyRetry(() => import('./pages/HealthPage'));
const ProgressPage = lazyRetry(() => import('./pages/ProgressPage'));
const PlanEditorPage = lazyRetry(() => import('./pages/PlanEditorPage'));
const PlanPage = lazyRetry(() => import('./pages/PlanPage'));
const SessionPage = lazyRetry(() => import('./pages/SessionPage'));
const SettingsPage = lazyRetry(() => import('./pages/SettingsPage'));
const CarePage = lazyRetry(() => import('./pages/CarePage'));
const IncidentsPage = lazyRetry(() => import('./pages/IncidentsPage'));
const InjuryPage = lazyRetry(() => import('./injury/InjuryPage'));
const ChoresPage = lazyRetry(() => import('./pages/ChoresPage'));
const RationPage = lazyRetry(() => import('./pages/RationPage'));
const BarnPage = lazyRetry(() => import('./pages/BarnPage'));
const SuppliesPage = lazyRetry(() => import('./pages/SuppliesPage'));
const BillingPage = lazyRetry(() => import('./pages/BillingPage'));
const PaymentsPage = lazyRetry(() => import('./pages/PaymentsPage'));
const MySessionsPage = lazyRetry(() => import('./pages/MySessionsPage'));
const TimetablePage = lazyRetry(() => import('./pages/TimetablePage'));
const BillingPrintPage = lazyRetry(() => import('./pages/BillingPrintPage'));

/** Route-chunk fallback: nothing for fast loads, a page skeleton after 200 ms. */
function PageFallback() {
  const show = useDelayedFlag(true);
  return show ? <DetailSkeleton /> : null;
}

function Page({ children }: { children: ReactNode }) {
  const { pathname } = useLocation();
  return (
    <ErrorBoundary resetKey={pathname}>
      <Suspense fallback={<PageFallback />}>{children}</Suspense>
    </ErrorBoundary>
  );
}

function Splash() {
  const show = useDelayedFlag(true);
  return (
    <div role="status" aria-label={vi.common.loading} className="grid min-h-dvh place-items-center">
      {show && <span className="size-8 animate-spin rounded-full border-2 border-border border-t-primary" />}
    </div>
  );
}

/** Goes to the page chosen at sign-in before any page of the old URL is drawn, then forgets it (BR-191, TECH-127). */
function Landing({ to }: { to: string }) {
  const { consumeLanding } = useAuth();
  useEffect(() => consumeLanding(), [consumeLanding]);
  return <Navigate to={to} replace />;
}

function Routed() {
  const { me, loading, landing } = useAuth();
  if (loading) return <Splash />;
  if (!me) {
    return (
      <Routes>
        <Route path="/forgot-password" element={<ForgotPasswordPage />} />
        <Route path="*" element={<LoginPage />} />
      </Routes>
    );
  }
  if (landing) return <Landing to={landing} />;
  const home = homeFor(me);
  return (
    <Routes>
      <Route element={<Layout />}>
        <Route path="/horses" element={<Page><HorsesPage /></Page>} />
        <Route path="/horses/new" element={<Page><HorseFormPage /></Page>} />
        <Route path="/horses/:id" element={<Page><HorseDetailPage /></Page>} />
        <Route path="/horses/:id/edit" element={<Page><HorseFormPage /></Page>} />
        <Route path="/horses/:id/injuries" element={<Page><InjuryPage /></Page>} />
        <Route path="/horses/:id/ration" element={<Page><RationPage /></Page>} />
        <Route path="/health" element={<Page><HealthPage /></Page>} />
        <Route path="/progress" element={<Page><ProgressPage /></Page>} />
        <Route path="/horses/:id/plans/new" element={<Page><PlanEditorPage /></Page>} />
        <Route path="/plans/:id" element={<Page><PlanPage /></Page>} />
        <Route path="/plans/:planId/edit" element={<Page><PlanEditorPage /></Page>} />
        <Route path="/sessions/:id" element={<Page><SessionPage /></Page>} />
        <Route path="/settings" element={<Page><SettingsPage /></Page>} />
        <Route path="/care" element={<Page><CarePage /></Page>} />
        <Route path="/incidents" element={<Page><IncidentsPage /></Page>} />
        <Route path="/chores" element={<Page><ChoresPage /></Page>} />
        <Route path="/barn" element={<Page><BarnPage /></Page>} />
        <Route path="/supplies" element={<Page><SuppliesPage /></Page>} />
        <Route path="/billing" element={<Page><BillingPage /></Page>} />
        <Route path="/payments" element={<Page><PaymentsPage /></Page>} />
        <Route path="/timetable" element={<Page><TimetablePage /></Page>} />
        <Route path="/billing/print/:kind/:id" element={<Page><BillingPrintPage /></Page>} />
        <Route path="/my-sessions" element={<Page><MySessionsPage /></Page>} />
        <Route path="/accounts" element={<Page><AccountsPage /></Page>} />
        <Route path="/audit" element={<Page><AuditPage /></Page>} />
        <Route
          path="/none"
          element={<EmptyState icon={ShieldOff} title={vi.common.accessDenied} className="mt-8" />}
        />
        <Route path="*" element={<Navigate to={home} replace />} />
      </Route>
    </Routes>
  );
}

export default function App({ initialMe, onSignedOut }: { initialMe?: Me | null; onSignedOut?: () => void }) {
  return (
    <AuthProvider initial={initialMe} onSignedOut={onSignedOut}>
      <Routed />
    </AuthProvider>
  );
}

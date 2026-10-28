import { Component, type ErrorInfo, type ReactNode } from 'react';
import { ErrorState } from './ui/states';
import { vi } from '@/i18n/vi';

/**
 * Catches a render error or a failed route chunk so the person sees a message with a retry instead of a blank page.
 * `resetKey` (the path) clears the error on navigation.
 */
export class ErrorBoundary extends Component<{ children: ReactNode; resetKey?: string }, { error: Error | null }> {
  state: { error: Error | null } = { error: null };

  static getDerivedStateFromError(error: Error) {
    return { error };
  }

  componentDidCatch(error: Error, info: ErrorInfo) {
    console.error(error, info.componentStack);
  }

  componentDidUpdate(previous: { resetKey?: string }) {
    if (this.state.error && previous.resetKey !== this.props.resetKey) this.setState({ error: null });
  }

  render() {
    if (!this.state.error) return this.props.children;
    return (
      <div className="mx-auto max-w-xl p-6">
        <ErrorState message={vi.common.unexpectedError} onRetry={() => window.location.reload()} />
      </div>
    );
  }
}

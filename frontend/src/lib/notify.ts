import { toast } from 'sonner';
import { messageOf } from '../components/ErrorText';

/** The one toast helper (ui-kit § Feedback): success after a save that changes another screen, error after a refusal. */
export const notify = {
  success: (message: string) => toast.success(message),
  error: (e: unknown) => toast.error(typeof e === 'string' ? e : messageOf(e)),
};

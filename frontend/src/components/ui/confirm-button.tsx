import { useState, type ReactNode } from 'react';
import { vi } from '@/i18n/vi';
import { AlertDialog, AlertDialogAction, AlertDialogCancel, AlertDialogContent, AlertDialogDescription, AlertDialogFooter, AlertDialogTitle } from './alert-dialog';
import { Button, type ButtonProps } from './button';

interface ConfirmButtonProps {
  /** The button's text and icon; an icon-only use passes no `children` text and an `aria-label`. */
  children: ReactNode;
  /** The popup's title and what will happen, so a click is never a surprise. */
  title: string;
  description?: string;
  confirmLabel?: string;
  onConfirm: () => void | Promise<unknown>;
  /** A destructive confirmation (red button) for deactivating, cancelling or removing. */
  destructive?: boolean;
  variant?: ButtonProps['variant'];
  size?: ButtonProps['size'];
  disabled?: boolean;
  loading?: boolean;
  'aria-label'?: string;
  className?: string;
}

/**
 * A button in a table row that changes something at once (schedule, deactivate, approve): it opens a popup that says
 * what will happen, and only the popup's confirm button does it.
 */
export function ConfirmButton({
  children,
  title,
  description,
  confirmLabel,
  onConfirm,
  destructive,
  variant = 'outline',
  size = 'sm',
  disabled,
  loading,
  className,
  'aria-label': ariaLabel,
}: ConfirmButtonProps) {
  const [open, setOpen] = useState(false);
  return (
    <>
      <Button type="button" variant={destructive ? 'destructive-outline' : variant} size={size} disabled={disabled} loading={loading} aria-label={ariaLabel} className={className} onClick={() => setOpen(true)}>
        {children}
      </Button>
      <AlertDialog open={open} onOpenChange={setOpen}>
        <AlertDialogContent>
          <AlertDialogTitle>{title}</AlertDialogTitle>
          <AlertDialogDescription>{description ?? vi.common.confirmText}</AlertDialogDescription>
          <AlertDialogFooter>
            <AlertDialogCancel>{vi.common.cancel}</AlertDialogCancel>
            <AlertDialogAction destructive={destructive} onClick={() => void onConfirm()}>
              {confirmLabel ?? vi.common.confirm}
            </AlertDialogAction>
          </AlertDialogFooter>
        </AlertDialogContent>
      </AlertDialog>
    </>
  );
}

import { forwardRef, type ComponentPropsWithoutRef, type ElementRef, type HTMLAttributes } from 'react';
import * as DialogPrimitive from '@radix-ui/react-dialog';
import { X } from 'lucide-react';
import { cn } from '@/lib/utils';
import { vi } from '@/i18n/vi';

export const Dialog = DialogPrimitive.Root;
export const DialogTrigger = DialogPrimitive.Trigger;
export const DialogClose = DialogPrimitive.Close;
export const DialogPortal = DialogPrimitive.Portal;

export const overlayClass =
  'fixed inset-0 z-50 bg-foreground/30 data-[state=open]:animate-[fade-in_var(--dur-base)_var(--ease-out)] data-[state=closed]:animate-[fade-out_var(--dur-fast)_var(--ease-out)]';

export const DialogOverlay = forwardRef<ElementRef<typeof DialogPrimitive.Overlay>, ComponentPropsWithoutRef<typeof DialogPrimitive.Overlay>>(
  function DialogOverlay({ className, ...props }, ref) {
    return <DialogPrimitive.Overlay ref={ref} className={cn(overlayClass, className)} {...props} />;
  },
);

export const dialogContentClass =
  'fixed top-1/2 left-1/2 z-50 grid max-h-[calc(100dvh-2rem)] w-[calc(100vw-2rem)] max-w-lg -translate-x-1/2 -translate-y-1/2 gap-4 overflow-y-auto rounded-lg border bg-popover p-6 text-popover-foreground shadow-pop data-[state=open]:animate-[dialog-in_var(--dur-base)_var(--ease-out)] data-[state=closed]:animate-[dialog-out_var(--dur-fast)_var(--ease-out)]';

/** Centered dialog: traps focus, returns it on close, Escape closes (ui-kit contract 5). */
export const DialogContent = forwardRef<ElementRef<typeof DialogPrimitive.Content>, ComponentPropsWithoutRef<typeof DialogPrimitive.Content>>(
  function DialogContent({ className, children, ...props }, ref) {
    return (
      <DialogPortal>
        <DialogOverlay />
        <DialogPrimitive.Content ref={ref} className={cn(dialogContentClass, className)} {...props}>
          {children}
          <DialogPrimitive.Close
            aria-label={vi.common.close}
            className="absolute top-3 right-3 grid size-9 place-items-center rounded-md text-muted-foreground hover:bg-secondary hover:text-foreground"
          >
            <X aria-hidden className="size-4" />
          </DialogPrimitive.Close>
        </DialogPrimitive.Content>
      </DialogPortal>
    );
  },
);

/** Right-hand sheet on desktop (ui-kit § Overlays); phones use the vaul Drawer instead. */
export const SheetContent = forwardRef<ElementRef<typeof DialogPrimitive.Content>, ComponentPropsWithoutRef<typeof DialogPrimitive.Content>>(
  function SheetContent({ className, children, ...props }, ref) {
    return (
      <DialogPortal>
        <DialogOverlay />
        <DialogPrimitive.Content
          ref={ref}
          className={cn(
            'fixed inset-y-0 right-0 z-50 flex w-full max-w-md flex-col gap-4 overflow-y-auto border-l bg-popover p-6 shadow-pop data-[state=open]:animate-[sheet-in_var(--dur-slow)_var(--ease-out)] data-[state=closed]:animate-[sheet-out_var(--dur-base)_var(--ease-out)]',
            className,
          )}
          {...props}
        >
          {children}
          <DialogPrimitive.Close
            aria-label={vi.common.close}
            className="absolute top-3 right-3 grid size-9 place-items-center rounded-md text-muted-foreground hover:bg-secondary hover:text-foreground"
          >
            <X aria-hidden className="size-4" />
          </DialogPrimitive.Close>
        </DialogPrimitive.Content>
      </DialogPortal>
    );
  },
);

export function DialogHeader({ className, ...props }: HTMLAttributes<HTMLDivElement>) {
  return <div className={cn('grid gap-1.5 pr-8', className)} {...props} />;
}

export function DialogFooter({ className, ...props }: HTMLAttributes<HTMLDivElement>) {
  return <div className={cn('flex flex-col-reverse gap-2 sm:flex-row sm:justify-end', className)} {...props} />;
}

export const DialogTitle = forwardRef<ElementRef<typeof DialogPrimitive.Title>, ComponentPropsWithoutRef<typeof DialogPrimitive.Title>>(
  function DialogTitle({ className, ...props }, ref) {
    return <DialogPrimitive.Title ref={ref} className={cn('font-display text-h2', className)} {...props} />;
  },
);

export const DialogDescription = forwardRef<
  ElementRef<typeof DialogPrimitive.Description>,
  ComponentPropsWithoutRef<typeof DialogPrimitive.Description>
>(function DialogDescription({ className, ...props }, ref) {
  return <DialogPrimitive.Description ref={ref} className={cn('text-sm text-muted-foreground', className)} {...props} />;
});

import { forwardRef, type ComponentPropsWithoutRef, type ElementRef } from 'react';
import * as CheckboxPrimitive from '@radix-ui/react-checkbox';
import * as SwitchPrimitive from '@radix-ui/react-switch';
import * as RadioGroupPrimitive from '@radix-ui/react-radio-group';
import { Check } from 'lucide-react';
import { cn } from '@/lib/utils';

const box =
  'peer shrink-0 border border-input bg-card transition-colors duration-[var(--dur-fast)] focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-ring disabled:cursor-not-allowed disabled:opacity-55';

export const Checkbox = forwardRef<ElementRef<typeof CheckboxPrimitive.Root>, ComponentPropsWithoutRef<typeof CheckboxPrimitive.Root>>(
  function Checkbox({ className, ...props }, ref) {
    return (
      <CheckboxPrimitive.Root
        ref={ref}
        className={cn(
          box,
          'grid size-5 place-items-center rounded-sm data-[state=checked]:border-primary data-[state=checked]:bg-primary data-[state=checked]:text-primary-foreground lg:[@media(pointer:fine)]:size-4',
          className,
        )}
        {...props}
      >
        <CheckboxPrimitive.Indicator>
          <Check aria-hidden className="size-3.5 lg:[@media(pointer:fine)]:size-3" strokeWidth={3} />
        </CheckboxPrimitive.Indicator>
      </CheckboxPrimitive.Root>
    );
  },
);

/** The knob is a fixed square that only translates, so it never misdraws (ui-kit § Base, the Platehouse bug). */
export const Switch = forwardRef<ElementRef<typeof SwitchPrimitive.Root>, ComponentPropsWithoutRef<typeof SwitchPrimitive.Root>>(
  function Switch({ className, ...props }, ref) {
    return (
      <SwitchPrimitive.Root
        ref={ref}
        className={cn(
          box,
          'relative inline-flex h-6 w-10 items-center rounded-full p-0.5 data-[state=checked]:border-primary data-[state=checked]:bg-primary',
          className,
        )}
        {...props}
      >
        <SwitchPrimitive.Thumb className="block size-[18px] rounded-full bg-foreground/80 transition-transform duration-[var(--dur-fast)] ease-[var(--ease-out)] data-[state=checked]:translate-x-4 data-[state=checked]:bg-primary-foreground" />
      </SwitchPrimitive.Root>
    );
  },
);

export const RadioGroup = forwardRef<ElementRef<typeof RadioGroupPrimitive.Root>, ComponentPropsWithoutRef<typeof RadioGroupPrimitive.Root>>(
  function RadioGroup({ className, ...props }, ref) {
    return <RadioGroupPrimitive.Root ref={ref} className={cn('grid gap-2', className)} {...props} />;
  },
);

export const RadioGroupItem = forwardRef<ElementRef<typeof RadioGroupPrimitive.Item>, ComponentPropsWithoutRef<typeof RadioGroupPrimitive.Item>>(
  function RadioGroupItem({ className, ...props }, ref) {
    return (
      <RadioGroupPrimitive.Item
        ref={ref}
        className={cn(box, 'grid size-5 place-items-center rounded-full data-[state=checked]:border-primary lg:[@media(pointer:fine)]:size-4', className)}
        {...props}
      >
        <RadioGroupPrimitive.Indicator className="size-2.5 rounded-full bg-primary lg:[@media(pointer:fine)]:size-2" />
      </RadioGroupPrimitive.Item>
    );
  },
);

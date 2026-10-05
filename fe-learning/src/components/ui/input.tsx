import type {
  HTMLAttributes,
  InputHTMLAttributes,
  LabelHTMLAttributes,
  ReactNode,
} from 'react';
import { forwardRef, useId } from 'react';

import { cn } from '@/lib/utils';

export type InputProps = InputHTMLAttributes<HTMLInputElement> & {
  label?: string;
  hint?: string;
  error?: string;
  leftIcon?: ReactNode;
  rightSlot?: ReactNode;
};

export const Input = forwardRef<HTMLInputElement, InputProps>(function Input(
  { className, label, hint, error, leftIcon, rightSlot, id, ...props },
  ref,
) {
  const generatedId = useId();
  const inputId: string = id ?? generatedId;
  const describedBy: string | undefined = error ? `${inputId}-error` : hint ? `${inputId}-hint` : undefined;

  const input = (
    <div className="relative">
      {leftIcon !== undefined && (
        <span className="pointer-events-none absolute left-3 top-1/2 -translate-y-1/2 text-slate-400 [&_svg]:size-4">
          {leftIcon}
        </span>
      )}
      <input
        ref={ref}
        id={inputId}
        aria-invalid={error !== undefined}
        aria-describedby={describedBy}
        className={cn(
          'h-10 w-full rounded-[--radius-field] border bg-white px-3 text-sm text-slate-900 shadow-subtle transition-colors placeholder:text-slate-400',
          'focus-visible:border-brand-400 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-brand-500/25',
          'disabled:cursor-not-allowed disabled:bg-slate-50 disabled:text-slate-400',
          error !== undefined && 'border-danger focus-visible:border-danger focus-visible:ring-danger/25',
          leftIcon !== undefined && 'pl-9',
          rightSlot !== undefined && 'pr-10',
          className,
        )}
        {...props}
      />
      {rightSlot !== undefined && (
        <span className="absolute right-2.5 top-1/2 -translate-y-1/2">{rightSlot}</span>
      )}
    </div>
  );

  if (label === undefined && hint === undefined && error === undefined) {
    return input;
  }

  return (
    <div className="space-y-1.5">
      {label !== undefined && (
        <Label htmlFor={inputId} className="text-slate-700">
          {label}
        </Label>
      )}
      {input}
      {error !== undefined ? (
        <p id={`${inputId}-error`} role="alert" className="text-xs text-danger">
          {error}
        </p>
      ) : hint !== undefined ? (
        <p id={`${inputId}-hint`} className="text-xs text-slate-500">
          {hint}
        </p>
      ) : null}
    </div>
  );
});

export const Label = forwardRef<HTMLLabelElement, LabelHTMLAttributes<HTMLLabelElement>>(
  function Label({ className, ...props }, ref) {
    return (
      <label
        ref={ref}
        className={cn('text-sm font-medium leading-none', className)}
        {...props}
      />
    );
  },
);

/** Accessible loading placeholder used while data resolves. */
export function Skeleton({ className, ...props }: HTMLAttributes<HTMLDivElement>) {
  return (
    <div
      aria-hidden="true"
      className={cn('animate-shimmer rounded-md bg-slate-200/70', className)}
      style={{ backgroundImage: 'linear-gradient(90deg, transparent, rgb(255 255 255 / 0.6), transparent)', backgroundSize: '200% 100%' }}
      {...props}
    />
  );
}
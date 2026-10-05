import type { LucideIcon } from 'lucide-react';
import type { HTMLAttributes, ReactElement, ReactNode } from 'react';

import { cn } from '@/lib/utils';

interface PageHeaderProps extends HTMLAttributes<HTMLDivElement> {
  title: string;
  description?: string;
  icon?: LucideIcon;
  actions?: ReactNode;
}

/** Consistent page title block with an optional icon and right-aligned actions. */
export function PageHeader({
  title,
  description,
  icon: Icon,
  actions,
  className,
  ...props
}: PageHeaderProps): ReactElement {
  return (
    <div className={cn('flex flex-col gap-4 sm:flex-row sm:items-start sm:justify-between', className)} {...props}>
      <div className="flex min-w-0 items-start gap-3.5">
        {Icon !== undefined && (
          <div className="flex size-10 shrink-0 items-center justify-center rounded-xl bg-brand-50">
            <Icon className="size-5 text-brand-600" aria-hidden="true" />
          </div>
        )}
        <div className="min-w-0">
          <h1 className="truncate text-xl font-semibold tracking-tight text-slate-900 sm:text-2xl">
            {title}
          </h1>
          {description !== undefined && (
            <p className="mt-1 text-sm text-slate-500">{description}</p>
          )}
        </div>
      </div>

      {actions !== undefined && (
        <div className="flex shrink-0 items-center gap-2">{actions}</div>
      )}
    </div>
  );
}
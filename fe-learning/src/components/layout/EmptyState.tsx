import type { LucideIcon } from 'lucide-react';
import type { ReactElement, ReactNode } from 'react';

interface EmptyStateProps {
  icon: LucideIcon;
  title: string;
  description?: string;
  action?: ReactNode;
}

/** Friendly zero-state used by every list view. */
export function EmptyState({ icon: Icon, title, description, action }: EmptyStateProps): ReactElement {
  return (
    <div className="flex flex-col items-center justify-center rounded-[--radius-card] border border-dashed border-slate-300 bg-white px-6 py-14 text-center">
      <div className="flex size-12 items-center justify-center rounded-2xl bg-slate-100">
        <Icon className="size-6 text-slate-400" aria-hidden="true" />
      </div>
      <h3 className="mt-4 text-sm font-semibold text-slate-900">{title}</h3>
      {description !== undefined && (
        <p className="mt-1 max-w-sm text-sm text-slate-500">{description}</p>
      )}
      {action !== undefined && <div className="mt-5">{action}</div>}
    </div>
  );
}
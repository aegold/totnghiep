import type { LucideIcon } from 'lucide-react';
import { TrendingDown, TrendingUp } from 'lucide-react';
import type { ReactElement } from 'react';

import { Card } from '@/components/ui/card';
import { cn } from '@/lib/utils';

interface StatCardProps {
  label: string;
  value: string | number;
  icon: LucideIcon;
  /** Percentage change vs. the previous period. */
  trend?: number;
  hint?: string;
  tone?: 'brand' | 'success' | 'warning' | 'neutral';
}

const TONES: Record<NonNullable<StatCardProps['tone']>, string> = {
  brand: 'bg-brand-50 text-brand-600',
  success: 'bg-emerald-50 text-emerald-600',
  warning: 'bg-amber-50 text-amber-600',
  neutral: 'bg-slate-100 text-slate-600',
};

/** KPI tile used across the three role dashboards. */
export function StatCard({
  label,
  value,
  icon: Icon,
  trend,
  hint,
  tone = 'brand',
}: StatCardProps): ReactElement {
  const isPositive: boolean = (trend ?? 0) >= 0;
  const TrendIcon: LucideIcon = isPositive ? TrendingUp : TrendingDown;

  return (
    <Card className="p-5">
      <div className="flex items-start justify-between gap-3">
        <div className="min-w-0">
          <p className="truncate text-sm font-medium text-slate-500">{label}</p>
          <p className="mt-1.5 text-2xl font-semibold tracking-tight text-slate-900">{value}</p>
        </div>
        <div className={cn('flex size-10 shrink-0 items-center justify-center rounded-xl', TONES[tone])}>
          <Icon className="size-5" aria-hidden="true" />
        </div>
      </div>

      {(trend !== undefined || hint !== undefined) && (
        <div className="mt-3 flex items-center gap-2 text-xs">
          {trend !== undefined && (
            <span
              className={cn(
                'inline-flex items-center gap-1 font-medium',
                isPositive ? 'text-emerald-600' : 'text-danger',
              )}
            >
              <TrendIcon className="size-3.5" aria-hidden="true" />
              {isPositive ? '+' : ''}
              {trend}%
            </span>
          )}
          {hint !== undefined && <span className="text-slate-400">{hint}</span>}
        </div>
      )}
    </Card>
  );
}
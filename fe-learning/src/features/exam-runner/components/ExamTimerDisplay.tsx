import { Clock } from 'lucide-react';
import type { ReactElement } from 'react';

import { cn } from '@/lib/utils';

export interface ExamTimerDisplayProps {
  /** Pre-formatted `HH:MM:SS`. */
  formatted: string;
  isUrgent: boolean;
  isCritical: boolean;
  isPaused?: boolean;
}

/**
 * Remaining-time readout.
 *
 * Pulses when time is short so it is noticeable peripherally, and switches to
 * red under one minute. The digit string itself is the accessible label; the
 * pulse is purely decorative and suppressed for reduced-motion users.
 */
export function ExamTimerDisplay({
  formatted,
  isUrgent,
  isCritical,
  isPaused = false,
}: ExamTimerDisplayProps): ReactElement {
  const shouldPulse: boolean = !isPaused && (isUrgent || isCritical);

  return (
    <div
      className={cn(
        'flex items-center gap-2 rounded-[--radius-field] border px-3 py-1.5 transition-colors',
        isCritical && 'border-red-300 bg-red-50 text-red-700',
        isUrgent && !isCritical && 'border-amber-300 bg-amber-50 text-amber-700',
        !isUrgent && 'border-slate-200 bg-slate-50 text-slate-700',
      )}
      role="timer"
      aria-live={isUrgent ? 'assertive' : 'off'}
    >
      <Clock className="size-4 shrink-0" aria-hidden="true" />

      <span className="text-sm font-medium text-slate-500">Thời gian còn lại</span>

      <span
        className={cn(
          'font-mono text-base font-semibold tabular-nums',
          shouldPulse && 'animate-pulse',
        )}
        aria-label={`Còn lại ${formatted}`}
      >
        {formatted}
      </span>
    </div>
  );
}

/** Progress bar for the bottom navigation. */
export function ExamProgressBar({
  answeredCount,
  totalCount,
  className,
}: {
  answeredCount: number;
  totalCount: number;
  className?: string;
}): ReactElement {
  const percent: number = totalCount === 0 ? 0 : Math.round((answeredCount / totalCount) * 100);

  return (
    <div className={cn('flex items-center gap-3', className)}>
      <div
        className="h-1.5 flex-1 overflow-hidden rounded-full bg-slate-200"
        role="progressbar"
        aria-valuenow={answeredCount}
        aria-valuemin={0}
        aria-valuemax={totalCount}
        aria-label="Số câu đã trả lời"
      >
        <div
          className="h-full rounded-full bg-brand-600 transition-[width] duration-300"
          style={{ width: `${percent}%` }}
        />
      </div>

      <span className="shrink-0 text-xs font-medium text-slate-600">
        Đã trả lời {answeredCount}/{totalCount} câu
      </span>
    </div>
  );
}
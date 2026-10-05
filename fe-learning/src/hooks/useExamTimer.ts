import { useCallback, useEffect, useMemo, useRef, useState } from 'react';

import { EXAM_TIMING } from '@/features/exam-runner/types';

/**
 * Drift-free exam countdown.
 *
 * ## Why not `setInterval` arithmetic
 * Decrementing a counter each tick accumulates drift, and browsers throttle
 * timers in background tabs — a 60-minute exam could silently gain minutes, or
 * fire long after the deadline. Worse, `performance.now()` is monotonic but
 * excludes system suspend time on most platforms, so a closed laptop would
 * resume with time still on the clock.
 *
 * ## How this stays honest
 * 1. The deadline is absolute: `serverStartTime + duration`, taken from the
 *    server. Remaining time is always `serverEndTime - Date.now()`.
 * 2. Ticks only trigger a *recomputation*; they never accumulate a delta, so
 *    tick frequency cannot affect the answer.
 * 3. `visibilitychange` / `focus` recompute immediately, closing any gap left
 *    by a throttled or suspended tab.
 * 4. `performance.now()` is used only to DETECT a suspension gap, never as the
 *    source of truth for time remaining.
 */

/** Thresholds, in seconds, that must each fire exactly once. */
const URGENT_THRESHOLD_S: number = EXAM_TIMING.urgentMinutes * 60;
const CRITICAL_THRESHOLD_S: number = EXAM_TIMING.criticalMinutes * 60;

export type ExamWarningLevel = 'urgent' | 'critical';

export interface UseExamTimerOptions {
  /** Paper duration in minutes. */
  durationMinutes: number;
  /** ISO-8601 or epoch ms start reported by the server. */
  serverStartTime: string | number;
  /** Overrides the derived deadline when the server sends one explicitly. */
  serverEndTime?: string | number;
  /** Fired once when each threshold is crossed. */
  onWarning?: (level: ExamWarningLevel, remainingSeconds: number) => void;
  /** Fired once when the deadline passes. */
  onTimeUp?: () => void;
  /** Optional heartbeat used to correct client/server clock skew. */
  onResync?: () => void;
  /** Set false to freeze the clock (e.g. while a blocking dialog is open). */
  enabled?: boolean;
}

export interface UseExamTimerResult {
  remainingSeconds: number;
  /** Below 5 minutes. */
  isUrgent: boolean;
  /** Below 1 minute. */
  isCritical: boolean;
  hasExpired: boolean;
  /** `HH:MM:SS`, always zero-padded, for the exam header. */
  formatted: string;
  /** Recompute now — call after a server resync. */
  sync: () => void;
}

/** Accepts ISO-8601 or epoch ms; returns NaN for anything unusable. */
const toEpochMs = (value: string | number): number => {
  const parsed: number = typeof value === 'number' ? value : Date.parse(value);
  return Number.isFinite(parsed) ? parsed : Number.NaN;
};

/** `HH:MM:SS`, zero-padded, clamped at zero. */
export const formatCountdown = (totalSeconds: number): string => {
  const safe: number = Math.max(0, Math.floor(totalSeconds));
  const hours: number = Math.floor(safe / 3600);
  const minutes: number = Math.floor((safe % 3600) / 60);
  const seconds: number = safe % 60;

  return [hours, minutes, seconds]
    .map((part: number) => String(part).padStart(2, '0'))
    .join(':');
};

/**
 * @param durationMinutes - paper duration
 * @param serverStartTime - ISO-8601 (or epoch ms) start time from the server
 */
export function useExamTimer(
  durationMinutes: number,
  serverStartTime: string | number,
  options: Omit<UseExamTimerOptions, 'durationMinutes' | 'serverStartTime'> = {},
): UseExamTimerResult {
  const { onWarning, onTimeUp, onResync, enabled = true } = options;

  /*
   * Absolute deadline in epoch ms. Computed once per paper and kept stable so
   * it never shifts as the component re-renders.
   */
  const endTimeMs: number = useMemo(() => {
    const explicit: number = options.serverEndTime !== undefined
      ? toEpochMs(options.serverEndTime)
      : Number.NaN;

    if (Number.isFinite(explicit)) {
      return explicit;
    }

    const startMs: number = toEpochMs(serverStartTime);
    return startMs + durationMinutes * 60_000;
  }, [durationMinutes, serverStartTime, options.serverEndTime]);

  const [remainingSeconds, setRemainingSeconds] = useState<number>(() =>
    Math.max(0, Math.ceil((endTimeMs - Date.now()) / 1000)),
  );

  /** Warnings already emitted — guarantees each threshold fires exactly once. */
  const warnedRef = useRef<Record<ExamWarningLevel, boolean>>({
    urgent: false,
    critical: false,
  });
  const timeUpFiredRef = useRef<boolean>(false);

  /** Monotonic marker used only to detect a suspended/stalled tab. */
  const lastTickPerfRef = useRef<number>(performance.now());

  /** Latest callbacks without re-subscribing listeners on every render. */
  const callbacksRef = useRef<{
    onWarning?: UseExamTimerOptions['onWarning'];
    onTimeUp?: () => void;
  }>({ onWarning, onTimeUp });
  callbacksRef.current = { onWarning, onTimeUp };

  /**
   * Recomputes from the absolute deadline. Single source of truth for
   * `remainingSeconds`, the thresholds, and expiry.
   */
  const recompute = useCallback((): void => {
    const seconds: number = Math.max(0, Math.ceil((endTimeMs - Date.now()) / 1000));

    setRemainingSeconds(seconds);

    /*
     * Emit in descending-threshold order so a tab suspended from 10 minutes
     * straight to 0 still reports "urgent" before "critical".
     */
    if (!warnedRef.current.critical && seconds <= CRITICAL_THRESHOLD_S) {
      warnedRef.current.critical = true;
      warnedRef.current.urgent = true; // implied; never warn "urgent" afterwards
      callbacksRef.current.onWarning?.('critical', seconds);
    } else if (!warnedRef.current.urgent && seconds <= URGENT_THRESHOLD_S) {
      warnedRef.current.urgent = true;
      callbacksRef.current.onWarning?.('urgent', seconds);
    }

    if (seconds <= 0 && !timeUpFiredRef.current) {
      timeUpFiredRef.current = true;
      callbacksRef.current.onTimeUp?.();
    }
  }, [endTimeMs]);

  /** Ticking only re-runs `recompute`; no state is accumulated between ticks. */
  useEffect(() => {
    if (!enabled) {
      return;
    }

    recompute();

    const intervalId: number = window.setInterval(() => {
      const gap: number = performance.now() - lastTickPerfRef.current;
      lastTickPerfRef.current = performance.now();

      /*
       * A gap far larger than the tick interval means the tab was throttled or
       * suspended. Re-derive immediately and offer the host a chance to
       * re-sync against the server clock.
       */
      if (gap > EXAM_TIMING.suspendThresholdMs * 2) {
        recompute();
        onResync?.();
      } else {
        recompute();
      }
    }, EXAM_TIMING.tickIntervalMs);

    return () => window.clearInterval(intervalId);
  }, [enabled, recompute, onResync]);

  /**
   * Close the gap left by a backgrounded tab the instant the learner returns.
   * This is what makes a closed-laptop resume land on the true remaining time.
   */
  useEffect(() => {
    if (!enabled) {
      return;
    }

    const handleVisibility = (): void => {
      if (document.visibilityState === 'visible') {
        lastTickPerfRef.current = performance.now();
        recompute();
      }
    };

    document.addEventListener('visibilitychange', handleVisibility);
    window.addEventListener('focus', handleVisibility);

    return () => {
      document.removeEventListener('visibilitychange', handleVisibility);
      window.removeEventListener('focus', handleVisibility);
    };
  }, [enabled, recompute]);

  const hasExpired: boolean = remainingSeconds <= 0;
  const isCritical: boolean =
    remainingSeconds > 0 && remainingSeconds <= CRITICAL_THRESHOLD_S;
  const isUrgent: boolean =
    remainingSeconds > 0 && remainingSeconds <= URGENT_THRESHOLD_S;

  const sync = useCallback((): void => {
    recompute();
    onResync?.();
  }, [recompute, onResync]);

  return {
    remainingSeconds,
    isUrgent,
    isCritical,
    hasExpired,
    formatted: formatCountdown(remainingSeconds),
    sync,
  };
}
import { act, renderHook } from '@testing-library/react';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';

import { formatCountdown, useExamTimer } from '@/hooks/useExamTimer';

interface MockClock {
  set: (value: number) => void;
  advance: (ms: number) => void;
  restore: () => void;
}

/** Installs a controllable `Date.now`. */
const mockNow = (start: number): MockClock => {
  let current = start;

  const spy = vi.spyOn(Date, 'now').mockImplementation(() => current);

  return {
    set: (value: number): void => {
      current = value;
    },
    advance: (ms: number): void => {
      current += ms;
    },
    restore: (): void => spy.mockRestore(),
  };
};

const START_MS: number = 1_700_000_000_000;

describe('formatCountdown', () => {
  it('zero-pads every component', () => {
    expect(formatCountdown(3661)).toBe('01:01:01');
    expect(formatCountdown(59)).toBe('00:00:59');
  });

  it('clamps negative values to zero', () => {
    expect(formatCountdown(-10)).toBe('00:00:00');
  });
});

describe('useExamTimer', () => {
  let clock: MockClock;

  beforeEach(() => {
    clock = mockNow(START_MS);

    /*
     * Only fake the timer functions. Vitest fakes `Date` by default, which
     * would replace the `Date.now` spy above and make `clock.advance()` a no-op
     * — the hook must keep reading the clock the test controls.
     */
    vi.useFakeTimers({
      toFake: ['setInterval', 'clearInterval', 'setTimeout', 'clearTimeout'],
    });
  });

  afterEach(() => {
    vi.useRealTimers();
    clock.restore();
  });

  it('derives remaining time from the absolute deadline', () => {
    const { result } = renderHook(() =>
      useExamTimer(60, new Date(START_MS).toISOString()),
    );

    expect(result.current.remainingSeconds).toBe(3600);
    expect(result.current.formatted).toBe('01:00:00');
  });

  it('prefers an explicit serverEndTime over duration + start', () => {
    const endTime: string = new Date(START_MS + 120_000).toISOString();

    const { result } = renderHook(() =>
      useExamTimer(999, new Date(START_MS).toISOString(), { serverEndTime: endTime }),
    );

    expect(result.current.remainingSeconds).toBe(120);
  });

  it('does not accumulate drift across many ticks', () => {
    const { result } = renderHook(() => useExamTimer(10, START_MS));

    /*
     * Simulate 100 ticks spanning 60s. If the hook decremented per tick, the
     * result would drift; deriving from the absolute deadline cannot.
     */
    for (let i = 0; i < 100; i += 1) {
      clock.advance(600);
      act(() => {
        vi.advanceTimersByTime(600);
      });
    }

    expect(result.current.remainingSeconds).toBe(600 - 60);
  });

  it('catches up after a long suspension', () => {
    const { result } = renderHook(() => useExamTimer(10, START_MS));

    /* Laptop closed for 5 minutes with no ticks running. */
    clock.advance(300_000);
    act(() => {
      vi.advanceTimersByTime(300_000);
    });

    expect(result.current.remainingSeconds).toBe(600 - 300);
  });

  it('fires the urgent warning exactly once', () => {
    const onWarning = vi.fn();

    const { result } = renderHook(() =>
      useExamTimer(10, START_MS, { onWarning }),
    );

    clock.advance(320_000); // 280s left -> under the 5 minute threshold
    act(() => {
      vi.advanceTimersByTime(1_000);
    });

    expect(result.current.isUrgent).toBe(true);
    expect(onWarning).toHaveBeenCalledTimes(1);
    expect(onWarning).toHaveBeenCalledWith('urgent', expect.any(Number));

    clock.advance(60_000);
    act(() => {
      vi.advanceTimersByTime(60_000);
    });

    /* Crossing the threshold repeatedly must not re-fire. */
    expect(onWarning).toHaveBeenCalledTimes(1);
  });

  it('reports only the critical level when suspended past both thresholds', () => {
    const onWarning = vi.fn();
    const onTimeUp = vi.fn();

    renderHook(() => useExamTimer(10, START_MS, { onWarning, onTimeUp }));

    /* Straight from 10 minutes remaining to zero in one jump. */
    clock.advance(600_000);
    act(() => {
      vi.advanceTimersByTime(1_000);
    });

    /*
     * Only "critical" is emitted: crossing the 1-minute threshold implies the
     * 5-minute one, so the learner gets one warning rather than two stacked
     * messages for the same instant.
     */
    const levels: string[] = onWarning.mock.calls.map((call: unknown[]) => String(call[0]));
    expect(levels).toEqual(['critical']);
    expect(onTimeUp).toHaveBeenCalledTimes(1);
  });

  it('invokes onTimeUp once when the deadline passes', () => {
    const onTimeUp = vi.fn();

    const { result } = renderHook(() => useExamTimer(1, START_MS, { onTimeUp }));

    clock.advance(61_000);
    act(() => {
      vi.advanceTimersByTime(1_000);
    });

    expect(result.current.hasExpired).toBe(true);
    expect(result.current.remainingSeconds).toBe(0);
    expect(onTimeUp).toHaveBeenCalledTimes(1);

    clock.advance(60_000);
    act(() => {
      vi.advanceTimersByTime(60_000);
    });

    expect(onTimeUp).toHaveBeenCalledTimes(1);
  });

  it('freezes when disabled', () => {
    const onTimeUp = vi.fn();
    const { result } = renderHook(() =>
      useExamTimer(1, START_MS, { onTimeUp, enabled: false }),
    );

    clock.advance(120_000);
    act(() => {
      vi.advanceTimersByTime(120_000);
    });

    expect(onTimeUp).not.toHaveBeenCalled();
    expect(result.current.hasExpired).toBe(false);
  });
});
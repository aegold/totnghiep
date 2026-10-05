import { useCallback, useEffect, useMemo, useRef, useState } from 'react';

import {
  PROCTORING_DEFAULTS,
  type ProctoringViolation,
  type ViolationKind,
} from '@/features/exam-runner/types';
import { VIOLATION_DETAIL } from '@/lib/i18n/labels';

/**
 * Client-side proctoring.
 *
 * ## Threat model — read this before trusting it
 * These controls are a DETERRENT AND AN INTEGRITY SIGNAL, not a security
 * boundary. Everything here runs on a machine the candidate controls:
 *  - DevTools cannot be reliably blocked. Chrome does not emit a `keydown` for
 *    F12 at all, and every shortcut can be sidestepped with another browser,
 *    a remapped keyboard, or devtools' own undockable window.
 *  - Clipboard and context-menu blocking is cosmetic; the OS and browser both
 *    offer alternate routes.
 *  - `tab_switch_count` originates in this client and is trivially spoofable.
 *
 * Enforcement that actually matters must therefore be server-side: the backend
 * owns the deadline and should reconcile `exam_submissions.tab_switch_count`
 * from its own signals rather than trusting the browser. This hook supplies
 * honest evidence for an invigilator and makes casual cheating awkward.
 */

/**
 * Detects a blocked shortcut.
 *
 * Matched in the capture phase because several of these combinations are
 * consumed by the browser before they would bubble to `window`.
 */
export const detectBlockedShortcut = (event: KeyboardEvent): ViolationKind | null => {
  const key: string = event.key.toLowerCase();

  /* F12 is the classic devtools toggle. */
  if (event.key === 'F12' || key === 'f12') {
    return 'DEVTOOLS_SHORTCUT';
  }

  const hasCtrl: boolean = event.ctrlKey || event.metaKey;
  if (!hasCtrl) {
    return null;
  }

  /* Ctrl/Cmd + Shift + I/J/C opens devtools under various modifiers. */
  if (event.shiftKey && (key === 'i' || key === 'j' || key === 'c')) {
    return 'DEVTOOLS_SHORTCUT';
  }

  /* Clipboard: copy, cut, paste. */
  if (key === 'c' || key === 'x' || key === 'v') {
    return 'COPY_ATTEMPT';
  }

  /* View source (u), print (p), save page (s). */
  if (key === 'u' || key === 'p' || key === 's') {
    return 'DEVTOOLS_SHORTCUT';
  }

  return null;
};

export interface UseExamProctoringOptions {
  /** Strikes tolerated before `onForceSubmit` fires. */
  maxViolations?: number;
  /** Switches closer together than this count as a single event. */
  debounceMs?: number;
  /** Set false to detach every listener (e.g. on the results screen). */
  enabled?: boolean;
  /** Fired once, when the strike limit is reached. */
  onForceSubmit?: (violations: ProctoringViolation[]) => void;
  /** Observe each strike, e.g. to raise a toast. */
  onViolation?: (violation: ProctoringViolation) => void;
}

export interface UseExamProctoringResult {
  violationCount: number;
  tabSwitchCount: number;
  isWarningModalOpen: boolean;
  acknowledgeWarning: () => void;
  violations: ProctoringViolation[];
  /** Strikes left before a forced submission. */
  strikesRemaining: number;
}

/**
 * @param maxViolations - strikes tolerated before forcing a submission
 */
export function useExamProctoring(
  maxViolations: number = PROCTORING_DEFAULTS.maxViolations,
  options: Omit<UseExamProctoringOptions, 'maxViolations'> = {},
): UseExamProctoringResult {
  const {
    debounceMs = PROCTORING_DEFAULTS.debounceMs,
    enabled = true,
    onForceSubmit,
    onViolation,
  } = options;

  const [violations, setViolations] = useState<ProctoringViolation[]>([]);
  const [tabSwitchCount, setTabSwitchCount] = useState<number>(0);
  const [isWarningModalOpen, setIsWarningModalOpen] = useState<boolean>(false);

  /** Timestamp of the last recorded strike, used for debouncing. */
  const lastViolationAtRef = useRef<number>(0);
  /** Guarantees a forced submission is requested at most once. */
  const forceSubmittedRef = useRef<boolean>(false);
  /** Mirror of `violations` for reads inside event handlers. */
  const violationsRef = useRef<ProctoringViolation[]>([]);

  /** Latest callbacks, so listeners need not be re-bound on every render. */
  const callbacksRef = useRef<{
    onForceSubmit?: UseExamProctoringOptions['onForceSubmit'];
    onViolation?: UseExamProctoringOptions['onViolation'];
  }>({ onForceSubmit, onViolation });
  callbacksRef.current = { onForceSubmit, onViolation };

  /**
   * Records one strike, subject to a short debounce.
   *
   * The debounce matters: `visibilitychange` also fires when the window is
   * merely minimised or briefly loses focus to a system notification, and
   * without it a single mis-click would cost the learner a strike.
   */
  const recordViolation = useCallback(
    (kind: ViolationKind): void => {
      const now: number = Date.now();

      if (now - lastViolationAtRef.current < debounceMs) {
        return;
      }
      lastViolationAtRef.current = now;

      const violation: ProctoringViolation = {
        kind,
        at: new Date(now).toISOString(),
        detail: VIOLATION_DETAIL[kind],
      };

      if (kind === 'TAB_HIDDEN') {
        setTabSwitchCount((current: number) => current + 1);
      }

      /*
       * The threshold check reads a ref rather than a `setState` updater:
       * updaters must stay pure (React may invoke them twice in StrictMode),
       * so side effects such as `onForceSubmit` cannot live inside one.
       */
      const next: ProctoringViolation[] = [...violationsRef.current, violation];
      violationsRef.current = next;

      setViolations(next);
      callbacksRef.current.onViolation?.(violation);

      if (next.length >= maxViolations && !forceSubmittedRef.current) {
        forceSubmittedRef.current = true;
        callbacksRef.current.onForceSubmit?.(next);
      }

      /* Acknowledgeable dialog so the learner knows a strike was recorded. */
      setIsWarningModalOpen(true);
    },
    [debounceMs, maxViolations],
  );

  useEffect(() => {
    if (!enabled) {
      return;
    }

    /* ---- Visibility: the primary tab-switch signal ---- */
    const handleVisibilityChange = (): void => {
      if (document.visibilityState === 'hidden') {
        recordViolation('TAB_HIDDEN');
      }
    };

    /* ---- Keyboard: block shortcut combos in the capture phase ---- */
    const handleKeyDown = (event: KeyboardEvent): void => {
      const kind: ViolationKind | null = detectBlockedShortcut(event);
      if (kind !== null) {
        event.preventDefault();
        event.stopPropagation();
        recordViolation(kind);
      }
    };

    /*
     * Chrome never fires keydown for F12, so keyup is the only observation
     * point. Blocking there is cosmetic but closes the common path.
     */
    const handleKeyUp = (event: KeyboardEvent): void => {
      if (event.key === 'F12') {
        event.preventDefault();
        recordViolation('DEVTOOLS_SHORTCUT');
      }
    };

    const handleContextMenu = (event: MouseEvent): void => {
      event.preventDefault();
      recordViolation('CONTEXT_MENU');
    };

    const handleClipboard = (event: ClipboardEvent): void => {
      event.preventDefault();
      recordViolation('COPY_ATTEMPT');
    };

    /* Stops question text being dragged off-screen to another window. */
    const handleDragStart = (event: DragEvent): void => {
      event.preventDefault();
    };

    const handleBeforePrint = (event: Event): void => {
      event.preventDefault();
    };

    document.addEventListener('visibilitychange', handleVisibilityChange);
    window.addEventListener('keydown', handleKeyDown, true);
    window.addEventListener('keyup', handleKeyUp, true);
    document.addEventListener('contextmenu', handleContextMenu);
    document.addEventListener('copy', handleClipboard);
    document.addEventListener('cut', handleClipboard);
    document.addEventListener('paste', handleClipboard);
    document.addEventListener('dragstart', handleDragStart);
    window.addEventListener('beforeprint', handleBeforePrint);

    /* Every listener is removed on unmount — an exam must not leak handlers
       into the rest of the SPA. */
    return () => {
      document.removeEventListener('visibilitychange', handleVisibilityChange);
      window.removeEventListener('keydown', handleKeyDown, true);
      window.removeEventListener('keyup', handleKeyUp, true);
      document.removeEventListener('contextmenu', handleContextMenu);
      document.removeEventListener('copy', handleClipboard);
      document.removeEventListener('cut', handleClipboard);
      document.removeEventListener('paste', handleClipboard);
      document.removeEventListener('dragstart', handleDragStart);
      window.removeEventListener('beforeprint', handleBeforePrint);
    };
  }, [enabled, recordViolation]);

  const acknowledgeWarning = useCallback((): void => setIsWarningModalOpen(false), []);

  return useMemo(
    () => ({
      violationCount: violations.length,
      tabSwitchCount,
      isWarningModalOpen,
      acknowledgeWarning,
      violations,
      strikesRemaining: Math.max(0, maxViolations - violations.length),
    }),
    [violations, tabSwitchCount, isWarningModalOpen, acknowledgeWarning, maxViolations],
  );
}
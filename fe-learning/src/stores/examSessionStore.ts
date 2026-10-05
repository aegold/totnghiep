import { create } from 'zustand';

import type { ExamCacheSnapshot } from '@/lib/examCache';
import { clearExamCache, readExamCache, writeExamCache } from '@/lib/examCache';
import type { SubmissionStatus } from '@/features/exam-runner/types';
import { parseResponse } from '@/features/question-engine';
import type { AnyQuestionPayload, AnyQuestionResponse, QuestionType } from '@/features/question-engine';

/**
 * Live state for one exam attempt.
 *
 * ## Why not Zustand `persist`
 * `persist` serialises with `JSON.stringify`, and Maps/Sets become `{}` — every
 * answer would silently vanish on reload. Persistence is therefore explicit: a
 * debounced write-through to `exam_cache_{submissionId}` via `examCache.ts`.
 *
 * ## Immutability
 * `answers` and `flaggedQuestions` are mutated copy-on-write (a new Map / Set
 * on every change). An in-place `Map.set` would leave the previous reference
 * intact, so subscribers would never re-render.
 */

/** Debounce for cache writes: short enough to feel instant, long enough to batch. */
const CACHE_WRITE_DEBOUNCE_MS = 400;

/** Minimal question shape the store needs; the full type lives in types.ts. */
export interface SessionQuestion {
  versionId: number;
  questionType: QuestionType;
  payload: AnyQuestionPayload;
}

export interface ExamSessionState {
  /** `null` until the paper has been started. */
  submissionId: number | null;
  /** Answers keyed by `question_versions.id`. */
  answers: Map<number, AnyQuestionResponse>;
  /** Version ids the learner marked for review. */
  flaggedQuestions: Set<number>;
  /** Questions in presentation order; the palette and navigation use this. */
  questions: readonly SessionQuestion[];
  currentIndex: number;
  status: SubmissionStatus;
  /** False when localStorage rejected the last write (quota / private mode). */
  isCacheHealthy: boolean;
  /** Epoch ms of the most recent successful cache write. */
  lastSavedAt: number | null;
  /** Set when a cached attempt was recovered after a crash. */
  restoredAt: number | null;
}

export type RestoreReason = 'ok' | 'empty' | 'expired' | 'corrupt';

export interface RestoreSummary {
  restored: boolean;
  answers: number;
  flagged: number;
  reason: RestoreReason;
}

export interface ExamSessionActions {
  /** Begins a new attempt. */
  start: (submissionId: number, questions: readonly SessionQuestion[]) => void;
  /** Persists one answer after validating it against its question type. */
  saveAnswer: (
    versionId: number,
    questionType: QuestionType,
    payload: AnyQuestionPayload,
    response: AnyQuestionResponse,
  ) => void;
  toggleFlag: (versionId: number) => void;
  goTo: (index: number) => void;
  next: () => void;
  previous: () => void;
  /** Reads `exam_cache_{submissionId}` and rehydrates. Never throws. */
  restoreFromCache: (submissionId: number) => RestoreSummary;
  /** Drops the local cache for this attempt. */
  clearCache: () => void;
  markSubmitting: () => void;
  markSubmitted: () => void;
  markFailed: () => void;
  /** Returns to a pristine state (used when leaving an attempt). */
  reset: () => void;
}

export type ExamSessionStore = ExamSessionState & ExamSessionActions;

const initialState: ExamSessionState = {
  submissionId: null,
  answers: new Map<number, AnyQuestionResponse>(),
  flaggedQuestions: new Set<number>(),
  questions: [],
  currentIndex: 0,
  status: 'not_started',
  isCacheHealthy: true,
  lastSavedAt: null,
  restoredAt: null,
};

/** Pending cache-write timer, deliberately kept outside React state. */
let writeTimerId: number | null = null;

/** Writes the current snapshot straight through to localStorage. */
const persistNow = (state: ExamSessionStore): boolean => {
  if (state.submissionId === null) {
    return true;
  }

  return writeExamCache(state.submissionId, {
    answers: state.answers,
    flaggedQuestions: state.flaggedQuestions,
    currentIndex: state.currentIndex,
  });
};

/**
 * Queues a debounced write. The tab-hidden listener installed by `ExamScreen`
 * calls `flushExamCache` so a crash or killed tab loses at most one batch.
 */
const schedulePersist = (state: ExamSessionStore): void => {
  if (state.submissionId === null) {
    return;
  }

  if (writeTimerId !== null) {
    window.clearTimeout(writeTimerId);
  }

  writeTimerId = window.setTimeout(() => {
    writeTimerId = null;
    const ok: boolean = persistNow(useExamSessionStore.getState());
    useExamSessionStore.setState({
      isCacheHealthy: ok,
      ...(ok ? { lastSavedAt: Date.now() } : {}),
    });
  }, CACHE_WRITE_DEBOUNCE_MS);
};

/** Immediate write, used when leaving or hiding the page. */
export const flushExamCache = (): void => {
  if (writeTimerId !== null) {
    window.clearTimeout(writeTimerId);
    writeTimerId = null;
  }
  persistNow(useExamSessionStore.getState());
};

export const useExamSessionStore = create<ExamSessionStore>()((set, get) => ({
  ...initialState,

  start: (submissionId, questions) =>
    set({ ...initialState, submissionId, questions, status: 'in_progress' }),

  saveAnswer: (versionId, questionType, payload, response) => {
    /*
     * Validate through the engine's repair function so a malformed or stale
     * document can never enter the store, and the shape always matches the
     * question type.
     */
    const sanitized = parseResponse(
      questionType,
      response,
      payload,
    ) as AnyQuestionResponse;

    set((state: ExamSessionStore) => {
      /* Copy-on-write: a new Map keeps change detection working. */
      const answers = new Map(state.answers);
      answers.set(versionId, sanitized);
      return { answers };
    });

    schedulePersist(get());
  },

  toggleFlag: (versionId) => {
    set((state: ExamSessionStore) => {
      const flaggedQuestions = new Set(state.flaggedQuestions);
      if (flaggedQuestions.has(versionId)) {
        flaggedQuestions.delete(versionId);
      } else {
        flaggedQuestions.add(versionId);
      }
      return { flaggedQuestions };
    });

    schedulePersist(get());
  },

  goTo: (index) => {
    const { currentIndex, questions } = get();
    if (index < 0 || index >= questions.length || index === currentIndex) {
      return;
    }
    set({ currentIndex: index });
    schedulePersist(get());
  },

  next: () => {
    const { currentIndex, questions } = get();
    if (currentIndex + 1 < questions.length) {
      set({ currentIndex: currentIndex + 1 });
      schedulePersist(get());
    }
  },

  previous: () => {
    const { currentIndex } = get();
    if (currentIndex > 0) {
      set({ currentIndex: currentIndex - 1 });
      schedulePersist(get());
    }
  },

  restoreFromCache: (submissionId) => {
    const result = readExamCache(submissionId);

    if (result.status !== 'ok') {
      const reason: RestoreReason = result.status;
      return { restored: false, answers: 0, flagged: 0, reason };
    }

    const snapshot: ExamCacheSnapshot = result.snapshot;
    const { questions } = get();

    /*
     * Re-validate each cached answer against the CURRENT payload. A cache
     * written against an older question version may reference options that no
     * longer exist; `parseResponse` strips those rather than letting them be
     * submitted.
     */
    const answers = new Map<number, AnyQuestionResponse>();

    for (const question of questions) {
      const cached = snapshot.answers.get(question.versionId);
      if (cached !== undefined) {
        answers.set(
          question.versionId,
          parseResponse(question.questionType, cached, question.payload) as AnyQuestionResponse,
        );
      }
    }

    const flaggedQuestions = new Set<number>(
      [...snapshot.flaggedQuestions].filter((versionId: number) =>
        questions.some((question) => question.versionId === versionId),
      ),
    );

    set({
      answers,
      flaggedQuestions,
      currentIndex: Math.min(snapshot.currentIndex, Math.max(0, questions.length - 1)),
      restoredAt: Date.now(),
    });

    return {
      restored: answers.size > 0 || flaggedQuestions.size > 0,
      answers: answers.size,
      flagged: flaggedQuestions.size,
      reason: 'ok',
    };
  },

  clearCache: () => {
    const { submissionId } = get();
    if (submissionId !== null) {
      clearExamCache(submissionId);
    }
    set({ lastSavedAt: null });
  },

  markSubmitting: () => set({ status: 'submitting' }),
  markSubmitted: () => set({ status: 'submitted' }),
  markFailed: () => set({ status: 'failed' }),

  reset: () => {
    const { submissionId } = get();
    if (submissionId !== null) {
      clearExamCache(submissionId);
    }
    set({ ...initialState });
  },
}));
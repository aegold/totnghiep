import type { AnyQuestionResponse } from '@/features/question-engine';

/**
 * Crash-tolerant answer cache.
 *
 * ## Why this is hand-rolled rather than Zustand `persist`
 * `persist` + `createJSONStorage` runs `JSON.stringify`, and Maps/Sets
 * serialise to `{}` — every answer would silently vanish on reload. This module
 * serialises to plain records explicitly and hydrates back into Maps/Sets.
 *
 * ## Keying
 * One cache per attempt, under `exam_cache_{submissionId}`, so two exams never
 * bleed into each other and an attempt can be cleared independently.
 */

const CACHE_PREFIX = 'exam_cache_';

/** Bumped whenever the cached shape changes; older caches are discarded. */
export const CACHE_SCHEMA_VERSION = 1;

/**
 * Caches older than this are ignored. Guards against restoring yesterday's
 * answers into a paper that has since been regenerated or reset.
 */
export const CACHE_TTL_MS = 12 * 60 * 60 * 1000;

/** Scoped storage key for an attempt. */
export const examCacheKey = (submissionId: number): string =>
  `${CACHE_PREFIX}${submissionId}`;

/** On-disk shape: Maps and Sets flattened into JSON-safe records. */
export interface SerializedExamCache {
  schemaVersion: number;
  /** Epoch ms of the last successful write. */
  savedAt: number;
  /** versionId -> student response. */
  answers: Record<string, AnyQuestionResponse>;
  flaggedVersionIds: number[];
  currentIndex: number;
}

/** Hydrated shape handed back to the store. */
export interface ExamCacheSnapshot {
  answers: Map<number, AnyQuestionResponse>;
  flaggedQuestions: Set<number>;
  currentIndex: number;
  savedAt: number;
}

export type ExamCacheReadResult =
  | { status: 'ok'; snapshot: ExamCacheSnapshot }
  | { status: 'empty' }
  | { status: 'expired' }
  | { status: 'corrupt'; reason: string };

/** Flattens live Maps/Sets into the serialisable record. */
export const serializeExamCache = (input: {
  answers: ReadonlyMap<number, AnyQuestionResponse>;
  flaggedQuestions: ReadonlySet<number>;
  currentIndex: number;
}): SerializedExamCache => {
  const answers: Record<string, AnyQuestionResponse> = {};

  for (const [versionId, response] of input.answers) {
    answers[String(versionId)] = response;
  }

  return {
    schemaVersion: CACHE_SCHEMA_VERSION,
    savedAt: Date.now(),
    answers,
    flaggedVersionIds: [...input.flaggedQuestions],
    currentIndex: input.currentIndex,
  };
};

/** Rebuilds Maps/Sets from the on-disk record. */
export const deserializeExamCache = (
  raw: SerializedExamCache,
): ExamCacheSnapshot => {
  const answers = new Map<number, AnyQuestionResponse>();

  for (const [key, response] of Object.entries(raw.answers ?? {})) {
    const versionId: number = Number.parseInt(key, 10);
    if (Number.isInteger(versionId) && typeof response === 'object' && response !== null) {
      answers.set(versionId, response);
    }
  }

  return {
    answers,
    flaggedQuestions: new Set<number>(
      (raw.flaggedVersionIds ?? []).filter(
        (id): id is number => typeof id === 'number' && Number.isInteger(id),
      ),
    ),
    currentIndex: Number.isInteger(raw.currentIndex) ? raw.currentIndex : 0,
    savedAt: raw.savedAt,
  };
};

const isRecord = (value: unknown): value is Record<string, unknown> =>
  typeof value === 'object' && value !== null;

/**
 * Reads the cache for an attempt.
 *
 * Never throws: a corrupted cache is reported, not propagated, because losing
 * the cache must not stop the learner from taking the exam.
 */
export const readExamCache = (
  submissionId: number,
  now: number = Date.now(),
): ExamCacheReadResult => {
  let raw: string | null;

  try {
    raw = window.localStorage.getItem(examCacheKey(submissionId));
  } catch (error: unknown) {
    /* Private browsing / storage disabled — treat as an empty cache. */
    return { status: 'corrupt', reason: `storage unreadable: ${String(error)}` };
  }

  if (raw === null) {
    return { status: 'empty' };
  }

  let parsed: unknown;
  try {
    parsed = JSON.parse(raw);
  } catch {
    return { status: 'corrupt', reason: 'malformed JSON' };
  }

  if (!isRecord(parsed)) {
    return { status: 'corrupt', reason: 'unexpected payload shape' };
  }

  if (parsed['schemaVersion'] !== CACHE_SCHEMA_VERSION) {
    return { status: 'corrupt', reason: 'schema version mismatch' };
  }

  const savedAt: unknown = parsed['savedAt'];
  if (typeof savedAt !== 'number' || now - savedAt > CACHE_TTL_MS) {
    return { status: 'expired' };
  }

  try {
    return { status: 'ok', snapshot: deserializeExamCache(parsed as unknown as SerializedExamCache) };
  } catch (error: unknown) {
    return { status: 'corrupt', reason: `deserialisation failed: ${String(error)}` };
  }
};

/**
 * Persists the current answers. Returns `false` when storage is unavailable or
 * full so the caller can surface a visible "not saved" state rather than
 * silently losing work.
 */
export const writeExamCache = (
  submissionId: number,
  input: {
    answers: ReadonlyMap<number, AnyQuestionResponse>;
    flaggedQuestions: ReadonlySet<number>;
    currentIndex: number;
  },
): boolean => {
  try {
    window.localStorage.setItem(
      examCacheKey(submissionId),
      JSON.stringify(serializeExamCache(input)),
    );
    return true;
  } catch {
    /* QuotaExceededError or storage disabled. */
    return false;
  }
};

/** Removes the cache for an attempt. Called once a submission is acknowledged. */
export const clearExamCache = (submissionId: number): void => {
  try {
    window.localStorage.removeItem(examCacheKey(submissionId));
  } catch {
    /* Nothing actionable — the attempt is already being finalised. */
  }
};
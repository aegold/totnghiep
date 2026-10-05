import { beforeEach, describe, expect, it, vi } from 'vitest';

import {
  CACHE_SCHEMA_VERSION,
  CACHE_TTL_MS,
  clearExamCache,
  deserializeExamCache,
  examCacheKey,
  readExamCache,
  serializeExamCache,
  writeExamCache,
} from '@/lib/examCache';
import type { AnyQuestionResponse } from '@/features/question-engine';

const SUBMISSION_ID: number = 4242;

const sampleAnswers = (): Map<number, AnyQuestionResponse> =>
  new Map<number, AnyQuestionResponse>([
    [9001, { selectedOptionIds: ['opt_a'] }],
    [9002, { answers: { twoSubscript: '2' } }],
  ]);

beforeEach(() => {
  window.localStorage.clear();
});

describe('examCacheKey', () => {
  it('scopes the key to the submission id', () => {
    expect(examCacheKey(77)).toBe('exam_cache_77');
  });
});

describe('serialize / deserialize', () => {
  it('round-trips Maps and Sets into plain JSON', () => {
    const flagged = new Set<number>([9001, 9003]);

    const raw = serializeExamCache({
      answers: sampleAnswers(),
      flaggedQuestions: flagged,
      currentIndex: 2,
    });

    /* Must be JSON-safe, which is exactly what JSON.stringify would produce. */
    expect(JSON.parse(JSON.stringify(raw))).toEqual(raw);

    const restored = deserializeExamCache(raw);
    expect(restored.answers.get(9001)).toEqual({ selectedOptionIds: ['opt_a'] });
    expect(restored.flaggedQuestions.has(9003)).toBe(true);
    expect(restored.currentIndex).toBe(2);
  });

  it('produces genuinely different objects (no shared references)', () => {
    const answers = sampleAnswers();
    const first = serializeExamCache({ answers, flaggedQuestions: new Set(), currentIndex: 0 });
    const second = serializeExamCache({ answers, flaggedQuestions: new Set(), currentIndex: 0 });

    expect(first).not.toBe(second);
    expect(first.answers).not.toBe(second.answers);
  });
});

describe('readExamCache', () => {
  it('round-trips through localStorage', () => {
    expect(
      writeExamCache(SUBMISSION_ID, {
        answers: sampleAnswers(),
        flaggedQuestions: new Set([9002]),
        currentIndex: 1,
      }),
    ).toBe(true);

    const result = readExamCache(SUBMISSION_ID);
    expect(result.status).toBe('ok');

    if (result.status === 'ok') {
      expect(result.snapshot.answers.size).toBe(2);
      expect(result.snapshot.flaggedQuestions.has(9002)).toBe(true);
    }
  });

  it('reports empty when nothing was cached', () => {
    expect(readExamCache(999).status).toBe('empty');
  });

  it('reports corrupt for malformed JSON instead of throwing', () => {
    window.localStorage.setItem(examCacheKey(SUBMISSION_ID), '{ not json');
    expect(readExamCache(SUBMISSION_ID).status).toBe('corrupt');
  });

  it('rejects a cache written by a different schema version', () => {
    window.localStorage.setItem(
      examCacheKey(SUBMISSION_ID),
      JSON.stringify({ schemaVersion: CACHE_SCHEMA_VERSION + 1, savedAt: Date.now(), answers: {}, flaggedVersionIds: [], currentIndex: 0 }),
    );

    expect(readExamCache(SUBMISSION_ID).status).toBe('corrupt');
  });

  it('rejects a cache older than the TTL', () => {
    window.localStorage.setItem(
      examCacheKey(SUBMISSION_ID),
      JSON.stringify({
        schemaVersion: CACHE_SCHEMA_VERSION,
        savedAt: Date.now() - CACHE_TTL_MS - 1_000,
        answers: {},
        flaggedVersionIds: [],
        currentIndex: 0,
      }),
    );

    expect(readExamCache(SUBMISSION_ID).status).toBe('expired');
  });

  it('keeps attempts isolated from one another', () => {
    writeExamCache(1, { answers: sampleAnswers(), flaggedQuestions: new Set(), currentIndex: 0 });
    writeExamCache(2, { answers: new Map(), flaggedQuestions: new Set(), currentIndex: 0 });

    const first = readExamCache(1);
    const second = readExamCache(2);

    expect(first.status === 'ok' && first.snapshot.answers.size).toBe(2);
    expect(second.status === 'ok' && second.snapshot.answers.size).toBe(0);
  });
});

describe('writeExamCache', () => {
  it('reports failure instead of throwing when storage is unavailable', () => {
    /* Simulate private browsing / a full quota. */
    const spy = vi.spyOn(Storage.prototype, 'setItem').mockImplementation(() => {
      throw new DOMException('QuotaExceededError');
    });

    const ok: boolean = writeExamCache(SUBMISSION_ID, {
      answers: sampleAnswers(),
      flaggedQuestions: new Set(),
      currentIndex: 0,
    });

    expect(ok).toBe(false);
    spy.mockRestore();
  });
});

describe('clearExamCache', () => {
  it('removes only the targeted attempt', () => {
    writeExamCache(1, { answers: sampleAnswers(), flaggedQuestions: new Set(), currentIndex: 0 });
    writeExamCache(2, { answers: sampleAnswers(), flaggedQuestions: new Set(), currentIndex: 0 });

    clearExamCache(1);

    expect(readExamCache(1).status).toBe('empty');
    expect(readExamCache(2).status).toBe('ok');
  });
});
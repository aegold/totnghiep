import { beforeEach, describe, expect, it } from 'vitest';

import { CACHE_SCHEMA_VERSION, examCacheKey, writeExamCache } from '@/lib/examCache';
import { flushExamCache, useExamSessionStore } from '@/stores/examSessionStore';
import type { SessionQuestion } from '@/stores/examSessionStore';
import { QuestionType } from '@/features/question-engine';
import type { AnyQuestionResponse } from '@/features/question-engine';

const SUBMISSION_ID: number = 555;

const questions: SessionQuestion[] = [
  {
    versionId: 9001,
    questionType: QuestionType.MULTIPLE_CHOICE,
    payload: {
      prompt: 'Pick one',
      allowMultiple: false,
      options: [
        { id: 'opt_a', text: 'A' },
        { id: 'opt_b', text: 'B' },
      ],
    },
  },
  {
    versionId: 9002,
    questionType: QuestionType.FILL_IN_BLANK,
    payload: { prompt: 'H{{two}}O', blanks: [{ id: 'two' }] },
  },
];

/** Non-null accessor, since `noUncheckedIndexedAccess` widens array reads. */
const mc = (): SessionQuestion => questions[0] as SessionQuestion;

beforeEach(() => {
  window.localStorage.clear();
  useExamSessionStore.getState().reset();
});

describe('examSessionStore — answers', () => {
  it('stores an answer under its version id', () => {
    const store = useExamSessionStore.getState();
    store.start(SUBMISSION_ID, questions);
    store.saveAnswer(9001, QuestionType.MULTIPLE_CHOICE, mc().payload, {
      selectedOptionIds: ['opt_a'],
    } as AnyQuestionResponse);

    expect(useExamSessionStore.getState().answers.get(9001)).toEqual({
      selectedOptionIds: ['opt_a'],
    });
  });

  it('uses copy-on-write so the Map reference changes', () => {
    useExamSessionStore.getState().start(SUBMISSION_ID, questions);
    const before: Map<number, AnyQuestionResponse> = useExamSessionStore.getState().answers;

    useExamSessionStore.getState().saveAnswer(
      9001,
      QuestionType.MULTIPLE_CHOICE,
      mc().payload,
      { selectedOptionIds: ['opt_a'] } as AnyQuestionResponse,
    );

    const after: Map<number, AnyQuestionResponse> = useExamSessionStore.getState().answers;

    /* A new reference is what makes Zustand subscribers re-render. */
    expect(after).not.toBe(before);
    expect(before.size).toBe(0);
    expect(after.size).toBe(1);
  });

  it('sanitises a malformed response before storing it', () => {
    const store = useExamSessionStore.getState();
    store.start(SUBMISSION_ID, questions);

    /* "deleted_option" no longer exists in the payload. */
    store.saveAnswer(9001, QuestionType.MULTIPLE_CHOICE, mc().payload, {
      selectedOptionIds: ['opt_a', 'deleted_option'],
    } as AnyQuestionResponse);

    expect(useExamSessionStore.getState().answers.get(9001)).toEqual({
      selectedOptionIds: ['opt_a'],
    });
  });
});

describe('examSessionStore — flags and navigation', () => {
  it('toggles a flag on and off with copy-on-write', () => {
    const store = useExamSessionStore.getState();
    store.start(SUBMISSION_ID, questions);

    store.toggleFlag(9001);
    const afterAdd = useExamSessionStore.getState().flaggedQuestions;
    expect(afterAdd.has(9001)).toBe(true);

    store.toggleFlag(9001);
    const afterRemove = useExamSessionStore.getState().flaggedQuestions;
    expect(afterRemove.has(9001)).toBe(false);
    expect(afterRemove).not.toBe(afterAdd);
  });

  it('moves within bounds only', () => {
    const store = useExamSessionStore.getState();
    store.start(SUBMISSION_ID, questions);

    store.previous();
    expect(useExamSessionStore.getState().currentIndex).toBe(0);

    store.next();
    expect(useExamSessionStore.getState().currentIndex).toBe(1);

    store.next(); // already last
    expect(useExamSessionStore.getState().currentIndex).toBe(1);

    store.goTo(-1);
    expect(useExamSessionStore.getState().currentIndex).toBe(1);

    store.goTo(99);
    expect(useExamSessionStore.getState().currentIndex).toBe(1);
  });
});

describe('examSessionStore — cache round trip', () => {
  it('persists answers as JSON, not as an empty Map', () => {
    const store = useExamSessionStore.getState();
    store.start(SUBMISSION_ID, questions);
    store.saveAnswer(9001, QuestionType.MULTIPLE_CHOICE, mc().payload, {
      selectedOptionIds: ['opt_a'],
    } as AnyQuestionResponse);
    store.toggleFlag(9002);

    flushExamCache();

    const raw: unknown = JSON.parse(
      window.localStorage.getItem(examCacheKey(SUBMISSION_ID)) ?? 'null',
    );

    /*
     * Regression guard for the Map/Set serialisation trap: `JSON.stringify(new
     * Map(...))` yields `{}`, which would silently lose every answer.
     */
    expect(raw).toMatchObject({
      schemaVersion: CACHE_SCHEMA_VERSION,
      answers: { '9001': { selectedOptionIds: ['opt_a'] } },
      flaggedVersionIds: [9002],
    });
  });

  it('restores answers, flags and position after a crash', () => {
    useExamSessionStore.getState().start(SUBMISSION_ID, questions);
    useExamSessionStore.getState().saveAnswer(
      9001,
      QuestionType.MULTIPLE_CHOICE,
      mc().payload,
      { selectedOptionIds: ['opt_b'] } as AnyQuestionResponse,
    );
    useExamSessionStore.getState().toggleFlag(9002);
    useExamSessionStore.getState().goTo(1);
    flushExamCache();

    /*
     * Simulate a reload WITHOUT `reset()`, which would delete the very cache we
     * are trying to recover. Blanking the in-memory state models a fresh page.
     */
    useExamSessionStore.setState({
      submissionId: null,
      answers: new Map(),
      flaggedQuestions: new Set(),
      questions: [],
      currentIndex: 0,
      status: 'not_started',
      lastSavedAt: null,
      restoredAt: null,
    });
    useExamSessionStore.getState().start(SUBMISSION_ID, questions);

    const summary = useExamSessionStore.getState().restoreFromCache(SUBMISSION_ID);

    expect(summary).toMatchObject({ restored: true, answers: 1, flagged: 1, reason: 'ok' });
    expect(useExamSessionStore.getState().answers.get(9001)).toEqual({
      selectedOptionIds: ['opt_b'],
    });
    expect(useExamSessionStore.getState().flaggedQuestions.has(9002)).toBe(true);
    expect(useExamSessionStore.getState().currentIndex).toBe(1);
  });

  it('drops cached entries referencing an unknown version', () => {
    writeExamCache(SUBMISSION_ID, {
      answers: new Map<number, AnyQuestionResponse>([
        [9001, { selectedOptionIds: ['opt_a'] }],
        [1234, { selectedOptionIds: ['ghost'] }],
      ]),
      flaggedQuestions: new Set<number>([9002, 4321]),
      currentIndex: 0,
    });

    useExamSessionStore.getState().start(SUBMISSION_ID, questions);
    const summary = useExamSessionStore.getState().restoreFromCache(SUBMISSION_ID);

    expect(summary.answers).toBe(1);
    expect(summary.flagged).toBe(1);
    expect(useExamSessionStore.getState().answers.has(1234)).toBe(false);
    expect(useExamSessionStore.getState().flaggedQuestions.has(4321)).toBe(false);
  });

  it('clamps a cached position beyond the paper length', () => {
    writeExamCache(SUBMISSION_ID, {
      answers: new Map(),
      flaggedQuestions: new Set(),
      currentIndex: 99,
    });

    useExamSessionStore.getState().start(SUBMISSION_ID, questions);
    useExamSessionStore.getState().restoreFromCache(SUBMISSION_ID);

    expect(useExamSessionStore.getState().currentIndex).toBe(1);
  });

  it('reports empty when nothing is cached', () => {
    useExamSessionStore.getState().start(SUBMISSION_ID, questions);

    expect(useExamSessionStore.getState().restoreFromCache(SUBMISSION_ID)).toEqual({
      restored: false,
      answers: 0,
      flagged: 0,
      reason: 'empty',
    });
  });
});

describe('examSessionStore — lifecycle', () => {
  it('clears the cache and resets state', () => {
    const store = useExamSessionStore.getState();
    store.start(SUBMISSION_ID, questions);
    store.saveAnswer(9001, QuestionType.MULTIPLE_CHOICE, mc().payload, {
      selectedOptionIds: ['opt_a'],
    } as AnyQuestionResponse);
    flushExamCache();

    useExamSessionStore.getState().reset();

    const state = useExamSessionStore.getState();
    expect(state.submissionId).toBeNull();
    expect(state.answers.size).toBe(0);
    expect(state.status).toBe('not_started');
    expect(window.localStorage.getItem(examCacheKey(SUBMISSION_ID))).toBeNull();
  });
});
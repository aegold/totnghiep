import { describe, expect, it } from 'vitest';

import { QuestionType } from '@/features/question-engine';
import type { MultipleChoicePayload, OrderingPayload } from '@/features/question-engine';
import {
  assertNoSolutionLeakage,
  formatQuestionPayloads,
  toQuestionRequestBody,
  type QuestionFormValues,
} from '@/features/teacher/schemas/questionFormSchema';

/**
 * THE MOST IMPORTANT TEST IN THIS STAGE.
 *
 * `content_payload` is served to every student. If an answer key or grading
 * explanation ever appears in it, the exam is compromised — one student reading
 * the page source gets every correct answer.
 *
 * These assert the split holds for every question type, and that the runtime
 * guard actually fires when violated.
 */

const mcqValues = (): QuestionFormValues => ({
  questionType: QuestionType.MULTIPLE_CHOICE,
  prompt: 'What is $O(n \\log n)$?',
  hint: 'Hint text',
  allowMultiple: false,
  options: [
    { id: 'a', text: 'Linear', isCorrect: false, explanation: 'WRONG because…' },
    { id: 'b', text: 'Linearithmic', isCorrect: true, explanation: 'RIGHT because…' },
  ],
  gradingNote: 'Full marks only for linearithmic.',
});

const fibValues = (): QuestionFormValues => ({
  questionType: QuestionType.FILL_IN_BLANK,
  prompt: 'R is {{gasConstant}} J/(mol·K).',
  hint: '',
  blanks: [
    {
      id: 'gasConstant',
      hint: 'number',
      acceptedAnswers: ['8.314', '8.31'],
      pattern: '^8\\.\\d+$',
    },
  ],
  gradingNote: 'Accept 8.31.',
});

const matchingValues = (): QuestionFormValues => ({
  questionType: QuestionType.MATCHING,
  prompt: 'Match protocols to layers.',
  hint: '',
  leftItems: [{ id: 'l1', text: 'TCP' }],
  rightItems: [{ id: 'r1', text: 'Transport' }],
  pairs: [{ leftId: 'l1', rightId: 'r1', explanation: 'TCP is transport.' }],
  explainEachMatch: true,
  gradingNote: '',
});

const orderingValues = (): QuestionFormValues => ({
  questionType: QuestionType.ORDERING,
  prompt: 'Order the handshake.',
  hint: '',
  items: [
    { id: 'i1', text: 'SYN' },
    { id: 'i2', text: 'ACK' },
  ],
  correctOrder: ['i1', 'i2'],
  gradingNote: '',
});

const ALL_FORMS: readonly (() => QuestionFormValues)[] = [
  mcqValues,
  fibValues,
  matchingValues,
  orderingValues,
];

describe('content_payload never contains solution data', () => {
  it('leaks no forbidden key, for any question type', () => {
    for (const build of ALL_FORMS) {
      const { content_payload } = formatQuestionPayloads(build());
      /* Throws if any forbidden key is present at any depth. */
      expect(() => assertNoSolutionLeakage(content_payload)).not.toThrow();
    }
  });

  it('survives JSON round-trip without leaking', () => {
    /*
     * Serialising is what actually ships to the browser, so the guard must hold
     * on the serialised form, not merely the in-memory object.
     */
    for (const build of ALL_FORMS) {
      const serialised: string = JSON.stringify(
        formatQuestionPayloads(build()).content_payload,
      );

      for (const forbidden of [
        'isCorrect',
        'correctOptionIds',
        'correctOrder',
        'correctPairs',
        'acceptedAnswers',
        'gradingNote',
        'explanation',
      ]) {
        expect(serialised).not.toContain(forbidden);
      }
    }
  });

  it('keeps the answer key in solution_payload for every type', () => {
    expect(formatQuestionPayloads(mcqValues()).solution_payload).toMatchObject({
      correctOptionIds: ['b'],
    });
    expect(formatQuestionPayloads(fibValues()).solution_payload).toMatchObject({
      acceptedAnswers: { gasConstant: ['8.314', '8.31'] },
      patterns: { gasConstant: '^8\\.\\d+$' },
    });
    expect(formatQuestionPayloads(matchingValues()).solution_payload).toMatchObject({
      correctPairs: { l1: 'r1' },
    });
    expect(formatQuestionPayloads(orderingValues()).solution_payload).toMatchObject({
      correctOrder: ['i1', 'i2'],
    });
  });

  it('strips per-option explanation text out of the public MCQ payload', () => {
    const payload = formatQuestionPayloads(mcqValues()).content_payload as MultipleChoicePayload;

    /* Options keep id + text only. */
    for (const option of payload.options) {
      expect(Object.keys(option).sort()).toEqual(['id', 'text']);
    }
  });

  it('exposes only the blank id and hint publicly for fill-in-blank', () => {
    const serialised: string = JSON.stringify(
      formatQuestionPayloads(fibValues()).content_payload,
    );

    expect(serialised).not.toContain('8.314');
    /* The blank id itself is public; its answers are not. */
    expect(serialised).toContain('gasConstant');
  });

  it('keeps the correct ordering out of the public payload', () => {
    const payload = formatQuestionPayloads(orderingValues()).content_payload as OrderingPayload;

    /* `items` exists, but nothing tells a student how to arrange them. */
    expect(payload.items).toHaveLength(2);
    expect(JSON.stringify(payload)).not.toContain('correctOrder');
  });

  it('returns fresh objects on every call (no shared references)', () => {
    const first = formatQuestionPayloads(mcqValues());
    const second = formatQuestionPayloads(mcqValues());

    expect(first.content_payload).not.toBe(second.content_payload);
    expect(first.content_payload).toEqual(second.content_payload);
  });
});

describe('assertNoSolutionLeakage', () => {
  it('throws when a forbidden key is present at the top level', () => {
    expect(() =>
      assertNoSolutionLeakage({ prompt: 'q', correctOptionIds: ['a'] }),
    ).toThrow(/leaked private field/i);
  });

  it('throws when a forbidden key is nested inside an array', () => {
    expect(() =>
      assertNoSolutionLeakage({
        prompt: 'q',
        options: [{ id: 'a', text: 'A', isCorrect: true }],
      }),
    ).toThrow(/isCorrect/);
  });

  it('accepts a legitimately shaped payload', () => {
    expect(() =>
      assertNoSolutionLeakage({
        prompt: 'q',
        allowMultiple: false,
        options: [{ id: 'a', text: 'A' }],
        hint: 'a hint',
      }),
    ).not.toThrow();
  });
});

describe('toQuestionRequestBody', () => {
  it('returns the pair unchanged when it is clean', () => {
    const pair = formatQuestionPayloads(mcqValues());
    expect(toQuestionRequestBody(pair)).toEqual(pair);
  });

  it('refuses to send a leaking pair', () => {
    const pair = {
      content_payload: { prompt: 'q', acceptedAnswers: { a: ['x'] } },
      solution_payload: { correctOptionIds: [], explanations: {}, gradingNote: null },
    };

    expect(() => toQuestionRequestBody(pair as never)).toThrow(/leaked private field/i);
  });
});
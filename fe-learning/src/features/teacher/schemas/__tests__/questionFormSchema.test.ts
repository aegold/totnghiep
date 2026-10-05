import { describe, expect, it } from 'vitest';

import { QuestionType } from '@/features/question-engine';
import {
  extractBlankTokens,
  questionFormSchema,
  type FillInBlankFormValues,
  type MatchingFormValues,
  type MultipleChoiceFormValues,
  type OrderingFormValues,
} from '@/features/teacher/schemas/questionFormSchema';

/* ----------------------------- Fixtures ---------------------------------- */

const mcq = (o: Partial<MultipleChoiceFormValues> = {}): MultipleChoiceFormValues => ({
  questionType: QuestionType.MULTIPLE_CHOICE,
  prompt: 'What is $O(n \\log n)$?',
  hint: 'Think about merge sort.',
  allowMultiple: false,
  options: [
    { id: 'a', text: 'Linear', isCorrect: false, explanation: 'No.' },
    { id: 'b', text: 'Linearithmic', isCorrect: true, explanation: 'Correct: merges dominate.' },
    { id: 'c', text: 'Quadratic', isCorrect: false, explanation: '' },
  ],
  gradingNote: 'Award full marks only for linearithmic.',
  ...o,
});

const fib = (o: Partial<FillInBlankFormValues> = {}): FillInBlankFormValues => ({
  questionType: QuestionType.FILL_IN_BLANK,
  prompt: 'The gas constant is approximately {{gasConstant}} J/(mol·K).',
  hint: 'Recall the ideal gas law.',
  blanks: [
    {
      id: 'gasConstant',
      hint: 'number',
      acceptedAnswers: ['8.314', '8.31'],
      pattern: '^8\\.\\d+$',
    },
  ],
  gradingNote: 'Accept 8.31 or 8.314.',
  ...o,
});

const matching = (o: Partial<MatchingFormValues> = {}): MatchingFormValues => ({
  questionType: QuestionType.MATCHING,
  prompt: 'Match each protocol to its layer.',
  hint: '',
  leftItems: [
    { id: 'l1', text: 'TCP' },
    { id: 'l2', text: 'IP' },
  ],
  rightItems: [
    { id: 'r1', text: 'Transport' },
    { id: 'r2', text: 'Network' },
  ],
  pairs: [
    { leftId: 'l1', rightId: 'r1', explanation: 'TCP is transport-layer.' },
    { leftId: 'l2', rightId: 'r2', explanation: 'IP is network-layer.' },
  ],
  explainEachMatch: true,
  gradingNote: '',
  ...o,
});

const ordering = (o: Partial<OrderingFormValues> = {}): OrderingFormValues => ({
  questionType: QuestionType.ORDERING,
  prompt: 'Order the TCP handshake.',
  hint: '',
  items: [
    { id: 'i1', text: 'SYN' },
    { id: 'i2', text: 'SYN+ACK' },
    { id: 'i3', text: 'ACK' },
  ],
  correctOrder: ['i1', 'i2', 'i3'],
  gradingNote: '',
  ...o,
});

/* ------------------------- Discriminated union -------------------------- */

describe('questionFormSchema', () => {
  it('accepts a valid form for each type', () => {
    for (const values of [mcq(), fib(), matching(), ordering()]) {
      expect(questionFormSchema.safeParse(values).success).toBe(true);
    }
  });

  it('rejects an empty prompt', () => {
    expect(questionFormSchema.safeParse(mcq({ prompt: '   ' })).success).toBe(false);
  });

  it('only validates the active branch of the union', () => {
    /*
     * The MCQ branch has no `blanks`; the MATCHING branch has no `options`.
     * Each is valid because only its own branch is parsed.
     */
    expect(questionFormSchema.safeParse(mcq()).success).toBe(true);
    expect(questionFormSchema.safeParse(matching()).success).toBe(true);
  });
});

/* ------------------------------ MCQ rules -------------------------------- */

describe('multiple choice rules', () => {
  it('requires at least two options', () => {
    const result = questionFormSchema.safeParse(
      mcq({ options: [{ id: 'a', text: 'Only', isCorrect: true, explanation: '' }] }),
    );
    expect(result.success).toBe(false);
  });

  it('requires at least one correct option', () => {
    const result = questionFormSchema.safeParse(
      mcq({
        options: [
          { id: 'a', text: 'A', isCorrect: false, explanation: '' },
          { id: 'b', text: 'B', isCorrect: false, explanation: '' },
        ],
      }),
    );
    expect(result.success).toBe(false);
  });

  it('rejects two correct options on a single-answer question', () => {
    const result = questionFormSchema.safeParse(
      mcq({
        allowMultiple: false,
        options: [
          { id: 'a', text: 'A', isCorrect: true, explanation: '' },
          { id: 'b', text: 'B', isCorrect: true, explanation: '' },
        ],
      }),
    );
    expect(result.success).toBe(false);
  });

  it('allows two correct options when multi-select', () => {
    const result = questionFormSchema.safeParse(
      mcq({
        allowMultiple: true,
        options: [
          { id: 'a', text: 'A', isCorrect: true, explanation: '' },
          { id: 'b', text: 'B', isCorrect: true, explanation: '' },
        ],
      }),
    );
    expect(result.success).toBe(true);
  });

  it('rejects duplicate option ids', () => {
    const result = questionFormSchema.safeParse(
      mcq({
        options: [
          { id: 'dup', text: 'A', isCorrect: true, explanation: '' },
          { id: 'dup', text: 'B', isCorrect: false, explanation: '' },
        ],
      }),
    );
    expect(result.success).toBe(false);
  });
});

/* --------------------------- Fill-in-blank rules ------------------------ */

describe('extractBlankTokens', () => {
  it('finds every token', () => {
    expect(extractBlankTokens('H{{two}}O and {{mass}}')).toEqual(['two', 'mass']);
  });

  it('returns nothing for a plain prompt', () => {
    expect(extractBlankTokens('No blanks here.')).toEqual([]);
  });
});

describe('fill in blank rules', () => {
  it('rejects a prompt token with no declared blank', () => {
    const result = questionFormSchema.safeParse(
      fib({ prompt: 'Value is {{gasConstant}} and also {{missing}}.' }),
    );
    expect(result.success).toBe(false);
  });

  it('rejects a declared blank the prompt never uses', () => {
    const result = questionFormSchema.safeParse(
      fib({
        blanks: [
          { id: 'gasConstant', hint: '', acceptedAnswers: ['8.314'], pattern: '' },
          { id: 'unused', hint: '', acceptedAnswers: ['x'], pattern: '' },
        ],
      }),
    );
    expect(result.success).toBe(false);
  });

  it('requires at least one accepted answer per blank', () => {
    const result = questionFormSchema.safeParse(
      fib({ blanks: [{ id: 'gasConstant', hint: '', acceptedAnswers: [], pattern: '' }] }),
    );
    expect(result.success).toBe(false);
  });

  it('rejects duplicate blank names', () => {
    const result = questionFormSchema.safeParse(
      fib({
        prompt: '{{x}} and {{x}}',
        blanks: [
          { id: 'x', hint: '', acceptedAnswers: ['1'], pattern: '' },
          { id: 'x', hint: '', acceptedAnswers: ['2'], pattern: '' },
        ],
      }),
    );
    expect(result.success).toBe(false);
  });
});

/* ---------------------------- Ordering rules ----------------------------- */

describe('ordering rules', () => {
  it('requires the sequence to cover every item', () => {
    expect(questionFormSchema.safeParse(ordering({ correctOrder: ['i1', 'i2'] })).success).toBe(
      false,
    );
  });

  it('rejects a repeated item in the sequence', () => {
    expect(
      questionFormSchema.safeParse(ordering({ correctOrder: ['i1', 'i1', 'i2'] })).success,
    ).toBe(false);
  });

  it('rejects a sequence referencing an unknown item', () => {
    expect(
      questionFormSchema.safeParse(ordering({ correctOrder: ['i1', 'i2', 'ghost'] })).success,
    ).toBe(false);
  });
});

/* ---------------------------- Matching rules ----------------------------- */

describe('matching rules', () => {
  it('rejects pairing a left item twice', () => {
    const result = questionFormSchema.safeParse(
      matching({
        pairs: [
          { leftId: 'l1', rightId: 'r1', explanation: '' },
          { leftId: 'l1', rightId: 'r2', explanation: '' },
        ],
      }),
    );
    expect(result.success).toBe(false);
  });

  it('rejects a pairing to an unknown right item', () => {
    const result = questionFormSchema.safeParse(
      matching({
        pairs: [
          { leftId: 'l1', rightId: 'r1', explanation: '' },
          { leftId: 'l2', rightId: 'ghost', explanation: '' },
        ],
      }),
    );
    expect(result.success).toBe(false);
  });
});
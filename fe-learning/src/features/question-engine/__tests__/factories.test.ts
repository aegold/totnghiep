import { describe, expect, it } from 'vitest';

import { createEmptyResponse, hasAnswer, parseResponse } from '@/features/question-engine/factories';
import { chemistryFillInBlank, mathSingleChoice, networkLayerMatching } from '@/features/question-engine/mockQuestions';
import { QuestionType } from '@/features/question-engine/types';

describe('createEmptyResponse', () => {
  it('builds the correct shape per type', () => {
    expect(createEmptyResponse(QuestionType.MULTIPLE_CHOICE, mathSingleChoice.payload)).toEqual({
      selectedOptionIds: [],
    });
    expect(createEmptyResponse(QuestionType.MATCHING, networkLayerMatching.payload)).toEqual({
      pairs: {},
    });
  });

  it('pre-seeds one key per declared blank', () => {
    const response = createEmptyResponse(QuestionType.FILL_IN_BLANK, chemistryFillInBlank.payload);

    expect(response).toEqual({
      answers: { twoSubscript: '', oneSubscript: '', molarMass: '' },
    });
  });

  it('returns a fresh object each call (never shared state)', () => {
    const first = createEmptyResponse(QuestionType.MULTIPLE_CHOICE, mathSingleChoice.payload);
    const second = createEmptyResponse(QuestionType.MULTIPLE_CHOICE, mathSingleChoice.payload);

    expect(first).not.toBe(second);
    expect(first.selectedOptionIds).not.toBe(second.selectedOptionIds);
  });
});

describe('parseResponse (repairs malformed stored JSON)', () => {
  it('keeps only option ids that still exist in the payload', () => {
    const parsed = parseResponse(QuestionType.MULTIPLE_CHOICE, {
      selectedOptionIds: ['opt_a', 'deleted_option', 42],
    }, mathSingleChoice.payload);

    expect(parsed.selectedOptionIds).toEqual(['opt_a']);
  });

  it('drops matching pairs referencing removed items', () => {
    const parsed = parseResponse(QuestionType.MATCHING, {
      pairs: { l1: 'r_cable', l2: 'r_deleted', ghost: 'r_router' },
    }, networkLayerMatching.payload);

    expect(parsed.pairs).toEqual({ l1: 'r_cable' });
  });

  it('removes duplicate and unknown ids from an ordering response', () => {
    const parsed = parseResponse(QuestionType.ORDERING, {
      submittedOrder: ['i_tet', 'i_tet', 'unknown', 'i_ho'],
    }, { prompt: 'p', items: [
      { id: 'i_ho', text: 'a' },
      { id: 'i_tet', text: 'b' },
      { id: 'i_fall', text: 'c' },
    ] });

    /* Duplicate collapsed, unknown dropped, missing item appended. */
    expect(parsed.submittedOrder).toEqual(['i_tet', 'i_ho', 'i_fall']);
  });

  it('falls back to an empty response for null or wrong types', () => {
    expect(parseResponse(QuestionType.MULTIPLE_CHOICE, null, mathSingleChoice.payload)).toEqual({
      selectedOptionIds: [],
    });
    expect(parseResponse(QuestionType.MATCHING, 'nope', networkLayerMatching.payload)).toEqual({
      pairs: {},
    });
    expect(parseResponse(QuestionType.MULTIPLE_CHOICE, { selectedOptionIds: 'x' }, mathSingleChoice.payload)).toEqual({
      selectedOptionIds: [],
    });
  });

  it('does not mutate the raw input object', () => {
    const raw = { selectedOptionIds: ['opt_a', 'gone'] };
    parseResponse(QuestionType.MULTIPLE_CHOICE, raw, mathSingleChoice.payload);

    expect(raw.selectedOptionIds).toEqual(['opt_a', 'gone']);
  });
});

describe('hasAnswer', () => {
  it('detects an answered multiple choice question', () => {
    expect(hasAnswer(mathSingleChoice.payload, { selectedOptionIds: ['opt_a'] })).toBe(true);
    expect(hasAnswer(mathSingleChoice.payload, { selectedOptionIds: [] })).toBe(false);
  });

  it('ignores whitespace-only fill-in answers', () => {
    expect(hasAnswer(chemistryFillInBlank.payload, { answers: { twoSubscript: '  ' } })).toBe(false);
    expect(hasAnswer(chemistryFillInBlank.payload, { answers: { twoSubscript: '2' } })).toBe(true);
  });

  it('requires every left item to be paired', () => {
    expect(hasAnswer(networkLayerMatching.payload, { pairs: { l1: 'r_cable' } })).toBe(false);
    expect(
      hasAnswer(networkLayerMatching.payload, {
        pairs: { l1: 'r_cable', l2: 'r_switch', l3: 'r_router', l7: 'r_http' },
      }),
    ).toBe(true);
  });

  it('returns false for malformed input', () => {
    expect(hasAnswer(mathSingleChoice.payload, null)).toBe(false);
    expect(hasAnswer(mathSingleChoice.payload, {})).toBe(false);
  });
});
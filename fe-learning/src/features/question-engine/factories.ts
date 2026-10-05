import type {
  AnyQuestionPayload,
  BlankDefinition,
  ChoiceOption,
  FillInBlankPayload,
  FillInBlankResponse,
  MatchingPayload,
  MatchingResponse,
  MatchItem,
  MultipleChoicePayload,
  MultipleChoiceResponse,
  OrderedItem,
  OrderingPayload,
  OrderingResponse,
  QuestionType,
  QuestionTypeMap,
} from '@/features/question-engine/types';

/**
 * Pure factories that build `student_response` documents.
 *
 * Every function returns a NEW object. Nothing here mutates the payload or a
 * previous response, which keeps the engine's immutability contract enforceable
 * from one place.
 */

/**
 * Builds a blank response for `questionType`.
 *
 * For `FILL_IN_BLANK` the payload is used to pre-seed one key per declared
 * blank, keeping the stored JSON shape stable instead of omitting untouched
 * blanks.
 */
export const createEmptyResponse = <T extends QuestionType>(
  questionType: T,
  payload?: QuestionTypeMap[T]['payload'],
): QuestionTypeMap[T]['response'] => {
  switch (questionType) {
    case 'MULTIPLE_CHOICE':
      return { selectedOptionIds: [] } as QuestionTypeMap[T]['response'];

    case 'FILL_IN_BLANK': {
      const blanks = (payload as FillInBlankPayload | undefined)?.blanks ?? [];
      const answers: FillInBlankResponse['answers'] = {};
      for (const blank of blanks) {
        answers[blank.id] = '';
      }
      return { answers } as QuestionTypeMap[T]['response'];
    }

    case 'MATCHING':
      return { pairs: {} } as QuestionTypeMap[T]['response'];

    case 'ORDERING':
      return { submittedOrder: [] } as QuestionTypeMap[T]['response'];

    default: {
      /*
       * Exhaustiveness guard: adding a member to `QuestionType` without a
       * branch here becomes a compile-time error, not a silent `undefined`.
       */
      const exhaustive: never = questionType;
      return exhaustive;
    }
  }
};

/**
 * Reads a stored response, repairing anything malformed.
 *
 * `student_response` is JSON in MySQL and may be `NULL`, truncated, or written
 * by an older client — so it is treated as untrusted input rather than trusted.
 * Every branch filters to ids that exist in the payload, so a stale answer
 * referencing a deleted option can never be submitted.
 */
export const parseResponse = <T extends QuestionType>(
  questionType: T,
  raw: unknown,
  payload: QuestionTypeMap[T]['payload'],
): QuestionTypeMap[T]['response'] => {
  const fallback = createEmptyResponse(questionType, payload);

  if (typeof raw !== 'object' || raw === null) {
    return fallback;
  }

  switch (questionType) {
    case 'MULTIPLE_CHOICE': {
      const candidate = (raw as { selectedOptionIds?: unknown }).selectedOptionIds;
      if (!Array.isArray(candidate)) {
        return fallback;
      }
      /* Narrow the union: the case label determines the payload member. */
      const typed = payload as MultipleChoicePayload;
      const validIds = new Set(typed.options.map((option: ChoiceOption) => option.id));
      const selectedOptionIds: MultipleChoiceResponse['selectedOptionIds'] = candidate.filter(
        (id: unknown): id is string => typeof id === 'string' && validIds.has(id),
      );
      return { selectedOptionIds } as QuestionTypeMap[T]['response'];
    }

    case 'FILL_IN_BLANK': {
      const candidate = (raw as { answers?: unknown }).answers;
      if (typeof candidate !== 'object' || candidate === null) {
        return fallback;
      }
      const typed = payload as FillInBlankPayload;
      const blankIds = new Set(typed.blanks.map((blank: BlankDefinition) => blank.id));
      const answers: FillInBlankResponse['answers'] = {};

      for (const [blankId, answer] of Object.entries(candidate as Record<string, unknown>)) {
        if (blankIds.has(blankId) && typeof answer === 'string') {
          answers[blankId] = answer;
        }
      }
      return { answers } as QuestionTypeMap[T]['response'];
    }

    case 'MATCHING': {
      const candidate = (raw as { pairs?: unknown }).pairs;
      if (typeof candidate !== 'object' || candidate === null) {
        return fallback;
      }
      const typed = payload as MatchingPayload;
      const leftIds = new Set(typed.leftItems.map((item: MatchItem) => item.id));
      const rightIds = new Set(typed.rightItems.map((item: MatchItem) => item.id));
      const pairs: MatchingResponse['pairs'] = {};

      for (const [leftId, rightId] of Object.entries(candidate as Record<string, unknown>)) {
        if (leftIds.has(leftId) && typeof rightId === 'string' && rightIds.has(rightId)) {
          pairs[leftId] = rightId;
        }
      }
      return { pairs } as QuestionTypeMap[T]['response'];
    }

    case 'ORDERING': {
      const candidate = (raw as { submittedOrder?: unknown }).submittedOrder;
      if (!Array.isArray(candidate)) {
        return fallback;
      }
      const typed = payload as OrderingPayload;
      const itemIds = new Set(typed.items.map((item: OrderedItem) => item.id));
      const seen = new Set<string>();
      const submittedOrder: OrderingResponse['submittedOrder'] = [];

      /* Drop duplicates and unknown ids, then append anything never answered. */
      for (const id of candidate) {
        if (typeof id === 'string' && itemIds.has(id) && !seen.has(id)) {
          seen.add(id);
          submittedOrder.push(id);
        }
      }
      for (const item of typed.items) {
        if (!seen.has(item.id)) {
          submittedOrder.push(item.id);
        }
      }
      return { submittedOrder } as QuestionTypeMap[T]['response'];
    }

    default: {
      const exhaustive: never = questionType;
      return exhaustive;
    }
  }
};

/**
 * Whether the learner has supplied an answer.
 *
 * Hosts use this to enforce "answer every question" before submission, and to
 * highlight unanswered items in a question navigator.
 */
export const hasAnswer = (payload: AnyQuestionPayload, response: unknown): boolean => {
  if (typeof response !== 'object' || response === null) {
    return false;
  }

  if ('selectedOptionIds' in response) {
    const ids = (response as { selectedOptionIds: unknown[] }).selectedOptionIds;
    return Array.isArray(ids) && ids.length > 0;
  }

  if ('answers' in response) {
    const answers = (response as { answers: Record<string, string> }).answers;
    return Object.values(answers).some((answer: string) => answer.trim().length > 0);
  }

  if ('pairs' in response) {
    const pairs = (response as { pairs: Record<string, string> }).pairs;
    const expected: number = 'leftItems' in payload ? payload.leftItems.length : 0;
    return expected > 0 && Object.keys(pairs).length >= expected;
  }

  if ('submittedOrder' in response) {
    const order = (response as { submittedOrder: string[] }).submittedOrder;
    const expected: number = 'items' in payload ? payload.items.length : 0;
    return Array.isArray(order) && expected > 0 && order.length === expected;
  }

  return false;
};
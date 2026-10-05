import { z } from 'zod';

import { QuestionType } from '@/features/question-engine';
import type { AnyQuestionPayload } from '@/features/question-engine';

/**
 * Authoring contracts for `question_versions`.
 *
 * ## The one invariant that matters
 * A question is stored as TWO JSON documents:
 *  - `content_payload`  — what a student sees. Public. Must contain NO answers.
 *  - `solution_payload` — the answer key, regex patterns, and grading notes.
 *                        Private. Never shipped to a student.
 *
 * `formatQuestionPayloads` builds the public document from an explicit
 * allow-list rather than by spreading the form, so adding a private field to
 * the form can never leak into it by accident. `assertNoSolutionLeakage` then
 * verifies the result as a belt-and-braces check.
 */

/* -------------------------------------------------------------------------- */
/*  Shared primitives                                                           */
/* -------------------------------------------------------------------------- */

/** Prompts support Markdown + LaTeX, so length is generous. */
const promptSchema = z
  .string()
  .trim()
  .min(1, 'Nội dung câu hỏi là bắt buộc.')
  .max(8_000, 'Nội dung câu hỏi quá dài (tối đa 8.000 ký tự).');

const optionalHint = z
  .string()
  .trim()
  .max(500, 'Gợi ý quá dài.')
  .optional()
  .or(z.literal(''));

/** Option/blank/item ids are authored, stable, and never re-generated. */
const optionIdSchema = z
  .string()
  .trim()
  .min(1, 'Mã phương án là bắt buộc.')
  .max(64, 'Mã phương án quá dài.');

const optionTextSchema = z
  .string()
  .trim()
  .min(1, 'Nội dung phương án không được để trống.')
  .max(1_000, 'Nội dung phương án quá dài.');

const explanationSchema = z
  .string()
  .trim()
  .max(2_000, 'Phần giải thích quá dài.')
  .optional()
  .or(z.literal(''));

/* -------------------------------------------------------------------------- */
/*  MULTIPLE_CHOICE                                                             */
/* -------------------------------------------------------------------------- */

const multipleChoiceOptionSchema = z.object({
  id: optionIdSchema,
  text: optionTextSchema,
  isCorrect: z.boolean(),
  /** Private: rationale shown after grading. */
  explanation: explanationSchema,
});

export const multipleChoiceFormSchema = z
  .object({
    questionType: z.literal(QuestionType.MULTIPLE_CHOICE),
    prompt: promptSchema,
    hint: optionalHint,
    allowMultiple: z.boolean(),
    options: z.array(multipleChoiceOptionSchema).min(2, 'Hãy thêm ít nhất hai phương án.'),
    /** Private: instructor-facing grading rubric. */
    gradingNote: explanationSchema,
  })
  .superRefine((values, ctx) => {
    const correctCount: number = values.options.filter((option) => option.isCorrect).length;

    if (correctCount === 0) {
      ctx.addIssue({
        code: 'custom',
        message: 'Hãy đánh dấu ít nhất một phương án là đúng.',
        path: ['options'],
      });
      return;
    }

    /*
     * A single-answer question with two correct options can never be marked
     * right by a student, so it is almost certainly an authoring mistake.
     */
    if (!values.allowMultiple && correctCount > 1) {
      ctx.addIssue({
        code: 'custom',
        message: 'Câu hỏi chọn một đáp án chỉ có thể có duy nhất một phương án đúng.',
        path: ['options'],
      });
    }

    const ids: string[] = values.options.map((option) => option.id);
    if (new Set(ids).size !== ids.length) {
      ctx.addIssue({
        code: 'custom',
        message: 'Mã các phương án phải là duy nhất.',
        path: ['options'],
      });
    }
  });

/* -------------------------------------------------------------------------- */
/*  FILL_IN_BLANK                                                               */
/* -------------------------------------------------------------------------- */

const blankSchema = z.object({
  id: z
    .string()
    .trim()
    .min(1, 'Tên ô trống là bắt buộc.')
    .max(64, 'Tên ô trống quá dài.')
    .regex(/^[\w-]+$/, 'Chỉ dùng chữ, số, gạch dưới hoặc gạch ngang.'),
  hint: optionalHint,
  /** Private: accepted answers, compared case-insensitively at grading time. */
  acceptedAnswers: z
    .array(z.string().trim().min(1))
    .min(1, 'Hãy thêm ít nhất một đáp án được chấp nhận.'),
  /** Private: regex fallback for varied but valid input. */
  pattern: z
    .string()
    .trim()
    .max(300, 'Biểu thức quy chuẩn quá dài.')
    .optional()
    .or(z.literal('')),
});

/** Extracts `{{blank_id}}` tokens from a prompt. Mirrors the engine's parser. */
export const extractBlankTokens = (prompt: string): string[] => {
  const tokens: string[] = [];
  for (const match of prompt.matchAll(/\{\{([^}]+)\}\}/g)) {
    const id: string | undefined = match[1];
    if (id !== undefined) {
      tokens.push(id.trim());
    }
  }
  return tokens;
};

export const fillInBlankFormSchema = z
  .object({
    questionType: z.literal(QuestionType.FILL_IN_BLANK),
    prompt: promptSchema,
    hint: optionalHint,
    blanks: z.array(blankSchema).min(1, 'Hãy khai báo ít nhất một ô trống.'),
    gradingNote: explanationSchema,
  })
  .superRefine((values, ctx) => {
    const blankIds: string[] = values.blanks.map((blank) => blank.id);

    if (new Set(blankIds).size !== blankIds.length) {
      ctx.addIssue({
        code: 'custom',
        message: 'Tên các ô trống phải là duy nhất.',
        path: ['blanks'],
      });
    }

    /*
     * The prompt is the source of truth for which slots exist. A `{{blank}}`
     * with no matching definition renders as a bare input with no hint, and a
     * defined blank that never appears in the prompt is silently unanswerable.
     * Both are authoring bugs worth catching before publish.
     */
    const referenced: string[] = extractBlankTokens(values.prompt);

    for (const token of referenced) {
      if (!blankIds.includes(token)) {
        ctx.addIssue({
          code: 'custom',
          message: `Nội dung câu hỏi tham chiếu tới {{${token}}}, nhưng ô trống này chưa được khai báo bên dưới.`,
          path: ['prompt'],
        });
      }
    }

    for (const blankId of blankIds) {
      if (!referenced.includes(blankId)) {
        ctx.addIssue({
          code: 'custom',
          message: `Ô trống "${blankId}" đã được khai báo nhưng không xuất hiện trong nội dung câu hỏi.`,
          path: ['blanks'],
        });
      }
    }
  });

/* -------------------------------------------------------------------------- */
/*  MATCHING                                                                    */
/* -------------------------------------------------------------------------- */

const matchItemSchema = z.object({
  id: optionIdSchema,
  text: optionTextSchema,
});

/** One authored left -> right pairing; this IS the answer key. */
const matchPairSchema = z.object({
  leftId: z.string().trim().min(1, 'Hãy chọn một mục ở cột bên trái.'),
  rightId: z.string().trim().min(1, 'Hãy chọn mục tương ứng ở cột bên phải.'),
  explanation: explanationSchema,
});

export const matchingFormSchema = z
  .object({
    questionType: z.literal(QuestionType.MATCHING),
    prompt: promptSchema,
    hint: optionalHint,
    leftItems: z.array(matchItemSchema).min(2, 'Hãy thêm ít nhất hai mục ở cột trái.'),
    rightItems: z.array(matchItemSchema).min(2, 'Hãy thêm ít nhất hai mục ở cột phải.'),
    /** Private: the correct pairing, left id -> right id. */
    pairs: z.array(matchPairSchema).min(1, 'Hãy xác định ít nhất một cặp nối đúng.'),
    /** Enables a per-match explanation once the answer is graded. */
    explainEachMatch: z.boolean(),
    gradingNote: explanationSchema,
  })
  .superRefine((values, ctx) => {
    const leftIds: string[] = values.leftItems.map((item) => item.id);
    const rightIds: string[] = values.rightItems.map((item) => item.id);

    const assertUnique = (ids: string[], path: ['leftItems'] | ['rightItems']): void => {
      if (new Set(ids).size !== ids.length) {
        ctx.addIssue({ code: 'custom', message: 'Mã các mục phải là duy nhất.', path });
      }
    };
    assertUnique(leftIds, ['leftItems']);
    assertUnique(rightIds, ['rightItems']);

    /* Each left item may only appear once, or a student could be marked right
       against two contradictory answers. */
    const matchedLeftIds: string[] = values.pairs.map((pair) => pair.leftId);
    if (new Set(matchedLeftIds).size !== matchedLeftIds.length) {
      ctx.addIssue({
        code: 'custom',
        message: 'Mỗi mục bên trái chỉ được nối với một mục.',
        path: ['pairs'],
      });
    }

    values.pairs.forEach((pair, index) => {
      if (!leftIds.includes(pair.leftId)) {
        ctx.addIssue({
          code: 'custom',
          message: 'Cặp nối tham chiếu tới một mục bên trái không tồn tại.',
          path: ['pairs', index, 'leftId'],
        });
      }
      if (!rightIds.includes(pair.rightId)) {
        ctx.addIssue({
          code: 'custom',
          message: 'Cặp nối tham chiếu tới một mục bên phải không tồn tại.',
          path: ['pairs', index, 'rightId'],
        });
      }
    });
  });

/* -------------------------------------------------------------------------- */
/*  ORDERING                                                                    */
/* -------------------------------------------------------------------------- */

export const orderingFormSchema = z
  .object({
    questionType: z.literal(QuestionType.ORDERING),
    prompt: promptSchema,
    hint: optionalHint,
    /** Public: the items to display, in a shuffled permutation. */
    items: z.array(matchItemSchema).min(2, 'Hãy thêm ít nhất hai mục.'),
    /** Private: the authored correct sequence of item ids. */
    correctOrder: z
      .array(optionIdSchema)
      .min(2, 'Hãy xác định thứ tự đúng.'),
    gradingNote: explanationSchema,
  })
  .superRefine((values, ctx) => {
    const itemIds: string[] = values.items.map((item) => item.id);

    if (new Set(itemIds).size !== itemIds.length) {
      ctx.addIssue({
        code: 'custom',
        message: 'Mã các mục phải là duy nhất.',
        path: ['items'],
      });
    }

    /*
     * The correct sequence must be a permutation of the items: every item
     * appears exactly once. A partial or duplicated sequence would leave the
     * question impossible to grade.
     */
    const order: string[] = values.correctOrder;
    const uniqueOrder: Set<string> = new Set(order);

    if (uniqueOrder.size !== order.length) {
      ctx.addIssue({
        code: 'custom',
        message: 'Thứ tự đúng không được lặp lại một mục.',
        path: ['correctOrder'],
      });
    }

    for (const id of order) {
      if (!itemIds.includes(id)) {
        ctx.addIssue({
          code: 'custom',
          message: `Correct sequence references unknown item "${id}".`,
          path: ['correctOrder'],
        });
      }
    }

    for (const id of itemIds) {
      if (!uniqueOrder.has(id)) {
        ctx.addIssue({
          code: 'custom',
          message: `Item "${id}" is missing from the correct sequence.`,
          path: ['correctOrder'],
        });
      }
    }
  });

/* -------------------------------------------------------------------------- */
/*  Discriminated union                                                          */
/* -------------------------------------------------------------------------- */

/**
 * The whole authoring form.
 *
 * A discriminated union on `questionType` means switching the selector
 * validates only the active branch — an MCQ form cannot be rejected for a
 * malformed MATCHING payload, because that branch is never parsed.
 */
export const questionFormSchema = z.discriminatedUnion('questionType', [
  multipleChoiceFormSchema,
  fillInBlankFormSchema,
  matchingFormSchema,
  orderingFormSchema,
]);

export type QuestionFormValues = z.infer<typeof questionFormSchema>;
export type MultipleChoiceFormValues = z.infer<typeof multipleChoiceFormSchema>;
export type FillInBlankFormValues = z.infer<typeof fillInBlankFormSchema>;
export type MatchingFormValues = z.infer<typeof matchingFormSchema>;
export type OrderingFormValues = z.infer<typeof orderingFormSchema>;

/* -------------------------------------------------------------------------- */
/*  solution_payload (private)                                                  */
/* -------------------------------------------------------------------------- */

/** Private grading data. NEVER serialised into `content_payload`. */
export interface MultipleChoiceSolutionPayload {
  correctOptionIds: string[];
  /** Per-option rationale, keyed by option id. */
  explanations: Record<string, string>;
  gradingNote: string | null;
}

export interface FillInBlankSolutionPayload {
  /** blank id -> accepted answers. The answer key. */
  acceptedAnswers: Record<string, string[]>;
  /** blank id -> optional regex for varied input. */
  patterns: Record<string, string>;
  gradingNote: string | null;
}

export interface MatchingSolutionPayload {
  /** left id -> right id. The answer key. */
  correctPairs: Record<string, string>;
  /** left id -> rationale, when per-match explanation is enabled. */
  explanations: Record<string, string>;
  gradingNote: string | null;
}

export interface OrderingSolutionPayload {
  /** The authored correct sequence of item ids. */
  correctOrder: string[];
  gradingNote: string | null;
}

export type SolutionPayload =
  | MultipleChoiceSolutionPayload
  | FillInBlankSolutionPayload
  | MatchingSolutionPayload
  | OrderingSolutionPayload;

export interface QuestionPayloadPair {
  content_payload: AnyQuestionPayload;
  solution_payload: SolutionPayload;
}

/**
 * Keys that must never appear anywhere in `content_payload`.
 * Kept as data so the guard and its test cannot drift apart.
 */
const FORBIDDEN_PUBLIC_KEYS: readonly string[] = [
  'isCorrect',
  'correctOptionIds',
  'correctPairs',
  'correctOrder',
  'acceptedAnswers',
  'patterns',
  'explanation',
  'explanations',
  'gradingNote',
  'pairs',
  'pattern',
] as const;

/** Normalises an optional trimmed string into `string | null`. */
const nullable = (value: string | undefined | null): string | null => {
  if (typeof value !== 'string') {
    return null;
  }
  const trimmed: string = value.trim();
  return trimmed.length > 0 ? trimmed : null;
};

/* -------------------------------------------------------------------------- */
/*  Serialisation                                                               */
/* -------------------------------------------------------------------------- */

/**
 * Splits validated form values into the public and private JSON documents.
 *
 * ## Why this is an explicit allow-list
 * The public document is assembled field by field. It is NEVER produced by
 * spreading the form or deleting keys — that pattern silently ships any new
 * private field an author adds later. Here, adding `secretFlag: true` to the
 * form is inert by construction because nothing reads it into the output.
 */
export function formatQuestionPayloads(values: QuestionFormValues): QuestionPayloadPair {
  switch (values.questionType) {
    case QuestionType.MULTIPLE_CHOICE: {
      const content_payload: AnyQuestionPayload = {
        prompt: values.prompt,
        allowMultiple: values.allowMultiple,
        /* Only id + text are public. */
        options: values.options.map((option) => ({ id: option.id, text: option.text })),
        ...(nullable(values.hint) !== null ? { hint: values.hint as string } : {}),
      };

      const explanations: Record<string, string> = {};
      for (const option of values.options) {
        const note: string | null = nullable(option.explanation);
        if (note !== null) {
          explanations[option.id] = note;
        }
      }

      const solution_payload: MultipleChoiceSolutionPayload = {
        correctOptionIds: values.options
          .filter((option) => option.isCorrect)
          .map((option) => option.id),
        explanations,
        gradingNote: nullable(values.gradingNote),
      };

      return { content_payload, solution_payload };
    }

    case QuestionType.FILL_IN_BLANK: {
      const content_payload: AnyQuestionPayload = {
        prompt: values.prompt,
        /* Public: slot id + optional placeholder only. */
        blanks: values.blanks.map((blank) => ({
          id: blank.id,
          ...(nullable(blank.hint) !== null ? { hint: blank.hint as string } : {}),
        })),
        ...(nullable(values.hint) !== null ? { hint: values.hint as string } : {}),
      };

      const acceptedAnswers: Record<string, string[]> = {};
      const patterns: Record<string, string> = {};
      for (const blank of values.blanks) {
        acceptedAnswers[blank.id] = blank.acceptedAnswers;
        const pattern: string | null = nullable(blank.pattern);
        if (pattern !== null) {
          patterns[blank.id] = pattern;
        }
      }

      const solution_payload: FillInBlankSolutionPayload = {
        acceptedAnswers,
        patterns,
        gradingNote: nullable(values.gradingNote),
      };

      return { content_payload, solution_payload };
    }

    case QuestionType.MATCHING: {
      const content_payload: AnyQuestionPayload = {
        prompt: values.prompt,
        leftItems: values.leftItems.map((item) => ({ id: item.id, text: item.text })),
        rightItems: values.rightItems.map((item) => ({ id: item.id, text: item.text })),
        ...(nullable(values.hint) !== null ? { hint: values.hint as string } : {}),
      };

      const correctPairs: Record<string, string> = {};
      const explanations: Record<string, string> = {};
      for (const pair of values.pairs) {
        correctPairs[pair.leftId] = pair.rightId;
        const note: string | null = nullable(pair.explanation);
        if (note !== null) {
          explanations[pair.leftId] = note;
        }
      }

      const solution_payload: MatchingSolutionPayload = {
        correctPairs,
        explanations,
        gradingNote: nullable(values.gradingNote),
      };

      return { content_payload, solution_payload };
    }

    case QuestionType.ORDERING: {
      const content_payload: AnyQuestionPayload = {
        prompt: values.prompt,
        items: values.items.map((item) => ({ id: item.id, text: item.text })),
        ...(nullable(values.hint) !== null ? { hint: values.hint as string } : {}),
      };

      const solution_payload: OrderingSolutionPayload = {
        correctOrder: [...values.correctOrder],
        gradingNote: nullable(values.gradingNote),
      };

      return { content_payload, solution_payload };
    }

    default: {
      /* Exhaustiveness: a new question type must add a branch here. */
      const exhaustive: never = values;
      return exhaustive;
    }
  }
}

/* -------------------------------------------------------------------------- */
/*  Leakage guard                                                               */
/* -------------------------------------------------------------------------- */

const isRecord = (value: unknown): value is Record<string, unknown> =>
  typeof value === 'object' && value !== null;

/** Recursively collects every key present anywhere in a value. */
const collectKeys = (value: unknown, found: Set<string> = new Set()): Set<string> => {
  if (Array.isArray(value)) {
    for (const item of value) {
      collectKeys(item, found);
    }
    return found;
  }
  if (isRecord(value)) {
    for (const [key, nested] of Object.entries(value)) {
      found.add(key);
      collectKeys(nested, found);
    }
  }
  return found;
};

/**
 * Verifies that `content_payload` carries no private grading data.
 *
 * This is a defence-in-depth check, not the primary mechanism — the allow-list
 * in `formatQuestionPayloads` is. It exists so that a future refactor to a
 * spread-based approach fails loudly in development instead of quietly shipping
 * the answer key to every student.
 *
 * @throws when a forbidden key is present
 */
export const assertNoSolutionLeakage = (contentPayload: unknown): void => {
  const keys: Set<string> = collectKeys(contentPayload);
  const leaked: string[] = [...keys].filter((key) =>
    FORBIDDEN_PUBLIC_KEYS.includes(key),
  );

  if (leaked.length > 0) {
    throw new Error(
      `[questionFormSchema] content_payload leaked private field(s): ${leaked.join(', ')}. ` +
        'Public payloads must never contain answers, patterns, or explanations.',
    );
  }
};

/** Serialises a payload pair to the exact JSON body sent to the backend. */
export const toQuestionRequestBody = (
  pair: QuestionPayloadPair,
): { content_payload: AnyQuestionPayload; solution_payload: SolutionPayload } => {
  assertNoSolutionLeakage(pair.content_payload);
  return pair;
};
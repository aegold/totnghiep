/**
 * Question Engine — domain contracts.
 *
 * ## Data model
 * A question is split into two tables:
 *  - `questions`         -> metadata (stable identity, type, difficulty, points)
 *  - `question_versions` -> immutable revisions holding `content_payload` JSON
 *
 * This module types ONLY the JSON documents exchanged with those columns:
 *  - `{@link MultipleChoicePayload}` etc. describe `content_payload`
 *  - `{@link MultipleChoiceResponse}` etc. describe `student_response`
 *
 * The engine is intentionally transport- and layout-agnostic: it never imports
 * exam, modal, or router code, so it can be mounted anywhere.
 */

/** Discriminator stored in `questions.question_type`. */
export enum QuestionType {
  MULTIPLE_CHOICE = 'MULTIPLE_CHOICE',
  FILL_IN_BLANK = 'FILL_IN_BLANK',
  MATCHING = 'MATCHING',
  ORDERING = 'ORDERING',
}

/** Every type the engine can currently render. */
export const SUPPORTED_QUESTION_TYPES: readonly QuestionType[] = [
  QuestionType.MULTIPLE_CHOICE,
  QuestionType.FILL_IN_BLANK,
  QuestionType.MATCHING,
  QuestionType.ORDERING,
] as const;

/* -------------------------------------------------------------------------- */
/*  Shared leaf shapes                                                          */
/* -------------------------------------------------------------------------- */

/**
 * An `{ id, text }` pair, used by every option-bearing question type.
 *
 * The three public names below are structural aliases rather than copies, so a
 * change to the stored option shape propagates everywhere without drift.
 */
export interface LabeledItem {
  /** Stable identifier authored in the CMS; never re-generated at runtime. */
  id: string;
  /** Markdown/LaTeX source text. */
  text: string;
}

/** Option inside a `MULTIPLE_CHOICE` question. */
export type ChoiceOption = LabeledItem;

/** Side of a `MATCHING` question. */
export type MatchItem = LabeledItem;

/** Entry of an `ORDERING` question. */
export type OrderedItem = LabeledItem;

/** Declared answer slot inside a `FILL_IN_BLANK` prompt. */
export interface BlankDefinition {
  /** Referenced in the prompt as `{{id}}`. */
  id: string;
  hint?: string;
}

/* -------------------------------------------------------------------------- */
/*  1. MULTIPLE_CHOICE                                                          */
/* -------------------------------------------------------------------------- */

/**
 * `content_payload` for `MULTIPLE_CHOICE`.
 *
 * ## Private data lives elsewhere
 * Correct answers and grading explanations are NOT part of this payload. They
 * live in `solution_payload` (teacher schema, Stage 6) and are never sent to a
 * student — a leak here would hand them the answer key in the page source.
 */
export interface MultipleChoicePayload {
  prompt: string;
  /** `false` renders radio buttons; `true` renders checkboxes. */
  allowMultiple: boolean;
  options: ChoiceOption[];
  /** Optional pedagogy metadata surfaced by the host layout. Public by design. */
  hint?: string;
}

/** `student_response` for `MULTIPLE_CHOICE`. */
export interface MultipleChoiceResponse {
  selectedOptionIds: string[];
}

/* -------------------------------------------------------------------------- */
/*  2. FILL_IN_BLANK                                                           */
/* -------------------------------------------------------------------------- */

/**
 * `content_payload` for `FILL_IN_BLANK`.
 *
 * `prompt` embeds answer slots as `{{blank_id}}`; each referenced id should
 * also appear in {@link blanks} so the engine can attach hints and a11y labels.
 *
 * ## Private data lives elsewhere
 * `acceptedAnswers` is deliberately NOT part of this payload — it is the
 * answer key. It belongs in `solution_payload` and must never reach a student.
 */
export interface FillInBlankPayload {
  prompt: string;
  blanks: BlankDefinition[];
  hint?: string;
}

/** `student_response` for `FILL_IN_BLANK`; keyed by blank id. */
export interface FillInBlankResponse {
  answers: Record<string, string>;
}

/* -------------------------------------------------------------------------- */
/*  3. MATCHING                                                                */
/* -------------------------------------------------------------------------- */

/** `content_payload` for `MATCHING`. Private pairing lives in `solution_payload`. */
export interface MatchingPayload {
  prompt: string;
  leftItems: MatchItem[];
  rightItems: MatchItem[];
  hint?: string;
}

/** `student_response` for `MATCHING`; maps left id -> right id. */
export interface MatchingResponse {
  pairs: Record<string, string>;
}

/* -------------------------------------------------------------------------- */
/*  4. ORDERING                                                                */
/* -------------------------------------------------------------------------- */

/**
 * `content_payload` for `ORDERING`.
 *
 * The authored (correct) sequence is PRIVATE and lives in `solution_payload`.
 * The engine presents a shuffled permutation of `items` instead.
 */
export interface OrderingPayload {
  prompt: string;
  items: OrderedItem[];
  hint?: string;
}

/** `student_response` for `ORDERING`; every item id exactly once. */
export interface OrderingResponse {
  submittedOrder: string[];
}

/* -------------------------------------------------------------------------- */
/*  Renderer contract                                                           */
/* -------------------------------------------------------------------------- */

/**
 * Props every renderer receives. Renderers are fully controlled: they hold no
 * answer state and always emit a brand-new response object via `onChange`.
 *
 * @typeParam TPayload - The `content_payload` shape for this question type.
 * @typeParam TResponse - The `student_response` shape for this question type.
 */
export interface QuestionProps<TPayload, TResponse> {
  payload: TPayload;
  /** Current answer. Never mutated — treat as immutable. */
  value: TResponse;
  /** Receives a new response object. The previous reference is left intact. */
  onChange: (next: TResponse) => void;
  /** Locks interaction, e.g. while the exam is submitted or submitting. */
  disabled?: boolean;
  /** Namespaces generated DOM ids so several questions can share a page. */
  idPrefix?: string;
}

/* -------------------------------------------------------------------------- */
/*  Registry typing                                                             */
/* -------------------------------------------------------------------------- */

/**
 * Maps each question type to its payload/response pair.
 *
 * Adding a question type means adding one key here; every generic in the
 * engine then narrows correctly with no further casts.
 */
export interface QuestionTypeMap {
  [QuestionType.MULTIPLE_CHOICE]: {
    payload: MultipleChoicePayload;
    response: MultipleChoiceResponse;
  };
  [QuestionType.FILL_IN_BLANK]: {
    payload: FillInBlankPayload;
    response: FillInBlankResponse;
  };
  [QuestionType.MATCHING]: {
    payload: MatchingPayload;
    response: MatchingResponse;
  };
  [QuestionType.ORDERING]: {
    payload: OrderingPayload;
    response: OrderingResponse;
  };
}

/** Union of every payload the engine understands. */
export type AnyQuestionPayload = QuestionTypeMap[QuestionType]['payload'];

/** Union of every response the engine can emit. */
export type AnyQuestionResponse = QuestionTypeMap[QuestionType]['response'];

/**
 * Metadata from the `questions` table, joined with the active
 * `question_versions` row. Passed alongside the payload so hosts can render
 * numbering, points, and difficulty without the engine knowing about them.
 */
export interface QuestionMetadata {
  questionId: number;
  versionId: number;
  questionType: QuestionType;
  /** 1 = easiest. */
  difficultyLevel: number;
  points: number;
}
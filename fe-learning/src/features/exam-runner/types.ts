import type { AnyQuestionPayload, AnyQuestionResponse, QuestionType } from '@/features/question-engine';

/**
 * Exam Runner — domain contracts.
 *
 * ## Data model
 *  - `exam_submissions`  -> one row per attempt (holds `tab_switch_count`)
 *  - `submission_answers`-> one row per answered `question_version`
 *
 * The client treats this module as the boundary between the exam UI and both
 * those tables. Answer documents are shaped exactly as Stage 2 emits them, so
 * the Question Engine never needs to know about exams.
 */

/** One question inside a running paper. */
export interface ExamQuestion {
  /** `question_versions.id` — the key every answer is stored under. */
  versionId: number;
  /** `questions.id`; a version may be re-served across papers. */
  questionId: number;
  questionType: QuestionType;
  /** Parsed `content_payload` from the joined version row. */
  payload: AnyQuestionPayload;
  difficultyLevel: number;
  points: number;
}

/** Immutable paper metadata returned when the attempt starts. */
export interface ExamSessionMeta {
  /** `exam_submissions.id`; also scopes the local cache key. */
  submissionId: number;
  examId: number;
  title: string;
  instructions?: string;
  durationMinutes: number;
  /** ISO-8601 from the server. Combined with the duration to derive the end. */
  serverStartTime: string;
  /**
   * ISO-8601 deadline. Authoritative — the client never computes its own
   * deadline from a locally-observed clock.
   */
  serverEndTime: string;
  totalPoints: number;
  /** Proctoring strikes tolerated before the attempt is force-submitted. */
  maxViolations: number;
}

/** A paper ready to be taken. */
export interface ExamSession extends ExamSessionMeta {
  questions: readonly ExamQuestion[];
}

export type SubmissionStatus =
  | 'not_started'
  | 'in_progress'
  | 'submitting'
  | 'submitted'
  | 'failed';

/** A single answer as it goes to `submission_answers`. */
export interface SubmissionAnswer {
  versionId: number;
  studentResponse: AnyQuestionResponse;
}

/** Body of `POST /exam-submissions/{id}/submit`. */
export interface SubmitExamRequest {
  submissionId: number;
  answers: SubmissionAnswer[];
  /** Persisted to `exam_submissions.tab_switch_count`. */
  tabSwitchCount: number;
  violationCount: number;
  flaggedVersionIds: number[];
  submittedAt: string;
  /** True when the exam ended by timeout or a proctoring strike. */
  autoSubmitted: boolean;
}

/** One question's verdict, as returned after grading. */
export interface ExamQuestionResult {
  versionId: number;
  questionType: string;
  orderIndex: number | null;
  earnedPoints: number;
  maxPoints: number;
  /** True only at full marks, even where partial credit is possible. */
  fullyCorrect: boolean;
  answered: boolean;
}

/**
 * Result of a submission.
 *
 * Mirrors the backend's ExamResultResponse. The previous shape here was four
 * invented fields the server never sends, so a live response would have typed as
 * a nearly empty object and the result screen would have rendered nothing.
 */
export interface SubmitExamResult {
  submissionId: number;
  examId: number;
  totalScore: number;
  maxScore: number;
  /** 0-100, or null when the paper carried no marks. */
  percentage: number | null;
  answeredCount: number;
  questionCount: number;
  correctCount: number;
  /** True when the hand-in arrived past deadline + grace; still graded. */
  isLate: boolean;
  graceSeconds: number;
  submittedAt: string;
  results: ExamQuestionResult[];
}

/** Kinds of proctoring strike the client can observe. */
export type ViolationKind =
  | 'TAB_HIDDEN'
  | 'CONTEXT_MENU'
  | 'COPY_ATTEMPT'
  | 'DEVTOOLS_SHORTCUT'
  | 'WINDOW_BLUR';

/** One recorded strike, surfaced to the learner. */
export interface ProctoringViolation {
  kind: ViolationKind;
  at: string;
  /** Short human-readable description for the warning dialog. */
  detail: string;
}

/** Thresholds the UI and hooks agree on. */
export const EXAM_TIMING = {
  /** Minutes remaining at which the timer turns critical. */
  urgentMinutes: 5,
  /** Minutes remaining at which the timer turns critical-red. */
  criticalMinutes: 1,
  /** Gap after which the timer assumes the tab was suspended. */
  suspendThresholdMs: 2_000,
  /** Cadence of the display tick. Correctness does not depend on this. */
  tickIntervalMs: 250,
} as const;

/** Proctoring defaults, overridable per paper. */
export const PROCTORING_DEFAULTS = {
  /** Strikes tolerated before a forced submission. */
  maxViolations: 3,
  /** Switches closer together than this are treated as one event. */
  debounceMs: 2_000,
} as const;

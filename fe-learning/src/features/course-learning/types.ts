import type { QuestionMetadata } from '@/features/question-engine';

/**
 * Course Learning workspace — domain contracts.
 *
 * ## Services
 *  - Spring Boot owns courses, enrolment, and `learning_progress`
 *  - FastAPI (RAG) owns `/ai/tutor/*` and returns grounded, cited answers
 *
 * ## Hallucination policy
 * The tutor is constrained to course document chunks. Every answer MUST carry
 * citations; an answer with no sources is a retrieval failure and is surfaced
 * as such in the UI rather than presented as fact.
 */

export type MaterialType = 'VIDEO' | 'PDF' | 'DOCUMENT' | 'SLIDES';

/** A retrievable source backing a tutor answer. */
export interface TutorCitation {
  /** Stable id, used to de-duplicate badges. */
  sourceId: string;
  /** Human label, e.g. "Slide 14" or "Lecture notes p.7". */
  label: string;
  /** Where the chunk came from, for provenance. */
  documentTitle: string | null;
  /** Page for PDFs / slide index for slides; null for video timestamps. */
  location: string | null;
  /** Short excerpt; shown when a badge is expanded. */
  snippet: string;
  /** 0..1 retrieval similarity, when the service reports it. */
  score: number | null;
}

/** One unit of course content. */
export interface CourseMaterial {
  materialId: number;
  title: string;
  materialType: MaterialType;
  /** Playable URL for VIDEO. */
  videoUrl: string | null;
  /** Document URL for PDF / DOCUMENT / SLIDES. */
  documentUrl: string | null;
  /** Extracted plain text used for in-page selection and RAG context. */
  textContent: string | null;
  durationSeconds: number | null;
  totalSlides: number | null;
}

/** A material plus the learner's own progress through it. */
export interface MaterialProgress {
  materialId: number;
  lastWatchedSeconds: number;
  /** 0..1, for videos. */
  percentComplete: number;
  lastViewedAt: string | null;
}

export interface CourseModule {
  moduleId: number;
  title: string;
  description: string | null;
  orderIndex: number;
  materials: CourseMaterial[];
}

export interface CourseOutline {
  courseId: number;
  code: string;
  title: string;
  term: string;
  modules: CourseModule[];
}

/* -------------------------------------------------------------------------- */
/*  SSE event contract                                                          */
/* -------------------------------------------------------------------------- */

/**
 * Events emitted by `POST /api/v1/ai/tutor/chat-stream`.
 *
 * Wire format is SSE (`data: {json}\n\n`). Each payload is one of:
 *  - `{ type: 'token',   content: string }`  — an incremental answer fragment
 *  - `{ type: 'citation', source: TutorCitation }` — a retrieved chunk
 *  - `{ type: 'sources', sources: TutorCitation[] }` — final source list
 *  - `{ type: 'done',    finishReason?: string }`
 *  - `{ type: 'error',   message: string }`
 *
 * The parser also tolerates a bare `[DONE]` sentinel and a truncated final
 * frame, so a mid-flight disconnect degrades instead of hanging.
 */
export type TutorStreamEvent =
  | { type: 'token'; content: string }
  | { type: 'citation'; source: TutorCitation }
  | { type: 'sources'; sources: TutorCitation[] }
  | { type: 'done'; finishReason?: string }
  | { type: 'error'; message: string };

/** Request body for a tutor turn. */
export interface TutorChatRequest {
  courseId: number;
  message: string;
  /** Prior turns, so the service can resolve follow-up references. */
  history: { role: 'user' | 'assistant'; content: string }[];
  /** Text the learner selected, grounding the question in the material. */
  selectedText: string | null;
  /** Material the selection came from, when applicable. */
  materialId: number | null;
}

/* -------------------------------------------------------------------------- */
/*  Chat message model                                                           */
/* -------------------------------------------------------------------------- */

export type ChatRole = 'user' | 'assistant';

/** A completed turn in the transcript. */
export interface ChatMessage {
  id: string;
  role: ChatRole;
  content: string;
  citations: TutorCitation[];
  /** True when the turn was cut short by the learner pressing Stop. */
  wasAborted: boolean;
  createdAt: number;
}

/** Text the learner selected in the material. */
export interface MaterialSelection {
  text: string;
  /** Material the selection came from. */
  materialId: number;
  materialTitle: string;
  /** 1-based page/slide, when the viewer knows it. */
  location: string | null;
}

/** Progress payload written to `learning_progress`. */
export interface ProgressUpdate {
  materialId: number;
  lastWatchedSeconds: number;
  percentComplete: number;
}

/** Suggested follow-ups rendered as chips under an answer. */
export interface TutorSuggestion {
  id: string;
  label: string;
  prompt: string;
}

/** Progressive disclosure constants. */
export const LEARNING_LIMITS = {
  /** Debounce before persisting video progress, in ms. */
  progressDebounceMs: 5_000,
  /** Above this ratio the panel counts as "read". */
  completeThreshold: 0.9,
  /** Longest selection forwarded as context; longer text is truncated. */
  maxSelectionChars: 2_000,
} as const;

export type { QuestionMetadata };
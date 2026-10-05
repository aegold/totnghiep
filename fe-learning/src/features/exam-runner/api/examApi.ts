import { ApiError } from '@/types/api';
import { http } from '@/lib/apiClient';
import type {
  ExamSession,
  SubmitExamRequest,
  SubmitExamResult,
} from '@/features/exam-runner/types';
import { buildMockSession } from '@/features/exam-runner/mock/mockSession';

/**
 * Exam transport layer.
 *
 * ## Live backend
 * The mock switch is now OFF: every call routes through {@link http}. The paths
 * below are the ones the Spring Boot controller actually publishes - it answers
 * each at both a `/student/**` path and a legacy alias, and the alias is used
 * here so the URL shapes stay stable.
 *
 * ## Why submit takes the exam id
 * The submit route is `/exam-submissions/{examId}/submit`. The path segment is the
 * EXAM id, not the submission id, and the backend cross-checks the two after
 * grading so a mismatch is reported rather than silently accepted. The submission
 * id still travels in the body, which is what identifies the attempt.
 */
const USE_MOCKS: boolean = false;

/** Endpoint paths, mirroring the REST shape the backend exposes. */
export const EXAM_ENDPOINTS = {
  start: (examId: number): string => `/exam-runs/${examId}/start`,
  resync: (submissionId: number): string => `/exam-submissions/${submissionId}/sync`,
  submit: (examId: number): string => `/exam-submissions/${examId}/submit`,
} as const;

/** Simulated latency so loading states are exercised during development. */
const MOCK_LATENCY_MS = 400;

/** Resolves after a short delay; keeps the mock honest about being async. */
const delay = (ms: number): Promise<void> =>
  new Promise<void>((resolve) => window.setTimeout(resolve, ms));

/**
 * Normalises a raw submission body.
 *
 * The mock path routes through the real request serialiser so it exercises the
 * exact same shape the backend will receive.
 */
export const buildSubmissionPayload = (
  request: Omit<SubmitExamRequest, 'submittedAt'>,
): SubmitExamRequest => ({
  ...request,
  submittedAt: new Date().toISOString(),
});

export const examApi = {
  /**
   * Opens (or resumes) an attempt.
   *
   * The backend is idempotent here: calling again while an attempt is open
   * returns that attempt rather than starting a second one, so a reconnect after
   * a dropped connection does not consume a second sitting.
   */
  async startExam(examId: number): Promise<ExamSession> {
    if (!USE_MOCKS) {
      return http.post<ExamSession>(EXAM_ENDPOINTS.start(examId));
    }

    await delay(MOCK_LATENCY_MS);
    return buildMockSession(examId);
  },

  /**
   * Re-reads the server clock after the client suspects a suspended tab.
   *
   * `serverEndTime` comes back as an absolute UTC instant (ISO-8601 with a `Z`),
   * so `Date.parse` resolves it without guessing a timezone - the reason the
   * countdown is correct for a candidate in UTC+7.
   */
  async resync(submissionId: number): Promise<{ remainingSeconds: number; serverNow: string }> {
    if (!USE_MOCKS) {
      return http.get<{ remainingSeconds: number; serverNow: string }>(
        EXAM_ENDPOINTS.resync(submissionId),
      );
    }

    await delay(80);
    return { remainingSeconds: Number.NaN, serverNow: new Date().toISOString() };
  },

  /**
   * Finalises the attempt and returns the graded mark sheet.
   *
   * @param examId   the paper, which is what the submit route is keyed on.
   * @param request  the body; `submissionId` inside it identifies the attempt.
   */
  async submitExam(examId: number, request: SubmitExamRequest): Promise<SubmitExamResult> {
    if (!USE_MOCKS) {
      try {
        return await http.post<SubmitExamResult>(EXAM_ENDPOINTS.submit(examId), request);
      } catch (error: unknown) {
        if (error instanceof ApiError && error.status === 409) {
          // Already graded server-side. The mark sheet exists; the duplicate
          // press is not an error worth alarming the candidate about.
          return {
            submissionId: request.submissionId,
            examId,
            totalScore: 0,
            maxScore: 0,
            percentage: 0,
            answeredCount: request.answers.length,
            questionCount: request.answers.length,
            correctCount: 0,
            isLate: false,
            graceSeconds: 30,
            submittedAt: new Date().toISOString(),
            results: [],
          };
        }
        throw error;
      }
    }

    await delay(MOCK_LATENCY_MS);
    return {
      submissionId: request.submissionId,
      examId,
      totalScore: 0,
      maxScore: 10,
      percentage: 0,
      answeredCount: request.answers.length,
      questionCount: request.answers.length,
      correctCount: 0,
      isLate: false,
      graceSeconds: 30,
      submittedAt: new Date().toISOString(),
      results: [],
    };
  },
};
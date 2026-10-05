import { MOCK_QUESTION_ENTRIES } from '@/features/question-engine';
import type { ExamQuestion, ExamSession } from '@/features/exam-runner/types';
import { PROCTORING_DEFAULTS } from '@/features/exam-runner/types';

/**
 * A realistic paper assembled from the Stage 2 fixtures.
 *
 * Ordering is intentionally NOT chronological — it proves the runner depends
 * on presentation order rather than on payload order. Version ids are offset
 * from the fixture ids so cache keys are unmistakably per-question.
 */
const VERSION_ID_OFFSET: number = 9000;

const DIFFICULTY_BY_POSITION: readonly number[] = [1, 2, 3, 2, 4, 1, 3, 2];
const POINTS_BY_POSITION: readonly number[] = [2, 3, 5, 2, 4, 1, 3, 2];

/** Mock paper length in minutes. */
const MOCK_DURATION_MINUTES: number = 45;

/**
 * Builds a mock paper.
 *
 * `now` is injectable so tests and the "resume" flow can control the deadline.
 */
export const buildMockSession = (examId: number, now: number = Date.now()): ExamSession => {
  const questions: ExamQuestion[] = MOCK_QUESTION_ENTRIES.map((entry, index: number) => ({
    versionId: entry.id + VERSION_ID_OFFSET,
    questionId: entry.id,
    questionType: entry.questionType,
    payload: entry.payload,
    difficultyLevel: DIFFICULTY_BY_POSITION[index] ?? 1,
    points: POINTS_BY_POSITION[index] ?? 1,
  }));

  const startMs: number = now;
  const endMs: number = startMs + MOCK_DURATION_MINUTES * 60_000;

  return {
    submissionId: 1000 + examId,
    examId,
    title: 'Giữa kỳ · Nền tảng Khoa học Máy tính',
    instructions:
      'Hãy trả lời tất cả các câu hỏi. Bạn có thể đánh dấu câu hỏi để xem lại trước khi nộp bài. ' +
      'Việc chuyển sang tab khác sẽ được ghi nhận là một vi phạm quy chế giám sát thi.',
    durationMinutes: MOCK_DURATION_MINUTES,
    serverStartTime: new Date(startMs).toISOString(),
    serverEndTime: new Date(endMs).toISOString(),
    totalPoints: questions.reduce((sum: number, question: ExamQuestion) => sum + question.points, 0),
    maxViolations: PROCTORING_DEFAULTS.maxViolations,
    questions,
  };
};
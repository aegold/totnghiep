import { http } from '@/lib/apiClient';
import type {
  AtRiskStudent,
  CourseAnalytics,
  DifficultNode,
  DifficultQuestion,
  LiveAtRiskStudent,
  ScoreBucket,
} from '@/features/teacher/types';

/** Live analytics endpoints, matching the backend controller exactly. */
export const ANALYTICS_ENDPOINTS = {
  course: (courseId: number | string): string => `/teacher/analytics/courses/${courseId}`,
  exam: (examId: number | string): string => `/teacher/analytics/exams/${examId}`,
} as const;

/** Raw course payload, one layer below the ApiResponse envelope. */
interface CourseAnalyticsResponse {
  courseId: number;
  courseTitle: string;
  overview: {
    totalStudents: number;
    averageScore: number | null;
    /** Percentage 0-100; the dashboard models completion as a 0-1 fraction. */
    completionRate: number | null;
    atRiskStudentCount: number;
  };
  weakKnowledgeNodes: {
    nodeId: number;
    nodeName: string;
    averageMastery: number | null;
    studentCount: number;
  }[];
  atRiskStudents: LiveAtRiskStudent[];
}

/** Raw exam payload, one layer below the ApiResponse envelope. */
interface ExamAnalyticsResponse {
  examId: number;
  examTitle: string;
  courseId: number;
  courseTitle: string;
  submissionCount: number;
  averageScore: number | null;
  scoreBuckets: { bucketLabel: string; studentCount: number; percentage: number }[];
  questionFailureRates: {
    questionId: number;
    versionId: number;
    promptSnippet: string;
    questionType: string;
    totalAttempts: number;
    failurePercentage: number;
  }[];
  atRiskParticipants: LiveAtRiskStudent[];
}

/** Band edges, used only to label a bar chart axis. */
const BUCKET_RANGES: readonly { min: number; max: number }[] = [
  { min: 0, max: 40 },
  { min: 40, max: 60 },
  { min: 60, max: 80 },
  { min: 80, max: 100 },
];

export const analyticsApi = {
  /** Course dashboard payload straight from the server. */
  getCourseAnalytics(courseId: number | string): Promise<CourseAnalyticsResponse> {
    return http.get<CourseAnalyticsResponse>(ANALYTICS_ENDPOINTS.course(courseId));
  },

  /** Paper dashboard payload straight from the server. */
  getExamAnalytics(examId: number | string): Promise<ExamAnalyticsResponse> {
    return http.get<ExamAnalyticsResponse>(ANALYTICS_ENDPOINTS.exam(examId));
  },
};

/**
 * Folds the two endpoints into the shape the dashboard already renders.
 *
 * ## Why a mapping layer at all
 * The dashboard was built against mock fixtures that carry a per-student table.
 * The server has no endpoint returning one - and does not need one, because it
 * computes every aggregate in SQL. Rather than rewrite the Recharts wiring, the
 * server aggregates are passed through as authoritative fields (`scoreBuckets`,
 * `atRisk`) which the dashboard prefers, while the derived fields keep working
 * for the fixtures.
 *
 * ## Why the exam call is optional
 * A course may legitimately have no paper yet, in which case there are no
 * question-level failure rates to show. That must render as an empty list, not a
 * failed page load.
 */
export const toCourseAnalytics = async (
  courseId: number | string,
  examId?: number | string,
): Promise<CourseAnalytics> => {
  const course = await analyticsApi.getCourseAnalytics(courseId);

  let exam: ExamAnalyticsResponse | null = null;
  if (examId !== undefined && examId !== null && `${examId}` !== '') {
    try {
      exam = await analyticsApi.getExamAnalytics(examId);
    } catch {
      // The paper may not exist yet, or carry no submissions. The course-level
      // figures are still valid and worth rendering.
      exam = null;
    }
  }

  const difficultQuestions: DifficultQuestion[] = (exam?.questionFailureRates ?? []).map((q) => ({
    questionId: q.questionId,
    questionText: q.promptSnippet,
    // The dashboard models a rate as a 0-1 fraction.
    failureRate: (q.failurePercentage ?? 0) / 100,
    attempts: q.totalAttempts,
  }));

  const difficultNodes: DifficultNode[] = course.weakKnowledgeNodes.map((n) => ({
    nodeId: String(n.nodeId),
    label: n.nodeName,
    mastery: n.averageMastery ?? 0,
    studentCount: n.studentCount,
  }));

  const scoreBuckets: ScoreBucket[] = (exam?.scoreBuckets ?? []).map((b, index) => ({
    label: b.bucketLabel,
    min: BUCKET_RANGES[index]?.min ?? index * 20,
    max: BUCKET_RANGES[index]?.max ?? (index + 1) * 20,
    count: b.studentCount,
  }));

  return {
    courseId: course.courseId,
    // The endpoint returns no course code; the header renders title alone.
    courseCode: '',
    courseTitle: course.courseTitle,
    totalEnrolled: course.overview.totalStudents,
    averageScore: course.overview.averageScore ?? 0,
    // Server sends a percentage; the dashboard models a 0-1 fraction.
    completionRate: (course.overview.completionRate ?? 0) / 100,
    atRiskCount: course.overview.atRiskStudentCount,
    students: [],
    difficultQuestions,
    difficultNodes,
    scoreBuckets,
    atRisk: course.atRiskStudents.map(toAtRiskRow),
  };
};


/**
 * Folds a server at-risk row into the shape the dashboard table renders.
 *
 * The reason sentence is passed through verbatim - the backend already composed it
 * in Vietnamese and explained every trigger that fired, which is exactly what a
 * teacher needs and something a client-side threshold check cannot reproduce.
 *
 * `riskScore` is derived only to keep the sort stable; it is not shown.
 */
const toAtRiskRow = (live: LiveAtRiskStudent): AtRiskStudent => ({
  student: {
    studentId: live.studentId,
    studentName: live.fullName,
    email: live.email,
    averageScore: live.averageScore ?? 0,
    // Not reported per student by this endpoint; the cohort figure covers it.
    completionRate: 0,
    // The backend emits both spellings; prefer the documented one.
    tabSwitchCount: live.tabSwitchCount ?? live.maxTabSwitches,
    weakestNodeMastery: 0,
    lastActiveAt: '',
  },
  reasons: [live.riskReason],
  riskScore: 100 - (live.averageScore ?? 0),
});
export type { CourseAnalyticsResponse, ExamAnalyticsResponse };
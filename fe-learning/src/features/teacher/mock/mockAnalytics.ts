import type {
  AtRiskStudent,
  CourseAnalytics,
  DifficultNode,
  DifficultQuestion,
  ScoreBucket,
  StudentPerformance,
} from '@/features/teacher/types';
import { RISK_THRESHOLDS } from '@/features/teacher/types';

/**
 * Deterministic mock cohort.
 *
 * Scores come from a fixed seed rather than `Math.random()`, so the charts,
 * the metric cards, and the at-risk table always agree. A dashboard whose own
 * numbers contradict each other is worse than no dashboard.
 */
const FIRST_NAMES = [
  'Linh', 'Minh', 'Thu', 'An', 'Bao', 'Chi', 'Duc', 'Gia', 'Hoa', 'Kien',
  'Lan', 'Mai', 'Nam', 'Phuong', 'Quan', 'Sang', 'Trang', 'Vu', 'Yen', 'Binh',
] as const;

const LAST_NAMES = [
  'Nguyễn', 'Trần', 'Lê', 'Phạm', 'Hoàng', 'Phan', 'Vũ', 'Đặng', 'Bùi', 'Đỗ',
] as const;

/** Small deterministic PRNG (mulberry32). */
const seededRandom = (seed: number): (() => number) => {
  let state = seed;
  return (): number => {
    state += 0x6d2b79f5;
    let t = state;
    t = Math.imul(t ^ (t >>> 15), t | 1);
    t ^= t + Math.imul(t ^ (t >>> 7), t | 61);
    return ((t ^ (t >>> 14)) >>> 0) / 4294967296;
  };
};

const TOTAL_STUDENTS = 128;

/**
 * Emails must stay ASCII: local-parts are case-insensitive identifiers, and
 * diacritics are not universally permitted in the unquoted local-part even
 * though the domain may not be ASCII. `stripDiacritics` yields a stable handle
 * without leaking an un-ASCII address into the data.
 */
const stripDiacritics = (value: string): string =>
  value.normalize('NFD').replace(/\p{Diacritic}/gu, '');

const buildStudents = (): StudentPerformance[] => {
  const random = seededRandom(42);

  return Array.from({ length: TOTAL_STUDENTS }, (_unused, index) => {
    const first = FIRST_NAMES[index % FIRST_NAMES.length] as string;
    const last = LAST_NAMES[Math.floor(index / FIRST_NAMES.length) % LAST_NAMES.length] as string;

    /* Sum of two uniforms: most students mid-range, with tails at both ends. */
    const raw = random() + random();
    const averageScore: number = Math.round(25 + raw * 65);
    const completionRate: number = Math.min(1, Math.max(0.05, random() * 1.1));
    const weakestNodeMastery: number = Math.round(15 + random() * 75);
    const tabSwitchCount: number = Math.floor(random() * 9);

    return {
      studentId: 2_000 + index,
      studentName: `${first} ${last}`,
      email: `${stripDiacritics(first).toLowerCase()}.${stripDiacritics(last).toLowerCase()}@sv.edu.vn`,
      averageScore,
      completionRate,
      tabSwitchCount,
      weakestNodeMastery,
      lastActiveAt: new Date(
        Date.UTC(2026, 2, 1 + Math.floor(random() * 20), 8 + Math.floor(random() * 10)),
      ).toISOString(),
    };
  });
};

const DIFFICULT_QUESTIONS: readonly DifficultQuestion[] = [
  { questionId: 501, questionText: 'Độ phức tạp của dãy Fibonacci đệ quy không ghi nhớ', failureRate: 0.78, attempts: 126 },
  { questionId: 502, questionText: 'Lựa chọn hàm băm khi gặp khoá đối kháng', failureRate: 0.71, attempts: 119 },
  { questionId: 503, questionText: 'Phân tích chi phí quy hoạch khi bảng thay đổi kích thước', failureRate: 0.63, attempts: 124 },
  { questionId: 504, questionText: 'Xác định vòng lặp nào vi phạm trường hợp 2 của định lý Master', failureRate: 0.58, attempts: 131 },
  { questionId: 505, questionText: 'Truy vết phép tìm kiếm theo chiều rộng trên đồ thị có chu trình', failureRate: 0.47, attempts: 120 },
  { questionId: 506, questionText: 'Chèn vào cây đỏ-đen khi nút chú là nút đỏ', failureRate: 0.41, attempts: 108 },
] as const;

const DIFFICULT_NODES: readonly DifficultNode[] = [
  { nodeId: 'cs310', label: 'Bài toán NP-đầy đủ', mastery: 31, studentCount: 128 },
  { nodeId: 'cs309', label: 'Quay lui và Phân nhánh-Cắt', mastery: 38, studentCount: 126 },
  { nodeId: 'math105', label: 'Xác suất rời rạc', mastery: 42, studentCount: 119 },
  { nodeId: 'cs307', label: 'Đường đi ngắn nhất (Dijkstra)', mastery: 47, studentCount: 124 },
  { nodeId: 'cs207', label: 'Đồ thị và Giải thuật trên đồ thị', mastery: 51, studentCount: 131 },
  { nodeId: 'cs406', label: 'Đồng thời và Đồng bộ', mastery: 55, studentCount: 108 },
] as const;

const STUDENTS: StudentPerformance[] = buildStudents();

/** Buckets scores into the four bands the dashboard charts. */
export const buildScoreBuckets = (
  students: readonly StudentPerformance[],
): ScoreBucket[] => {
  const bands: { label: string; min: number; max: number }[] = [
    { label: '0–40%', min: 0, max: 40 },
    { label: '40–60%', min: 41, max: 60 },
    { label: '60–80%', min: 61, max: 80 },
    { label: '80–100%', min: 81, max: 100 },
  ];

  return bands.map((band) => ({
    ...band,
    count: students.filter(
      (student: StudentPerformance) =>
        student.averageScore >= band.min && student.averageScore <= band.max,
    ).length,
  }));
};

/**
 * Flags students needing intervention.
 *
 * Two independent signal groups are combined:
 *  - academic: weak mastery, low completion, low average
 *  - integrity: high `tab_switch_count` from the Stage 3 proctoring hook
 *
 * Every triggering reason is listed, so an instructor can tell a struggling
 * student apart from one whose exam behaviour needs a conversation.
 */
export const buildAtRiskStudents = (
  students: readonly StudentPerformance[],
): AtRiskStudent[] => {
  const flagged = students
    .map((student: StudentPerformance): AtRiskStudent => {
      const reasons: string[] = [];

      if (student.weakestNodeMastery < RISK_THRESHOLDS.lowMastery) {
        reasons.push(`Mức độ thành thạo thấp (${student.weakestNodeMastery}%)`);
      }
      if (student.completionRate < RISK_THRESHOLDS.lowCompletion) {
        reasons.push(
          `Theo sau tài liệu (đã học ${Math.round(student.completionRate * 100)}%)`,
        );
      }
      if (student.averageScore < RISK_THRESHOLDS.lowMastery) {
        reasons.push(`Điểm trung bình thấp (${student.averageScore}%)`);
      }
      if (student.tabSwitchCount > RISK_THRESHOLDS.highTabSwitches) {
        reasons.push(`${student.tabSwitchCount} lần chuyển tab trong kỳ thi`);
      }

      /*
       * Weighted so an integrity signal outweighs any single weak topic — it
       * calls for a conversation, not another remedial lesson.
       */
      const riskScore: number = Math.min(
        100,
        (100 - student.weakestNodeMastery) * 0.4 +
          (1 - student.completionRate) * 30 +
          (100 - student.averageScore) * 0.3 +
          student.tabSwitchCount * 8,
      );

      return { student, reasons, riskScore: Math.round(riskScore) };
    })
    .filter((entry: AtRiskStudent) => entry.reasons.length > 0);

  return flagged.sort((a: AtRiskStudent, b: AtRiskStudent) => b.riskScore - a.riskScore);
};

/** Most-challenging questions, highest failure rate first. */
export const buildDifficultQuestions = (
  questions: readonly DifficultQuestion[],
): DifficultQuestion[] =>
  [...questions].sort(
    (a: DifficultQuestion, b: DifficultQuestion) => b.failureRate - a.failureRate,
  );

/** Weakest knowledge nodes, lowest mastery first. */
export const buildDifficultNodes = (
  nodes: readonly DifficultNode[],
): DifficultNode[] =>
  [...nodes].sort((a: DifficultNode, b: DifficultNode) => a.mastery - b.mastery);

const AT_RISK: AtRiskStudent[] = buildAtRiskStudents(STUDENTS);

export const MOCK_COURSE_ANALYTICS: CourseAnalytics = {
  courseId: 1,
  courseCode: 'CS201',
  courseTitle: 'Cấu trúc dữ liệu và Giải thuật',
  totalEnrolled: STUDENTS.length,
  averageScore: Math.round(
    STUDENTS.reduce((sum: number, s: StudentPerformance) => sum + s.averageScore, 0) /
      STUDENTS.length,
  ),
  completionRate:
    STUDENTS.reduce((sum: number, s: StudentPerformance) => sum + s.completionRate, 0) /
    STUDENTS.length,
  atRiskCount: AT_RISK.length,
  students: STUDENTS,
  difficultQuestions: buildDifficultQuestions(DIFFICULT_QUESTIONS),
  difficultNodes: buildDifficultNodes(DIFFICULT_NODES),
};
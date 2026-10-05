/** Learning analytics — domain contracts. */

/** A single student's performance in one course. */
export interface StudentPerformance {
  studentId: number;
  studentName: string;
  email: string;
  /** 0–100 average across graded attempts. */
  averageScore: number;
  /** 0–1 share of course material completed. */
  completionRate: number;
  /** From Stage 3 proctoring: tab switches recorded during exams. */
  tabSwitchCount: number;
  /** 0–100 weakest knowledge-node mastery. */
  weakestNodeMastery: number;
  lastActiveAt: string;
}

/** Aggregate figures for one course. */
export interface CourseAnalytics {
  courseId: number;
  courseCode: string;
  courseTitle: string;
  totalEnrolled: number;
  /** Mean of `averageScore` across all students. */
  averageScore: number;
  /** Mean of `completionRate`, as a fraction. */
  completionRate: number;
  atRiskCount: number;
  students: StudentPerformance[];
  /** Question-level failure rates, most-failed first. */
  difficultQuestions: DifficultQuestion[];
  /** Knowledge-node mastery, weakest first. */
  difficultNodes: DifficultNode[];

  /**
   * Server-computed histogram, when one was fetched.
   *
   * The dashboard otherwise derives buckets from students[].averageScore, which is
   * only possible when every student's row is loaded. The endpoint aggregates in
   * SQL, so this is authoritative and is preferred whenever present.
   */
  scoreBuckets?: ScoreBucket[];

  /**
   * At-risk rows carrying the server's own Vietnamese reason sentence, already
   * folded into the shape the table renders.
   */
  atRisk?: AtRiskStudent[];
}

export interface DifficultQuestion {
  questionId: number;
  questionText: string;
  /** 0–1 share of attempts answered incorrectly. */
  failureRate: number;
  attempts: number;
}

export interface DifficultNode {
  nodeId: string;
  label: string;
  /** 0–100 mean mastery across the cohort. */
  mastery: number;
  studentCount: number;
}

/**
 * An at-risk student exactly as the analytics endpoint reports one.
 *
 * The server decides membership and writes the Vietnamese reason, so this shape
 * is preferred over re-deriving it locally from thresholds.
 */
export interface LiveAtRiskStudent {
  studentId: number;
  fullName: string;
  email: string;
  /** Mean percentage, or null when nothing has been graded yet. */
  averageScore: number | null;
  /** Highest strike count in any single attempt. */
  maxTabSwitches: number;
  /** Alias the backend emits under the same value; both are accepted. */
  tabSwitchCount?: number;
  riskReason: string;
}
/** A student flagged for intervention. */
export interface AtRiskStudent {
  student: StudentPerformance;
  /** Human-readable reasons, e.g. "Low mastery (42%)". */
  reasons: string[];
  /** 0–100; higher means more urgent. */
  riskScore: number;
}

/** Score distribution bucket. */
export interface ScoreBucket {
  label: string;
  /** 0–100 range covered by this bucket. */
  min: number;
  max: number;
  count: number;
}

/** Thresholds defining who counts as at risk. */
export const RISK_THRESHOLDS = {
  /** Mastery below this is a concern. */
  lowMastery: 60,
  /** Tab switches above this suggest integrity concerns (Stage 3 default). */
  highTabSwitches: 3,
  /** Completion below this (as a fraction) is a concern. */
  lowCompletion: 0.4,
} as const;
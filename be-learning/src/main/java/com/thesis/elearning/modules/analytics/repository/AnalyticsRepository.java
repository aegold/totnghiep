package com.thesis.elearning.modules.analytics.repository;

import com.thesis.elearning.modules.course.entity.Course;
import java.math.BigDecimal;
import java.util.List;
import org.springframework.data.repository.Repository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * Every aggregate the analytics module needs, computed by MySQL.
 *
 * <h2>Why native SQL rather than JPQL</h2>
 * Three of the contracts cannot be expressed in JPQL at all. Bucketing needs a
 * {@code CASE} over a ratio of two aggregates; the histogram needs a
 * {@code LEFT JOIN} against a fixed four-row skeleton so empty bands survive;
 * and per-submission percentages need a derived table joined to its own exam's
 * point total. Writing them in JPQL would mean either a native query anyway or
 * loading rows into Java to average them - the one thing this module exists to
 * avoid.
 *
 * <h2>Why {@code Object[]} instead of interface projections</h2>
 * The {@code test} profile runs H2 with {@code DATABASE_TO_LOWER=TRUE}, which
 * lowercases every column label, so an {@code AS studentId} alias arrives as
 * {@code studentid} and alias-based projection mapping resolves nothing. Positional
 * mapping is identical on both engines, so the repository returns ordered arrays
 * and {@link com.thesis.elearning.modules.analytics.service.AnalyticsService}
 * names each position. This costs a little ceremony in one place and buys
 * queries that run unchanged on MySQL and on the integration-test database.
 *
 * <h2>Why every aggregate is scoped to submitted attempts</h2>
 * {@code submitted_at IS NOT NULL} appears throughout. An attempt in flight has
 * no {@code submission_answers} rows yet, so averaging it in would treat an
 * unfinished paper as a zero and quietly depress every figure on the dashboard.
 * The one deliberate exception is the proctoring maximum, documented on
 * {@link #atRiskStudentsByExam}.
 */
public interface AnalyticsRepository extends Repository<Course, Long> {

    /**
     * Per-submission percentage for one exam, as the shared building block.
     *
     * <p>A bare column list: it appears verbatim inside several larger queries
     * as a derived table, so it must not carry its own {@code SELECT} keyword.
     */
    String EXAM_SUBMISSION_PCT = """
            SELECT es.submission_id   AS submissionId,
                   es.student_id      AS studentId,
                   es.tab_switch_count AS tabSwitchCount,
                   SUM(sa.earned_points) * 100.0 / NULLIF(t.totalPoints, 0) AS pct
            FROM exam_submissions es
            JOIN (SELECT exam_id, SUM(points) AS totalPoints
                  FROM exam_questions
                  GROUP BY exam_id) t ON t.exam_id = es.exam_id
            JOIN submission_answers sa ON sa.submission_id = es.submission_id
            WHERE es.exam_id = :examId
              AND es.submitted_at IS NOT NULL
            GROUP BY es.submission_id, es.student_id, es.tab_switch_count, t.totalPoints
            """;

    /** Same, widened to every paper in a course. */
    String COURSE_SUBMISSION_PCT = """
            SELECT es.submission_id   AS submissionId,
                   es.student_id      AS studentId,
                   es.tab_switch_count AS tabSwitchCount,
                   SUM(sa.earned_points) * 100.0 / NULLIF(t.totalPoints, 0) AS pct
            FROM exam_submissions es
            JOIN exams e ON e.exam_id = es.exam_id AND e.is_deleted = 0
            JOIN (SELECT eq.exam_id, SUM(eq.points) AS totalPoints
                  FROM exam_questions eq
                  JOIN exams e2 ON e2.exam_id = eq.exam_id AND e2.is_deleted = 0
                  GROUP BY eq.exam_id) t ON t.exam_id = es.exam_id
            JOIN submission_answers sa ON sa.submission_id = es.submission_id
            WHERE e.course_id = :courseId
              AND es.submitted_at IS NOT NULL
            GROUP BY es.submission_id, es.student_id, es.tab_switch_count, t.totalPoints
            """;

    /**
     * Score histogram for one paper, always four rows.
     *
     * <p>Built as a LEFT JOIN onto a literal skeleton so an empty band comes back
     * as {@code 0}. Without it the chart loses that category and Recharts shifts
     * the remaining bars left, so the teacher reads a distribution that is not
     * the one in the database.
     *
     * @return rows of {@code [bucketIndex, studentCount, percentage]}, ascending.
     */
    @Query(value = """
            SELECT s.ord AS bucketIndex,
                   COALESCE(c.cnt, 0) AS studentCount,
                   COALESCE(ROUND(COALESCE(c.cnt, 0) * 100.0
                                   / NULLIF(SUM(COALESCE(c.cnt, 0)) OVER (), 0), 2), 0) AS pct
            FROM (SELECT 0 AS ord UNION ALL SELECT 1 UNION ALL SELECT 2 UNION ALL SELECT 3) s
            LEFT JOIN (
                SELECT CASE WHEN x.pct < :weakUpper THEN 0
                            WHEN x.pct < :averageUpper THEN 1
                            WHEN x.pct < :goodUpper THEN 2
                            ELSE 3 END AS ord,
                       COUNT(*) AS cnt
                FROM (""" + EXAM_SUBMISSION_PCT + """
                      ) x
                WHERE x.pct IS NOT NULL
                GROUP BY CASE WHEN x.pct < :weakUpper THEN 0
                              WHEN x.pct < :averageUpper THEN 1
                              WHEN x.pct < :goodUpper THEN 2
                              ELSE 3 END
            ) c ON c.ord = s.ord
            ORDER BY s.ord
            """, nativeQuery = true)
    List<Object[]> examHistogram(
            @Param("examId") Long examId,
            @Param("weakUpper") BigDecimal weakUpper,
            @Param("averageUpper") BigDecimal averageUpper,
            @Param("goodUpper") BigDecimal goodUpper);

    /**
     * {@code [submissionCount, averageScore]} for one paper; {@code [0, null]} when none.
     *
     * <p>Declared as a {@code List} even though the aggregate always yields exactly
     * one row: Spring Data hands back a bare {@code Object[]} wrapped one level deep,
     * which the service would read as a row whose first column is itself a row.
     */

    @Query(value = """
            SELECT COUNT(*) AS submissionCount,
                   ROUND(AVG(x.pct), 2) AS averageScore
            FROM (""" + EXAM_SUBMISSION_PCT + """
                  ) x
            """, nativeQuery = true)
    List<Object[]> examSummary(@Param("examId") Long examId);

    /**
     * {@code [totalStudents, averageScore]} for a course.
     *
     * <p>Participants are the union of two independent sources, because there is
     * no enrolment table: students who sat one of the course's papers, and
     * students with lesson progress in it. Either alone under-reports - a student
     * who attended but never sat an exam, or one who sat a paper without touching
     * a lesson - and the count drives the denominator of every rate on the page.
     */
    @Query(value = """
            SELECT (SELECT COUNT(*) FROM (
                       SELECT DISTINCT es.student_id
                       FROM exam_submissions es
                       JOIN exams e ON e.exam_id = es.exam_id AND e.is_deleted = 0
                       WHERE e.course_id = :courseId
                       UNION
                       SELECT DISTINCT lp.student_id
                       FROM learning_progress lp
                       JOIN lessons l ON l.lesson_id = lp.lesson_id AND l.is_deleted = 0
                       JOIN chapters c ON c.chapter_id = l.chapter_id AND c.is_deleted = 0
                       WHERE c.course_id = :courseId
                   ) participants) AS totalStudents,
                   (SELECT ROUND(AVG(y.pct), 2) FROM (""" + COURSE_SUBMISSION_PCT + """
                       ) y) AS averageScore
            """, nativeQuery = true)
    List<Object[]> courseSummary(@Param("courseId") Long courseId);

    /**
     * Mean completed-lessons ratio, as a percentage.
     *
     * <p>Averaged per student rather than pooled over progress rows, so one
     * student with many rows cannot dominate the figure. The total-lessons count
     * is CROSS JOINed as a scalar so it is computed once for the whole course
     * instead of being re-evaluated per student.
     */
    @Query(value = """
            SELECT COALESCE(ROUND(AVG(p.done * 100.0 / NULLIF(t.totalLessons, 0)), 2), 0) AS completionRate
            FROM (
                SELECT lp.student_id,
                       SUM(CASE WHEN lp.is_completed = 1 THEN 1 ELSE 0 END) AS done
                FROM learning_progress lp
                JOIN lessons l ON l.lesson_id = lp.lesson_id AND l.is_deleted = 0
                JOIN chapters c ON c.chapter_id = l.chapter_id AND c.is_deleted = 0
                WHERE c.course_id = :courseId
                GROUP BY lp.student_id
            ) p
            CROSS JOIN (
                SELECT COUNT(*) AS totalLessons
                FROM lessons l2
                JOIN chapters c2 ON c2.chapter_id = l2.chapter_id AND c2.is_deleted = 0
                WHERE c2.course_id = :courseId AND l2.is_deleted = 0
            ) t
            """, nativeQuery = true)
    List<Object[]> courseCompletionRate(@Param("courseId") Long courseId);

    /**
     * At-risk students on one paper.
     *
     * <p>The only query that looks at attempts still in flight. A live session
     * with three tab switches is exactly the situation a teacher wants surfaced,
     * and gating on {@code submitted_at} would hide it until the paper closed -
     * by which time the behaviour is over and nothing can be done about it. Such
     * a student contributes no percentage, so {@code AVG} yields {@code null}
     * rather than a misleading zero, and the record documents that a null score
     * is possible.
     *
     * <p>Ordering puts ungraded students last: {@code COALESCE(pct, 101)} sorts a
     * student with no graded attempt behind every scored one, instead of leaving
     * the database's NULL-ordering rule to decide it.
     *
     * @return rows of {@code [studentId, fullName, email, averageScore, maxTabSwitches]}.
     */
    @Query(value = """
            SELECT u.user_id AS studentId,
                   u.full_name AS fullName,
                   u.email AS email,
                   ROUND(AVG(a.pct), 2) AS averageScore,
                   MAX(a.tabSwitchCount) AS maxTabSwitches
            FROM (
                SELECT es.submission_id,
                       es.student_id,
                       es.tab_switch_count AS tabSwitchCount,
                       CASE WHEN es.submitted_at IS NOT NULL
                            THEN SUM(sa.earned_points) * 100.0 / NULLIF(t.totalPoints, 0)
                            ELSE NULL END AS pct
                FROM exam_submissions es
                JOIN (SELECT exam_id, SUM(points) AS totalPoints
                      FROM exam_questions GROUP BY exam_id) t ON t.exam_id = es.exam_id
                LEFT JOIN submission_answers sa ON sa.submission_id = es.submission_id
                WHERE es.exam_id = :examId
                GROUP BY es.submission_id, es.student_id, es.tab_switch_count,
                         es.submitted_at, t.totalPoints
            ) a
            JOIN users u ON u.user_id = a.student_id AND u.is_deleted = 0
            GROUP BY u.user_id, u.full_name, u.email
            HAVING AVG(a.pct) < :scoreThreshold OR MAX(a.tabSwitchCount) >= :tabThreshold
            ORDER BY COALESCE(AVG(a.pct), 101) ASC, u.user_id ASC
            """, nativeQuery = true)
    List<Object[]> atRiskStudentsByExam(
            @Param("examId") Long examId,
            @Param("scoreThreshold") BigDecimal scoreThreshold,
            @Param("tabThreshold") Integer tabThreshold);

    /** At-risk students across every paper in a course. Same shape as above. */
    @Query(value = """
            SELECT u.user_id AS studentId,
                   u.full_name AS fullName,
                   u.email AS email,
                   ROUND(AVG(a.pct), 2) AS averageScore,
                   MAX(a.tabSwitchCount) AS maxTabSwitches
            FROM (
                SELECT es.submission_id,
                       es.student_id,
                       es.tab_switch_count AS tabSwitchCount,
                       CASE WHEN es.submitted_at IS NOT NULL
                            THEN SUM(sa.earned_points) * 100.0 / NULLIF(t.totalPoints, 0)
                            ELSE NULL END AS pct
                FROM exam_submissions es
                JOIN exams e ON e.exam_id = es.exam_id AND e.is_deleted = 0
                JOIN (SELECT eq.exam_id, SUM(eq.points) AS totalPoints
                      FROM exam_questions eq
                      JOIN exams e2 ON e2.exam_id = eq.exam_id AND e2.is_deleted = 0
                      GROUP BY eq.exam_id) t ON t.exam_id = es.exam_id
                LEFT JOIN submission_answers sa ON sa.submission_id = es.submission_id
                WHERE e.course_id = :courseId
                GROUP BY es.submission_id, es.student_id, es.tab_switch_count,
                         es.submitted_at, t.totalPoints
            ) a
            JOIN users u ON u.user_id = a.student_id AND u.is_deleted = 0
            GROUP BY u.user_id, u.full_name, u.email
            HAVING AVG(a.pct) < :scoreThreshold OR MAX(a.tabSwitchCount) >= :tabThreshold
            ORDER BY COALESCE(AVG(a.pct), 101) ASC, u.user_id ASC
            """, nativeQuery = true)
    List<Object[]> atRiskStudentsByCourse(
            @Param("courseId") Long courseId,
            @Param("scoreThreshold") BigDecimal scoreThreshold,
            @Param("tabThreshold") Integer tabThreshold);

    /**
     * Per-question failure rate on one paper, hardest first.
     *
     * <p>The denominator is {@code COUNT(*)} over the answer rows, which Stage 5
     * guarantees exists for every paper question even when the student submitted
     * nothing - so this counts attempts, not answers, and a skipped question
     * correctly reads as a failure rather than vanishing from the average.
     *
     * <p>{@code content_payload} is returned raw and cast to text. The stem lives
     * at {@code $.prompt}, but {@code JSON_VALUE} / {@code JSON_EXTRACT} exist in
     * MySQL and not in the H2 build the integration tests run on, so the portable
     * form is a plain cast and the key is read out by the service. The cast is
     * what makes this query run unchanged on both engines.
     *
     * @return rows of {@code [questionId, versionId, questionType, promptRaw,
     *                   totalAttempts, failurePercentage]}.
     */
    @Query(value = """
            SELECT q.question_id AS questionId,
                   qv.version_id AS versionId,
                   q.question_type AS questionType,
                   CAST(qv.content_payload AS CHAR(2000)) AS promptRaw,
                   COUNT(*) AS totalAttempts,
                   ROUND(SUM(CASE WHEN sa.is_correct = 0 THEN 1 ELSE 0 END) * 100.0
                         / NULLIF(COUNT(*), 0), 2) AS failurePercentage
            FROM submission_answers sa
            JOIN exam_submissions es ON es.submission_id = sa.submission_id
                                     AND es.submitted_at IS NOT NULL
            JOIN question_versions qv ON qv.version_id = sa.version_id
            JOIN questions q ON q.question_id = qv.question_id
            WHERE es.exam_id = :examId
            GROUP BY q.question_id, qv.version_id, q.question_type, qv.content_payload
            ORDER BY failurePercentage DESC, qv.version_id ASC
            """, nativeQuery = true)
    List<Object[]> questionFailureRates(@Param("examId") Long examId);

    /**
     * Competencies the cohort is weakest at, for the course's own lessons.
     *
     * <p>Restricted to nodes actually reachable from the course through
     * {@code lesson_knowledge_nodes}. Without that restriction the query would
     * return the weakest node in the entire curriculum, which is a fact about
     * other courses and answers no question this page asks.
     *
     * @return rows of {@code [nodeId, nodeName, averageMastery, studentCount]}.
     */
    @Query(value = """
            SELECT kn.node_id AS nodeId,
                   kn.node_name AS nodeName,
                   ROUND(AVG(m.mastery_score), 2) AS averageMastery,
                   COUNT(*) AS studentCount
            FROM knowledge_node_mastery m
            JOIN knowledge_nodes kn ON kn.node_id = m.node_id
            WHERE m.node_id IN (
                SELECT DISTINCT lkn.node_id
                FROM lesson_knowledge_nodes lkn
                JOIN lessons l ON l.lesson_id = lkn.lesson_id AND l.is_deleted = 0
                JOIN chapters c ON c.chapter_id = l.chapter_id AND c.is_deleted = 0
                WHERE c.course_id = :courseId
            )
            GROUP BY kn.node_id, kn.node_name
            ORDER BY averageMastery ASC, kn.node_id ASC
            """, nativeQuery = true)
    List<Object[]> weakKnowledgeNodes(@Param("courseId") Long courseId);
}
package com.thesis.elearning.modules.analytics.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.thesis.elearning.common.exception.AppException;
import com.thesis.elearning.common.exception.ErrorCode;
import com.thesis.elearning.modules.analytics.domain.AtRiskRule;
import com.thesis.elearning.modules.analytics.domain.ScoreBucket;
import com.thesis.elearning.modules.analytics.dto.AtRiskStudentDto;
import com.thesis.elearning.modules.analytics.dto.CourseAnalyticsResponse;
import com.thesis.elearning.modules.analytics.dto.CourseOverviewMetricsDto;
import com.thesis.elearning.modules.analytics.dto.ExamAnalyticsResponse;
import com.thesis.elearning.modules.analytics.dto.QuestionFailureRateDto;
import com.thesis.elearning.modules.analytics.dto.ScoreDistributionBucketDto;
import com.thesis.elearning.modules.analytics.dto.WeakKnowledgeNodeDto;
import com.thesis.elearning.modules.analytics.repository.AnalyticsRepository;
import com.thesis.elearning.modules.course.entity.Course;
import com.thesis.elearning.modules.course.repository.CourseRepository;
import com.thesis.elearning.modules.exam.entity.Exam;
import com.thesis.elearning.modules.exam.repository.ExamRepository;
import com.thesis.elearning.modules.question.domain.ActorContext;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Teacher-facing analytics for one course or one paper.
 *
 * <h2>What this class does and does not compute</h2>
 * It computes nothing numeric. Every average, count, ratio and bucket assignment
 * happens in MySQL (see {@link AnalyticsRepository}); what happens here is
 * ownership enforcement, mapping ordered arrays onto DTOs, attaching the
 * Vietnamese labels and reason sentences, and presentation ordering. The one
 * piece of arithmetic performed locally is a division by the histogram total,
 * and even that is already computed by the query - this only reads it back.
 *
 * <h2>Ownership</h2>
 * A teacher sees only what they authored; an admin sees everything. The check is
 * {@code course.teacher_id == actor.userId}, resolved through {@code exams ->
 * courses} for the paper endpoint, because {@code exams} has no {@code teacher_id}
 * of its own. Notably the existence check runs <em>before</em> the ownership
 * check: answering 404 for someone else's course would confirm the id is real,
 * which is an enumeration oracle for other teachers' catalogues.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AnalyticsService {

    /** Shared by both failure reports; the sentence must not run past the stem. */
    private static final int SNIPPET_LIMIT = 160;

    private final AnalyticsRepository analyticsRepository;
    private final CourseRepository courseRepository;
    private final ExamRepository examRepository;
    private final ObjectMapper objectMapper;

    /* ================================ Course ================================ */

    /**
     * Assembles the course dashboard.
     *
     * @param courseId the course to analyse.
     * @param actor    the caller, whose role and id decide visibility.
     * @return headline metrics, weakest competencies and at-risk students.
     * @throws AppException 404 when no such course, 403 when the caller does not
     *                      own it.
     */
    public CourseAnalyticsResponse getCourseAnalytics(final Long courseId, final ActorContext actor) {
        final Course course = requireOwnedCourse(courseId, actor);

        final Object[] summary = firstRow(analyticsRepository.courseSummary(courseId));
        final Object[] completion = firstRow(analyticsRepository.courseCompletionRate(courseId));
        final List<AtRiskStudentDto> atRisk =
                mapAtRisk(analyticsRepository.atRiskStudentsByCourse(
                        courseId, AtRiskRule.SCORE_THRESHOLD, AtRiskRule.TAB_SWITCH_THRESHOLD));

        final CourseOverviewMetricsDto overview =
                new CourseOverviewMetricsDto(
                        asLong(summary, 0),
                        asBigDecimal(summary, 1),
                        asBigDecimal(completion, 0),
                        atRisk.size());

        return new CourseAnalyticsResponse(
                courseId, course.getTitle(), overview, mapWeakNodes(courseId), atRisk);
    }

    /* ================================= Exam ================================= */

    /**
     * Assembles the paper dashboard.
     *
     * @throws AppException 404 when no such paper or the caller does not own it,
     *                      400 when the paper has no graded submissions yet.
     */
    public ExamAnalyticsResponse getExamAnalytics(final Long examId, final ActorContext actor) {
        final Exam exam = requireOwnedExam(examId, actor);
        final Course course = exam.getCourse();

        final Object[] summary = firstRow(analyticsRepository.examSummary(examId));
        final int submissionCount = asInt(summary, 0);
        if (submissionCount == 0) {
            // A histogram of nothing would render four zero bars, which reads as
            // "everyone failed" rather than "no one has sat this yet".
            throw new AppException(
                    ErrorCode.BAD_REQUEST, "Không có dữ liệu bài nộp nào để thống kê cho bài thi này.");
        }

        return new ExamAnalyticsResponse(
                examId,
                exam.getTitle(),
                course.getCourseId(),
                course.getTitle(),
                submissionCount,
                asBigDecimal(summary, 1),
                mapBuckets(examId),
                mapFailureRates(examId),
                mapAtRisk(
                        analyticsRepository.atRiskStudentsByExam(
                                examId, AtRiskRule.SCORE_THRESHOLD, AtRiskRule.TAB_SWITCH_THRESHOLD)));
    }

    /* =============================== Ownership ============================== */

    /** Loads a course the caller is allowed to see, or explains why not. */
    private Course requireOwnedCourse(final Long courseId, final ActorContext actor) {
        final Course course =
                courseRepository
                        .findById(courseId)
                        .orElseThrow(
                                () ->
                                        new AppException(
                                                ErrorCode.RESOURCE_NOT_FOUND,
                                                "Không tìm thấy khóa học yêu cầu: " + courseId + "."));
        assertOwnership(course, actor);
        return course;
    }

    /**
     * Loads a paper the caller is allowed to see.
     *
     * <p>Ownership resolves through the owning course, since {@code exams} carries
     * no teacher of its own and the course is the only place authorship is
     * recorded.
     */
    private Exam requireOwnedExam(final Long examId, final ActorContext actor) {
        final Exam exam =
                examRepository
                        .findById(examId)
                        .orElseThrow(
                                () ->
                                        new AppException(
                                                ErrorCode.RESOURCE_NOT_FOUND,
                                                "Không tìm thấy bài thi yêu cầu: " + examId + "."));
        assertOwnership(exam.getCourse(), actor);
        return exam;
    }

    /**
     * Rejects a caller who neither authored the course nor is an admin.
     *
     * <p>Admins bypass by role rather than by being listed everywhere: a
     * university auditor must be able to read any cohort without being added to
     * each course.
     */
    private static void assertOwnership(final Course course, final ActorContext actor) {
        if (actor != null && actor.admin()) {
            return;
        }
        final Long authorId = course.getTeacher() == null ? null : course.getTeacher().getUserId();
        if (actor == null || authorId == null || !authorId.equals(actor.userId())) {
            throw new AppException(
                    ErrorCode.FORBIDDEN,
                    "Bạn không có quyền xem thống kê của khóa học hoặc bài thi này.");
        }
    }

    /* ================================ Mapping =============================== */

    /**
     * Turns the histogram rows into labelled buckets.
     *
     * <p>The query already returns exactly {@link ScoreBucket#POOL} rows in order,
     * including the empty ones. This maps position to ordinal to label; it does
     * not sort, and does not drop a zero.
     */
    private List<ScoreDistributionBucketDto> mapBuckets(final Long examId) {
        final List<Object[]> rows =
                analyticsRepository.examHistogram(
                        examId, ScoreBucket.WEAK_UPPER, ScoreBucket.AVERAGE_UPPER, ScoreBucket.GOOD_UPPER);

        final List<ScoreDistributionBucketDto> buckets = new ArrayList<>(ScoreBucket.POOL.length);
        for (final Object[] row : rows) {
            final int index = asInt(row, 0);
            buckets.add(
                    new ScoreDistributionBucketDto(
                            ScoreBucket.ofIndex(index).label(),
                            asInt(row, 1),
                            asBigDecimal(row, 2)));
        }
        return buckets;
    }

    /** Maps failure-rate rows, reading the stem out of the raw payload text. */
    private List<QuestionFailureRateDto> mapFailureRates(final Long examId) {
        final List<Object[]> rows = analyticsRepository.questionFailureRates(examId);
        final List<QuestionFailureRateDto> rates = new ArrayList<>(rows.size());

        for (final Object[] row : rows) {
            rates.add(
                    new QuestionFailureRateDto(
                            asLong(row, 0),
                            asLong(row, 1),
                            snippetOf(asString(row, 3)),
                            asString(row, 2),
                            asInt(row, 4),
                            asBigDecimal(row, 5)));
        }
        return rates;
    }

    /** Maps at-risk rows, composing the Vietnamese reason sentence per student. */
    private List<AtRiskStudentDto> mapAtRisk(final List<Object[]> rows) {
        final List<AtRiskStudentDto> students = new ArrayList<>(rows.size());

        for (final Object[] row : rows) {
            final BigDecimal averageScore = asBigDecimal(row, 3);
            final int maxTabSwitches = asInt(row, 4);
            students.add(
                    new AtRiskStudentDto(
                            asLong(row, 0),
                            asString(row, 1),
                            asString(row, 2),
                            averageScore,
                            maxTabSwitches,
                            AtRiskRule.reason(averageScore, maxTabSwitches)));
        }
        // The query orders by average ascending; re-sorting keeps the response
        // stable even if a future engine returns NULL ordering differently.
        students.sort(Comparator.comparing(AtRiskStudentDto::studentId));
        return students;
    }

    private List<WeakKnowledgeNodeDto> mapWeakNodes(final Long courseId) {
        final List<Object[]> rows = analyticsRepository.weakKnowledgeNodes(courseId);
        final List<WeakKnowledgeNodeDto> nodes = new ArrayList<>(rows.size());

        for (final Object[] row : rows) {
            nodes.add(
                    new WeakKnowledgeNodeDto(
                            asLong(row, 0), asString(row, 1), asBigDecimal(row, 2), asInt(row, 3)));
        }
        return nodes;
    }

    /* ================================ Snippet =============================== */

    /**
     * Extracts the stem from a serialised {@code content_payload}.
     *
     * <p>Why parse rather than let SQL do it: {@code JSON_VALUE} and
     * {@code JSON_EXTRACT} exist in MySQL but not in the H2 build the integration
     * tests run against, so a dialect-specific query would pass in production and
     * fail in CI. The query casts the column to text - portable on both - and the
     * one key read happens here.
     *
     * <p>Collapses whitespace because authored stems carry line breaks and
     * indentation that would otherwise break a table cell. Falls back to the raw
     * text on unparseable input rather than dropping the row: a missing snippet
     * is a cosmetic loss, whereas an exception would lose the failure rate too,
     * and that number is the reason the teacher opened the page.
     */
    private String snippetOf(final String promptRaw) {
        if (promptRaw == null || promptRaw.isBlank()) {
            return "";
        }
        String text;
        try {
            JsonNode document = objectMapper.readTree(promptRaw);
            /*
             * A JSON column does not round-trip identically on every engine. MySQL
             * returns the document itself - {"prompt":"..."} - whereas H2 hands back
             * a JSON *string* scalar, i.e. the same document escaped inside quotes.
             * Unwrapping once makes the extraction engine-independent instead of
             * correct on whichever database the fixture happened to be built for.
             */
            if (document.isTextual()) {
                document = objectMapper.readTree(document.asText());
            }
            final JsonNode prompt = document.path("prompt");
            text = prompt.isMissingNode() || prompt.isNull() ? promptRaw : prompt.asText();
        } catch (final com.fasterxml.jackson.core.JsonProcessingException ex) {
            text = promptRaw;
        }
        text = text.replaceAll("\\s+", " ").trim();
        return text.length() <= SNIPPET_LIMIT ? text : text.substring(0, SNIPPET_LIMIT).trim() + "…";
    }

    /* ================================ Readers =============================== */

    /*
     * Positional readers. The repository returns Object[] rather than projections
     * because the test profile's H2 lowercases every column alias, which breaks
     * alias-based mapping; positional access is identical on both engines. Each
     * reader tolerates null so an empty aggregate reads as zero rather than NPE.
     */

    /**
     * Unwraps the single aggregate row.
     *
     * <p>These queries have no {@code GROUP BY}, so exactly one row always comes
     * back; the empty case only guards a database that returned nothing, and the
     * positional readers already treat a missing column as zero.
     */
    private static Object[] firstRow(final List<Object[]> rows) {
        return rows == null || rows.isEmpty() ? new Object[0] : rows.get(0);
    }

    private static long asLong(final Object[] row, final int index) {
        if (row == null || row.length <= index || row[index] == null) {
            return 0L;
        }
        final Object value = row[index];
        return value instanceof Number number ? number.longValue() : Long.parseLong(value.toString());
    }

    private static int asInt(final Object[] row, final int index) {
        if (row == null || row.length <= index || row[index] == null) {
            return 0;
        }
        final Object value = row[index];
        return value instanceof Number number ? number.intValue() : Integer.parseInt(value.toString());
    }

    private static String asString(final Object[] row, final int index) {
        if (row == null || row.length <= index || row[index] == null) {
            return "";
        }
        return row[index].toString();
    }

    private static BigDecimal asBigDecimal(final Object[] row, final int index) {
        if (row == null || row.length <= index || row[index] == null) {
            return null;
        }
        final Object value = row[index];
        return value instanceof BigDecimal decimal
                ? decimal
                : new BigDecimal(value.toString());
    }
}
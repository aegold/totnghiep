package com.thesis.elearning.modules.analytics.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.thesis.elearning.common.exception.AppException;
import com.thesis.elearning.common.exception.ErrorCode;
import com.thesis.elearning.modules.analytics.domain.AtRiskRule;
import com.thesis.elearning.modules.analytics.domain.ScoreBucket;
import com.thesis.elearning.modules.analytics.dto.AtRiskStudentDto;
import com.thesis.elearning.modules.analytics.dto.CourseAnalyticsResponse;
import com.thesis.elearning.modules.analytics.dto.ExamAnalyticsResponse;
import com.thesis.elearning.modules.analytics.dto.ScoreDistributionBucketDto;
import com.thesis.elearning.modules.analytics.repository.AnalyticsRepository;
import com.thesis.elearning.modules.auth.entity.User;
import com.thesis.elearning.modules.course.entity.Course;
import com.thesis.elearning.modules.course.repository.CourseRepository;
import com.thesis.elearning.modules.exam.entity.Exam;
import com.thesis.elearning.modules.exam.repository.ExamRepository;
import com.thesis.elearning.modules.question.domain.ActorContext;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.data.jpa.repository.Query;

/**
 * Bucketing, risk evaluation, ownership and label mapping.
 *
 * <p>The repository is mocked throughout, which is what makes this suite about
 * the service's rules rather than about SQL. The arithmetic the database performs
 * is verified separately against a live MySQL by the integration test.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class AnalyticsServiceTest {

    private static final Long COURSE_ID = 1L;
    private static final Long EXAM_ID = 900L;
    private static final Long OWNER_ID = 7L;
    private static final Long STRANGER_ID = 99L;

    @Mock private AnalyticsRepository analyticsRepository;
    @Mock private CourseRepository courseRepository;
    @Mock private ExamRepository examRepository;

    private AnalyticsService service;
    private ActorContext owner;
    private ActorContext stranger;
    private ActorContext admin;

    @BeforeEach
    void setUp() {
        service =
                new AnalyticsService(
                        analyticsRepository, courseRepository, examRepository, new ObjectMapper());
        owner = new ActorContext(OWNER_ID, false);
        stranger = new ActorContext(STRANGER_ID, false);
        admin = new ActorContext(STRANGER_ID, true);

        when(courseRepository.findById(COURSE_ID))
                .thenReturn(Optional.of(course(OWNER_ID)));
        when(examRepository.findById(EXAM_ID))
                .thenReturn(Optional.of(exam(OWNER_ID)));

        when(analyticsRepository.courseSummary(anyLong()))
                .thenReturn(List.<Object[]>of(new Object[] {3L, new BigDecimal("62.50")}));
        when(analyticsRepository.courseCompletionRate(anyLong()))
                .thenReturn(List.<Object[]>of(new Object[] {new BigDecimal("40.00")}));
        when(analyticsRepository.atRiskStudentsByCourse(anyLong(), any(), anyInt()))
                .thenReturn(List.of());
        when(analyticsRepository.weakKnowledgeNodes(anyLong())).thenReturn(List.of());
        when(analyticsRepository.examSummary(anyLong()))
                .thenReturn(List.<Object[]>of(new Object[] {4L, new BigDecimal("55.00")}));
        when(analyticsRepository.atRiskStudentsByExam(anyLong(), any(), anyInt()))
                .thenReturn(List.of());
        when(analyticsRepository.questionFailureRates(anyLong())).thenReturn(List.of());
        when(analyticsRepository.examHistogram(anyLong(), any(), any(), any()))
                .thenReturn(histogram());
    }

    /* =============================== Fixtures =============================== */

    private static Course course(final Long teacherId) {
        return Course.builder()
                .courseId(COURSE_ID)
                .title("Toan hoc")
                .teacher(User.builder().userId(teacherId).fullName("Giao vien").build())
                .build();
    }

    private static Exam exam(final Long teacherId) {
        return Exam.builder()
                .examId(EXAM_ID)
                .title("Kiem tra giua ky")
                .course(course(teacherId))
                .build();
    }

    /** Four rows, in bucket order, matching the fixed band skeleton. */
    private static List<Object[]> histogram() {
        return List.of(
                new Object[] {0, 1, new BigDecimal("25.00")},
                new Object[] {1, 1, new BigDecimal("25.00")},
                new Object[] {2, 0, new BigDecimal("0.00")},
                new Object[] {3, 2, new BigDecimal("50.00")});
    }

    /* =============================== Bucketing ============================== */

    @Nested
    @DisplayName("Score bucketing")
    class Bucketing {

        @Test
        @DisplayName("boundaries are half-open so no score falls in two bands")
        void boundariesDoNotOverlap() {
            // 40 / 60 / 80 are the three values most likely to be tested by hand,
            // and a closed-lower range would count each of them twice.
            assertThat(ScoreBucket.of(new BigDecimal("39.99"))).isEqualTo(ScoreBucket.WEAK);
            assertThat(ScoreBucket.of(new BigDecimal("40"))).isEqualTo(ScoreBucket.AVERAGE);
            assertThat(ScoreBucket.of(new BigDecimal("59.99"))).isEqualTo(ScoreBucket.AVERAGE);
            assertThat(ScoreBucket.of(new BigDecimal("60"))).isEqualTo(ScoreBucket.GOOD);
            assertThat(ScoreBucket.of(new BigDecimal("79.99"))).isEqualTo(ScoreBucket.GOOD);
            assertThat(ScoreBucket.of(new BigDecimal("80"))).isEqualTo(ScoreBucket.EXCELLENT);
        }

        @Test
        @DisplayName("the extremes stay inside the scale")
        void extremesAreBounded() {
            assertThat(ScoreBucket.of(BigDecimal.ZERO)).isEqualTo(ScoreBucket.WEAK);
            assertThat(ScoreBucket.of(new BigDecimal("100"))).isEqualTo(ScoreBucket.EXCELLENT);
            assertThat(ScoreBucket.of(null)).isEqualTo(ScoreBucket.WEAK);
        }

        @Test
        @DisplayName("the SQL skeleton has exactly one row per band")
        void sqlSkeletonMatchesThePool() {
            final String sql =
                    assertDoesNotThrowQuery(
                            "examHistogram", Long.class, BigDecimal.class, BigDecimal.class, BigDecimal.class);
            // The histogram only shows every band because the query LEFT JOINs onto
            // a literal skeleton. If POOL grows and this string does not, the new
            // band silently disappears from charts.
            assertThat(ScoreBucket.POOL).hasSize(4);
            assertThat(sql).contains("SELECT 0 AS ord");
            assertThat(sql).contains("UNION ALL SELECT 1");
            assertThat(sql).contains("UNION ALL SELECT 2");
            assertThat(sql).contains("UNION ALL SELECT 3");
        }

        @Test
        @DisplayName("an empty band is reported as a zero, not omitted")
        void emptyBandSurvives() {
            final ExamAnalyticsResponse response = service.getExamAnalytics(EXAM_ID, owner);

            assertThat(response.scoreBuckets()).hasSize(4);
            final ScoreDistributionBucketDto empty = response.scoreBuckets().get(2);
            assertThat(empty.studentCount()).isZero();
            assertThat(empty.bucketLabel()).isEqualTo("Kh\u00E1 [6.0 - 8.0 \u0111i\u1EC3m]");
        }

        @Test
        @DisplayName("bucket labels come back in ascending order with Vietnamese text")
        void labelsAreOrdered() {
            final ExamAnalyticsResponse response = service.getExamAnalytics(EXAM_ID, owner);

            assertThat(response.scoreBuckets())
                    .extracting(ScoreDistributionBucketDto::bucketLabel)
                    .containsExactly(
                            "Y\u1EBFu / K\u00E9m [0 - 4.0 \u0111i\u1EC3m]",
                            "Trung b\u00ECnh [4.0 - 6.0 \u0111i\u1EC3m]",
                            "Kh\u00E1 [6.0 - 8.0 \u0111i\u1EC3m]",
                            "Gi\u1ECFi / Xu\u1EA5t s\u1EAFc [8.0 - 10.0 \u0111i\u1EC3m]");
        }
    }

    /* ================================ At-risk =============================== */

    @Nested
    @DisplayName("At-risk identification")
    class AtRisk {

        @Test
        @DisplayName("either trigger alone is enough")
        void eitherTriggerFires() {
            assertThat(AtRiskRule.isAtRisk(new BigDecimal("35.0"), 0)).isTrue();
            assertThat(AtRiskRule.isAtRisk(new BigDecimal("90.0"), 3)).isTrue();
            assertThat(AtRiskRule.isAtRisk(new BigDecimal("90.0"), 2)).isFalse();
            assertThat(AtRiskRule.isAtRisk(new BigDecimal("50.0"), 0)).isFalse();
        }

        @Test
        @DisplayName("the score trigger is strictly below 50")
        void scoreBoundary() {
            assertThat(AtRiskRule.isLowScoring(new BigDecimal("49.99"))).isTrue();
            assertThat(AtRiskRule.isLowScoring(new BigDecimal("50.00"))).isFalse();
        }

        @Test
        @DisplayName("the reason names every trigger that fired")
        void reasonCoversBothCauses() {
            assertThat(AtRiskRule.reason(new BigDecimal("35.0"), 4))
                    .isEqualTo(
                            "\u0110i\u1EC3m trung b\u00ECnh d\u01B0\u1EDBi 5.0 (3.5/10) v\u00E0"
                                    + " vi ph\u1EA1m r\u1EDDi tab 4 l\u1EA7n.");
        }

        @Test
        @DisplayName("a proctored student who scored well is not told they are failing")
        void reasonOmitsUnfiredClause() {
            assertThat(AtRiskRule.reason(new BigDecimal("90.0"), 5))
                    .isEqualTo("Vi ph\u1EA1m r\u1EDDi tab 5 l\u1EA7n.");
        }

        @Test
        @DisplayName("an ungraded but proctored student reports a null average, not zero")
        void ungradedProctoredStudent() {
            when(analyticsRepository.atRiskStudentsByExam(anyLong(), any(), anyInt()))
                    .thenReturn(
                            // Explicit type witness: List.of(oneArray) would spread the
                            // array into five varargs elements instead of one row.
                            List.<Object[]>of(new Object[] {3L, "Nguyen Van A", "a@b.c", null, 4}));

            final ExamAnalyticsResponse response = service.getExamAnalytics(EXAM_ID, owner);

            assertThat(response.atRiskParticipants()).hasSize(1);
            assertThat(response.atRiskParticipants().get(0).averageScore()).isNull();
            assertThat(response.atRiskParticipants().get(0).maxTabSwitches()).isEqualTo(4);
        }

        @Test
        @DisplayName("the course overview counts the students the query returned")
        void atRiskCountIsReported() {
            when(analyticsRepository.atRiskStudentsByCourse(anyLong(), any(), anyInt()))
                    .thenReturn(
                            List.of(
                                    new Object[] {9L, "B", "b@b.c", new BigDecimal("10.0"), 0},
                                    new Object[] {8L, "A", "a@b.c", new BigDecimal("20.0"), 5}));

            final CourseAnalyticsResponse response = service.getCourseAnalytics(COURSE_ID, owner);

            assertThat(response.overview().atRiskStudentCount()).isEqualTo(2);
            // Sorted by id for a stable response regardless of engine NULL ordering.
            assertThat(response.atRiskStudents())
                    .extracting(AtRiskStudentDto::studentId)
                    .containsExactly(8L, 9L);
        }
    }

    /* ============================== Ownership =============================== */

    @Nested
    @DisplayName("Teacher ownership")
    class Ownership {

        @Test
        @DisplayName("a stranger is refused course statistics")
        void strangerRefusedForCourse() {
            assertThatThrownBy(() -> service.getCourseAnalytics(COURSE_ID, stranger))
                    .isInstanceOf(AppException.class)
                    .hasMessage("B\u1EA1n kh\u00F4ng c\u00F3 quy\u1EC1n xem th\u1ED1ng k\u00EA"
                            + " c\u1EE7a kh\u00F3a h\u1ECDc ho\u1EB7c b\u00E0i thi n\u00E0y.")
                    .extracting(ex -> ((AppException) ex).getErrorCode())
                    .isEqualTo(ErrorCode.FORBIDDEN);
        }

        @Test
        @DisplayName("a stranger is refused exam statistics")
        void strangerRefusedForExam() {
            assertThatThrownBy(() -> service.getExamAnalytics(EXAM_ID, stranger))
                    .isInstanceOf(AppException.class)
                    .extracting(ex -> ((AppException) ex).getErrorCode())
                    .isEqualTo(ErrorCode.FORBIDDEN);
        }

        @Test
        @DisplayName("an admin reads any course without being its author")
        void adminBypassesOwnership() {
            final CourseAnalyticsResponse response = service.getCourseAnalytics(COURSE_ID, admin);
            assertThat(response.courseId()).isEqualTo(COURSE_ID);
        }

        @Test
        @DisplayName("an admin reads any exam without being its author")
        void adminBypassesOwnershipForExam() {
            assertThat(service.getExamAnalytics(EXAM_ID, admin).examId()).isEqualTo(EXAM_ID);
        }

        @Test
        @DisplayName("a refused caller triggers no aggregation query at all")
        void refusalShortCircuits() {
            assertThatThrownBy(() -> service.getCourseAnalytics(COURSE_ID, stranger))
                    .isInstanceOf(AppException.class);

            verify(analyticsRepository, never()).courseSummary(anyLong());
            verify(analyticsRepository, never()).atRiskStudentsByCourse(anyLong(), any(), anyInt());
        }
    }

    /* ================================ Errors ================================ */

    @Nested
    @DisplayName("Not-found and empty-data messages")
    class Errors {

        @Test
        @DisplayName("a missing course is named in the message")
        void missingCourse() {
            when(courseRepository.findById(404L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.getCourseAnalytics(404L, owner))
                    .isInstanceOf(AppException.class)
                    .hasMessage("Kh\u00F4ng t\u00ECm th\u1EA5y kh\u00F3a h\u1ECDc y\u00EAu c\u1EA7u: 404.")
                    .extracting(ex -> ((AppException) ex).getErrorCode())
                    .isEqualTo(ErrorCode.RESOURCE_NOT_FOUND);
        }

        @Test
        @DisplayName("a missing exam is named in the message")
        void missingExam() {
            when(examRepository.findById(404L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.getExamAnalytics(404L, owner))
                    .isInstanceOf(AppException.class)
                    .hasMessage("Kh\u00F4ng t\u00ECm th\u1EA5y b\u00E0i thi y\u00EAu c\u1EA7u: 404.");
        }

        @Test
        @DisplayName("a paper nobody has sat is rejected, not rendered as four zero bars")
        void examWithoutSubmissions() {
            when(analyticsRepository.examSummary(EXAM_ID)).thenReturn(List.<Object[]>of(new Object[] {0L, null}));

            assertThatThrownBy(() -> service.getExamAnalytics(EXAM_ID, owner))
                    .isInstanceOf(AppException.class)
                    .hasMessage("Kh\u00F4ng c\u00F3 d\u1EEF li\u1EC7u b\u00E0i n\u1ED9p n\u00E0o"
                            + " \u0111\u1EC3 th\u1ED1ng k\u00EA cho b\u00E0i thi n\u00E0y.")
                    .extracting(ex -> ((AppException) ex).getErrorCode())
                    .isEqualTo(ErrorCode.BAD_REQUEST);
        }
    }

    /* ================================ Helpers =============================== */

    private String assertDoesNotThrowQuery(
            final String method, final Class<?>... parameterTypes) {
        try {
            return AnalyticsRepository.class
                    .getMethod(method, parameterTypes)
                    .getAnnotation(Query.class)
                    .value();
        } catch (final NoSuchMethodException ex) {
            throw new AssertionError("Không tìm thấy phương thức truy vấn: " + method, ex);
        }
    }
}
package com.thesis.elearning.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.thesis.elearning.modules.auth.entity.User;
import com.thesis.elearning.modules.auth.repository.UserRepository;
import com.thesis.elearning.modules.course.entity.Course;
import com.thesis.elearning.modules.exam.dto.ExamResultResponse;
import com.thesis.elearning.modules.exam.dto.SubmitExamRequest;
import com.thesis.elearning.modules.exam.entity.Exam;
import com.thesis.elearning.modules.exam.entity.ExamQuestion;
import com.thesis.elearning.modules.exam.entity.ExamSubmission;
import com.thesis.elearning.modules.exam.entity.SubmissionAnswer;
import com.thesis.elearning.modules.exam.repository.ExamQuestionRepository;
import com.thesis.elearning.modules.exam.repository.ExamRepository;
import com.thesis.elearning.modules.exam.repository.ExamSubmissionRepository;
import com.thesis.elearning.modules.exam.repository.SubmissionAnswerRepository;
import com.thesis.elearning.modules.exam.service.ExamService;
import com.thesis.elearning.modules.exam.service.grading.FillInBlankGradingStrategy;
import com.thesis.elearning.modules.exam.service.grading.GradingStrategyFactory;
import com.thesis.elearning.modules.exam.service.grading.MatchingGradingStrategy;
import com.thesis.elearning.modules.exam.service.grading.MultipleChoiceGradingStrategy;
import com.thesis.elearning.modules.exam.service.grading.OrderingGradingStrategy;
import com.thesis.elearning.modules.knowledge.entity.KnowledgeNode;
import com.thesis.elearning.modules.knowledge.repository.KnowledgeNodeMasteryRepository;
import com.thesis.elearning.modules.question.entity.Question;
import com.thesis.elearning.modules.question.entity.QuestionVersion;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;

/**
 * Exact grading arithmetic over the seeded answer keys.
 *
 * <h2>Why this drives the service directly instead of over HTTP</h2>
 * H2 2.2.224 cannot hold a JSON <em>object</em> in a JSON column: every insert
 * form tried - a plain parameter, {@code CAST(? AS JSON)}, and
 * {@code setObject(..., Types.OTHER)} - stores the document as a JSON string
 * scalar, which deserialises to a text node rather than an object. Reading the
 * seeded key back therefore yields no {@code correctOptionIds}, and every
 * question grades as unanswered. That is a property of the test database, not of
 * the application: MySQL round-trips the same rows correctly, which is where the
 * HTTP path is verified.
 *
 * <p>So this suite still asserts against the real seeded data - the payloads are
 * read out of the database - and then feeds them through the real {@link ExamService}
 * with the real strategies. The one link not covered here is Hibernate's JSON
 * deserialiser, which is MySQL-only behaviour.
 *
 * <p>Points are 2.0 + 3.0 + 3.0 + 2.0. Only fill-in-blank and matching award
 * partial credit, and both take their denominator from the key - so the totals
 * below are what the strategies must produce, not an estimate.
 */
@SpringBootTest
@ActiveProfiles("test")
@TestPropertySource(
        properties = {
            "spring.datasource.url=jdbc:h2:mem:seed_grading;MODE=MySQL;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE",
            "app.seed.enabled=true"
        })
class ExamGradingIntegrationTest {

    private static final Long STUDENT_ID = 7L;
    private static final Long EXAM_ID = 1L;
    private static final Long SUBMISSION_ID = 900L;

    @Autowired private JdbcTemplate jdbc;
    @Autowired private ObjectMapper objectMapper;

    private List<String> contentPayloads;
    private List<String> solutionPayloads;

    @BeforeEach
    void readSeededPaper() {
        contentPayloads =
                jdbc.query(
                        "SELECT CAST(qv.content_payload AS VARCHAR(4000))"
                                + " FROM exam_questions eq"
                                + " JOIN question_versions qv ON qv.version_id = eq.version_id"
                                + " WHERE eq.exam_id = (SELECT MIN(exam_id) FROM exams)"
                                + " ORDER BY eq.order_index",
                        (rs, i) -> rs.getString(1));
        solutionPayloads =
                jdbc.query(
                        "SELECT CAST(qv.solution_payload AS VARCHAR(4000))"
                                + " FROM exam_questions eq"
                                + " JOIN question_versions qv ON qv.version_id = eq.version_id"
                                + " WHERE eq.exam_id = (SELECT MIN(exam_id) FROM exams)"
                                + " ORDER BY eq.order_index",
                        (rs, i) -> rs.getString(1));
    }

    @Test
    @DisplayName("the seeded bank carries exactly one answer key per renderer")
    void seededPayloadsCarryAnswerKeys() {
        assertThat(contentPayloads).hasSize(4);
        assertThat(solutionPayloads).hasSize(4);

        // Public documents must not contain any key - the same split PayloadLeakageGuard enforces.
        assertThat(String.join("", contentPayloads))
                .doesNotContain("correctOptionIds", "acceptedAnswers", "correctPairs", "correctOrder");

        assertThat(solutionPayloads.get(0)).contains("correctOptionIds");
        assertThat(solutionPayloads.get(1)).contains("acceptedAnswers");
        assertThat(solutionPayloads.get(2)).contains("correctPairs");
        assertThat(solutionPayloads.get(3)).contains("correctOrder");
    }

    /* ------------------------------------------------------------------ */
    /*  Grading                                                            */
    /* ------------------------------------------------------------------ */

    private ExamService service(
            final ExamSubmissionRepository submissions,
            final ExamQuestionRepository paper,
            final SubmissionAnswerRepository answers,
            final UserRepository users,
            final KnowledgeNodeMasteryRepository mastery) {
        return new ExamService(
                Mockito.mock(ExamRepository.class),
                paper,
                submissions,
                answers,
                users,
                mastery,
                new GradingStrategyFactory(
                        List.of(
                                new MultipleChoiceGradingStrategy(),
                                new FillInBlankGradingStrategy(),
                                new MatchingGradingStrategy(),
                                new OrderingGradingStrategy())),
                objectMapper);
    }

    /** Paper assembled from the seeded payloads, in presentation order. */
    private List<ExamQuestion> paper() {
        final Exam exam =
                Exam.builder()
                        .examId(EXAM_ID)
                        .course(Course.builder().courseId(1L).title("Khoa hoc").build())
                        .title("Bai thi")
                        .durationMinutes(45)
                        .maxAttempts(5)
                        .published(true)
                        .build();
        final List<ExamQuestion> items = new ArrayList<>();
        for (int i = 0; i < solutionPayloads.size(); i++) {
            final long versionId = 100L + i;
            items.add(
                    ExamQuestion.builder()
                            .id(new ExamQuestion.PaperItemId(EXAM_ID, versionId))
                            .exam(exam)
                            .version(
                                    QuestionVersion.builder()
                                            .versionId(versionId)
                                            .question(
                                                    Question.builder()
                                                            .questionId(versionId)
                                                            .questionType(
                                                                    switch (i) {
                                                                        case 0 -> "MULTIPLE_CHOICE";
                                                                        case 1 -> "FILL_IN_BLANK";
                                                                        case 2 -> "MATCHING";
                                                                        default -> "ORDERING";
                                                                    })
                                                            .node(
                                                                    KnowledgeNode.builder()
                                                                            .nodeId(1L)
                                                                            .nodeName("Node")
                                                                            .build())
                                                            .build())
                                            .contentPayload(read(contentPayloads.get(i)))
                                            .solutionPayload(read(solutionPayloads.get(i)))
                                            .active(true)
                                            .build())
                            .points(new BigDecimal(i == 0 || i == 3 ? "2.00" : "3.00"))
                            .orderIndex(i + 1)
                            .build());
        }
        return items;
    }
    /**
     * Parses a payload, unwrapping the JSON string scalar H2 stores.
     *
     * <p>MySQL hands back the document itself, so this is a no-op there. H2 wraps it
     * in quotes and escapes it, which Jackson reads as a text node - and a text node
     * has no {@code correctOptionIds} child, so every strategy would score the paper
     * zero. Unwrapping once restores the document the seeder intended.
     */
    private JsonNode read(final String raw) {
        try {
            final JsonNode first = objectMapper.readTree(raw);
            return first.isTextual() ? objectMapper.readTree(first.asText()) : first;
        } catch (final Exception ex) {
            throw new IllegalStateException(ex);
        }
    }

    private ExamResultResponse grade(final String... responses) {
        final ExamSubmissionRepository submissions = Mockito.mock(ExamSubmissionRepository.class);
        final ExamQuestionRepository paperRepository = Mockito.mock(ExamQuestionRepository.class);
        final SubmissionAnswerRepository answers = Mockito.mock(SubmissionAnswerRepository.class);
        final UserRepository users = Mockito.mock(UserRepository.class);
        final KnowledgeNodeMasteryRepository mastery = Mockito.mock(KnowledgeNodeMasteryRepository.class);

        final ExamSubmission submission =
                ExamSubmission.builder()
                        .submissionId(SUBMISSION_ID)
                        .exam(paper().get(0).getExam())
                        .student(User.builder().userId(STUDENT_ID).fullName("SV").build())
                        .startedAt(LocalDateTime.now().minusMinutes(10))
                        .submittedAt(null)
                        .tabSwitchCount(0)
                        .build();

        when(submissions.findByIdForUpdate(SUBMISSION_ID)).thenReturn(Optional.of(submission));
        when(submissions.save(any(ExamSubmission.class))).thenAnswer(inv -> inv.getArgument(0));
        when(paperRepository.findPaperOrdered(EXAM_ID)).thenReturn(paper());
        when(answers.saveAll(any())).thenAnswer(inv -> inv.getArgument(0));
        when(users.findById(STUDENT_ID))
                .thenReturn(Optional.of(User.builder().userId(STUDENT_ID).fullName("SV").build()));

        final List<SubmitExamRequest.AnswerPayload> payloads = new ArrayList<>();
        final List<ExamQuestion> items = paper();
        for (int i = 0; i < responses.length; i++) {
            payloads.add(
                    new SubmitExamRequest.AnswerPayload(items.get(i).getId().getVersionId(), read(responses[i])));
        }

        return service(submissions, paperRepository, answers, users, mastery)
                .submitExam(
                        SUBMISSION_ID,
                        new SubmitExamRequest(
                                SUBMISSION_ID, payloads, 0, 0, List.of(), null, false),
                        STUDENT_ID);
    }

    @Test
    @DisplayName("a fully correct paper scores 10.00 across all four renderers")
    void fullMarks() {
        final ExamResultResponse result =
                grade(
                        "{\"selectedOptionIds\":[\"opt_b\"]}",
                        "{\"answers\":{\"blank_1\":\"255.255.255.0\",\"blank_2\":\"443\"}}",
                        "{\"pairs\":{\"L1\":\"R2\",\"L2\":\"R3\",\"L3\":\"R1\"}}",
                        "{\"submittedOrder\":[\"step_2\",\"step_1\",\"step_3\"]}");

        assertThat(result.totalScore()).isEqualByComparingTo("10.00");
        assertThat(result.maxScore()).isEqualByComparingTo("10.00");
        assertThat(result.percentage()).isEqualTo(100);
        assertThat(result.correctCount()).isEqualTo(4);
        assertThat(result.answeredCount()).isEqualTo(4);
    }

    @Test
    @DisplayName("partial credit, a wrong order and whitespace normalisation produce 5.50")
    void partialCreditAndNormalisation() {
        // blank_1 is padded and blank_2 is absent, so fill-in-blank scores half its
        // marks; matching omits one pair so it scores two thirds; ordering is wrong,
        // which is all-or-nothing by design.
        final ExamResultResponse result =
                grade(
                        "{\"selectedOptionIds\":[\"opt_b\"]}",
                        "{\"answers\":{\"blank_1\":\"  255.255.255.0  \"}}",
                        "{\"pairs\":{\"L1\":\"R2\",\"L2\":\"R3\"}}",
                        "{\"submittedOrder\":[\"step_1\",\"step_2\",\"step_3\"]}");

        assertThat(result.totalScore())
                .as("2.00 + 1.50 + 2.00 + 0.00")
                .isEqualByComparingTo("5.50");
        assertThat(result.percentage()).isEqualTo(55);
        assertThat(result.correctCount())
                .as("only the multiple-choice item is at full marks")
                .isEqualTo(1);

        assertThat(result.results().get(1).earnedPoints()).isEqualByComparingTo("1.50");
        assertThat(result.results().get(2).earnedPoints()).isEqualByComparingTo("2.00");
    }

    @Test
    @DisplayName("a half-filled answer map cannot inflate the fill-in-blank ratio")
    void denominatorComesFromTheKey() {
        final ExamResultResponse result =
                grade(
                        "{\"selectedOptionIds\":[\"opt_b\"]}",
                        "{\"answers\":{\"blank_1\":\"255.255.255.0\"}}",
                        "{\"pairs\":{\"L1\":\"R2\"}}",
                        "{\"submittedOrder\":[\"step_2\",\"step_1\",\"step_3\"]}");

        assertThat(result.totalScore())
                .as("2.00 + 1.50 + 1.00 + 2.00")
                .isEqualByComparingTo("6.50");
    }

    @Test
    @DisplayName("an omitted question is stored as an empty document and scored zero")
    void omittedAnswersAreGradedAsZero() {
        final SubmissionAnswerRepository answers = Mockito.mock(SubmissionAnswerRepository.class);
        when(answers.saveAll(any())).thenAnswer(inv -> inv.getArgument(0));

        final ExamResultResponse result = grade("{\"selectedOptionIds\":[\"opt_a\"]}");

        assertThat(result.totalScore()).isEqualByComparingTo("0.00");
        assertThat(result.results()).hasSize(4);
        assertThat(result.answeredCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("a client sending only the legacy violation count is not recorded as clean")
    void proctoringCountIsReconciled() {
        final ExamSubmissionRepository submissions = Mockito.mock(ExamSubmissionRepository.class);
        final ExamQuestionRepository paperRepository = Mockito.mock(ExamQuestionRepository.class);
        final SubmissionAnswerRepository answers = Mockito.mock(SubmissionAnswerRepository.class);
        final UserRepository users = Mockito.mock(UserRepository.class);
        final KnowledgeNodeMasteryRepository mastery = Mockito.mock(KnowledgeNodeMasteryRepository.class);

        final ExamSubmission submission =
                ExamSubmission.builder()
                        .submissionId(SUBMISSION_ID)
                        .exam(paper().get(0).getExam())
                        .student(User.builder().userId(STUDENT_ID).fullName("SV").build())
                        .startedAt(LocalDateTime.now().minusMinutes(10))
                        .tabSwitchCount(0)
                        .build();
        when(submissions.findByIdForUpdate(SUBMISSION_ID)).thenReturn(Optional.of(submission));
        when(submissions.save(any(ExamSubmission.class))).thenAnswer(inv -> inv.getArgument(0));
        when(paperRepository.findPaperOrdered(EXAM_ID)).thenReturn(paper());
        when(answers.saveAll(any())).thenAnswer(inv -> inv.getArgument(0));

        final List<ExamQuestion> items = paper();
        service(submissions, paperRepository, answers, users, mastery)
                .submitExam(
                        SUBMISSION_ID,
                        new SubmitExamRequest(
                                SUBMISSION_ID,
                                List.of(
                                        new SubmitExamRequest.AnswerPayload(
                                                items.get(0).getId().getVersionId(),
                                                read("{\"selectedOptionIds\":[\"opt_b\"]}"))),
                                0,
                                5,
                                List.of(),
                                null,
                                false),
                        STUDENT_ID);

        assertThat(submission.getTabSwitchCount()).isEqualTo(5);
    }
}

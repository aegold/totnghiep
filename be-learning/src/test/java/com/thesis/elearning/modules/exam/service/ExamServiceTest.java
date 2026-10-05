package com.thesis.elearning.modules.exam.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.thesis.elearning.common.exception.AppException;
import com.thesis.elearning.common.exception.ErrorCode;
import com.thesis.elearning.modules.auth.entity.User;
import com.thesis.elearning.modules.auth.repository.UserRepository;
import com.thesis.elearning.modules.course.entity.Course;
import com.thesis.elearning.modules.exam.dto.SubmitExamRequest;
import com.thesis.elearning.modules.exam.entity.Exam;
import com.thesis.elearning.modules.exam.entity.ExamQuestion;
import com.thesis.elearning.modules.exam.entity.ExamSubmission;
import com.thesis.elearning.modules.exam.entity.SubmissionAnswer;
import com.thesis.elearning.modules.exam.repository.ExamQuestionRepository;
import com.thesis.elearning.modules.exam.repository.ExamRepository;
import com.thesis.elearning.modules.exam.repository.ExamSubmissionRepository;
import com.thesis.elearning.modules.exam.repository.SubmissionAnswerRepository;
import com.thesis.elearning.modules.exam.service.grading.FillInBlankGradingStrategy;
import com.thesis.elearning.modules.exam.service.grading.GradingStrategyFactory;
import com.thesis.elearning.modules.exam.service.grading.MatchingGradingStrategy;
import com.thesis.elearning.modules.exam.service.grading.MultipleChoiceGradingStrategy;
import com.thesis.elearning.modules.exam.service.grading.OrderingGradingStrategy;
import com.thesis.elearning.modules.knowledge.entity.KnowledgeNode;
import com.thesis.elearning.modules.knowledge.entity.KnowledgeNodeMastery;
import com.thesis.elearning.modules.knowledge.repository.KnowledgeNodeMasteryRepository;
import com.thesis.elearning.modules.question.entity.DifficultyLevel;
import com.thesis.elearning.modules.question.entity.Question;
import com.thesis.elearning.modules.question.entity.QuestionVersion;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

/** Attempt lifecycle: opening, resuming, and grading under the server's clock. */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ExamServiceTest {

    static final Long STUDENT_ID = 7L;
    static final Long OTHER_STUDENT_ID = 9L;
    static final Long EXAM_ID = 1L;
    static final Long SUBMISSION_ID = 500L;
    static final Long NODE_ID = 1L;
    static final Long V_MCQ = 101L;
    static final Long V_FIB = 102L;

    static final BigDecimal TEN = new BigDecimal("10.00");
    static final BigDecimal FOUR = new BigDecimal("4.00");

    @Mock private ExamRepository examRepository;
    @Mock private ExamQuestionRepository examQuestionRepository;
    @Mock private ExamSubmissionRepository submissionRepository;
    @Mock private SubmissionAnswerRepository answerRepository;
    @Mock private UserRepository userRepository;
    @Mock private KnowledgeNodeMasteryRepository masteryRepository;

    private ExamService service;
    private ObjectMapper mapper;

    @BeforeEach
    void setUp() {
        service =
                new ExamService(
                        examRepository,
                        examQuestionRepository,
                        submissionRepository,
                        answerRepository,
                        userRepository,
                        masteryRepository,
                        new GradingStrategyFactory(
                                List.of(
                                        new MultipleChoiceGradingStrategy(),
                                        new FillInBlankGradingStrategy(),
                                        new MatchingGradingStrategy(),
                                        new OrderingGradingStrategy())),
                        new ObjectMapper());
        mapper = new ObjectMapper();

        when(userRepository.findById(STUDENT_ID))
                .thenReturn(Optional.of(User.builder().userId(STUDENT_ID).fullName("SV").build()));
        when(submissionRepository.save(any(ExamSubmission.class)))
                .thenAnswer(
                        invocation -> {
                            final ExamSubmission saved = invocation.getArgument(0);
                            saved.setSubmissionId(SUBMISSION_ID);
                            return saved;
                        });
    }

    /* ----------------------------- Fixtures ----------------------------- */

    private static Exam exam(final boolean published, final int maxAttempts) {
        return Exam.builder()
                .examId(EXAM_ID)
                .course(Course.builder().courseId(1L).title("Khoa hoc").build())
                .title("Kiem tra giua ky")
                .durationMinutes(60)
                .maxAttempts(maxAttempts)
                .published(published)
                .deleted(false)
                .build();
    }

    private QuestionVersion version(
            final Long versionId, final String type, final String solution) throws Exception {
        return QuestionVersion.builder()
                .versionId(versionId)
                .question(
                        Question.builder()
                                .questionId(versionId)
                                .node(KnowledgeNode.builder().nodeId(NODE_ID).nodeName("Toan").build())
                                .author(User.builder().userId(1L).fullName("GV").build())
                                .difficulty(
                                        DifficultyLevel.builder()
                                                .difficultyId(1)
                                                .levelCode("EASY")
                                                .build())
                                .questionType(type)
                                .build())
                .versionNumber(1)
                .contentPayload(mapper.readTree("{\"prompt\":\"p\"}"))
                .solutionPayload(mapper.readTree(solution))
                .active(true)
                .build();
    }

    private ExamQuestion item(
            final Exam paper,
            final QuestionVersion version,
            final BigDecimal points,
            final int order) {
        return ExamQuestion.builder()
                .id(new ExamQuestion.PaperItemId(paper.getExamId(), version.getVersionId()))
                .exam(paper)
                .version(version)
                .points(points)
                .orderIndex(order)
                .build();
    }

    private ExamSubmission submission(final Exam paper, final LocalDateTime startedAt) {
        return ExamSubmission.builder()
                .submissionId(SUBMISSION_ID)
                .exam(paper)
                .student(User.builder().userId(STUDENT_ID).fullName("SV").build())
                .startedAt(startedAt)
                .submittedAt(null)
                .tabSwitchCount(0)
                .build();
    }

    /** Pairs of {@code (versionId, studentResponse)} in submission order. */
    private static SubmitExamRequest submit(final Object... versionIdAndResponse) {
        final List<SubmitExamRequest.AnswerPayload> answers = new java.util.ArrayList<>();
        for (int i = 0; i < versionIdAndResponse.length; i += 2) {
            answers.add(
                    new SubmitExamRequest.AnswerPayload(
                            (Long) versionIdAndResponse[i], versionIdAndResponse[i + 1]));
        }
        return new SubmitExamRequest(SUBMISSION_ID, answers, 0, 0, List.of(), null, false);
    }


    /* ------------------------------------------------------------------ */

    @Nested
    @DisplayName("startExam")
    class Start {

        @Test
        @DisplayName("Opens an attempt and returns the paper with a server deadline")
        void opensAttempt() {
            final Exam paper = exam(true, 1);
            when(examRepository.findById(EXAM_ID)).thenReturn(Optional.of(paper));
            when(submissionRepository
                    .findFirstByExamExamIdAndStudentUserIdAndSubmittedAtIsNullOrderBySubmissionIdDesc(
                            EXAM_ID, STUDENT_ID))
                    .thenReturn(Optional.empty());
            when(submissionRepository.countByExamExamIdAndStudentUserId(EXAM_ID, STUDENT_ID))
                    .thenReturn(0L);

            final var session = service.startExam(EXAM_ID, STUDENT_ID);

            assertThat(session.submissionId()).isEqualTo(SUBMISSION_ID);
            assertThat(session.durationMinutes()).isEqualTo(60);
            assertThat(session.serverEndTime()).isNotNull();
            // A 60-minute paper opened just now still has essentially all of it.
            assertThat(session.remainingSeconds()).isBetween(3500L, 3600L);
        }

        @Test
        @DisplayName("Resumes the in-flight attempt instead of opening a second one")
        void resumesInFlightAttempt() throws Exception {
            final Exam paper = exam(true, 1);
            final ExamSubmission open = submission(paper, LocalDateTime.now().minusMinutes(10));
            when(examRepository.findById(EXAM_ID)).thenReturn(Optional.of(paper));
            when(submissionRepository
                    .findFirstByExamExamIdAndStudentUserIdAndSubmittedAtIsNullOrderBySubmissionIdDesc(
                            EXAM_ID, STUDENT_ID))
                    .thenReturn(Optional.of(open));
            when(examQuestionRepository.findPaperOrdered(EXAM_ID))
                    .thenReturn(List.of(item(paper, version(V_MCQ, "MULTIPLE_CHOICE", "{}"), TEN, 1)));

            final var session = service.startExam(EXAM_ID, STUDENT_ID);

            assertThat(session.submissionId()).isEqualTo(SUBMISSION_ID);
            // Ten minutes already spent come off the remaining time.
            assertThat(session.remainingSeconds()).isBetween(2900L, 3000L);
            verify(submissionRepository, never()).save(any(ExamSubmission.class));
        }

        @Test
        @DisplayName("Refuses an unpublished paper")
        void refusesUnpublished() {
            when(examRepository.findById(EXAM_ID)).thenReturn(Optional.of(exam(false, 1)));

            assertThatThrownBy(() -> service.startExam(EXAM_ID, STUDENT_ID))
                    .isInstanceOf(AppException.class)
                    .hasMessage("Bài thi chưa được công bố nên bạn chưa thể làm bài.")
                    .extracting(ex -> ((AppException) ex).getErrorCode())
                    .isEqualTo(ErrorCode.FORBIDDEN);
        }

        @Test
        @DisplayName("Refuses once the attempt allowance is spent")
        void refusesWhenOutOfAttempts() {
            when(examRepository.findById(EXAM_ID)).thenReturn(Optional.of(exam(true, 1)));
            when(submissionRepository
                    .findFirstByExamExamIdAndStudentUserIdAndSubmittedAtIsNullOrderBySubmissionIdDesc(
                            EXAM_ID, STUDENT_ID))
                    .thenReturn(Optional.empty());
            when(submissionRepository.countByExamExamIdAndStudentUserId(EXAM_ID, STUDENT_ID))
                    .thenReturn(1L);

            assertThatThrownBy(() -> service.startExam(EXAM_ID, STUDENT_ID))
                    .isInstanceOf(AppException.class)
                    .hasMessageContaining("đã sử dụng hết số lần làm bài");
        }

        @Test
        @DisplayName("The paper never carries an answer key")
        void paperCarriesNoAnswerKey() throws Exception {
            final Exam paper = exam(true, 1);
            when(examRepository.findById(EXAM_ID)).thenReturn(Optional.of(paper));
            when(submissionRepository
                    .findFirstByExamExamIdAndStudentUserIdAndSubmittedAtIsNullOrderBySubmissionIdDesc(
                            EXAM_ID, STUDENT_ID))
                    .thenReturn(Optional.empty());
            when(submissionRepository.countByExamExamIdAndStudentUserId(EXAM_ID, STUDENT_ID))
                    .thenReturn(0L);
            when(examQuestionRepository.findPaperOrdered(EXAM_ID))
                    .thenReturn(
                            List.of(
                                    item(
                                            paper,
                                            version(
                                                    V_MCQ,
                                                    "MULTIPLE_CHOICE",
                                                    "{\"correctOptionIds\":[\"a\"]}"),
                                            TEN,
                                            1)));

            final var session = service.startExam(EXAM_ID, STUDENT_ID);
            final var question = session.questions().get(0);

            assertThat(question.contentPayload().toString()).doesNotContain("correctOptionIds");
            // points and orderIndex come from exam_questions, not left null.
            assertThat(question.points()).isEqualByComparingTo(TEN);
            assertThat(question.orderIndex()).isEqualTo(1);
            assertThat(session.totalPoints()).isEqualByComparingTo(TEN);
        }
    }


    /* ------------------------------------------------------------------ */

    @Nested
    @DisplayName("submitExam")
    class Submit {

        /** A two-question paper: one MCQ worth 10, one fill-in-blank worth 4. */
        private List<ExamQuestion> twoQuestionPaper(final Exam paper) throws Exception {
            return List.of(
                    item(paper, version(V_MCQ, "MULTIPLE_CHOICE",
                            "{\"correctOptionIds\":[\"a\"]}"), TEN, 1),
                    item(paper, version(V_FIB, "FILL_IN_BLANK",
                            "{\"acceptedAnswers\":{\"b1\":[\"hai muoi\"],"
                                    + "\"b2\":[\"ba muoi\"]}}"), FOUR, 2));
        }

        private void wireGrading(final Exam paper, final ExamSubmission open) throws Exception {
            when(submissionRepository.findByIdForUpdate(SUBMISSION_ID))
                    .thenReturn(Optional.of(open));
            when(examQuestionRepository.findPaperOrdered(EXAM_ID))
                    .thenReturn(twoQuestionPaper(paper));
            when(masteryRepository.findById(any(KnowledgeNodeMastery.MasteryId.class)))
                    .thenReturn(Optional.empty());
        }

        @Test
        @DisplayName("Grades a full paper, totals the marks and reports the percentage")
        void gradesFullPaper() throws Exception {
            final Exam paper = exam(true, 1);
            wireGrading(paper, submission(paper, LocalDateTime.now().minusMinutes(5)));

            final var result =
                    service.submitExam(
                            SUBMISSION_ID,
                            submit(
                                    V_MCQ,
                                    Map.of("selectedOptionIds", List.of("a")),
                                    V_FIB,
                                    Map.of("answers", Map.of("b1", "hai muoi"))),
                            STUDENT_ID);

            // MCQ 10/10, FIB one of two blanks = 2.00 of 4.00.
            assertThat(result.totalScore()).isEqualByComparingTo("12.00");
            assertThat(result.maxScore()).isEqualByComparingTo("14.00");
            assertThat(result.correctCount()).isEqualTo(1);
            assertThat(result.answeredCount()).isEqualTo(2);
            assertThat(result.questionCount()).isEqualTo(2);
            assertThat(result.percentage()).isEqualTo(86);
            assertThat(result.isLate()).isFalse();
        }

        @Test
        @DisplayName("Persists one graded answer row per paper question")
        void persistsEveryAnswer() throws Exception {
            final Exam paper = exam(true, 1);
            wireGrading(paper, submission(paper, LocalDateTime.now().minusMinutes(5)));

            service.submitExam(
                    SUBMISSION_ID,
                    submit(V_MCQ, Map.of("selectedOptionIds", List.of("a"))),
                    STUDENT_ID);

            @SuppressWarnings("unchecked")
            final ArgumentCaptor<List<SubmissionAnswer>> captor =
                    ArgumentCaptor.forClass((Class) List.class);
            verify(answerRepository).saveAll(captor.capture());

            // Even the omitted question gets a row, so a mark sheet can show it as
            // blank rather than as missing.
            assertThat(captor.getValue()).hasSize(2);
            assertThat(captor.getValue().get(0).getEarnedPoints()).isEqualByComparingTo(TEN);
            assertThat(captor.getValue().get(0).isCorrect()).isTrue();
            assertThat(captor.getValue().get(1).getEarnedPoints())
                    .isEqualByComparingTo(BigDecimal.ZERO);
            assertThat(captor.getValue().get(1).isCorrect()).isFalse();
        }


        @Test
        @DisplayName("Writes per-node mastery as a percentage of the marks available")
        void writesMastery() throws Exception {
            final Exam paper = exam(true, 1);
            wireGrading(paper, submission(paper, LocalDateTime.now().minusMinutes(5)));

            service.submitExam(
                    SUBMISSION_ID,
                    submit(
                            V_MCQ,
                            Map.of("selectedOptionIds", List.of("a")),
                            V_FIB,
                            Map.of("answers", Map.of("b1", "hai muoi"))),
                    STUDENT_ID);

            // 12 of 14 = 86%, above the 80% threshold the graph unlocks at.
            final ArgumentCaptor<KnowledgeNodeMastery> captor =
                    ArgumentCaptor.forClass(KnowledgeNodeMastery.class);
            verify(masteryRepository).save(captor.capture());

            assertThat(captor.getValue().getMasteryScore()).isEqualTo(86);
            assertThat(captor.getValue().getAttempts()).isEqualTo(1);
            assertThat(captor.getValue().getNode().getNodeId()).isEqualTo(NODE_ID);
        }

        @Test
        @DisplayName("A second attempt averages into existing mastery rather than replacing it")
        void averagesMasteryAcrossAttempts() throws Exception {
            final Exam paper = exam(true, 2);
            wireGrading(paper, submission(paper, LocalDateTime.now().minusMinutes(5)));

            final KnowledgeNodeMastery existing =
                    KnowledgeNodeMastery.builder()
                            .id(new KnowledgeNodeMastery.MasteryId(STUDENT_ID, NODE_ID))
                            .student(User.builder().userId(STUDENT_ID).fullName("SV").build())
                            .node(KnowledgeNode.builder().nodeId(NODE_ID).build())
                            .masteryScore(50)
                            .attempts(1)
                            .build();
            when(masteryRepository.findById(any(KnowledgeNodeMastery.MasteryId.class)))
                    .thenReturn(Optional.of(existing));

            service.submitExam(
                    SUBMISSION_ID,
                    submit(
                            V_MCQ,
                            Map.of("selectedOptionIds", List.of("a")),
                            V_FIB,
                            Map.of("answers", Map.of("b1", "hai muoi"))),
                    STUDENT_ID);

            // Mean of 50 and 86 across two attempts.
            assertThat(existing.getMasteryScore()).isEqualTo(68);
            assertThat(existing.getAttempts()).isEqualTo(2);
        }

        @Test
        @DisplayName("A hand-in inside the grace window is graded and not flagged late")
        void withinGraceIsNotLate() throws Exception {
            final Exam paper = exam(true, 1);
            // 60-minute paper opened 60 minutes and 20 seconds ago: past the
            // deadline but inside the 30s allowance.
            wireGrading(
                    paper,
                    submission(paper, LocalDateTime.now().minusMinutes(60).minusSeconds(20)));

            final var result =
                    service.submitExam(
                            SUBMISSION_ID,
                            submit(V_MCQ, Map.of("selectedOptionIds", List.of("a"))),
                            STUDENT_ID);

            assertThat(result.isLate()).isFalse();
            assertThat(result.totalScore()).isEqualByComparingTo(TEN);
        }

        @Test
        @DisplayName("A hand-in past the grace window is still graded, and flagged late")
        void beyondGraceIsLateButStillGraded() throws Exception {
            final Exam paper = exam(true, 1);
            wireGrading(
                    paper,
                    submission(paper, LocalDateTime.now().minusMinutes(61).minusSeconds(30)));

            final var result =
                    service.submitExam(
                            SUBMISSION_ID,
                            submit(V_MCQ, Map.of("selectedOptionIds", List.of("a"))),
                            STUDENT_ID);

            // Late work is never discarded: the marks are computed and stored.
            assertThat(result.isLate()).isTrue();
            assertThat(result.totalScore()).isEqualByComparingTo(TEN);
            verify(answerRepository).saveAll(any());
        }


        @Test
        @DisplayName("Refuses a second submission of the same attempt")
        void refusesSecondSubmission() throws Exception {
            final Exam paper = exam(true, 1);
            final ExamSubmission already = submission(paper, LocalDateTime.now().minusMinutes(5));
            already.setSubmittedAt(LocalDateTime.now().minusMinutes(1));
            when(submissionRepository.findByIdForUpdate(SUBMISSION_ID))
                    .thenReturn(Optional.of(already));

            assertThatThrownBy(
                            () ->
                                    service.submitExam(
                                            SUBMISSION_ID,
                                            submit(V_MCQ, Map.of("selectedOptionIds", List.of("a"))),
                                            STUDENT_ID))
                    .isInstanceOf(AppException.class)
                    .hasMessage("Bài thi này đã được nộp trước đó và không thể nộp lại.")
                    .extracting(ex -> ((AppException) ex).getErrorCode())
                    .isEqualTo(ErrorCode.DUPLICATE_RESOURCE);

            verify(answerRepository, never()).saveAll(any());
        }

        @Test
        @DisplayName("Refuses a student submitting somebody else's attempt")
        void refusesForeignStudent() throws Exception {
            final Exam paper = exam(true, 1);
            when(submissionRepository.findByIdForUpdate(SUBMISSION_ID))
                    .thenReturn(Optional.of(submission(paper, LocalDateTime.now())));

            assertThatThrownBy(
                            () ->
                                    service.submitExam(
                                            SUBMISSION_ID,
                                            submit(V_MCQ, Map.of("selectedOptionIds", List.of("a"))),
                                            OTHER_STUDENT_ID))
                    .isInstanceOf(AppException.class)
                    .extracting(ex -> ((AppException) ex).getErrorCode())
                    .isEqualTo(ErrorCode.FORBIDDEN);
        }

        @Test
        @DisplayName("Refuses an answer for a question that is not on this paper")
        void refusesOffPaperAnswer() throws Exception {
            final Exam paper = exam(true, 1);
            wireGrading(paper, submission(paper, LocalDateTime.now()));

            assertThatThrownBy(
                            () ->
                                    service.submitExam(
                                            SUBMISSION_ID,
                                            submit(
                                                    V_MCQ,
                                                    Map.of("selectedOptionIds", List.of("a")),
                                                    999L,
                                                    Map.of("selectedOptionIds", List.of("b"))),
                                            STUDENT_ID))
                    .isInstanceOf(AppException.class)
                    .hasMessageContaining("không thuộc bài thi này");
        }

        @Test
        @DisplayName("An entirely blank paper is graded zero rather than rejected")
        void blankPaperIsGradedZero() throws Exception {
            final Exam paper = exam(true, 1);
            wireGrading(paper, submission(paper, LocalDateTime.now()));

            final var result =
                    service.submitExam(SUBMISSION_ID, submit(V_MCQ, null), STUDENT_ID);

            assertThat(result.totalScore()).isEqualByComparingTo("0.00");
            assertThat(result.answeredCount()).isZero();
            assertThat(result.correctCount()).isZero();
            // Percentage still reports, so a blank paper is 0% rather than no data.
            assertThat(result.percentage()).isZero();
        }

        @Test
        @DisplayName("An omitted answer is stored as an empty document, never SQL null")
        void omittedAnswerStoresNonNullDocument() throws Exception {
            final Exam paper = exam(true, 1);
            wireGrading(paper, submission(paper, LocalDateTime.now()));

            service.submitExam(
                    SUBMISSION_ID,
                    submit(V_MCQ, Map.of("selectedOptionIds", List.of("a"))),
                    STUDENT_ID);

            @SuppressWarnings("unchecked")
            final ArgumentCaptor<List<SubmissionAnswer>> captor =
                    ArgumentCaptor.forClass((Class) List.class);
            verify(answerRepository).saveAll(captor.capture());

            // student_response is NOT NULL in the schema; Hibernate binds a
            // Jackson NullNode as SQL NULL, which would abort the whole batch.
            for (final SubmissionAnswer answer : captor.getValue()) {
                assertThat(answer.getStudentResponse()).as("v" + answer.getVersion().getVersionId())
                        .isNotNull();
                assertThat(answer.getStudentResponse().isNull())
                        .as("must be an empty document, not a JSON null")
                        .isFalse();
            }
        }

        @Test
        @DisplayName("The proctoring counter is stored and the client's own clock is ignored")
        void storesProctoringCounterIgnoringClientClock() throws Exception {
            final Exam paper = exam(true, 1);
            final ExamSubmission open = submission(paper, LocalDateTime.now().minusMinutes(5));
            wireGrading(paper, open);

            service.submitExam(
                    SUBMISSION_ID,
                    new SubmitExamRequest(
                            SUBMISSION_ID,
                            List.of(
                                    new SubmitExamRequest.AnswerPayload(
                                            V_MCQ, Map.of("selectedOptionIds", List.of("a")))),
                            4,
                            4,
                            List.of(V_FIB),
                            "1999-01-01T00:00:00",
                            true),
                    STUDENT_ID);

            assertThat(open.getTabSwitchCount()).isEqualTo(4);
            assertThat(open.isSubmitted()).isTrue();
            // The client's submittedAt must not have been written through.
            assertThat(open.getSubmittedAt()).isAfter(LocalDateTime.now().minusMinutes(1));
        }
    }
}


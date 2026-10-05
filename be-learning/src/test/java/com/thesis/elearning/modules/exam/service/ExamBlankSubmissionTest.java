package com.thesis.elearning.modules.exam.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.when;

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
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

/**
 * A blank hand-in is a score, not an error.
 *
 * <p>The reasoning matters more than the assertion. A student who runs out of
 * time, or never fills a single blank, still sat the paper. Rejecting the request
 * would leave the attempt open forever and - because it never reaches
 * {@code submitted_at} - drop it silently out of every aggregate, including the
 * teacher's mark sheet. That is worse than recording a zero.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ExamBlankSubmissionTest {

    private static final Long STUDENT_ID = 7L;
    private static final Long EXAM_ID = 1L;
    private static final Long SUBMISSION_ID = 500L;
    private static final Long V1 = 101L;
    private static final Long V2 = 102L;

    /** @Captor resolves the element type, so saveAll's generic needs no cast. */
    @Captor private ArgumentCaptor<List<SubmissionAnswer>> savedAnswers;

    @Mock private ExamRepository examRepository;
    @Mock private ExamQuestionRepository examQuestionRepository;
    @Mock private ExamSubmissionRepository submissionRepository;
    @Mock private SubmissionAnswerRepository answerRepository;
    @Mock private UserRepository userRepository;
    @Mock private KnowledgeNodeMasteryRepository masteryRepository;

    private ExamService service;
    private ExamSubmission submission;

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

        final Exam exam =
                Exam.builder()
                        .examId(EXAM_ID)
                        .course(Course.builder().courseId(1L).title("Khoa hoc").build())
                        .title("Bai thi")
                        .durationMinutes(60)
                        .maxAttempts(1)
                        .published(true)
                        .build();

        submission =
                ExamSubmission.builder()
                        .submissionId(SUBMISSION_ID)
                        .exam(exam)
                        .student(User.builder().userId(STUDENT_ID).fullName("SV").build())
                        .startedAt(LocalDateTime.now().minusMinutes(10))
                        .submittedAt(null)
                        .tabSwitchCount(0)
                        .build();

        when(submissionRepository.findByIdForUpdate(SUBMISSION_ID))
                .thenReturn(Optional.of(submission));
        when(userRepository.findById(STUDENT_ID))
                .thenReturn(Optional.of(User.builder().userId(STUDENT_ID).fullName("SV").build()));
        when(examQuestionRepository.findPaperOrdered(EXAM_ID))
                .thenReturn(List.of(paperItem(exam, V1, "10.00", 1), paperItem(exam, V2, "8.00", 2)));
        when(submissionRepository.save(any(ExamSubmission.class)))
                .thenAnswer(inv -> inv.getArgument(0));
        when(answerRepository.saveAll(any())).thenAnswer(inv -> inv.getArgument(0));
    }

    private static ExamQuestion paperItem(
            final Exam exam, final Long versionId, final String points, final int order) {
        final QuestionVersion version =
                QuestionVersion.builder()
                        .versionId(versionId)
                        .question(
                                Question.builder()
                                        .questionId(versionId)
                                        .questionType("MULTIPLE_CHOICE")
                                        .node(KnowledgeNode.builder().nodeId(1L).nodeName("Node").build())
                                        .build())
                        .contentPayload(new ObjectMapper().createObjectNode())
                        .solutionPayload(new ObjectMapper().createObjectNode())
                        .active(true)
                        .build();
        return ExamQuestion.builder()
                .id(new ExamQuestion.PaperItemId(EXAM_ID, versionId))
                .exam(exam)
                .version(version)
                .points(new BigDecimal(points))
                .orderIndex(order)
                .build();
    }

    @Test
    @DisplayName("an empty answer list grades the whole paper at zero and closes the attempt")
    void emptyAnswersGradeAsZero() {
        final ExamResultResponse result =
                service.submitExam(
                        SUBMISSION_ID,
                        new SubmitExamRequest(
                                SUBMISSION_ID, List.of(), 0, 0, List.of(), null, false),
                        STUDENT_ID);

        assertThat(result.totalScore()).isEqualByComparingTo("0.00");
        assertThat(result.maxScore()).isEqualByComparingTo("18.00");
        assertThat(result.answeredCount()).isZero();
        assertThat(result.submittedAt()).isNotNull();
        assertThat(submission.getSubmittedAt())
                .as("the attempt must be closed, or it disappears from every aggregate")
                .isNotNull();
    }

    @Test
    @DisplayName("every paper question still gets a stored answer row")
    void everyQuestionGetsARow() {
        service.submitExam(
                SUBMISSION_ID,
                new SubmitExamRequest(SUBMISSION_ID, List.of(), 0, 0, List.of(), null, false),
                STUDENT_ID);

        org.mockito.Mockito.verify(answerRepository).saveAll(savedAnswers.capture());
        assertThat(savedAnswers.getValue()).hasSize(2);
    }

    @Test
    @DisplayName("the proctoring count is persisted from whichever name the client used")
    void proctoringCountIsReconciled() {
        service.submitExam(
                SUBMISSION_ID,
                new SubmitExamRequest(SUBMISSION_ID, List.of(), 0, 5, List.of(), null, false),
                STUDENT_ID);

        assertThat(submission.getTabSwitchCount())
                .as("a legacy-only client must not be recorded as a clean session")
                .isEqualTo(5);
    }
}
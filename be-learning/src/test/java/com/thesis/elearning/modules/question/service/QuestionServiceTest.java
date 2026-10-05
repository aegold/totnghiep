package com.thesis.elearning.modules.question.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.thesis.elearning.common.exception.AppException;
import com.thesis.elearning.common.exception.ErrorCode;
import com.thesis.elearning.modules.auth.entity.User;
import com.thesis.elearning.modules.auth.repository.UserRepository;
import com.thesis.elearning.modules.knowledge.entity.KnowledgeNode;
import com.thesis.elearning.modules.knowledge.repository.KnowledgeNodeRepository;
import com.thesis.elearning.modules.question.domain.ActorContext;
import com.thesis.elearning.modules.question.dto.CreateQuestionRequest;
import com.thesis.elearning.modules.question.dto.UpdateQuestionRequest;
import com.thesis.elearning.modules.question.entity.DifficultyLevel;
import com.thesis.elearning.modules.question.entity.Question;
import com.thesis.elearning.modules.question.entity.QuestionVersion;
import com.thesis.elearning.modules.question.repository.DifficultyLevelRepository;
import com.thesis.elearning.modules.question.repository.QuestionRepository;
import com.thesis.elearning.modules.question.repository.QuestionVersionRepository;
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

/**
 * Versioning and access rules for {@link QuestionService}.
 *
 * <p>The leakage guard is the real implementation rather than a mock: the point
 * of these tests is that an answer key in the public document is refused, and a
 * mocked guard would happily report success for a payload that would actually
 * leak.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class QuestionServiceTest {

    static final Long AUTHOR_ID = 7L;
    static final Long OTHER_TEACHER_ID = 8L;
    static final Long NODE_ID = 1L;
    static final Integer DIFFICULTY_ID = 1;
    static final Long QUESTION_ID = 100L;

    static final String NOT_FOUND = "Không tìm thấy câu hỏi hoặc câu hỏi đã bị xóa.";
    static final String BAD_JSON = "Cấu trúc dữ liệu JSON của nội dung câu hỏi không hợp lệ.";

    @Mock private QuestionRepository questionRepository;
    @Mock private QuestionVersionRepository versionRepository;
    @Mock private DifficultyLevelRepository difficultyRepository;
    @Mock private KnowledgeNodeRepository nodeRepository;
    @Mock private UserRepository userRepository;

    QuestionService service;
    ObjectMapper mapper;

    @BeforeEach
    void setUp() {
        service =
                new QuestionService(
                        questionRepository,
                        versionRepository,
                        difficultyRepository,
                        nodeRepository,
                        userRepository,
                        new PayloadLeakageGuard());
        mapper = new ObjectMapper();

        when(nodeRepository.findById(NODE_ID))
                .thenReturn(
                        Optional.of(
                                KnowledgeNode.builder()
                                        .nodeId(NODE_ID)
                                        .nodeName("Toán")
                                        .build()));
        when(difficultyRepository.findById(DIFFICULTY_ID))
                .thenReturn(
                        Optional.of(
                                DifficultyLevel.builder()
                                        .difficultyId(DIFFICULTY_ID)
                                        .levelCode("EASY")
                                        .build()));
        when(userRepository.findById(AUTHOR_ID))
                .thenReturn(
                        Optional.of(
                                User.builder().userId(AUTHOR_ID).fullName("Giáo viên").build()));

        when(questionRepository.save(any(Question.class)))
                .thenAnswer(
                        invocation -> {
                            final Question saved = invocation.getArgument(0);
                            saved.setQuestionId(QUESTION_ID);
                            return saved;
                        });
        when(versionRepository.save(any(QuestionVersion.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
    }

    /* ----------------------------- Fixtures ----------------------------- */

    /** A clean public/private payload pair for the given renderer. */
    CreateQuestionRequest request(final String type) throws Exception {
        return new CreateQuestionRequest(
                NODE_ID,
                DIFFICULTY_ID,
                type,
                mapper.readTree(
                        "{\"prompt\":\"Which is linear?\",\"options\":"
                                + "[{\"id\":\"a\",\"text\":\"Linear\"},"
                                + "{\"id\":\"b\",\"text\":\"Quadratic\"}]}"),
                mapper.readTree("{\"correctOptionIds\":[\"a\"]}"));
    }

    UpdateQuestionRequest updateRequest(final String type) throws Exception {
        return new UpdateQuestionRequest(
                NODE_ID,
                DIFFICULTY_ID,
                type,
                mapper.readTree(
                        "{\"prompt\":\"REVISED stem\",\"options\":[{\"id\":\"a\",\"text\":\"X\"}]}"),
                mapper.readTree("{\"correctOptionIds\":[\"a\"]}"));
    }

    /** An existing, non-deleted question owned by AUTHOR_ID. */
    Question existingQuestion() {
        return Question.builder()
                .questionId(QUESTION_ID)
                .node(KnowledgeNode.builder().nodeId(NODE_ID).nodeName("Toán").build())
                .author(User.builder().userId(AUTHOR_ID).fullName("Giáo viên").build())
                .difficulty(
                        DifficultyLevel.builder()
                                .difficultyId(DIFFICULTY_ID)
                                .levelCode("EASY")
                                .build())
                .questionType("MULTIPLE_CHOICE")
                .deleted(false)
                .build();
    }

    /**
     * Wires an existing question that already has one active revision at v1, and
     * returns that revision so a test can assert it was left untouched.
     */
    QuestionVersion existingVersionOne() throws Exception {
        final QuestionVersion v1 =
                QuestionVersion.builder()
                        .versionId(500L)
                        .question(existingQuestion())
                        .versionNumber(1)
                        .contentPayload(mapper.readTree("{\"prompt\":\"ORIGINAL stem\"}"))
                        .solutionPayload(mapper.readTree("{\"correctOptionIds\":[\"b\"]}"))
                        .active(true)
                        .build();
        when(questionRepository.findByIdForUpdate(QUESTION_ID))
                .thenReturn(Optional.of(v1.getQuestion()));
        // updateQuestion re-reads the question after the bulk update clears the
        // persistence context, so this stub mirrors what the live session returns.
        when(questionRepository.findById(QUESTION_ID)).thenReturn(Optional.of(v1.getQuestion()));
        when(questionRepository.getReferenceById(QUESTION_ID)).thenReturn(v1.getQuestion());
        when(versionRepository.findMaxVersionNumber(QUESTION_ID)).thenReturn(1);
        when(versionRepository.findByQuestionQuestionIdAndActiveTrue(QUESTION_ID))
                .thenReturn(Optional.of(v1));
        return v1;
    }


    /* ------------------------------------------------------------------ */

    @Nested
    @DisplayName("createQuestion")
    class Create {

        @Test
        @DisplayName("Persists version 1 and flags it active")
        void createsVersionOne() throws Exception {
            final var response = service.createQuestion(request("MULTIPLE_CHOICE"), AUTHOR_ID);

            assertThat(response.versionNumber()).isEqualTo(1);
            assertThat(response.active()).isTrue();
            assertThat(response.questionId()).isEqualTo(QUESTION_ID);
            assertThat(response.authorName()).isEqualTo("Giáo viên");
            assertThat(response.difficultyCode()).isEqualTo("EASY");

            final ArgumentCaptor<QuestionVersion> saved =
                    ArgumentCaptor.forClass(QuestionVersion.class);
            verify(versionRepository).save(saved.capture());
            assertThat(saved.getValue().getVersionNumber()).isEqualTo(1);
            assertThat(saved.getValue().isActive()).isTrue();
        }

        @Test
        @DisplayName("Stores the answer key alongside the prompt, in separate columns")
        void storesBothPayloads() throws Exception {
            service.createQuestion(request("MULTIPLE_CHOICE"), AUTHOR_ID);

            final ArgumentCaptor<QuestionVersion> saved =
                    ArgumentCaptor.forClass(QuestionVersion.class);
            verify(versionRepository).save(saved.capture());

            assertThat(saved.getValue().getContentPayload().toString())
                    .contains("Which is linear");
            assertThat(saved.getValue().getSolutionPayload().toString())
                    .contains("correctOptionIds");
        }

        @Test
        @DisplayName("Rejects an unsupported renderer type, naming it in Vietnamese")
        void rejectsUnsupportedType() throws Exception {
            assertThatThrownBy(() -> service.createQuestion(request("ESSAY"), AUTHOR_ID))
                    .isInstanceOf(AppException.class)
                    .hasMessage("Loại câu hỏi không được hỗ trợ trong hệ thống: ESSAY.")
                    .extracting(ex -> ((AppException) ex).getErrorCode())
                    .isEqualTo(ErrorCode.BAD_REQUEST);

            verify(questionRepository, never()).save(any());
        }

        @Test
        @DisplayName("Rejects a public payload that smuggles in the answer key")
        void rejectsLeakyPayload() throws Exception {
            final String leaky =
                    "{\"prompt\":\"q\",\"options\":[{\"id\":\"a\",\"text\":\"A\",\"isCorrect\":true}]}";
            final CreateQuestionRequest bad =
                    new CreateQuestionRequest(
                            NODE_ID,
                            DIFFICULTY_ID,
                            "MULTIPLE_CHOICE",
                            mapper.readTree(leaky),
                            mapper.readTree("{\"correctOptionIds\":[\"a\"]}"));

            assertThatThrownBy(() -> service.createQuestion(bad, AUTHOR_ID))
                    .isInstanceOf(AppException.class)
                    .hasMessage(BAD_JSON);

            // Nothing may be written: an orphan question row would be a leak too.
            verify(questionRepository, never()).save(any());
        }

        @Test
        @DisplayName("Rejects an unknown node before writing anything")
        void rejectsUnknownNode() throws Exception {
            when(nodeRepository.findById(99L)).thenReturn(Optional.empty());

            final CreateQuestionRequest bad =
                    new CreateQuestionRequest(
                            99L,
                            DIFFICULTY_ID,
                            "MATCHING",
                            mapper.createObjectNode(),
                            mapper.createObjectNode());

            assertThatThrownBy(() -> service.createQuestion(bad, AUTHOR_ID))
                    .isInstanceOf(AppException.class)
                    .hasMessageContaining("nút kiến thức");

            verify(questionRepository, never()).save(any());
        }
    }


    @Nested
    @DisplayName("updateQuestion")
    class Update {

        @Test
        @DisplayName("Appends version 2 rather than editing version 1")
        void appendsNextVersion() throws Exception {
            final QuestionVersion v1 = existingVersionOne();

            final var response =
                    service.updateQuestion(
                            QUESTION_ID,
                            updateRequest("MULTIPLE_CHOICE"),
                            new ActorContext(AUTHOR_ID, false));

            assertThat(response.versionNumber()).isEqualTo(2);
            assertThat(response.active()).isTrue();

            final ArgumentCaptor<QuestionVersion> saved =
                    ArgumentCaptor.forClass(QuestionVersion.class);
            verify(versionRepository).save(saved.capture());
            assertThat(saved.getValue().getVersionNumber()).isEqualTo(2);
            assertThat(saved.getValue()).isNotSameAs(v1);
        }

        @Test
        @DisplayName("Leaves version 1 completely untouched, byte for byte")
        void doesNotMutateThePreviousVersion() throws Exception {
            final QuestionVersion v1 = existingVersionOne();
            final String originalContent = v1.getContentPayload().toString();
            final String originalSolution = v1.getSolutionPayload().toString();

            service.updateQuestion(
                    QUESTION_ID,
                    updateRequest("MULTIPLE_CHOICE"),
                    new ActorContext(AUTHOR_ID, false));

            assertThat(v1.getContentPayload().toString()).isEqualTo(originalContent);
            assertThat(v1.getSolutionPayload().toString()).isEqualTo(originalSolution);
            assertThat(v1.getVersionNumber()).isEqualTo(1);
        }

        @Test
        @DisplayName("Retires the previous revision so exactly one stays active")
        void deactivatesPreviousRevision() throws Exception {
            existingVersionOne();

            service.updateQuestion(
                    QUESTION_ID,
                    updateRequest("MULTIPLE_CHOICE"),
                    new ActorContext(AUTHOR_ID, false));

            verify(versionRepository).deactivateAllForQuestion(QUESTION_ID);
        }

        @Test
        @DisplayName("Numbers revisions from the whole history, not just the active row")
        void numbersFromFullHistory() throws Exception {
            existingVersionOne();
            when(versionRepository.findMaxVersionNumber(QUESTION_ID)).thenReturn(7);

            final var response =
                    service.updateQuestion(
                            QUESTION_ID,
                            updateRequest("MULTIPLE_CHOICE"),
                            new ActorContext(AUTHOR_ID, false));

            assertThat(response.versionNumber()).isEqualTo(8);
        }


        @Test
        @DisplayName("Refuses a teacher who is neither the author nor an admin")
        void refusesForeignTeacher() throws Exception {
            existingVersionOne();

            assertThatThrownBy(
                            () ->
                                    service.updateQuestion(
                                            QUESTION_ID,
                                            updateRequest("MULTIPLE_CHOICE"),
                                            new ActorContext(OTHER_TEACHER_ID, false)))
                    .isInstanceOf(AppException.class)
                    .extracting(ex -> ((AppException) ex).getErrorCode())
                    .isEqualTo(ErrorCode.FORBIDDEN);

            // Crucially, no revision was retired on the way to refusing.
            verify(versionRepository, never()).deactivateAllForQuestion(anyLong());
        }

        @Test
        @DisplayName("Allows an admin to revise someone else's question")
        void allowsAdmin() throws Exception {
            existingVersionOne();

            final var response =
                    service.updateQuestion(
                            QUESTION_ID,
                            updateRequest("MULTIPLE_CHOICE"),
                            new ActorContext(OTHER_TEACHER_ID, true));

            // Ownership is bypassed for an admin; the renderer is still frozen.
            assertThat(response.versionNumber()).isEqualTo(2);
            assertThat(response.authorName()).isEqualTo("Giáo viên");
        }

        @Test
        @DisplayName("Refuses to change the renderer, and keeps the live revision intact")
        void refusesQuestionTypeChange() throws Exception {
            // The existing question is a MULTIPLE_CHOICE; the request asks for
            // MATCHING, which the inherited schema cannot express per version.
            existingVersionOne();

            assertThatThrownBy(
                            () ->
                                    service.updateQuestion(
                                            QUESTION_ID,
                                            updateRequest("MATCHING"),
                                            new ActorContext(AUTHOR_ID, false)))
                    .isInstanceOf(AppException.class)
                    .hasMessage(
                            "Không được phép thay đổi định dạng câu hỏi (question_type) của câu hỏi đã tạo. "
                                    + "Vui lòng tạo câu hỏi mới.")
                    .extracting(ex -> ((AppException) ex).getErrorCode())
                    .isEqualTo(ErrorCode.BAD_REQUEST);

            // Crucially, the check runs before the revision is retired.
            verify(versionRepository, never()).deactivateAllForQuestion(anyLong());
        }

        @Test
        @DisplayName("Accepts a same-type revision, only the payload differs")
        void acceptsSameTypeRevision() throws Exception {
            existingVersionOne();

            final var response =
                    service.updateQuestion(
                            QUESTION_ID,
                            updateRequest("MULTIPLE_CHOICE"),
                            new ActorContext(AUTHOR_ID, false));

            assertThat(response.versionNumber()).isEqualTo(2);
        }

        @Test
        @DisplayName("Reports unknown and deleted alike with one message")
        void reportsUnknownQuestion() throws Exception {
            when(questionRepository.findByIdForUpdate(999L)).thenReturn(Optional.empty());

            assertThatThrownBy(
                            () ->
                                    service.updateQuestion(
                                            999L,
                                            updateRequest("MATCHING"),
                                            new ActorContext(AUTHOR_ID, false)))
                    .isInstanceOf(AppException.class)
                    .hasMessage(NOT_FOUND)
                    .extracting(ex -> ((AppException) ex).getErrorCode())
                    .isEqualTo(ErrorCode.RESOURCE_NOT_FOUND);
        }

        @Test
        @DisplayName("Rejects a leaky revision before retiring the live one")
        void rejectsLeakyRevisionWithoutLosingTheCurrent() throws Exception {
            existingVersionOne();

            final UpdateQuestionRequest leaky =
                    new UpdateQuestionRequest(
                            NODE_ID,
                            DIFFICULTY_ID,
                            "MULTIPLE_CHOICE",
                            mapper.readTree("{\"options\":[{\"id\":\"a\",\"isCorrect\":true}]}"),
                            mapper.readTree("{\"correctOptionIds\":[\"a\"]}"));

            assertThatThrownBy(
                            () ->
                                    service.updateQuestion(
                                            QUESTION_ID, leaky, new ActorContext(AUTHOR_ID, false)))
                    .isInstanceOf(AppException.class)
                    .hasMessage(BAD_JSON);

            // Validation precedes retiring, so a rejected request must leave the
            // live question exactly as it was.
            verify(versionRepository, never()).deactivateAllForQuestion(anyLong());
        }

        @Test
        @DisplayName("Rejects an unsupported renderer before touching stored state")
        void rejectsUnsupportedTypeWithoutLosingTheCurrent() throws Exception {
            existingVersionOne();

            assertThatThrownBy(
                            () ->
                                    service.updateQuestion(
                                            QUESTION_ID,
                                            updateRequest("ESSAY"),
                                            new ActorContext(AUTHOR_ID, false)))
                    .hasMessage("Loại câu hỏi không được hỗ trợ trong hệ thống: ESSAY.");

            verify(versionRepository, never()).deactivateAllForQuestion(anyLong());
        }
    }


    @Nested
    @DisplayName("softDeleteQuestion")
    class SoftDelete {

        @Test
        @DisplayName("Flags the row deleted rather than removing it")
        void softDeletes() {
            when(questionRepository.findByIdForUpdate(QUESTION_ID))
                    .thenReturn(Optional.of(existingQuestion()));

            service.softDeleteQuestion(QUESTION_ID, new ActorContext(AUTHOR_ID, false));

            verify(questionRepository).softDelete(QUESTION_ID);
            verify(questionRepository, never()).delete(any());
        }

        @Test
        @DisplayName("Refuses a teacher who does not own the question")
        void refusesForeignTeacher() {
            when(questionRepository.findByIdForUpdate(QUESTION_ID))
                    .thenReturn(Optional.of(existingQuestion()));

            assertThatThrownBy(
                            () ->
                                    service.softDeleteQuestion(
                                            QUESTION_ID, new ActorContext(OTHER_TEACHER_ID, false)))
                    .isInstanceOf(AppException.class)
                    .extracting(ex -> ((AppException) ex).getErrorCode())
                    .isEqualTo(ErrorCode.FORBIDDEN);

            verify(questionRepository, never()).softDelete(anyLong());
        }
    }

    @Nested
    @DisplayName("getQuestionForStudent")
    class StudentRead {

        @Test
        @DisplayName("Returns the public payload and nothing private")
        void returnsPublicPayloadOnly() throws Exception {
            final Question question = existingQuestion();
            final QuestionVersion version =
                    QuestionVersion.builder()
                            .versionId(500L)
                            .question(question)
                            .versionNumber(2)
                            .contentPayload(mapper.readTree("{\"prompt\":\"REVISED\"}"))
                            .solutionPayload(mapper.readTree("{\"correctOptionIds\":[\"a\"]}"))
                            .active(true)
                            .build();

            when(versionRepository.findById(500L)).thenReturn(Optional.of(version));
            when(questionRepository.findById(QUESTION_ID)).thenReturn(Optional.of(question));

            final var response = service.getQuestionForStudent(500L);

            assertThat(response.versionId()).isEqualTo(500L);
            assertThat(response.questionType()).isEqualTo("MULTIPLE_CHOICE");
            assertThat(response.contentPayload().toString()).contains("REVISED");
            assertThat(response.points()).isNull();
            assertThat(response.orderIndex()).isNull();
        }

        @Test
        @DisplayName("Reports an unknown version with the shared not-found message")
        void reportsUnknownVersion() {
            when(versionRepository.findById(999L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.getQuestionForStudent(999L))
                    .isInstanceOf(AppException.class)
                    .hasMessage(NOT_FOUND);
        }

        @Test
        @DisplayName("Hides a soft-deleted question behind the same not-found message")
        void hidesSoftDeletedQuestion() throws Exception {
            final Question question = existingQuestion();
            final QuestionVersion version =
                    QuestionVersion.builder()
                            .versionId(500L)
                            .question(question)
                            .versionNumber(1)
                            .contentPayload(mapper.readTree("{}"))
                            .solutionPayload(mapper.readTree("{}"))
                            .active(false)
                            .build();

            when(versionRepository.findById(500L)).thenReturn(Optional.of(version));
            // The is_deleted = 0 restriction makes the repository return empty.
            when(questionRepository.findById(QUESTION_ID)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.getQuestionForStudent(500L))
                    .isInstanceOf(AppException.class)
                    .hasMessage(NOT_FOUND);
        }
    }

    @Nested
    @DisplayName("getQuestionForTeacher")
    class TeacherRead {

        @Test
        @DisplayName("Returns the active revision including the answer key")
        void returnsActiveRevision() throws Exception {
            final Question question = existingQuestion();
            final QuestionVersion active =
                    QuestionVersion.builder()
                            .versionId(501L)
                            .question(question)
                            .versionNumber(2)
                            .contentPayload(mapper.readTree("{\"prompt\":\"v2\"}"))
                            .solutionPayload(mapper.readTree("{\"correctOptionIds\":[\"a\"]}"))
                            .active(true)
                            .build();

            when(questionRepository.findById(QUESTION_ID)).thenReturn(Optional.of(question));
            when(versionRepository.findByQuestionQuestionIdAndActiveTrue(QUESTION_ID))
                    .thenReturn(Optional.of(active));

            final var response = service.getQuestionForTeacher(QUESTION_ID);

            assertThat(response.versionNumber()).isEqualTo(2);
            assertThat(response.solutionPayload().toString()).contains("correctOptionIds");
            assertThat(response.authorName()).isEqualTo("Giáo viên");
        }

        @Test
        @DisplayName("Reports a deleted question with the shared not-found message")
        void reportsDeletedQuestion() {
            when(questionRepository.findById(QUESTION_ID)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.getQuestionForTeacher(QUESTION_ID))
                    .isInstanceOf(AppException.class)
                    .hasMessage(NOT_FOUND);
        }
    }
}


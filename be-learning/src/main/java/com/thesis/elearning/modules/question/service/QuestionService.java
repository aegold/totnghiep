package com.thesis.elearning.modules.question.service;

import com.thesis.elearning.common.exception.AppException;
import com.thesis.elearning.common.exception.ErrorCode;
import com.thesis.elearning.modules.auth.entity.User;
import com.thesis.elearning.modules.auth.repository.UserRepository;
import com.thesis.elearning.modules.knowledge.entity.KnowledgeNode;
import com.thesis.elearning.modules.knowledge.repository.KnowledgeNodeRepository;
import com.thesis.elearning.modules.question.domain.ActorContext;
import com.thesis.elearning.modules.question.domain.QuestionType;
import com.thesis.elearning.modules.question.dto.CreateQuestionRequest;
import com.thesis.elearning.modules.question.dto.StudentQuestionResponse;
import com.thesis.elearning.modules.question.dto.TeacherQuestionDetailResponse;
import com.thesis.elearning.modules.question.dto.UpdateQuestionRequest;
import com.thesis.elearning.modules.question.entity.DifficultyLevel;
import com.thesis.elearning.modules.question.entity.Question;
import com.thesis.elearning.modules.question.entity.QuestionVersion;
import com.thesis.elearning.modules.question.repository.DifficultyLevelRepository;
import com.thesis.elearning.modules.question.repository.QuestionRepository;
import com.thesis.elearning.modules.question.repository.QuestionVersionRepository;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Authoring and versioning for questions, with the answer key fenced off from
 * students.
 *
 * <h2>The two invariants this class exists to hold</h2>
 * <ol>
 *   <li><b>Revisions are append-only.</b> {@link #updateQuestion} never writes to
 *       an existing {@code question_versions} row. It flips the active flag off
 *       and inserts the next revision, so a {@code version_id} already pinned by
 *       {@code exam_questions} keeps denoting the exact bytes that were graded.</li>
 *   <li><b>The answer key never reaches a student.</b> Enforced structurally:
 *       {@link StudentQuestionResponse} has no {@code solutionPayload} property.
 *       The service additionally refuses to store a public document that carries
 *       answer-key keys, because the student DTO would otherwise faithfully
 *       replay whatever the author put there.</li>
 * </ol>
 *
 * <h2>Renderer type is immutable</h2>
 * {@code questions.question_type} is the only place the discriminator is stored -
 * the inherited schema gives {@code question_versions} no such column, so the type
 * necessarily describes the whole question rather than each revision.
 * {@link #updateQuestion} therefore refuses to change it, which keeps every
 * historical revision rendering and re-grading with the type it was authored
 * under. Switching renderer means authoring a new question.
 */
@Service
@RequiredArgsConstructor
public class QuestionService {

    /**
     * One message for both "no such id" and "soft-deleted".
     *
     * <p>The distinction is not useful to a caller and reporting it would turn the
     * endpoint into an oracle confirming that a deleted question once existed.
     */
    private static final String NOT_FOUND = "Không tìm thấy câu hỏi hoặc câu hỏi đã bị xóa.";

    private final QuestionRepository questionRepository;
    private final QuestionVersionRepository versionRepository;
    private final DifficultyLevelRepository difficultyRepository;
    private final KnowledgeNodeRepository nodeRepository;
    private final UserRepository userRepository;
    private final PayloadLeakageGuard leakageGuard;

    /* ------------------------------------------------------------------ */
    /*  Create                                                             */
    /* ------------------------------------------------------------------ */

    /**
     * Creates a question and its first revision.
     *
     * <p>Both payloads are validated before anything is written: the type must be
     * one of the four renderers, and the public document must not carry an answer
     * key. Validating first means a rejected request leaves no orphan
     * {@code questions} row behind.
     *
     * @param request  authoring payload pair.
     * @param authorId author of record, from the JWT subject.
     * @return the created question with its version 1.
     */
    @Transactional
    public TeacherQuestionDetailResponse createQuestion(
            final CreateQuestionRequest request, final Long authorId) {

        final QuestionType type = QuestionType.require(request.questionType());
        leakageGuard.assertNoAnswerKeyLeakage(request.contentPayload());

        final Question question =
                Question.builder()
                        .node(resolveNode(request.nodeId()))
                        .author(resolveAuthor(authorId))
                        .difficulty(resolveDifficulty(request.difficultyId()))
                        .questionType(type.name())
                        .deleted(false)
                        .build();
        final Question savedQuestion = questionRepository.save(question);

        final QuestionVersion version =
                QuestionVersion.builder()
                        .question(savedQuestion)
                        .versionNumber(1)
                        .contentPayload(request.contentPayload())
                        .solutionPayload(request.solutionPayload())
                        .active(true)
                        .build();
        final QuestionVersion savedVersion = versionRepository.save(version);

        return toTeacherResponse(savedQuestion, savedVersion);
    }


    /* ------------------------------------------------------------------ */
    /*  Update - append a revision, never mutate one                       */
    /* ------------------------------------------------------------------ */

    /**
     * Publishes a new revision of an existing question.
     *
     * <p>Sequence, and why it is ordered this way:
     * <ol>
     *   <li>Take a write lock on the question, so a concurrent author cannot read
     *       the same {@code versionNumber} and collide on
     *       {@code uq_question_version}.</li>
     *   <li>Check ownership - the author, or any admin.</li>
     *   <li>Validate the incoming payloads, <em>before</em> the old revision is
     *       retired, so a rejected request leaves the question exactly as it
     *       was.</li>
     *   <li>Update the question's metadata (node, difficulty, type).</li>
     *   <li>Deactivate every active revision in one bulk statement, then insert
     *       {@code max(version_number) + 1}.</li>
     * </ol>
     *
     * @param questionId target question.
     * @param request    new payload pair.
     * @param actor      caller, for the ownership check.
     * @return the newly published revision.
     */
    @Transactional
    public TeacherQuestionDetailResponse updateQuestion(
            final Long questionId, final UpdateQuestionRequest request, final ActorContext actor) {

        final Question question =
                questionRepository
                        .findByIdForUpdate(questionId)
                        .orElseThrow(() -> new AppException(ErrorCode.RESOURCE_NOT_FOUND, NOT_FOUND));
        assertMayModify(question, actor);

        final QuestionType type = QuestionType.require(request.questionType());
        leakageGuard.assertNoAnswerKeyLeakage(request.contentPayload());

        /*
         * The renderer is frozen for the life of a question.
         *
         * <p>question_type lives on `questions`, not on `question_versions`, so
         * every historical revision of this question reports whichever type the
         * question carries today. Retyping it from MULTIPLE_CHOICE to MATCHING
         * would leave an already-graded MCQ revision being rendered - and
         * re-parsed by the grading engine - as a MATCHING one. Rejecting the edit
         * keeps the invariant total without a schema change; changing one's mind
         * means authoring a new question.
         *
         * <p>Checked before the active revision is retired, so a rejected request
         * leaves the live question exactly as it was.
         */
        if (!type.name().equals(question.getQuestionType())) {
            throw new AppException(
                    ErrorCode.BAD_REQUEST,
                    "Không được phép thay đổi định dạng câu hỏi (question_type) của câu hỏi đã tạo. "
                            + "Vui lòng tạo câu hỏi mới.");
        }

        question.setNode(resolveNode(request.nodeId()));
        question.setDifficulty(resolveDifficulty(request.difficultyId()));
        question.setQuestionType(type.name());
        questionRepository.saveAndFlush(question);

        // Bulk update: a per-row save would leave two revisions flagged active in
        // the persistence context until flush, briefly publishing both.
        versionRepository.deactivateAllForQuestion(questionId);

        final int nextVersionNumber = versionRepository.findMaxVersionNumber(questionId) + 1;
        final QuestionVersion version =
                QuestionVersion.builder()
                        // getReferenceById, not the local entity: the bulk update
                        // above clears the persistence context, so the instance
                        // captured at the top of this method is now detached.
                        .question(questionRepository.getReferenceById(questionId))
                        .versionNumber(nextVersionNumber)
                        .contentPayload(request.contentPayload())
                        .solutionPayload(request.solutionPayload())
                        .active(true)
                        .build();
        final QuestionVersion savedVersion = versionRepository.save(version);

        /*
         * Re-read rather than reusing the entity loaded at the top of this method.
         * deactivateAllForQuestion clears the persistence context, which detaches
         * that instance and its lazy associations; projecting from it would throw
         * LazyInitializationException the moment toTeacherResponse reads the
         * author's name. A fresh load rebinds the graph to the live session.
         */
        return toTeacherResponse(requireVisibleQuestion(questionId), savedVersion);
    }

    /* ------------------------------------------------------------------ */
    /*  Delete                                                             */
    /* ------------------------------------------------------------------ */

    /**
     * Soft-deletes a question, hiding it from authoring and from students.
     *
     * <p>Rows are kept because {@code exam_questions} cites them; a hard delete
     * would cascade into past submissions and erase a student's attempt.
     *
     * @param questionId target question.
     * @param actor      caller, for the ownership check.
     * @throws AppException 404 when unknown or already deleted, 403 when the caller
     *                      is neither the author nor an admin.
     */
    @Transactional
    public void softDeleteQuestion(final Long questionId, final ActorContext actor) {
        final Question question =
                questionRepository
                        .findByIdForUpdate(questionId)
                        .orElseThrow(() -> new AppException(ErrorCode.RESOURCE_NOT_FOUND, NOT_FOUND));
        assertMayModify(question, actor);
        questionRepository.softDelete(questionId);
    }


    /* ------------------------------------------------------------------ */
    /*  Read                                                               */
    /* ------------------------------------------------------------------ */

    /**
     * Full detail including the answer key, for an authoring client.
     *
     * @param questionId target question.
     * @return the active revision, or the newest one if none is flagged active.
     */
    @Transactional(readOnly = true)
    public TeacherQuestionDetailResponse getQuestionForTeacher(final Long questionId) {
        final Question question = requireVisibleQuestion(questionId);
        final QuestionVersion version =
                versionRepository
                        .findByQuestionQuestionIdAndActiveTrue(questionId)
                        .orElseGet(
                                () ->
                                        versionRepository
                                                .findByQuestionQuestionIdOrderByVersionNumberDesc(
                                                        questionId)
                                                .stream()
                                                .findFirst()
                                                .orElseThrow(
                                                        () ->
                                                                new AppException(
                                                                        ErrorCode.RESOURCE_NOT_FOUND,
                                                                        NOT_FOUND)));
        return toTeacherResponse(question, version);
    }

    /**
     * Student-facing projection of one revision.
     *
     * <p>Reads {@code content_payload} and nothing else private. The question row
     * is re-read through the repository rather than through the version's lazy
     * association so that a soft-deleted question is caught by the entity's
     * {@code is_deleted = 0} restriction and reported as not-found, instead of
     * raising an {@code EntityNotFoundException} from inside the proxy.
     *
     * @param versionId the revision being served.
     * @return payload plus type; {@code points} and {@code orderIndex} are null
     *         because they belong to {@code exam_questions}.
     */
    @Transactional(readOnly = true)
    public StudentQuestionResponse getQuestionForStudent(final Long versionId) {
        final QuestionVersion version =
                versionRepository
                        .findById(versionId)
                        .orElseThrow(() -> new AppException(ErrorCode.RESOURCE_NOT_FOUND, NOT_FOUND));
        final Question question = requireVisibleQuestion(version.getQuestion().getQuestionId());

        return StudentQuestionResponse.of(
                version.getVersionId(), question.getQuestionType(), version.getContentPayload());
    }

    /* ------------------------------------------------------------------ */
    /*  Internals                                                          */
    /* ------------------------------------------------------------------ */

    /**
     * Loads a question that the {@code is_deleted = 0} restriction lets through.
     *
     * @throws AppException 404 with the shared message when missing or deleted.
     */
    private Question requireVisibleQuestion(final Long questionId) {
        return questionRepository
                .findById(questionId)
                .orElseThrow(() -> new AppException(ErrorCode.RESOURCE_NOT_FOUND, NOT_FOUND));
    }

    /**
     * Only the author may revise or delete; an admin may do either.
     *
     * <p>Checked here rather than left to {@code @PreAuthorize}, because the role
     * gate answers "is this a teacher at all", not "is this your question".
     */
    private static void assertMayModify(final Question question, final ActorContext actor) {
        if (actor == null) {
            throw AppException.forbidden();
        }
        if (!actor.admin() && !Objects.equals(question.getAuthor().getUserId(), actor.userId())) {
            throw AppException.forbidden();
        }
    }

    private KnowledgeNode resolveNode(final Long nodeId) {
        return nodeRepository
                .findById(nodeId)
                .orElseThrow(
                        () ->
                                AppException.badRequest(
                                        "Không tìm thấy nút kiến thức với mã " + nodeId + "."));
    }

    private DifficultyLevel resolveDifficulty(final Integer difficultyId) {
        return difficultyRepository
                .findById(difficultyId)
                .orElseThrow(
                        () ->
                                AppException.badRequest(
                                        "Không tìm thấy mức độ khó với mã " + difficultyId + "."));
    }

    private User resolveAuthor(final Long authorId) {
        return userRepository
                .findById(authorId)
                .orElseThrow(
                        () ->
                                new AppException(
                                        ErrorCode.RESOURCE_NOT_FOUND,
                                        "Không tìm thấy tài khoản tác giả."));
    }

    /**
     * Projects an entity pair onto the authoring DTO.
     *
     * <p>Associations are dereferenced here, inside the caller's transaction,
     * because {@code open-in-view} is disabled - the controller receives fully
     * populated scalars and touches no proxy.
     */
    private static TeacherQuestionDetailResponse toTeacherResponse(
            final Question question, final QuestionVersion version) {
        final KnowledgeNode node = question.getNode();
        final DifficultyLevel difficulty = question.getDifficulty();
        final User author = question.getAuthor();

        return new TeacherQuestionDetailResponse(
                question.getQuestionId(),
                node.getNodeId(),
                node.getNodeName(),
                difficulty.getDifficultyId(),
                difficulty.getLevelCode(),
                question.getQuestionType(),
                author.getUserId(),
                author.getFullName(),
                version.getVersionId(),
                version.getVersionNumber(),
                version.isActive(),
                version.getCreatedAt(),
                version.getContentPayload(),
                version.getSolutionPayload());
    }
}


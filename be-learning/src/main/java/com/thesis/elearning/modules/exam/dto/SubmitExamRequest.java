package com.thesis.elearning.modules.exam.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import java.util.List;

/**
 * A student's hand-in.
 *
 * <h2>Which fields are trusted, and which are not</h2>
 * {@code violationCount}, {@code flaggedVersionIds}, {@code submittedAt} and
 * {@code autoSubmitted} all originate in the browser and are therefore advisory
 * only. The one field that actually decides when the attempt ended is computed
 * server-side from {@code started_at} plus the paper's duration; the client's
 * {@code submittedAt} is accepted so the request shape matches the client's own
 * type, and is never read.
 *
 * <p>{@code flaggedVersionIds} is accepted and discarded. It is transient UI
 * state - "I want to come back to this one" - with nowhere to live in the
 * inherited schema, and refusing the request over it would make the client strip
 * a field it still needs locally.
 *
 * <h2>Why an empty or absent {@code answers} is a valid hand-in</h2>
 * A blank paper is a real outcome: a student who ran out of time, or who
 * misunderstood the paper, still sat it. Rejecting the submission with a 400
 * would leave the attempt open forever, let it be resumed, and - because the
 * attempt never reached {@code submitted_at} - quietly drop it from every
 * aggregate, including the teacher's mark sheet. A zero is a score; an error is
 * not. The grading pass already substitutes an empty document for every omitted
 * question, so an empty list simply grades the whole paper at zero.
 *
 * @param submissionId     the attempt being closed; echoed by the client and
 *                         checked against the attempt's owner.
 * @param answers          answers for the questions the student chose to answer.
 *                         May be empty or absent; never {@code null} on the way
 *                         in, see the compact constructor.
 * @param tabSwitchCount   proctoring strikes observed. Boxed so "not supplied"
 *                         is distinguishable from a genuine zero.
 * @param violationCount   older client spelling of the same signal; see
 *                         {@link #resolvedTabSwitchCount()}.
 * @param flaggedVersionIds accepted and ignored; see above.
 * @param submittedAt      ignored; the server's own clock is authoritative.
 * @param autoSubmitted    ignored; the server decides lateness.
 */
public record SubmitExamRequest(
        @NotNull(message = "Thiếu mã lần thi") Long submissionId,

        List<@Valid @NotNull(message = "Câu trả lời không hợp lệ") AnswerPayload> answers,

        @PositiveOrZero(message = "Số lần chuyển tab không hợp lệ") @Min(0) Integer tabSwitchCount,

        @PositiveOrZero(message = "Số lần vi phạm không hợp lệ") @Min(0) Integer violationCount,

        List<Long> flaggedVersionIds,

        String submittedAt,

        boolean autoSubmitted) {

    /**
     * Normalises {@code answers} so no downstream code has to null-check it.
     *
     * <p>Deliberately not {@code List.copyOf}: that rejects null elements, which
     * would turn the per-element {@code @NotNull} into an NPE thrown during
     * deserialisation instead of a field error rendered into the envelope.
     */
    public SubmitExamRequest {
        answers = answers == null ? List.of() : answers;
    }

    /**
     * The proctoring count actually persisted, however the client spelled it.
     *
     * <p>Two spellings of one signal exist because the client renamed the field
     * once. Reading only {@code tabSwitchCount} would silently discard strikes
     * from any client still sending the old name, and the count is what the
     * at-risk report is built on - so a lost value becomes a lost intervention.
     *
     * <p>The zero case is treated as "not reported" and falls through to the
     * alias. A client that genuinely observed no strikes sends zero in both
     * fields and still resolves to zero, so the fallback costs nothing.
     *
     * @return strikes to write to {@code exam_submissions.tab_switch_count}.
     */
    public int resolvedTabSwitchCount() {
        if (tabSwitchCount != null && tabSwitchCount > 0) {
            return tabSwitchCount;
        }
        if (violationCount != null && violationCount > 0) {
            return violationCount;
        }
        return 0;
    }

    /**
     * One question's answer.
     *
     * <p>{@code studentResponse} is a free-form {@code Object} on purpose: the
     * four renderers have four different response shapes, and the strategy that
     * understands the shape is the right place to validate it. Binding all four
     * into one union here would put the knowledge of every renderer in the
     * request layer.
     */
    public record AnswerPayload(
            @NotNull(message = "Thiếu mã phiên bản câu hỏi") Long versionId,
            Object studentResponse) {}
}
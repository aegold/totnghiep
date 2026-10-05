package com.thesis.elearning.modules.exam.dto;

import com.thesis.elearning.modules.question.dto.StudentQuestionResponse;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;

/**
 * The paper handed to a student when an attempt starts.
 *
 * <h2>The clock is the server's</h2>
 * The client is expected to display a countdown derived from the
 * <em>remaining duration</em>, never to add a duration to its own clock. A device
 * whose clock is wrong, or which the user changes mid-exam, then cannot extend
 * its own time.
 *
 * <h2>Why these are {@link Instant} and not a bare local timestamp</h2>
 * An {@code Instant} always serialises with an explicit offset, ending in {@code Z},
 * so the browser parses it as an unambiguous point in time. The previous shape -
 * {@code "2026-10-04T05:08:29"}, produced by a hand-rolled {@code DateTimeFormatter}
 * with no zone - was read by {@code Date.parse} as <em>browser-local</em> time.
 * For a candidate in Vietnam that silently shifted the deadline by seven hours:
 * the countdown started wrong, and a paper already handed in could be shown as
 * still having time on it.
 *
 * <h2>Why the conversion uses the system zone, not {@code ZoneOffset.UTC}</h2>
 * The wall clock behind these values is {@link LocalDateTime#now()} - the JVM's
 * local time - and it is compared arithmetically against a {@code datetime} column
 * that carries no zone. Converting with {@code ZoneOffset.UTC} would reinterpret
 * that same wall clock as UTC and shift it by the JVM's offset, reintroducing the
 * very error this change removes. {@link ZoneId#systemDefault()} converts the
 * instant the arithmetic actually means; Jackson then renders it in UTC
 * regardless. {@code ClockSyncResponse} converts the same way, so the two
 * endpoints cannot disagree.
 *
 * <p>Questions reuse {@link StudentQuestionResponse}, which is the Stage 4 type
 * that structurally cannot carry an answer key. That is the whole point: the
 * exam paper is served through the same zero-leak projection as a single
 * question, so there is no second path onto {@code solution_payload}.
 *
 * @param submissionId    the attempt to submit against.
 * @param examId          the paper.
 * @param title           display title.
 * @param durationMinutes the nominal sitting time.
 * @param serverStartTime when this attempt began.
 * @param serverEndTime   the authoritative deadline.
 * @param remainingSeconds seconds left at the moment this response was built.
 * @param totalPoints     sum of the paper's item marks.
 * @param questions       the items in presentation order.
 */
public record ExamSessionResponse(
        Long submissionId,
        Long examId,
        String title,
        Integer durationMinutes,
        Instant serverStartTime,
        Instant serverEndTime,
        long remainingSeconds,
        BigDecimal totalPoints,
        List<StudentQuestionResponse> questions) {

    /**
     * Assembles the session, computing the deadline and the remaining time from
     * the server's own clock.
     *
     * <p>The zone is read once so both timestamps in a response are anchored to
     * the same reference; taking it twice could straddle a zone-database reload on
     * a long-lived JVM and produce a deadline a microsecond inconsistent with the
     * start time it was derived from.
     *
     * @param startedAt  when the attempt began.
     * @param endTime    the derived deadline.
     * @param now        the server's current time.
     */
    public static ExamSessionResponse of(
            final Long submissionId,
            final Long examId,
            final String title,
            final Integer durationMinutes,
            final LocalDateTime startedAt,
            final LocalDateTime endTime,
            final LocalDateTime now,
            final BigDecimal totalPoints,
            final List<StudentQuestionResponse> questions) {

        final ZoneId zone = ZoneId.systemDefault();
        return new ExamSessionResponse(
                submissionId,
                examId,
                title,
                durationMinutes,
                startedAt == null ? null : startedAt.atZone(zone).toInstant(),
                endTime.atZone(zone).toInstant(),
                java.time.Duration.between(now, endTime).getSeconds(),
                totalPoints,
                questions);
    }
}
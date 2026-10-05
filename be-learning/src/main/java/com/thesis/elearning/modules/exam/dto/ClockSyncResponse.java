package com.thesis.elearning.modules.exam.dto;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;

/**
 * The server's clock, re-read on demand.
 *
 * <h2>Why this endpoint exists</h2>
 * A laptop lid, a sleeping phone or a suspended tab can freeze the countdown for
 * minutes at a time. The client detects a gap in its own tick and asks what time
 * it really is, rather than trusting a local clock that stopped advancing.
 *
 * <h2>Why the timestamps are {@link Instant} and not a bare string</h2>
 * An instant always serialises with an explicit offset, so the browser can compare
 * it against {@code Date.now()} without guessing a zone. A zone-less
 * {@code LocalDateTime} would be read as <em>browser-local</em> time, and the
 * countdown would then be wrong by the client's UTC offset - which for a
 * Vietnamese classroom is seven hours. {@code serverNow} is emitted alongside
 * {@code serverTime} purely so the client's existing type keeps compiling.
 *
 * @param serverTime       the server's current instant.
 * @param serverNow        identical to {@code serverTime}; alias for the client.
 * @param serverEndTime    the attempt's authoritative deadline.
 * @param remainingSeconds seconds left, negative once the window has passed. The
 *                        client clamps at zero; the raw value is returned so a
 *                        client can distinguish "just expired" from "long gone".
 * @param isLate           whether the deadline plus the grace period has passed.
 *                        Informational: the attempt is still accepted either way.
 */
public record ClockSyncResponse(
        Instant serverTime,
        Instant serverNow,
        Instant serverEndTime,
        long remainingSeconds,
        boolean isLate) {

    /**
     * Builds the response from the server's own arithmetic.
     *
     * <p>The zone is the JVM's, not a hard-coded UTC, so the emitted instant
     * matches the same wall-clock the deadline arithmetic above it compared -
     * converting through a different zone would shift both by a constant offset
     * and quietly desynchronise the countdown.
     *
     * @param now      server wall clock at the moment of the response.
     * @param deadline start plus duration.
     * @param grace    allowance applied before lateness is judged.
     */
    public static ClockSyncResponse of(
            final LocalDateTime now, final LocalDateTime deadline, final Duration grace) {
        final ZoneId zone = ZoneId.systemDefault();
        final long remaining = Duration.between(now, deadline).getSeconds();
        return new ClockSyncResponse(
                now.atZone(zone).toInstant(),
                now.atZone(zone).toInstant(),
                deadline.atZone(zone).toInstant(),
                remaining,
                now.isAfter(deadline.plus(grace)));
    }
}
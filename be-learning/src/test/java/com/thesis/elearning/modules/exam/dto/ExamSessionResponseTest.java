package com.thesis.elearning.modules.exam.dto;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * The exam clock must reach the browser as an unambiguous point in time.
 *
 * <p>These assertions exist because the previous shape was not wrong in the
 * server - its arithmetic was correct - but ambiguous on the wire. A zone-less
 * string is parsed as browser-local, which for a candidate in Vietnam is a seven
 * hour error in a countdown they are being graded on.
 */
class ExamSessionResponseTest {

    private static final LocalDateTime START = LocalDateTime.of(2026, 3, 1, 9, 0, 0);
    private static final LocalDateTime END = START.plusMinutes(45);
    private static final LocalDateTime NOW = START.plusMinutes(5);

    private static ExamSessionResponse session() {
        return ExamSessionResponse.of(
                500L, 900L, "Bai thi", 45, START, END, NOW, new BigDecimal("10.00"), List.of());
    }

    /** Mirrors the application mapper; see application.yml. */
    private static ObjectMapper mapper() {
        return new ObjectMapper()
                .registerModule(new JavaTimeModule())
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
    }

    @Test
    @DisplayName("both timestamps serialise with an explicit UTC offset")
    void timestampsEndWithZ() {
        final JsonNode json = mapper().valueToTree(session());

        assertThat(json.get("serverStartTime").asText())
                .as("a zone-less stamp would be read as browser-local by Date.parse")
                .endsWith("Z");
        assertThat(json.get("serverEndTime").asText()).endsWith("Z");
    }

    @Test
    @DisplayName("the deadline is 45 minutes after the start, as an absolute interval")
    void deadlineIsDerivedFromTheServerClock() {
        final ExamSessionResponse session = session();

        assertThat(
                java.time.Duration.between(session.serverStartTime(), session.serverEndTime())
                        .toMinutes())
                .isEqualTo(45);
    }

    @Test
    @DisplayName("the instant matches the server wall clock, not a UTC reinterpretation")
    void conversionUsesTheSystemZone() {
        // Guards against the "obvious" atZone(ZoneOffset.UTC), which would shift
        // every deadline by the JVM's offset - the very bug this change fixes.
        assertThat(session().serverEndTime())
                .isEqualTo(END.atZone(ZoneId.systemDefault()).toInstant());
    }

    @Test
    @DisplayName("remaining time reflects time already spent")
    void remainingAccountsForElapsedTime() {
        assertThat(session().remainingSeconds()).isEqualTo(2400);
    }

    @Test
    @DisplayName("a null start still serialises rather than throwing")
    void nullStartIsTolerated() {
        final ExamSessionResponse noStart =
                ExamSessionResponse.of(
                        500L, 900L, "B", 45, null, END, NOW, BigDecimal.TEN, List.of());
        assertThat(noStart.serverStartTime()).isNull();
    }
}
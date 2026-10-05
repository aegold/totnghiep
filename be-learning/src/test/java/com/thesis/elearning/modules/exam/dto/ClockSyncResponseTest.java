package com.thesis.elearning.modules.exam.dto;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneId;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * The clock-sync payload.
 *
 * <p>The offset assertions are the point: a zone-less timestamp would be read by
 * the browser as local time and shift a countdown by the client''s UTC offset.
 */
class ClockSyncResponseTest {

    private static final Duration GRACE = Duration.ofSeconds(30);

    @Test
    @DisplayName("an attempt inside its window is not late and has time left")
    void insideWindow() {
        final LocalDateTime now = LocalDateTime.of(2026, 3, 1, 10, 0, 0);
        final ClockSyncResponse response = ClockSyncResponse.of(now, now.plusMinutes(45), GRACE);

        assertThat(response.remainingSeconds()).isEqualTo(2700);
        assertThat(response.isLate()).isFalse();
    }

    @Test
    @DisplayName("20 seconds past the deadline is inside the grace window")
    void insideGrace() {
        final LocalDateTime now = LocalDateTime.of(2026, 3, 1, 10, 45, 20);
        assertThat(ClockSyncResponse.of(now, now.minusSeconds(20), GRACE).isLate()).isFalse();
    }

    @Test
    @DisplayName("beyond the grace window is reported late")
    void beyondGrace() {
        final LocalDateTime now = LocalDateTime.of(2026, 3, 1, 10, 50, 0);
        assertThat(ClockSyncResponse.of(now, now.minusMinutes(5), GRACE).isLate()).isTrue();
    }

    @Test
    @DisplayName("timestamps carry an explicit offset so the client cannot misread the zone")
    void timestampsAreOffsetAware() {
        final LocalDateTime now = LocalDateTime.of(2026, 3, 1, 10, 0, 0);
        final ClockSyncResponse response = ClockSyncResponse.of(now, now.plusMinutes(45), GRACE);

        // Spring Boot registers JSR-310 on the runtime mapper (the live API emits
        // "....Z" instants); a bare ObjectMapper has to be told explicitly.
        // Mirrors the application mapper: JSR-310 registered and ISO strings rather
        // than epoch decimals (application.yml sets write-dates-as-timestamps: false).
        final ObjectMapper mapper =
                new ObjectMapper()
                        .registerModule(new JavaTimeModule())
                        .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        final JsonNode json = mapper.valueToTree(response);

        assertThat(json.get("serverTime").asText()).endsWith("Z");
        assertThat(json.get("serverEndTime").asText()).endsWith("Z");
        assertThat(json.get("serverNow").asText()).isEqualTo(json.get("serverTime").asText());
        assertThat(response.serverEndTime())
                .isEqualTo(now.plusMinutes(45).atZone(ZoneId.systemDefault()).toInstant());
    }
}
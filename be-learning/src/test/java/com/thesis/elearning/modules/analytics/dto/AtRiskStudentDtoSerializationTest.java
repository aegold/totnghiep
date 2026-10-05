package com.thesis.elearning.modules.analytics.dto;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * The at-risk record must satisfy both spellings of the proctoring counter.
 *
 * <p>The dashboard reads {@code tabSwitchCount}; the column is
 * {@code tab_switch_count} and the field is named for it. Both are emitted, so
 * neither consumer has to be broken to satisfy the other.
 */
class AtRiskStudentDtoSerializationTest {

    private static AtRiskStudentDto sample() {
        return new AtRiskStudentDto(
                7L,
                "Nguyen Van A",
                "a@b.c",
                new BigDecimal("35.0"),
                4,
                "Diem trung binh duoi 5.0 (3.5/10) va vi pham roi tab 4 lan.");
    }

    @Test
    @DisplayName("both counter names are present and carry the same value")
    void bothNamesAreSerialised() {
        final JsonNode json = new ObjectMapper().valueToTree(sample());

        assertThat(json.has("maxTabSwitches")).isTrue();
        assertThat(json.has("tabSwitchCount")).isTrue();
        assertThat(json.get("tabSwitchCount").asInt()).isEqualTo(4);
        assertThat(json.get("maxTabSwitches").asInt()).isEqualTo(4);
    }

    @Test
    @DisplayName("the alias carries no independent state that could drift")
    void aliasIsDerivedNotStored() {
        assertThat(sample().tabSwitchCount()).isEqualTo(sample().maxTabSwitches());
    }

    @Test
    @DisplayName("the reason sentence survives serialisation intact")
    void reasonIsPreserved() {
        final JsonNode json = new ObjectMapper().valueToTree(sample());
        assertThat(json.get("riskReason").asText()).contains("roi tab 4 lan");
    }
}
package com.thesis.elearning.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;

/**
 * The student-facing paper must not carry any answer key, at any depth.
 *
 * <h2>Why the walk is recursive</h2>
 * A grep for key names at the top level would pass even when a key is nested
 * inside {@code contentPayload} - which is precisely where it would be, and
 * precisely the mistake worth catching. The check therefore descends through every
 * object and array in the response.
 *
 * <p>The list is asserted against itself as well, so a key added to the guard
 * without being listed here still fails.
 */
@ActiveProfiles("test")
@TestPropertySource(
        properties = {
            "spring.datasource.url=jdbc:h2:mem:seed_leak;MODE=MySQL;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE",
            "app.seed.enabled=true"
        })
class SecurityZeroLeakageIntegrationTest extends SeededIntegrationTest {

    /** Mirrors PayloadLeakageGuard plus the payload-level keys. */
    private static final Set<String> FORBIDDEN =
            Set.of(
                    "solutionPayload",
                    "correctOptionIds",
                    "acceptedAnswers",
                    "correctPairs",
                    "correctOrder",
                    "isCorrect",
                    "patterns",
                    "explanation",
                    "explanations",
                    "gradingNote",
                    "pattern");

    @Autowired private JdbcTemplate jdbc;

    private Long examId;

    @BeforeEach
    void resolveExam() {
        examId = jdbc.queryForObject("SELECT MIN(exam_id) FROM exams", Long.class);
    }

    @Test
    @DisplayName("starting an attempt returns no answer key anywhere in the document")
    void paperCarriesNoAnswerKey() throws Exception {
        final String token = tokenFor("student5", "123456");

        final String body =
                mockMvc.perform(post("/student/exams/{id}/start", examId).header("Authorization", "Bearer " + token))
                        .andExpect(status().isCreated())
                        .andReturn()
                        .getResponse()
                        .getContentAsString();

        final JsonNode root = json(body);
        assertThat(root.path("data").path("questions")).isNotEmpty();

        final List<String> found = new ArrayList<>();
        collectForbidden(root, found);
        assertThat(found)
                .as("an answer key must not be reachable from any student response")
                .isEmpty();
    }

    @Test
    @DisplayName("the clock fields the client depends on are present and zone-qualified")
    void sessionCarriesAnAbsoluteClock() throws Exception {
        final String token = tokenFor("student5", "123456");

        final String body =
                mockMvc.perform(post("/student/exams/{id}/start", examId).header("Authorization", "Bearer " + token))
                        .andReturn()
                        .getResponse()
                        .getContentAsString();

        final JsonNode data = json(body).path("data");
        assertThat(data.path("serverEndTime").asText())
                .as("a zone-less stamp would be read as browser-local by Date.parse")
                .endsWith("Z");
        assertThat(data.path("serverStartTime").asText()).endsWith("Z");
        assertThat(data.path("remainingSeconds").asLong()).isPositive();
    }

    @Test
    @DisplayName("fetching a single question is leak-free on the same terms")
    void singleQuestionIsAlsoClean() throws Exception {
        final String token = tokenFor("student5", "123456");
        final Long versionId = jdbc.queryForObject("SELECT MIN(version_id) FROM question_versions", Long.class);

        final String body =
                mockMvc.perform(get("/questions/{id}", versionId).header("Authorization", "Bearer " + token))
                        .andExpect(status().isOk())
                        .andReturn()
                        .getResponse()
                        .getContentAsString();

        final List<String> found = new ArrayList<>();
        collectForbidden(json(body), found);
        assertThat(found).isEmpty();
        // The prompt must reach the client. H2 round-trips a JSON column as a string
        // scalar where MySQL returns the document, so the assertion is made against
        // the rendered payload rather than a nested `prompt` node.
        final String payload = json(body).path("data").path("contentPayload").toString();
        assertThat(payload).contains("TCP");
    }

    /** Depth-first walk over every property name in the document. */
    private static void collectForbidden(final JsonNode node, final List<String> found) {
        if (node == null || node.isNull()) {
            return;
        }
        if (node.isObject()) {
            node.fieldNames()
                    .forEachRemaining(
                            name -> {
                                if (FORBIDDEN.contains(name)) {
                                    found.add(name);
                                }
                                collectForbidden(node.get(name), found);
                            });
        } else if (node.isArray()) {
            node.forEach(child -> collectForbidden(child, found));
        }
    }
}
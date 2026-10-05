package com.thesis.elearning.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;

/**
 * The teacher dashboard must agree with the seeded submissions, exactly.
 *
 * <p>Four attempts of 9.0, 7.5, 3.5 and 4.5 out of 10 give 90%, 75%, 35% and 45%.
 * Note that 45% lands in the 40-59.99 band rather than the 0-39.99 one, so the
 * histogram is one submission per band and no band is empty.
 */
@ActiveProfiles("test")
@TestPropertySource(
        properties = {
            "spring.datasource.url=jdbc:h2:mem:seed_analytics;MODE=MySQL;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE",
            "app.seed.enabled=true"
        })
class TeacherAnalyticsIntegrationTest extends SeededIntegrationTest {

    @Autowired private JdbcTemplate jdbc;

    private Long examId;
    private Long courseId;

    @BeforeEach
    void resolveIds() {
        examId = jdbc.queryForObject("SELECT MIN(exam_id) FROM exams", Long.class);
        courseId = jdbc.queryForObject("SELECT MIN(course_id) FROM courses", Long.class);
    }

    private JsonNode examAnalytics(final String username, final String password) throws Exception {
        final String body =
                mockMvc.perform(
                                get("/teacher/analytics/exams/{id}", examId)
                                        .header("Authorization", "Bearer " + tokenFor(username, password)))
                        .andExpect(status().isOk())
                        .andReturn()
                        .getResponse()
                        .getContentAsString();
        return json(body).path("data");
    }

    @Test
    @DisplayName("the histogram reports one submission per band, all four present")
    void histogramMatchesTheSeed() throws Exception {
        final JsonNode data = examAnalytics("teacher1", "123456");

        assertThat(data.path("submissionCount").asInt()).isEqualTo(4);
        assertThat(data.path("averageScore").decimalValue())
                .as("(90 + 75 + 35 + 45) / 4")
                .isEqualByComparingTo("61.25");

        final JsonNode buckets = data.path("scoreBuckets");
        assertThat(buckets).hasSize(4);

        final List<String> labels = new ArrayList<>();
        buckets.forEach(b -> labels.add(b.path("bucketLabel").asText()));
        assertThat(labels)
                .containsExactly(
                        "Y\u1EBFu / K\u00E9m [0 - 4.0 \u0111i\u1EC3m]",
                        "Trung b\u00ECnh [4.0 - 6.0 \u0111i\u1EC3m]",
                        "Kh\u00E1 [6.0 - 8.0 \u0111i\u1EC3m]",
                        "Gi\u1ECFi / Xu\u1EA5t s\u1EAFc [8.0 - 10.0 \u0111i\u1EC3m]");

        // 35% weak, 45% average, 75% good, 90% excellent.
        assertThat(buckets.get(0).path("studentCount").asInt()).isEqualTo(1);
        assertThat(buckets.get(1).path("studentCount").asInt()).isEqualTo(1);
        assertThat(buckets.get(2).path("studentCount").asInt()).isEqualTo(1);
        assertThat(buckets.get(3).path("studentCount").asInt()).isEqualTo(1);
        assertThat(buckets.get(0).path("percentage").decimalValue()).isEqualByComparingTo("25.00");
    }

    @Test
    @DisplayName("per-question failure rates match the seeded is_correct flags")
    void failureRatesMatchTheSeed() throws Exception {
        final JsonNode rates = examAnalytics("teacher1", "123456").path("questionFailureRates");

        assertThat(rates).hasSize(4);
        final List<String> order = new ArrayList<>();
        rates.forEach(r -> order.add(r.path("questionType").asText()));
        // Sorted hardest first: matching nobody got, then blanks, ordering, MCQ.
        assertThat(order)
                .containsExactly(
                        "MATCHING", "FILL_IN_BLANK", "ORDERING", "MULTIPLE_CHOICE");

        final List<String> percentages = new ArrayList<>();
        rates.forEach(r -> percentages.add(r.path("failurePercentage").asText()));
        assertThat(percentages).containsExactly("100.0", "75.0", "50.0", "25.0");

        assertThat(rates.get(0).path("promptSnippet").asText()).startsWith("N\u1ED1i");
        assertThat(rates.get(0).path("totalAttempts").asInt()).isEqualTo(4);
    }

    @Test
    @DisplayName("exactly the two intended students are flagged, with the right reasons")
    void atRiskFlagsExactlyTheIntendedStudents() throws Exception {
        final JsonNode atRisk = examAnalytics("teacher1", "123456").path("atRiskParticipants");

        assertThat(atRisk).hasSize(2);
        final List<String> names = new ArrayList<>();
        atRisk.forEach(s -> names.add(s.path("fullName").asText()));
        assertThat(names).containsExactly("Ph\u1EA1m Th\u00F9y Linh", "V\u0169 H\u1EA3i Nam");

        final JsonNode lowScore = atRisk.get(0);
        assertThat(lowScore.path("averageScore").decimalValue()).isEqualByComparingTo("35.0");
        assertThat(lowScore.path("maxTabSwitches").asInt()).isZero();
        assertThat(lowScore.path("riskReason").asText()).isEqualTo("\u0110i\u1EC3m trung b\u00ECnh d\u01B0\u1EDBi 5.0 (3.5/10).");

        final JsonNode proctored = atRisk.get(1);
        assertThat(proctored.path("averageScore").decimalValue()).isEqualByComparingTo("45.0");
        assertThat(proctored.path("maxTabSwitches").asInt()).isEqualTo(4);
        assertThat(proctored.path("riskReason").asText())
                .as("both triggers fired, so both clauses must be named")
                .isEqualTo(
                        "\u0110i\u1EC3m trung b\u00ECnh d\u01B0\u1EDBi 5.0 (4.5/10)"
                                + " v\u00E0 vi ph\u1EA1m r\u1EDDi tab 4 l\u1EA7n.");

        // The counter is emitted under both names for the dashboard.
        assertThat(proctored.path("tabSwitchCount").asInt()).isEqualTo(4);
        assertThat(proctored.path("maxTabSwitches").asInt()).isEqualTo(4);
    }

    @Test
    @DisplayName("the course overview aggregates the cohort and names the weak competencies")
    void courseOverviewMatchesTheSeed() throws Exception {
        final String body =
                mockMvc.perform(
                                get("/teacher/analytics/courses/{id}", courseId)
                                        .header("Authorization", "Bearer " + tokenFor("teacher1", "123456")))
                        .andExpect(status().isOk())
                        .andReturn()
                        .getResponse()
                        .getContentAsString();
        final JsonNode data = json(body).path("data");

        assertThat(data.path("courseTitle").asText())
                .isEqualTo("Nh\u1EADp m\u00F4n M\u1EA1ng m\u00E1y t\u00EDnh v\u00E0 An to\u00E0n h\u1EC7 th\u1ED1ng");
        assertThat(data.path("overview").path("totalStudents").asInt()).isEqualTo(5);
        assertThat(data.path("overview").path("averageScore").decimalValue())
                .isEqualByComparingTo("61.25");
        assertThat(data.path("overview").path("completionRate").decimalValue())
                .as("mean completed-lessons ratio across the five students")
                .isEqualByComparingTo("50.00");
        assertThat(data.path("overview").path("atRiskStudentCount").asInt()).isEqualTo(2);

        final JsonNode weak = data.path("weakKnowledgeNodes");
        assertThat(weak).isNotEmpty();
        assertThat(weak.get(0).path("nodeName").asText()).startsWith("NET_01");
        assertThat(weak.get(0).path("averageMastery").decimalValue())
                .isEqualByComparingTo("58.5");
        assertThat(weak.get(0).path("studentCount").asInt()).isEqualTo(4);
    }

    @Test
    @DisplayName("a second teacher cannot read a course they do not own")
    void ownershipIsEnforced() throws Exception {
        mockMvc.perform(
                        get("/teacher/analytics/courses/{id}", courseId)
                                .header("Authorization", "Bearer " + tokenFor("teacher2", "123456")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errorCode").value("FORBIDDEN"))
                .andExpect(
                        jsonPath("$.message")
                                .value(
                                        "B\u1EA1n kh\u00F4ng c\u00F3 quy\u1EC1n xem th\u1ED1ng k\u00EA"
                                                + " c\u1EE7a kh\u00F3a h\u1ECDc ho\u1EB7c b\u00E0i thi n\u00E0y."));
    }

    @Test
    @DisplayName("an admin reads the same figures without being the author")
    void adminSeesEverything() throws Exception {
        final JsonNode data = examAnalytics("admin", "admin123");
        assertThat(data.path("submissionCount").asInt()).isEqualTo(4);
    }
}

package com.thesis.elearning.modules.question.service;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.thesis.elearning.common.exception.AppException;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * The backend half of the zero-leak guarantee.
 *
 * <p>The authoring form refuses to submit a public payload carrying an answer key.
 * These tests assert the server refuses it too, because the client-side guard
 * protects nothing once the API is reachable by curl.
 */
class PayloadLeakageGuardTest {

    private final PayloadLeakageGuard guard = new PayloadLeakageGuard();
    private final ObjectMapper mapper = new ObjectMapper();

    private void reject(final String json) {
        assertThatThrownBy(() -> guard.assertNoAnswerKeyLeakage(mapper.readTree(json)))
                .isInstanceOf(AppException.class)
                .hasMessage("Cấu trúc dữ liệu JSON của nội dung câu hỏi không hợp lệ.");
    }

    /* ------------------------------ Rejects ------------------------------ */

    @ParameterizedTest(name = "rejects top-level key ''{0}''")
    @ValueSource(
            strings = {
                "isCorrect", "correctOptionIds", "correctPairs", "correctOrder",
                "acceptedAnswers", "patterns", "explanation", "explanations",
                "gradingNote", "pairs", "pattern"
            })
    @DisplayName("Every answer-key key is rejected at the top level")
    void rejectsTopLevelKeys(final String key) throws Exception {
        reject("{\"prompt\":\"q\",\"" + key + "\":\"anything\"}");
    }

    @Test
    @DisplayName("Rejects isCorrect nested inside an option, where it naturally hides")
    void rejectsNestedIsCorrectInsideOption() throws Exception {
        reject(
                "{\"prompt\":\"q\",\"options\":["
                        + "{\"id\":\"a\",\"text\":\"A\",\"isCorrect\":true},"
                        + "{\"id\":\"b\",\"text\":\"B\",\"isCorrect\":false}]}");
    }

    @Test
    @DisplayName("Rejects a key buried several levels down")
    void rejectsDeeplyNestedKey() throws Exception {
        reject("{\"prompt\":\"q\",\"a\":{\"b\":[{\"c\":{\"acceptedAnswers\":{\"x\":[\"1\"]}}}]}}");
    }

    @Test
    @DisplayName("Rejects a key present only in the second array element")
    void rejectsKeyInLaterArrayElement() throws Exception {
        reject("{\"items\":[{\"prompt\":\"ok\"},{\"prompt\":\"ok\",\"correctOrder\":[\"a\"]}]}");
    }

    /* ------------------------------ Accepts ------------------------------ */

    @Test
    @DisplayName("Accepts a clean multiple-choice public payload")
    void acceptsCleanPayload() throws Exception {
        final JsonNode clean =
                mapper.readTree(
                        "{\"prompt\":\"Which is linear?\",\"allowMultiple\":false,"
                                + "\"options\":[{\"id\":\"a\",\"text\":\"Linear\"},"
                                + "{\"id\":\"b\",\"text\":\"Quadratic\"}]}");
        assertThatCode(() -> guard.assertNoAnswerKeyLeakage(clean)).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("Accepts fill-in-blank structure with blanks and hints")
    void acceptsFillInBlankStructure() throws Exception {
        final JsonNode blank =
                mapper.readTree(
                        "{\"prompt\":\"The set of integers is ____ .\","
                                + "\"blanks\":[{\"id\":\"b1\",\"hint\":\"a countable set\"}]}");
        assertThatCode(() -> guard.assertNoAnswerKeyLeakage(blank)).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("Accepts matching left/right item lists")
    void acceptsMatchingStructure() throws Exception {
        final JsonNode matching =
                mapper.readTree(
                        "{\"prompt\":\"Match them\",\"left\":[{\"id\":\"l1\",\"text\":\"TCP\"}],"
                                + "\"right\":[{\"id\":\"r1\",\"text\":\"Transport\"}]}");
        assertThatCode(() -> guard.assertNoAnswerKeyLeakage(matching)).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("Accepts an ordering item list")
    void acceptsOrderingStructure() throws Exception {
        final JsonNode ordering =
                mapper.readTree(
                        "{\"prompt\":\"Order\",\"items\":[{\"id\":\"i1\",\"text\":\"SYN\"},"
                                + "{\"id\":\"i2\",\"text\":\"ACK\"}]}");
        assertThatCode(() -> guard.assertNoAnswerKeyLeakage(ordering)).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("Treats a null payload as acceptable")
    void acceptsNullPayload() {
        assertThatCode(() -> guard.assertNoAnswerKeyLeakage(null)).doesNotThrowAnyException();
    }

    /**
     * Deep nesting must be rejected cleanly, not overflow the stack into a 500.
     *
     * <p>Capped at 900 because Jackson itself refuses documents nested deeper than
     * 1000, which is the real upper bound the guard ever sees - so the deepest
     * tree it must walk is bounded, and an iterative walk is not a micro-optimisation
     * but the reason a hostile payload cannot produce a 500.
     */
    @Test
    @DisplayName("Walks a deeply nested payload without a StackOverflowError")
    void survivesDeepNesting() throws Exception {
        final JsonNode deep = mapper.readTree(nest(900, "1"));
        assertThatCode(() -> guard.assertNoAnswerKeyLeakage(deep)).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("Finds the forbidden key even at the bottom of a deep structure")
    void findsForbiddenKeyAtDepth() throws Exception {
        reject(nest(900, "{\"isCorrect\":true}"));
    }

    @Test
    @DisplayName("A key that merely contains a forbidden substring is still fine")
    void doesNotRejectSubstringMatches() throws Exception {
        final JsonNode innocent =
                mapper.readTree(
                        "{\"prompt\":\"isCorrectness\",\"correctness\":\"x\","
                                + "\"note\":\"acceptedAnswers are stored separately\"}");
        assertThatCode(() -> guard.assertNoAnswerKeyLeakage(innocent)).doesNotThrowAnyException();
    }

    /** Builds {@code {"n":{"n":{ ... innermost ... }}}} {@code depth} levels deep. */
    private static String nest(final int depth, final String innermost) {
        final StringBuilder json = new StringBuilder();
        for (int i = 0; i < depth; i++) {
            json.append("{\"n\":");
        }
        json.append(innermost);
        for (int i = 0; i < depth; i++) {
            json.append('}');
        }
        return json.toString();
    }

    static Stream<String> allForbiddenKeys() {
        return PayloadLeakageGuard.forbiddenPublicKeys().stream().sorted();
    }

    @Test
    @DisplayName("Guard rejects exactly the keys it advertises, used as property names")
    void guardRejectsAdvertisedKeys() throws Exception {
        for (final String key : allForbiddenKeys().toList()) {
            reject("{\"" + key + "\":1}");
        }
    }
}

    /** Deep nesting must be rejected, not overflow the stack into a 500. */

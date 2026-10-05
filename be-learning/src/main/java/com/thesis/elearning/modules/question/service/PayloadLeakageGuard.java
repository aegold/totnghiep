package com.thesis.elearning.modules.question.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.thesis.elearning.common.exception.AppException;
import com.thesis.elearning.common.exception.ErrorCode;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Component;

/**
 * Server-side guarantee that {@code content_payload} never carries an answer key.
 *
 * <h2>Why the client-side guard is not enough</h2>
 * The authoring form keeps a single source of truth per question and splits it
 * into a public and a private document with an explicit allow-list, and it refuses
 * to submit a payload containing {@code isCorrect}. That protects students only
 * for as long as the client is the one assembling the request. The API is public:
 * anything that can hold a token - a script, curl, a browser extension - can post
 * {@code {"options":[{"id":"a","text":"A","isCorrect":true}]}} straight to
 * {@code POST /teacher/questions}. Because the backend is the authoritative
 * security boundary, it has to re-check what the client already checked.
 *
 * <h2>Why it scans rather than validates the shape</h2>
 * Validating a full schema per question type would be a second, permanently
 * drifting copy of the client's Zod definitions, and it would still have to be
 * extended for every new renderer. Scanning for a closed set of answer-key key
 * names is additive-safe: a payload that contains none of them cannot reveal a key,
 * however creative its shape.
 *
 * <p>The key list is held here as data so {@code PayloadLeakageGuardTest} and this
 * method cannot drift apart, mirroring the client's own {@code FORBIDDEN_PUBLIC_KEYS}.
 */
@Component
public class PayloadLeakageGuard {

    /**
     * Property names that only ever belong in {@code solution_payload}.
     *
     * <p>Both singular and plural spellings are present because the authored forms
     * genuinely use each of them: {@code explanation} on a single option versus
     * {@code explanations} keyed by option id, {@code pattern} versus
     * {@code patterns} for blank regular expressions.
     */
    private static final Set<String> FORBIDDEN_PUBLIC_KEYS = Set.of(
            "isCorrect",
            "correctOptionIds",
            "correctPairs",
            "correctOrder",
            "acceptedAnswers",
            "patterns",
            "explanation",
            "explanations",
            "gradingNote",
            "pairs",
            "pattern");

    /** Exposed for tests so the constant and the assertions cannot diverge. */
    public static Set<String> forbiddenPublicKeys() {
        return FORBIDDEN_PUBLIC_KEYS;
    }

    /**
     * Rejects a payload that embeds grading data in the student-visible document.
     *
     * <p>Walks the whole tree, not just the top level, because the natural place
     * for {@code isCorrect} is nested inside each option object where a shallow
     * check would miss it.
     *
     * @param contentPayload the document about to be persisted as student-visible.
     * @throws AppException 400 with {@code Cấu trúc dữ liệu JSON của nội dung câu hỏi
     *                      không hợp lệ.} when any forbidden key appears at any depth.
     */
    public void assertNoAnswerKeyLeakage(final JsonNode contentPayload) {
        if (contentPayload == null || contentPayload.isNull()) {
            return;
        }
        if (containsForbiddenKey(contentPayload)) {
            throw new AppException(
                    ErrorCode.BAD_REQUEST,
                    "Cấu trúc dữ liệu JSON của nội dung câu hỏi không hợp lệ.");
        }
    }

    /**
     * Depth-first search for a forbidden property name.
     *
     * <p>Iterative with an explicit stack rather than recursive: a deeply nested
     * authored payload should be rejected, not be able to overflow the stack and
     * surface as a 500.
     *
     * <p>Field names inside {@code solution_payload}-shaped value objects are not
     * exempt - {@code acceptedAnswers} maps blank ids to answer lists, so the map
     * values are inspected too.
     *
     * @return true when a forbidden key appears at any depth.
     */
    private boolean containsForbiddenKey(final JsonNode root) {
        final Deque<JsonNode> pending = new ArrayDeque<>();
        pending.push(root);

        while (!pending.isEmpty()) {
            final JsonNode current = pending.pop();

            if (current.isObject()) {
                final Iterator<Map.Entry<String, JsonNode>> fields = current.fields();
                while (fields.hasNext()) {
                    final Map.Entry<String, JsonNode> field = fields.next();
                    if (FORBIDDEN_PUBLIC_KEYS.contains(field.getKey())) {
                        return true;
                    }
                    pending.push(field.getValue());
                }
            } else if (current.isArray()) {
                for (final JsonNode element : current) {
                    pending.push(element);
                }
            }
        }
        return false;
    }
}

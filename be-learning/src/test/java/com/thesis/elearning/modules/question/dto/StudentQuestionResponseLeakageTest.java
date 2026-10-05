package com.thesis.elearning.modules.question.dto;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.lang.reflect.RecordComponent;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Proves the answer key cannot be serialised to a student.
 *
 * <p>The real protection is a type-level one: {@link StudentQuestionResponse} has
 * no {@code solutionPayload} property, so no code path - however careless - can
 * serialise it. That is only true as long as nobody adds the field back, so this
 * test asserts the absence reflectively. It fails the build if a later change
 * reintroduces the key, which is far more likely than a runtime scrubbing step
 * quietly being removed.
 */
class StudentQuestionResponseLeakageTest {

    private static final String LEAKAGE = "solutionPayload";

    @Test
    @DisplayName("StudentQuestionResponse has no solutionPayload record component")
    void hasNoSolutionComponent() {
        assertThat(componentNames()).doesNotContain(LEAKAGE);
    }

    @Test
    @DisplayName("StudentQuestionResponse has no accessor that could return the key")
    void hasNoSolutionAccessor() {
        assertThat(Arrays.stream(StudentQuestionResponse.class.getMethods())
                        .map(java.lang.reflect.Method::getName))
                .noneMatch(name -> name.toLowerCase(java.util.Locale.ROOT).contains("solution"));
    }

    @Test
    @DisplayName("StudentQuestionResponse has no field holding the key")
    void hasNoSolutionField() {
        assertThat(Arrays.stream(StudentQuestionResponse.class.getDeclaredFields())
                        .map(java.lang.reflect.Field::getName))
                .noneMatch(name -> name.toLowerCase(java.util.Locale.ROOT).contains("solution"));
    }

    @Test
    @DisplayName("Serialising the student DTO emits only the five public fields")
    void serialisesOnlyPublicFields() throws Exception {
        final ObjectMapper mapper = new ObjectMapper();
        final JsonNode payload = mapper.readTree("{\"prompt\":\"Which is linear?\"}");
        final StudentQuestionResponse response =
                StudentQuestionResponse.of(42L, "MULTIPLE_CHOICE", payload);

        final String json = mapper.writeValueAsString(response);

        assertThat(json).contains("\"versionId\":42");
        assertThat(json).contains("\"questionType\":\"MULTIPLE_CHOICE\"");
        assertThat(json).contains("\"contentPayload\"");
        assertThat(json).doesNotContain(LEAKAGE);
        assertThat(json.toLowerCase(java.util.Locale.ROOT)).doesNotContain("solution");
    }

    @Test
    @DisplayName("The student DTO is the only question response shape, and the teacher one has the key")
    void teacherDtoIsTheOnlyHolderOfTheKey() {
        assertThat(componentNamesOf(TeacherQuestionDetailResponse.class)).contains(LEAKAGE);
        assertThat(componentNamesOf(CreateQuestionRequest.class)).contains("solutionPayload");
        assertThat(componentNamesOf(UpdateQuestionRequest.class)).contains("solutionPayload");
    }

    @Test
    @DisplayName("points and orderIndex are null outside an exam context")
    void examFieldsAreNullOutsideAnExam() {
        final StudentQuestionResponse response =
                new StudentQuestionResponse(1L, "MULTIPLE_CHOICE", null, null, null);

        assertThat(response.points()).isNull();
        assertThat(response.orderIndex()).isNull();
    }

    private static List<String> componentNames() {
        return componentNamesOf(StudentQuestionResponse.class);
    }

    private static List<String> componentNamesOf(final Class<?> type) {
        return Arrays.stream(type.getRecordComponents())
                .map(RecordComponent::getName)
                .toList();
    }
}

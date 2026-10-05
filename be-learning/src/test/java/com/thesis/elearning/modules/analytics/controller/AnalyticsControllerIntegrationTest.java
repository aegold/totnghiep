package com.thesis.elearning.modules.analytics.controller;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

/**
 * HTTP-level authorisation for the analytics endpoints, against a real database.
 *
 * <h2>Why this suite exists separately from the unit test</h2>
 * Every figure on these pages is produced by hand-written native SQL. A mocked
 * repository proves the service's rules but cannot prove the SQL parses, that the
 * {@code LEFT JOIN} skeleton really returns four rows, or that a
 * {@code CAST(json AS CHAR)} survives the round trip. This suite runs the actual
 * queries against the {@code test} profile's database, which is the only way to
 * catch a syntax error that would otherwise reach production.
 *
 * <h2>Why fixtures are inserted with {@link JdbcTemplate}</h2>
 * Seeding through the repositories would mean constructing a subject, a chapter,
 * a lesson, a question and a revision for every row, and the graph would say
 * nothing about authorisation - which is what is under test here. Raw inserts
 * keep each fixture to one statement and let the assertions stay about status
 * codes and payload shape.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AnalyticsControllerIntegrationTest {

    private static final Long COURSE_ID = 1L;
    private static final Long EXAM_ID = 900L;
    private static final Long OWNER_TEACHER = 7L;
    private static final Long OTHER_TEACHER = 8L;
    private static final Long STUDENT = 3L;

    @Autowired private MockMvc mockMvc;
    @Autowired private JdbcTemplate jdbc;

    private static Authentication auth(final Long userId, final String authority) {
        return new UsernamePasswordAuthenticationToken(
                String.valueOf(userId), null, List.of(new SimpleGrantedAuthority(authority)));
    }

    /**
     * One course, one paper, one question and one graded submission.
     *
     * <p>The submission earns 8 of 10, i.e. exactly 80%, which sits on the
     * {@code GOOD}/{@code EXCELLENT} boundary - the value most likely to expose an
     * off-by-one in the bucketing.
     */
    @BeforeEach
    void seed() {
        jdbc.update("DELETE FROM submission_answers");
        jdbc.update("DELETE FROM exam_submissions");
        jdbc.update("DELETE FROM exam_questions");
        jdbc.update("DELETE FROM exams");
        jdbc.update("DELETE FROM learning_progress");
        jdbc.update("DELETE FROM lesson_knowledge_nodes");
        jdbc.update("DELETE FROM lessons");
        jdbc.update("DELETE FROM chapters");
        jdbc.update("DELETE FROM courses");
        jdbc.update("DELETE FROM question_versions");
        jdbc.update("DELETE FROM questions");
        jdbc.update("DELETE FROM knowledge_node_mastery");
        jdbc.update("DELETE FROM knowledge_nodes");
        // Reference data too: DB_CLOSE_DELAY=-1 keeps the in-memory database alive
        // between test methods, so the second seed would collide with the first.
        jdbc.update("DELETE FROM subjects");
        jdbc.update("DELETE FROM difficulty_levels");
        jdbc.update("DELETE FROM users WHERE user_id IN (3, 7, 8)");

        for (final Object[] user :
                new Object[][] {
                    {3L, "sinhvien", "sv@b.c", "{noop}", "SV"},
                    {7L, "giaovien", "gv@b.c", "{noop}", "Giang Vien A"},
                    {8L, "giaovien2", "gv2@b.c", "{noop}", "Giang Vien B"}
                }) {
            jdbc.update(
                    "INSERT INTO users (user_id, username, email, password_hash, full_name,"
                            + " is_active, is_deleted, created_at, updated_at)"
                            + " VALUES (?,?,?,?,?,1,0,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)",
                    user);
        }

        jdbc.update(
                "INSERT INTO subjects (subject_id, subject_code, subject_name)"
                        + " VALUES (1,'MATH','Toan')");
        jdbc.update(
                // Only the two columns DifficultyLevel maps. The H2 schema is built
                // from the entities, so it is narrower than the MySQL table, which
                // also carries level_name.
                "INSERT INTO difficulty_levels (difficulty_id, level_code) VALUES (1,'MEDIUM')");
        jdbc.update(
                "INSERT INTO knowledge_nodes (node_id, subject_id, node_name)"
                        + " VALUES (1, 1, 'He phuong trinh')");
        jdbc.update(
                "INSERT INTO courses (course_id, subject_id, teacher_id, title,"
                        + " is_published, is_deleted, created_at, updated_at)"
                        + " VALUES (1, 1, 7, 'Toan 10', 1, 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)");
        jdbc.update(
                "INSERT INTO chapters (chapter_id, course_id, title, order_index, is_deleted)"
                        + " VALUES (1, 1, 'Chuong 1', 1, 0)");
        jdbc.update(
                "INSERT INTO lessons (lesson_id, chapter_id, title, order_index, is_deleted,"
                        + " created_at) VALUES (1, 1, 'Bai 1', 1, 0, CURRENT_TIMESTAMP)");
        jdbc.update("INSERT INTO lesson_knowledge_nodes (lesson_id, node_id) VALUES (1, 1)");
        jdbc.update(
                "INSERT INTO learning_progress (student_id, lesson_id, is_completed,"
                        + " last_watched_seconds, updated_at) VALUES (3, 1, 1, 100, CURRENT_TIMESTAMP)");
        jdbc.update(
                "INSERT INTO questions (question_id, node_id, author_id, difficulty_id,"
                        + " question_type, is_deleted, created_at)"
                        + " VALUES (1, 1, 7, 1, 'MULTIPLE_CHOICE', 0, CURRENT_TIMESTAMP)");
        jdbc.update(
                "INSERT INTO question_versions (version_id, question_id, version_number,"
                        + " content_payload, solution_payload, is_active, created_at)"
                        + " VALUES (1, 1, 1, '{\"prompt\":\"Bang 2 + 2 bang bao nhieu?\","
                        + " \"allowMultiple\":false}', '{\"correctOptionIds\":[\"a\"]}', 1,"
                        + " CURRENT_TIMESTAMP)");
        jdbc.update(
                "INSERT INTO exams (exam_id, course_id, title, duration_minutes, max_attempts,"
                        + " is_published, is_deleted, created_at)"
                        + " VALUES (900, 1, 'Kiem tra 1', 60, 1, 1, 0, CURRENT_TIMESTAMP)");
        jdbc.update(
                "INSERT INTO exam_questions (exam_id, version_id, points, order_index)"
                        + " VALUES (900, 1, 10.00, 1)");
        jdbc.update(
                "INSERT INTO exam_submissions (submission_id, exam_id, student_id, started_at,"
                        + " submitted_at, tab_switch_count)"
                        + " VALUES (1, 900, 3, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 1)");
        jdbc.update(
                // answered_at exists in MySQL but SubmissionAnswer does not map it,
                // so the H2 schema generated from the entities has no such column.
                // No analytics query reads it.
                "INSERT INTO submission_answers (submission_id, version_id, student_response,"
                        + " earned_points, is_correct)"
                        + " VALUES (1, 1, '{}', 8.00, 0)");
    }

    /* ============================== Owning teacher ============================= */

    @Test
    @DisplayName("the authoring teacher reads their own course statistics")
    void ownerReadsCourseAnalytics() throws Exception {
        mockMvc.perform(
                        get("/teacher/analytics/courses/{id}", COURSE_ID)
                                .with(authentication(auth(OWNER_TEACHER, "ROLE_TEACHER"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("L\u1EA5y d\u1EEF li\u1EC7u th\u1ED1ng k\u00EA th\u00E0nh c\u00F4ng."))
                .andExpect(jsonPath("$.data.courseId").value(COURSE_ID))
                .andExpect(jsonPath("$.data.overview.totalStudents").value(1));
    }

    @Test
    @DisplayName("the authoring teacher reads their own exam statistics")
    void ownerReadsExamAnalytics() throws Exception {
        mockMvc.perform(
                        get("/teacher/analytics/exams/{id}", EXAM_ID)
                                .with(authentication(auth(OWNER_TEACHER, "ROLE_TEACHER"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.examId").value(EXAM_ID))
                .andExpect(jsonPath("$.data.submissionCount").value(1))
                .andExpect(jsonPath("$.data.averageScore").value(80.0))
                // The LEFT JOIN skeleton: four bands even though three are empty.
                .andExpect(jsonPath("$.data.scoreBuckets.length()").value(4))
                .andExpect(jsonPath("$.data.scoreBuckets[0].bucketLabel")
                        .value("Y\u1EBFu / K\u00E9m [0 - 4.0 \u0111i\u1EC3m]"))
                .andExpect(jsonPath("$.data.scoreBuckets[3].studentCount").value(1))
                // 8/10 graded but not fully correct, so the failure rate is 100%.
                .andExpect(jsonPath("$.data.questionFailureRates[0].failurePercentage").value(100.0))
                .andExpect(jsonPath("$.data.questionFailureRates[0].totalAttempts").value(1))
                .andExpect(jsonPath("$.data.questionFailureRates[0].promptSnippet")
                        .value("Bang 2 + 2 bang bao nhieu?"));
    }

    @Test
    @DisplayName("the prompt snippet is read out of the JSON payload, not leaked raw")
    void snippetIsExtractedFromJson() throws Exception {
        mockMvc.perform(
                        get("/teacher/analytics/exams/{id}", EXAM_ID)
                                .with(authentication(auth(OWNER_TEACHER, "ROLE_TEACHER"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.questionFailureRates[0].promptSnippet")
                        .value("Bang 2 + 2 bang bao nhieu?"))
                // A raw cast would have emitted the whole document, braces and all.
                .andExpect(jsonPath("$.data.questionFailureRates[0].promptSnippet")
                        .value(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("{"))));
    }

    /* ================================== Admin ================================= */

    @Test
    @DisplayName("an admin reads a course they do not own")
    void adminReadsAnyCourse() throws Exception {
        mockMvc.perform(
                        get("/teacher/analytics/courses/{id}", COURSE_ID)
                                .with(authentication(auth(OTHER_TEACHER, "ROLE_ADMIN"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.courseId").value(COURSE_ID));
    }

    @Test
    @DisplayName("an admin reads an exam they do not own")
    void adminReadsAnyExam() throws Exception {
        mockMvc.perform(
                        get("/teacher/analytics/exams/{id}", EXAM_ID)
                                .with(authentication(auth(OTHER_TEACHER, "ROLE_ADMIN"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.examId").value(EXAM_ID));
    }

    /* ============================ Unauthorized teacher ======================== */

    @Test
    @DisplayName("a teacher is refused another teacher's course statistics with 403")
    void strangerTeacherRefusedCourse() throws Exception {
        mockMvc.perform(
                        get("/teacher/analytics/courses/{id}", COURSE_ID)
                                .with(authentication(auth(OTHER_TEACHER, "ROLE_TEACHER"))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.errorCode").value("FORBIDDEN"))
                .andExpect(jsonPath("$.message")
                        .value("B\u1EA1n kh\u00F4ng c\u00F3 quy\u1EC1n xem th\u1ED1ng k\u00EA"
                                + " c\u1EE7a kh\u00F3a h\u1ECDc ho\u1EB7c b\u00E0i thi n\u00E0y."));
    }

    @Test
    @DisplayName("a teacher is refused another teacher's exam statistics with 403")
    void strangerTeacherRefusedExam() throws Exception {
        mockMvc.perform(
                        get("/teacher/analytics/exams/{id}", EXAM_ID)
                                .with(authentication(auth(OTHER_TEACHER, "ROLE_TEACHER"))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errorCode").value("FORBIDDEN"));
    }

    @Test
    @DisplayName("a student cannot reach the analytics subtree at all")
    void studentRefused() throws Exception {
        mockMvc.perform(
                        get("/teacher/analytics/courses/{id}", COURSE_ID)
                                .with(authentication(auth(STUDENT, "ROLE_STUDENT"))))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("an anonymous caller gets 401 before any handler runs")
    void anonymousRefused() throws Exception {
        mockMvc.perform(get("/teacher/analytics/courses/{id}", COURSE_ID))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.errorCode").value("UNAUTHORIZED"));
    }

    /* ================================ Not found ============================== */

    @Test
    @DisplayName("an unknown course is 404 and names the id")
    void unknownCourse() throws Exception {
        mockMvc.perform(
                        get("/teacher/analytics/courses/{id}", 424242L)
                                .with(authentication(auth(OWNER_TEACHER, "ROLE_TEACHER"))))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message")
                        .value("Kh\u00F4ng t\u00ECm th\u1EA5y kh\u00F3a h\u1ECDc y\u00EAu c\u1EA7u: 424242."));
    }

    @Test
    @DisplayName("an unknown exam is 404 and names the id")
    void unknownExam() throws Exception {
        mockMvc.perform(
                        get("/teacher/analytics/exams/{id}", 424242L)
                                .with(authentication(auth(OWNER_TEACHER, "ROLE_TEACHER"))))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message")
                        .value("Kh\u00F4ng t\u00ECm th\u1EA5y b\u00E0i thi y\u00EAu c\u1EA7u: 424242."));
    }

    /* ============================== No submissions =========================== */

    @Test
    @DisplayName("a paper nobody has sat is rejected rather than charted as all-zero")
    void examWithoutSubmissions() throws Exception {
        jdbc.update("DELETE FROM submission_answers");
        jdbc.update("DELETE FROM exam_submissions");

        mockMvc.perform(
                        get("/teacher/analytics/exams/{id}", EXAM_ID)
                                .with(authentication(auth(OWNER_TEACHER, "ROLE_TEACHER"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message")
                        .value("Kh\u00F4ng c\u00F3 d\u1EEF li\u1EC7u b\u00E0i n\u1ED9p n\u00E0o"
                                + " \u0111\u1EC3 th\u1ED1ng k\u00EA cho b\u00E0i thi n\u00E0y."));
    }
}
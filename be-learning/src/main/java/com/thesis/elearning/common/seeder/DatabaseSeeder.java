package com.thesis.elearning.common.seeder;

import java.util.List;
import java.time.LocalDateTime;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Loads a realistic Vietnamese networking course on an empty database.
 *
 * <h2>Why this is a seeder and not a test fixture</h2>
 * A demonstration dataset has to be the same for a lecturer, a reviewer and CI.
 * Tests build their own rows because they assert their own numbers; this builds a
 * course a human can actually click through.
 *
 * <h2>Why it is guarded by a property rather than a user count</h2>
 * {@code userRepository.count() == 0} is the obvious guard and the wrong one: a
 * development database that already holds unrelated accounts would skip seeding
 * forever, and the dataset would silently never appear. The marker is instead the
 * seeded {@code admin} account plus the seeded subject code, so seeding works into
 * a populated database and is still a no-op on the second run.
 *
 * <p>Disabled under the {@code test} profile by default. As a
 * {@link CommandLineRunner} it would otherwise execute on every
 * {@code @SpringBootTest} context - eight BCrypt hashes at strength 12 per context,
 * and seeded rows contaminating suites that count what they find.
 *
 * <h2>Why raw SQL rather than the repositories</h2>
 * Two reasons, both practical. Nineteen injected repositories for a fixed script is
 * a constructor nobody can read; and {@code exam_questions} and
 * {@code submission_answers} carry composite keys, which are exactly where JPA
 * {@code merge} has already bitten this project once during grading. Explicit SQL
 * also keeps the intended column set visible - notably {@code answered_at}, which
 * exists in MySQL but is unmapped, and therefore absent from the test schema.
 */
@Component
@ConditionalOnProperty(name = "app.seed.enabled", havingValue = "true", matchIfMissing = true)
public class DatabaseSeeder implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(DatabaseSeeder.class);

    /** Subjects and courses are looked up by code so ids may differ per database. */
    private static final String SUBJECT_CODE = "MMT";

    private static final String SKIP_MESSAGE =
            "Cơ sở dữ liệu đã có dữ liệu, bỏ qua bước khởi tạo mẫu.";
    private static final String START_MESSAGE =
            "Bắt đầu khởi tạo dữ liệu mẫu học thuật cho hệ thống Adaptive LMS...";

    private final JdbcTemplate jdbc;
    private final TransactionTemplate transaction;
    private final PasswordEncoder passwordEncoder;

    public DatabaseSeeder(
            final JdbcTemplate jdbc,
            final PasswordEncoder passwordEncoder,
            final TransactionTemplate transaction) {
        this.jdbc = jdbc;
        this.passwordEncoder = passwordEncoder;
        this.transaction = transaction;
    }

    @Override
    public void run(final String... args) {
        if (alreadySeeded()) {
            log.info(SKIP_MESSAGE);
            return;
        }
        /*
         * The transaction is opened explicitly rather than by an annotation on
         * this method. A catch block inside a @Transactional method swallows the
         * exception before the proxy ever sees it, so the transaction COMMITS a
         * half-written curriculum - exactly the state this seeder is supposed to
         * be impossible to leave behind. With the work in a callback and the catch
         * outside it, any failure rolls the whole thing back and the database is
         * left exactly as it was found.
         */
        try {
            transaction.executeWithoutResult(status -> {
                seedReferenceData();
                final Long subjectId = seedSubject();
                seedAccounts();
                final Long teacherId = userId("teacher1");
                final java.util.Map<String, Long> nodes = seedKnowledgeGraph(subjectId);
                final Long courseId = seedCourse(subjectId, teacherId);
                seedCurriculum(courseId, nodes);
                final java.util.Map<String, Long> questions =
                        seedQuestionBank(teacherId, nodes);
                seedExamAndAttempts(courseId, teacherId, questions);
                seedProgress();
                seedMastery(nodes);
            });

            log.info(
                    "Khởi tạo dữ liệu mẫu hoàn tất: {} câu hỏi, 1 khóa học và 4 lần nộp bài mẫu.",
                    4);
        } catch (final RuntimeException ex) {
            /*
             * Swallowed deliberately, and safe to swallow precisely because the
             * rollback above has already happened. Refusing to start the whole
             * application over optional demonstration data would make a development
             * machine unusable; the operator gets one clear log line and the API
             * stays available on an unchanged database.
             */
            log.error("Không thể khởi tạo dữ liệu mẫu: {}", ex.getMessage(), ex);
        }
    }

    /* ------------------------------------------------------------------ */
    /*  Guard                                                              */
    /* ------------------------------------------------------------------ */

    /**
     * Whether this dataset is already present.
     *
     * <p>Both halves are checked: the seeded admin proves the accounts went in,
     * and the seeded subject proves the curriculum did. Either alone could be
     * satisfied by an unrelated database that happens to share the name.
     */
    private boolean alreadySeeded() {
        return jdbc.queryForObject(
                        "SELECT COUNT(*) FROM users WHERE username = 'admin'", Long.class)
                > 0
                || jdbc.queryForObject(
                                "SELECT COUNT(*) FROM subjects WHERE subject_code = ?",
                                Long.class,
                                SUBJECT_CODE)
                        > 0;
    }

    /* ------------------------------------------------------------------ */
    /*  Reference data                                                     */
    /* ------------------------------------------------------------------ */

    /**
     * Roles and difficulty bands, inserted only when absent.
     *
     * <p>MySQL already carries all three roles from the baseline schema, while the
     * in-memory test schema carries only the roles. Unconditional inserts would
     * fail on one or the other.
     */
    private void seedReferenceData() {
        insertIfAbsent(
                "INSERT INTO roles (role_id, role_code, role_name) VALUES (?,?,?)",
                new Object[] {1, "ROLE_ADMIN", "Quản trị viên hệ thống"},
                "SELECT COUNT(*) FROM roles WHERE role_id = ?",
                1);
        insertIfAbsent(
                "INSERT INTO roles (role_id, role_code, role_name) VALUES (?,?,?)",
                new Object[] {2, "ROLE_TEACHER", "Giảng viên"},
                "SELECT COUNT(*) FROM roles WHERE role_id = ?",
                2);
        insertIfAbsent(
                "INSERT INTO roles (role_id, role_code, role_name) VALUES (?,?,?)",
                new Object[] {3, "ROLE_STUDENT", "Học sinh / Sinh viên"},
                "SELECT COUNT(*) FROM roles WHERE role_id = ?",
                3);

        // Questions carry a difficulty_id foreign key, and the test schema creates
        // that table empty, so the bands are part of what seeding must provide.
        insertIfAbsent(
                "INSERT INTO difficulty_levels (difficulty_id, level_code, level_name) VALUES (?,?,?)",
                new Object[] {1, "EASY", "Dễ"},
                "SELECT COUNT(*) FROM difficulty_levels WHERE level_code = ?",
                "EASY");
        insertIfAbsent(
                "INSERT INTO difficulty_levels (difficulty_id, level_code, level_name) VALUES (?,?,?)",
                new Object[] {2, "MEDIUM", "Trung bình"},
                "SELECT COUNT(*) FROM difficulty_levels WHERE level_code = ?",
                "MEDIUM");
        insertIfAbsent(
                "INSERT INTO difficulty_levels (difficulty_id, level_code, level_name) VALUES (?,?,?)",
                new Object[] {3, "HARD", "Khó"},
                "SELECT COUNT(*) FROM difficulty_levels WHERE level_code = ?",
                "HARD");
    }

    private void insertIfAbsent(
            final String insert,
            final Object[] values,
            final String existsSql,
            final Object... existsArgs) {
        final Long count = valuesOfCount(existsSql, existsArgs);
        if (count == 0L) {
            jdbc.update(insert, values);
        }
    }

    private Long valuesOfCount(final String sql, final Object... args) {
        final Long value = jdbc.queryForObject(sql, Long.class, args);
        return value == null ? 0L : value;
    }

    private Long seedSubject() {
        final Long existing =
                findOne("SELECT subject_id FROM subjects WHERE subject_code = ?", SUBJECT_CODE);
        if (existing != null) {
            return existing;
        }
        jdbc.update(
                "INSERT INTO subjects (subject_code, subject_name, description) VALUES (?,?,?)",
                SUBJECT_CODE,
                "Mạng máy tính và Truyền thông dữ liệu",
                "Kiến thức nền tảng về mạng: mô hình phân lớp, định tuyến và giao thức.");
        return jdbc.queryForObject(
                "SELECT subject_id FROM subjects WHERE subject_code = ?",
                Long.class,
                SUBJECT_CODE);
    }

    /* ------------------------------------------------------------------ */
    /*  Accounts                                                           */
    /* ------------------------------------------------------------------ */

    /**
     * Eight accounts across the three roles.
     *
     * <p>Hashing happens here rather than being precomputed so the stored hashes
     * are always produced by the application's own encoder, at its own configured
     * cost. That is the slowest part of the seed - strength 12 is deliberately so -
     * but it keeps the demo credentials genuinely valid for a real sign-in.
     */
    private void seedAccounts() {
        createUser("admin", "admin@elearning.edu.vn", "Quản trị hệ thống", "admin123", 1);
        createUser(
                "teacher1",
                "nguyen.an@elearning.edu.vn",
                "TS. Nguyễn Văn An",
                "123456",
                2);
        createUser(
                "teacher2",
                "tran.binh@elearning.edu.vn",
                "ThS. Trần Thị Bình",
                "123456",
                2);
        createUser(
                "student1",
                "sv.trung@elearning.edu.vn",
                "Nguyễn Thành Trung",
                "123456",
                3);
        createUser("student2", "sv.minh@elearning.edu.vn", "Lê Hoàng Minh", "123456", 3);
        createUser("student3", "sv.linh@elearning.edu.vn", "Phạm Thùy Linh", "123456", 3);
        createUser("student4", "sv.nam@elearning.edu.vn", "Vũ Hải Nam", "123456", 3);
        createUser("student5", "sv.hoa@elearning.edu.vn", "Đặng Quỳnh Hoa", "123456", 3);
    }

    private void createUser(
            final String username,
            final String email,
            final String fullName,
            final String rawPassword,
            final int roleId) {
        if (userId(username) != null) {
            return;
        }
        jdbc.update(
                "INSERT INTO users (username, email, password_hash, full_name, is_active, is_deleted)"
                        + " VALUES (?,?,?,?,1,0)",
                username,
                email,
                passwordEncoder.encode(rawPassword),
                fullName);
        jdbc.update(
                "INSERT INTO user_roles (user_id, role_id) VALUES (?,?)", userId(username), roleId);
    }

    /**
     * First column of the first row, or {@code null} when there is no row.
     *
     * <p>{@code queryForObject} raises {@code EmptyResultDataAccessException} on
     * an empty result, which turns an ordinary "not seeded yet" probe into a boot
     * failure.
     */
    private Long findOne(final String sql, final Object... args) {
        final List<Long> rows = jdbc.query(sql, (rs, i) -> rs.getLong(1), args);
        return rows.isEmpty() ? null : rows.get(0);
    }

    /** Null when the account does not exist; see {@link #findOne}. */
    private Long userId(final String username) {
        return findOne("SELECT user_id FROM users WHERE username = ?", username);
    }

    /* ------------------------------------------------------------------ */
    /*  Curriculum                                                         */
    /* ------------------------------------------------------------------ */

    /**
     * Six competencies and the edges between them.
     *
     * <p>The codes are folded into {@code node_name} because the table has no code
     * column; they are what a lecturer will read, so they belong in the name rather
     * than in a description they will never see.
     *
     * <p>The graph is a DAG by construction: 01 roots everything, 04 and 05 both
     * feed 06, and no edge points backwards. A cycle here would make every
     * dependent permanently unreachable.
     *
     * @return code to generated id.
     */
    private java.util.LinkedHashMap<String, Long> seedKnowledgeGraph(final Long subjectId) {
        final String[][] nodes = {
            {
                "NET_01",
                "NET_01 – Mô hình OSI và Kiến trúc TCP/IP",
                "Bảy tầng phân lớp, đơn vị dữ liệu của từng tầng và cách ánh xạ sang mô hình TCP/IP thực tế."
            },
            {
                "NET_02",
                "NET_02 – Giao thức Tầng Giao vận (TCP & UDP)",
                "Khác biệt về độ tin cậy và thứ tự: TCP bảo đảm giao hàng, UDP ưu tiên độ trễ thấp."
            },
            {
                "NET_03",
                "NET_03 – Định địa chỉ IPv4 và Kỹ thuật Chia Subnet",
                "Cấu trúc địa chỉ, mặt nạ mạng con, địa chỉ mạng và địa chỉ quảng bá."
            },
            {
                "NET_04",
                "NET_04 – Cơ chế Bắt tay 3 bước & Kiểm soát Tắc nghẽn TCP",
                "Cờ SYN/ACK/FIN, cửa sổ nhận và bốn trạng thái của thuật toán tránh tắc nghẽn."
            },
            {
                "NET_05",
                "NET_05 – Định tuyến IP và Giao thức Định tuyến OSPF",
                "Bảng định tuyếng, vùng OSPF, thông điệp LSA và thuật toán chọn đường ngắn nhất."
            },
            {
                "NET_06",
                "NET_06 – Giao thức Tầng Ứng dụng (DNS & HTTP/HTTPS)",
                "Phân giải tên miền, mô hình yêu cầu – phản hồi, và vai trò của chứng thư số TLS."
            },
        };

        final java.util.LinkedHashMap<String, Long> byCode = new java.util.LinkedHashMap<>();
        for (final String[] node : nodes) {
            if (jdbc.queryForObject(
                            "SELECT COUNT(*) FROM knowledge_nodes WHERE node_name = ?",
                            Long.class,
                            node[1])
                    == 0L) {
                jdbc.update(
                        "INSERT INTO knowledge_nodes (subject_id, node_name, description)"
                                + " VALUES (?,?,?)",
                        subjectId,
                        node[1],
                        node[2]);
            }
            byCode.put(
                    node[0],
                    jdbc.queryForObject(
                            "SELECT node_id FROM knowledge_nodes WHERE node_name = ?",
                            Long.class,
                            node[1]));
        }

        // prerequisite -> dependent
        final String[][] edges = {
            {"NET_01", "NET_02"}, {"NET_01", "NET_03"}, {"NET_02", "NET_04"},
            {"NET_03", "NET_05"}, {"NET_04", "NET_06"}, {"NET_05", "NET_06"},
        };
        for (final String[] edge : edges) {
            jdbc.update(
                    "INSERT INTO knowledge_prerequisites"
                            + " (prerequisite_node_id, dependent_node_id) VALUES (?,?)",
                    byCode.get(edge[0]),
                    byCode.get(edge[1]));
        }
        return byCode;
    }

    private Long seedCourse(final Long subjectId, final Long teacherId) {
        final String title = "Nhập môn Mạng máy tính và An toàn hệ thống";
        final List<Long> existing =
                jdbc.query(
                        "SELECT course_id FROM courses WHERE title = ?",
                        (rs, i) -> rs.getLong(1),
                        title);
        if (!existing.isEmpty()) {
            return existing.get(0);
        }
        jdbc.update(
                "INSERT INTO courses (subject_id, teacher_id, title, description, is_published,"
                        + " is_deleted) VALUES (?,?,?,?,1,0)",
                subjectId,
                teacherId,
                title,
                "Học phần 3 tín chỉ giới thiệu nguyên lý mạng, từ mô hình phân lớp đến định tuyến và bảo mật.");
        return jdbc.queryForObject(
                "SELECT course_id FROM courses WHERE title = ?", Long.class, title);
    }

    /**
     * One chapter, two lessons and their materials.
     *
     * @return the first lesson's id, which the progress rows reference.
     */
    private Long seedCurriculum(
            final Long courseId, final java.util.Map<String, Long> nodes) {
        jdbc.update(
                "INSERT INTO chapters (course_id, title, order_index, is_deleted) VALUES (?,?,?,0)",
                courseId,
                "Chương 1: Tổng quan về Mạng máy tính",
                1);

        final Long chapterId =
                jdbc.queryForObject(
                        "SELECT chapter_id FROM chapters WHERE course_id = ? ORDER BY order_index LIMIT 1",
                        Long.class,
                        courseId);

        jdbc.update(
                "INSERT INTO lessons (chapter_id, title, order_index, is_deleted)"
                        + " VALUES (?,?,?,0)",
                chapterId,
                "Bài 1: Mô hình OSI và Kiến trúc TCP/IP",
                1);
        final Long lesson1 =
                jdbc.queryForObject(
                        "SELECT lesson_id FROM lessons WHERE chapter_id = ? ORDER BY order_index LIMIT 1",
                        Long.class,
                        chapterId);

        jdbc.update(
                "INSERT INTO lessons (chapter_id, title, order_index, is_deleted)"
                        + " VALUES (?,?,?,0)",
                chapterId,
                "Bài 2: Định địa chỉ IPv4 và Chia Subnet",
                2);
        final Long lesson2 =
                jdbc.queryForObject(
                        "SELECT lesson_id FROM lessons WHERE chapter_id = ? ORDER BY order_index DESC LIMIT 1",
                        Long.class,
                        chapterId);

        linkNodes(lesson1, nodes, "NET_01", "NET_02", "NET_04");
        linkNodes(lesson2, nodes, "NET_03", "NET_05");

        jdbc.update(
                "INSERT INTO materials (lesson_id, material_type, title, file_url, duration_seconds,"
                        + " total_slides, is_indexed_for_ai) VALUES (?,?,?,?,?,?,?)",
                lesson1,
                "PDF",
                "Slide bài giảng Chương 1",
                "/uploads/materials/slides_ch1.pdf",
                null,
                42,
                1);
        jdbc.update(
                "INSERT INTO materials (lesson_id, material_type, title, file_url, duration_seconds,"
                        + " total_slides, is_indexed_for_ai) VALUES (?,?,?,?,?,?,?)",
                lesson1,
                "VIDEO",
                "Bài giảng video: Mô hình OSI và TCP/IP",
                "/uploads/materials/video_ch1_osi.mp4",
                2700,
                null,
                1);
        return lesson1;
    }

    private void linkNodes(
            final Long lessonId,
            final java.util.Map<String, Long> nodes,
            final String... codes) {
        for (final String code : codes) {
            jdbc.update(
                    "INSERT INTO lesson_knowledge_nodes (lesson_id, node_id) VALUES (?,?)",
                    lessonId,
                    nodes.get(code));
        }
    }

    /* ------------------------------------------------------------------ */
    /*  Question bank                                                      */
    /* ------------------------------------------------------------------ */

    /**
     * One question per renderer, each attached to the competency it assesses.
     *
     * <p>Answer keys live only in {@code solution_payload}. The public document
     * carries the prompt and the structure a renderer needs and nothing else -
     * the same split {@code PayloadLeakageGuard} enforces on authored questions,
     * so a seeded bank cannot become a leak the authored path would refuse.
     *
     * @return question key to generated version id.
     */
    private java.util.LinkedHashMap<String, Long> seedQuestionBank(
            final Long teacherId, final java.util.Map<String, Long> nodes) {
        final Integer medium =
                jdbc.queryForObject(
                        "SELECT difficulty_id FROM difficulty_levels WHERE level_code = ?",
                        (rs, i) -> rs.getInt(1),
                        "MEDIUM");

        final java.util.LinkedHashMap<String, Long> versions = new java.util.LinkedHashMap<>();

        versions.put(
                "Q1",
                createQuestion(
                        teacherId,
                        medium,
                        nodes.get("NET_04"),
                        "MULTIPLE_CHOICE",
                        """
                        {
                          "prompt": "Trong giao thức TCP, cờ (flag) nào được sử dụng để bắt đầu quá trình thiết lập kết nối 3 bước?",
                          "allowMultiple": false,
                          "options": [
                            {"id": "opt_a", "text": "ACK"},
                            {"id": "opt_b", "text": "SYN"},
                            {"id": "opt_c", "text": "FIN"},
                            {"id": "opt_d", "text": "RST"}
                          ]
                        }
                        """,
                        """
                        {
                          "correctOptionIds": ["opt_b"],
                          "explanation": "Cờ SYN (Synchronize Sequence Number) được gửi đầu tiên từ Client để mở đầu bắt tay ba bước."
                        }
                        """));

        versions.put(
                "Q2",
                createQuestion(
                        teacherId,
                        medium,
                        nodes.get("NET_03"),
                        "FILL_IN_BLANK",
                        """
                        {
                          "prompt": "Subnet mask mặc định của địa chỉ mạng Lớp C là {{blank_1}}, và cổng mặc định của HTTPS là {{blank_2}}.",
                          "blanks": [
                            {"id": "blank_1", "hint": "Định dạng thập phân phân cách bởi dấu chấm"},
                            {"id": "blank_2", "hint": "Số hiệu cổng"}
                          ],
                          "hint": "Gõ đúng ký tự, không cần dấu chấm ở cuối."
                        }
                        """,
                        """
                        {
                          "acceptedAnswers": {"blank_1": ["255.255.255.0"], "blank_2": ["443"]},
                          "explanation": "Mạng Lớp C dùng mặt nạ 255.255.255.0 (24 bit mạng). Cổng mặc định của HTTPS là 443."
                        }
                        """));

        versions.put(
                "Q3",
                createQuestion(
                        teacherId,
                        medium,
                        nodes.get("NET_01"),
                        "MATCHING",
                        """
                        {
                          "prompt": "Nối các tầng trong mô hình OSI với đơn vị dữ liệu giao thức (PDU) tương ứng:",
                          "leftItems": [
                            {"id": "L1", "text": "Tầng Vận chuyển (Transport)"},
                            {"id": "L2", "text": "Tầng Mạng (Network)"},
                            {"id": "L3", "text": "Tầng Liên kết dữ liệu (Data Link)"}
                          ],
                          "rightItems": [
                            {"id": "R1", "text": "Frame"},
                            {"id": "R2", "text": "Segment"},
                            {"id": "R3", "text": "Packet"}
                          ]
                        }
                        """,
                        """
                        {
                          "correctPairs": {"L1": "R2", "L2": "R3", "L3": "R1"},
                          "explanation": "Tầng Vận chuyển dùng Segment, tầng Mạng dùng Packet, tầng Liên kết dữ liệu dùng Frame."
                        }
                        """));

        versions.put(
                "Q4",
                createQuestion(
                        teacherId,
                        medium,
                        nodes.get("NET_04"),
                        "ORDERING",
                        """
                        {
                          "prompt": "Sắp xếp theo thứ tự đúng của quá trình Bắt tay 3 bước (3-way Handshake) trong TCP:",
                          "items": [
                            {"id": "step_1", "text": "Server phản hồi gói tin SYN-ACK"},
                            {"id": "step_2", "text": "Client gửi gói tin SYN"},
                            {"id": "step_3", "text": "Client gửi gói tin ACK để xác nhận hoàn tất kết nối"}
                          ]
                        }
                        """,
                        """
                        {
                          "correctOrder": ["step_2", "step_1", "step_3"],
                          "explanation": "Thứ tự chuẩn là Client SYN -> Server SYN-ACK -> Client ACK."
                        }
                        """));

        return versions;
    }

    private Long createQuestion(
            final Long authorId,
            final Integer difficultyId,
            final Long nodeId,
            final String type,
            final String contentPayload,
            final String solutionPayload) {

        jdbc.update(
                "INSERT INTO questions (node_id, author_id, difficulty_id, question_type,"
                        + " is_deleted) VALUES (?,?,?,?,0)",
                nodeId,
                authorId,
                difficultyId,
                type);
        final Long questionId =
                jdbc.queryForObject(
                        "SELECT MAX(question_id) FROM questions WHERE author_id = ?",
                        Long.class,
                        authorId);

        jdbc.update(
                "INSERT INTO question_versions (question_id, version_number, content_payload,"
                        + " solution_payload, is_active) VALUES (?,1,?,?,1)",
                questionId,
                contentPayload,
                solutionPayload);
        return jdbc.queryForObject(
                "SELECT version_id FROM question_versions WHERE question_id = ?",
                Long.class,
                questionId);
    }

    /* ------------------------------------------------------------------ */
    /*  Exam and attempts                                                  */
    /* ------------------------------------------------------------------ */

    /**
     * The paper, its four items, and four historical attempts that feed the
     * analytics dashboard.
     *
     * <h2>Why the marks are distributed by hand</h2>
     * Marks live in {@code submission_answers.earned_points}, and every dashboard
     * figure is a ratio over that column - so a seeded score that did not add up
     * would put the whole analytics page subtly wrong. The split below is chosen so
     * each total is reachable from the actual grading rules: multiple choice and
     * ordering are all-or-nothing, while fill-in-blank and matching award
     * {@code points * correct / total}. 2.0 + 3.0 + 3.0 + 2.0 leaves every blank and
     * every pair worth a clean 1.5 and 1.0.
     *
     * <p>The four attempts deliberately land one per score band, and the resulting
     * percentages (90, 75, 35, 45) leave the "Trung bình" band empty - which is the
     * case the histogram's fixed four-row skeleton exists to handle.
     */
    private void seedExamAndAttempts(
            final Long courseId, final Long teacherId, final java.util.Map<String, Long> questions) {

        final String title = "Kiểm tra Giữa kỳ: Mạng máy tính căn bản";
        jdbc.update(
                "INSERT INTO exams (course_id, title, duration_minutes, max_attempts,"
                        + " is_published, is_deleted) VALUES (?,?,?,?,1,0)",
                courseId,
                title,
                45,
                1);
        final Long examId =
                jdbc.queryForObject(
                        "SELECT exam_id FROM exams WHERE title = ?", Long.class, title);

        // order_index is 1-based and is what the paper is served in.
        final Object[][] paper = {
            {questions.get("Q1"), "2.00", 1},
            {questions.get("Q2"), "3.00", 2},
            {questions.get("Q3"), "3.00", 3},
            {questions.get("Q4"), "2.00", 4},
        };
        for (final Object[] item : paper) {
            jdbc.update(
                    "INSERT INTO exam_questions (exam_id, version_id, points, order_index)"
                            + " VALUES (?,?,?,?)",
                    examId,
                    item[0],
                    new java.math.BigDecimal((String) item[1]),
                    item[2]);
        }

        final LocalDateTime now = LocalDateTime.now();
        final Object[][] attempts = {
            // username, tabSwitches, daysAgo, then per question: response, earned, isCorrect
            {
                "student1", 0, 7,
                mcq("opt_b"), "2.00", 1,
                fib("255.255.255.0", "443"), "3.00", 1,
                pairs("L1", "R2", "L2", "R3"), "2.00", 0,
                order("step_2", "step_1", "step_3"), "2.00", 1,
            },
            {
                "student2", 1, 7,
                mcq("opt_b"), "2.00", 1,
                fib("255.255.255.0", null), "1.50", 0,
                pairs("L1", "R2", "L2", "R3"), "2.00", 0,
                order("step_2", "step_1", "step_3"), "2.00", 1,
            },
            {
                "student3", 0, 6,
                mcq("opt_a"), "0.00", 0,
                fib(null, "443"), "1.50", 0,
                pairs("L1", "R2", "L2", "R3"), "2.00", 0,
                order("step_1", "step_2", "step_3"), "0.00", 0,
            },
            {
                "student4", 4, 5,
                mcq("opt_b"), "2.00", 1,
                fib("255.255.255.0", null), "1.50", 0,
                pairs("L1", "R2"), "1.00", 0,
                order("step_1", "step_2", "step_3"), "0.00", 0,
            },
        };

        for (final Object[] attempt : attempts) {
            final Long studentId = userId((String) attempt[0]);
            final int tabSwitches = (Integer) attempt[1];
            final int daysAgo = (Integer) attempt[2];
            final LocalDateTime submittedAt = now.minusDays(daysAgo).withHour(10).withMinute(0);
            final LocalDateTime startedAt = submittedAt.minusMinutes(30);

            jdbc.update(
                    "INSERT INTO exam_submissions (exam_id, student_id, started_at, submitted_at,"
                            + " tab_switch_count) VALUES (?,?,?,?,?)",
                    examId,
                    studentId,
                    startedAt,
                    submittedAt,
                    tabSwitches);
            final Long submissionId =
                    jdbc.queryForObject(
                            "SELECT submission_id FROM exam_submissions WHERE exam_id = ?"
                                    + " AND student_id = ? ORDER BY submission_id DESC LIMIT 1",
                            Long.class,
                            examId,
                            studentId);

            for (int q = 0; q < 4; q++) {
                final int base = 3 + q * 3;
                jdbc.update(
                        "INSERT INTO submission_answers (submission_id, version_id,"
                                + " student_response, earned_points, is_correct)"
                                + " VALUES (?,?,?,?,?)",
                        submissionId,
                        questions.get("Q" + (q + 1)),
                        (String) attempt[base],
                        new java.math.BigDecimal((String) attempt[base + 1]),
                        attempt[base + 2]);
            }
        }
    }

    /* ------------------------------------------------------------------ */
    /*  Learner state                                                      */
    /* ------------------------------------------------------------------ */

    /**
     * Lesson progress for the five students.
     *
     * <p>Every student has at least one row, because the course completion rate is
     * averaged over students who appear in this table - one with no row at all
     * would silently drop out of the denominator rather than scoring zero.
     */
    private void seedProgress() {
        final Long lesson1 =
                jdbc.queryForObject(
                        "SELECT MIN(lesson_id) FROM lessons", Long.class);
        final Long lesson2 =
                jdbc.queryForObject(
                        "SELECT MAX(lesson_id) FROM lessons", Long.class);

        final Object[][] rows = {
            {"student1", lesson1, 1}, {"student1", lesson2, 1},
            {"student2", lesson1, 1},
            {"student3", lesson1, 1},
            {"student4", lesson1, 0},
            {"student5", lesson1, 1}, {"student5", lesson2, 0},
        };
        for (final Object[] row : rows) {
            jdbc.update(
                    "INSERT INTO learning_progress (student_id, lesson_id, is_completed,"
                            + " last_watched_seconds) VALUES (?,?,?,?)",
                    userId((String) row[0]),
                    row[1],
                    row[2],
                    ((Integer) row[2]) == 1 ? 1800 : 120);
        }
    }

    /**
     * Per-node mastery, matching what the grading stage would have written.
     *
     * <p>Materialised rather than recomputed here, because no exam-to-node link
     * exists to recompute it from - which is precisely why the grading stage writes
     * this table. Values are the mean of each student's percentage on the
     * questions attached to that node.
     */
    private void seedMastery(final java.util.Map<String, Long> nodes) {
        final Object[][] rows = {
            // node,        student1, student2, student3, student4
            {"NET_01", 67, 67, 67, 33},
            {"NET_03", 100, 50, 50, 50},
            {"NET_04", 100, 100, 0, 50},
        };
        final String[] students = {"student1", "student2", "student3", "student4"};
        for (final Object[] row : rows) {
            for (int s = 0; s < students.length; s++) {
                jdbc.update(
                        "INSERT INTO knowledge_node_mastery (student_id, node_id, mastery_score,"
                                + " attempts) VALUES (?,?,?,1)",
                        userId(students[s]),
                        nodes.get((String) row[0]),
                        row[s + 1]);
            }
        }
    }

    /* ------------------------------------------------------------------ */
    /*  Answer shapes                                                      */
    /* ------------------------------------------------------------------ */

    /** Rendered exactly as a renderer would submit it. */
    private static String mcq(final String... optionIds) {
        return "{\"selectedOptionIds\": ["
                + String.join(", ", java.util.Arrays.stream(optionIds).map(id -> "\"" + id + "\"").toList())
                + "]}";
    }

    private static String fib(final String first, final String second) {
        final StringBuilder json = new StringBuilder("{\"answers\": {");
        if (first != null) {
            json.append("\"blank_1\": \"").append(first).append("\"");
        }
        if (second != null) {
            if (first != null) {
                json.append(", ");
            }
            json.append("\"blank_2\": \"").append(second).append("\"");
        }
        return json.append("}}").toString();
    }

    private static String pairs(final String... alternatingIds) {
        final StringBuilder json = new StringBuilder("{\"pairs\": {");
        for (int i = 0; i < alternatingIds.length; i += 2) {
            if (i > 0) {
                json.append(", ");
            }
            json.append('"')
                    .append(alternatingIds[i])
                    .append("\": \"")
                    .append(alternatingIds[i + 1])
                    .append('"');
        }
        return json.append("}}").toString();
    }

    private static String order(final String... ids) {
        return "{\"submittedOrder\": ["
                + String.join(", ", java.util.Arrays.stream(ids).map(id -> "\"" + id + "\"").toList())
                + "]}";
    }
}

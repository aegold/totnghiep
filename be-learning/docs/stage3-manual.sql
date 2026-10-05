-- =====================================================================
-- Stage 3 - Curriculum & Knowledge Engine
--
-- Bản SQL THỦ CÔNG, tương đương với
--   src/main/resources/db/migration/V2__stage3_curriculum.sql
--
-- Dùng MỘT trong hai cách, không dùng cả hai:
--   (a) Để Flyway tự chạy V2  -> KHÔNG chạy file này.
--   (b) Tự áp dụng           -> chạy file này, và KHÔNG để Flyway chạy V2
--       (đặt spring.flyway.enabled=false hoặc xoá file V2 trước khi boot).
--
-- Chạy:
--   mysql -u root -p adaptive_learning_db < docs/stage3-manual.sql
--
-- Ghi chú: câu 2 dùng ALTER TABLE nên KHÔNG idempotent. Nếu đã chạy một lần
-- rồi chạy lại, MySQL sẽ báo lỗi "Duplicate column name" - đó là tín hiệu
-- bạn KHÔNG cần chạy lần nữa.
-- =====================================================================

-- ---------------------------------------------------------------------
-- 1. Cache điểm thành thạo theo (học sinh x nút kiến thức)
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS knowledge_node_mastery (
    student_id    BIGINT      NOT NULL,
    node_id       BIGINT      NOT NULL,
    mastery_score INT         NOT NULL DEFAULT 0,
    attempts      INT         NOT NULL DEFAULT 0,
    updated_at    DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6)
                      ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (student_id, node_id),
    KEY idx_knm_node (node_id),
    CONSTRAINT fk_knm_student FOREIGN KEY (student_id) REFERENCES users (user_id) ON DELETE CASCADE,
    CONSTRAINT fk_knm_node    FOREIGN KEY (node_id)    REFERENCES knowledge_nodes (node_id) ON DELETE CASCADE
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

-- ---------------------------------------------------------------------
-- 2. Bổ sung cột cho materials
-- ---------------------------------------------------------------------
ALTER TABLE materials
    ADD COLUMN title            VARCHAR(200) NOT NULL DEFAULT '' AFTER material_type,
    ADD COLUMN duration_seconds INT          NULL AFTER file_url,
    ADD COLUMN total_slides      INT          NULL AFTER duration_seconds,
    ADD COLUMN text_content     LONGTEXT     NULL AFTER total_slides;

-- FE phân biệt videoUrl (VIDEO) với documentUrl (PDF/DOCUMENT/SLIDES).
-- Một cột file_url NOT NULL không biểu đạt được cả hai, và một DOCUMENT
-- hoàn toàn không cần tệp nhị phân vì nội dung nằm ở text_content.
ALTER TABLE materials
    MODIFY COLUMN file_url VARCHAR(500) NULL;

-- =====================================================================
-- 3. Dữ liệu mẫu để demo ngay
--
-- Chuỗi tiền kiến hợp lệ (DAG, không vòng lặp):
--   Toán rời rạc -> Giải tích -> Đại số tuyến tính
--   Toán rời rạc -> Giải tích -> Phương pháp số
--   Toán rời rạc -> Lập trình cơ bản -> Cấu trúc dữ liệu
--
-- Dùng INSERT IGNORE + subquery theo subject_code / teacher username nên chạy
-- lại nhiều lần cũng không sinh bản ghi trùng.
-- =====================================================================

INSERT IGNORE INTO subjects (subject_code, subject_name, description)
VALUES ('MATH', 'Toán học', 'Các môn Toán cơ sở và chuyên ngành');

INSERT IGNORE INTO knowledge_nodes (subject_id, node_name, description)
SELECT s.subject_id, 'Toán rời rạc', 'Quan hệ, tổ hợp, đồ thị và logic.' FROM subjects s
WHERE s.subject_code = 'MATH';

INSERT IGNORE INTO knowledge_nodes (subject_id, node_name, description)
SELECT s.subject_id, 'Giải tích', 'Giới hạn, đạo hàm và tích phân.' FROM subjects s
WHERE s.subject_code = 'MATH';

INSERT IGNORE INTO knowledge_nodes (subject_id, node_name, description)
SELECT s.subject_id, 'Đại số tuyến tính', 'Véc-tơ, ma trận và phép biến đổi tuyến tính.' FROM subjects s
WHERE s.subject_code = 'MATH';

INSERT IGNORE INTO knowledge_nodes (subject_id, node_name, description)
SELECT s.subject_id, 'Phương pháp số', 'Giải phương trình số và xấp xỉ.' FROM subjects s
WHERE s.subject_code = 'MATH';

INSERT IGNORE INTO knowledge_nodes (subject_id, node_name, description)
SELECT s.subject_id, 'Lập trình cơ bản', 'Biến, vòng lặp, hàm và cấu trúc điều khiển.' FROM subjects s
WHERE s.subject_code = 'MATH';

INSERT IGNORE INTO knowledge_nodes (subject_id, node_name, description)
SELECT s.subject_id, 'Cấu trúc dữ liệu', 'Danh sách, ngăn xếp, cây và đồ thị.' FROM subjects s
WHERE s.subject_code = 'MATH';

-- Tiền kiến: (prerequisite_node_id, dependent_node_id)
INSERT IGNORE INTO knowledge_prerequisites (prerequisite_node_id, dependent_node_id)
SELECT a.node_id, b.node_id FROM knowledge_nodes a, knowledge_nodes b
WHERE (a.node_name, b.node_name) IN (
    ('Toán rời rạc',        'Giải tích'),
    ('Toán rời rạc',        'Lập trình cơ bản'),
    ('Giải tích',           'Đại số tuyến tính'),
    ('Giải tích',           'Phương pháp số'),
    ('Lập trình cơ bản',    'Cấu trúc dữ liệu')
);

-- Một môn học mẫu (teacher_id lấy từ tài khoản giảng viên đầu tiên nếu có).
INSERT IGNORE INTO courses (subject_id, teacher_id, title, description, is_published)
SELECT s.subject_id,
       (SELECT u.user_id FROM users u
          JOIN user_roles ur ON ur.user_id = u.user_id
          JOIN roles r ON r.role_id = ur.role_id
         WHERE r.role_code = 'ROLE_TEACHER' ORDER BY u.user_id LIMIT 1),
       'Cấu trúc dữ liệu và Giải thuật',
       'Môn học mẫu cho bản demo Stage 3.',
       1
FROM subjects s WHERE s.subject_code = 'MATH';

INSERT IGNORE INTO chapters (course_id, title, order_index, is_deleted)
SELECT c.course_id, 'Chương 1: Nền tảng', 1, 0
FROM courses c WHERE c.title = 'Cấu trúc dữ liệu và Giải thuật';

INSERT IGNORE INTO chapters (course_id, title, order_index, is_deleted)
SELECT c.course_id, 'Chương 2: Cây và Đồ thị', 2, 0
FROM courses c WHERE c.title = 'Cấu trúc dữ liệu và Giải thuật';

INSERT IGNORE INTO lessons (chapter_id, title, order_index, is_deleted)
SELECT ch.chapter_id, 'Buổi 1: Mảng và Danh sách liên kết', 1, 0
FROM chapters ch JOIN courses c ON c.course_id = ch.course_id
WHERE ch.title = 'Chương 1: Nền tảng';

INSERT IGNORE INTO lessons (chapter_id, title, order_index, is_deleted)
SELECT ch.chapter_id, 'Buổi 2: Ngăn xếp và Hàng đợi', 2, 0
FROM chapters ch JOIN courses c ON c.course_id = ch.course_id
WHERE ch.title = 'Chương 1: Nền tảng';

-- Material kèm đủ cột mới (title/duration/text_content).
INSERT IGNORE INTO materials (lesson_id, material_type, title, file_url, duration_seconds, text_content, is_indexed_for_ai)
SELECT l.lesson_id, 'DOCUMENT', 'Bài giảng 1 (PDF)', 'https://example.invalid/buoi1.pdf', 2700,
       'Nội dung bài giảng về mảng và danh sách liên kết.', 1
FROM lessons l WHERE l.title = 'Buổi 1: Mảng và Danh sách liên kết';

INSERT IGNORE INTO materials (lesson_id, material_type, title, file_url, duration_seconds, text_content, is_indexed_for_ai)
SELECT l.lesson_id, 'DOCUMENT', 'Bài tập 1', NULL, NULL,
       'Bài tập trắc nghiệm về độ phức tạp.', 1
FROM lessons l WHERE l.title = 'Buổi 1: Mảng và Danh sách liên kết';

-- Nối bài học với nút kiến thức (lesson_knowledge_nodes)
INSERT IGNORE INTO lesson_knowledge_nodes (lesson_id, node_id)
SELECT l.lesson_id, n.node_id
FROM lessons l, knowledge_nodes n
WHERE l.title = 'Buổi 1: Mảng và Danh sách liên kết'
  AND n.node_name IN ('Lập trình cơ bản', 'Cấu trúc dữ liệu');
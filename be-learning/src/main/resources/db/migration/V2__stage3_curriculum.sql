-- =====================================================================
-- V2 - Curriculum & knowledge engine support
--
-- Two gaps in the inherited schema are closed here.
--
-- 1. knowledge_node_mastery
--    Nothing in the inherited schema stores a per-student, per-node score,
--    yet the skill tree needs one. Deriving it live from submission_answers
--    would require joining
--    questions -> exam_questions -> exam_submissions -> submission_answers,
--    with no exam<->node link to disambiguate which questions belong to which
--    attempt. This table is the materialised answer: written when an exam is
--    graded, read as a single indexed lookup when a student opens the graph.
--
-- 2. materials columns
--    The client needs a title, a duration, a slide count, extracted text and
--    separate video/document URLs. The inherited table had only file_url,
--    which cannot express both URL kinds, so it becomes nullable.
-- =====================================================================

-- ---------------------------------------------------------------------
-- Per-student mastery cache
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS knowledge_node_mastery (
    student_id    BIGINT      NOT NULL,
    node_id       BIGINT      NOT NULL,
    -- Percentage 0..100. >= 80 counts as mastered.
    mastery_score INT         NOT NULL DEFAULT 0,
    attempts      INT         NOT NULL DEFAULT 0,
    updated_at    DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6)
                      ON UPDATE CURRENT_TIMESTAMP(6),

    PRIMARY KEY (student_id, node_id),
    -- Serves "mastery across the whole cohort" analytics, which reads every
    -- row for one node rather than one student.
    KEY idx_knm_node (node_id),

    CONSTRAINT fk_knm_student
        FOREIGN KEY (student_id) REFERENCES users (user_id)
        ON DELETE CASCADE,
    CONSTRAINT fk_knm_node
        FOREIGN KEY (node_id) REFERENCES knowledge_nodes (node_id)
        ON DELETE CASCADE
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci COMMENT ='Materialised per-student mastery score per knowledge node';

-- ---------------------------------------------------------------------
-- materials: complete the column set the client requires
-- ---------------------------------------------------------------------
ALTER TABLE materials
    ADD COLUMN title            VARCHAR(200) NOT NULL DEFAULT '' AFTER material_type,
    ADD COLUMN duration_seconds INT          NULL AFTER file_url,
    ADD COLUMN total_slides      INT          NULL AFTER duration_seconds,
    ADD COLUMN text_content     LONGTEXT     NULL AFTER total_slides;

-- The client distinguishes videoUrl (VIDEO) from documentUrl (PDF/DOCUMENT/
-- SLIDES). A single NOT NULL file_url cannot express both, and a DOCUMENT
-- legitimately has no binary asset at all because its content lives in
-- text_content. Hence the relaxation to NULL.
ALTER TABLE materials
    MODIFY COLUMN file_url VARCHAR(500) NULL;
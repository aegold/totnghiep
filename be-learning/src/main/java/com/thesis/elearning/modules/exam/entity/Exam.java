package com.thesis.elearning.modules.exam.entity;

import com.thesis.elearning.modules.course.entity.Course;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.ColumnDefault;
import org.hibernate.annotations.SQLDelete;
import org.hibernate.annotations.SQLRestriction;

/**
 * A paper: a titled set of question revisions with a time limit.
 *
 * <h2>Why an exam pins revisions, not questions</h2>
 * {@code exam_questions.version_id} rather than {@code question_id}: a paper
 * must stay reproducible. If it referenced the question, a teacher editing the
 * stem would silently change an exam that students have already sat, and a
 * submission graded against the new stem would no longer match what was shown.
 *
 * <h2>Timer authority</h2>
 * {@code durationMinutes} is a length, not a deadline. The deadline is derived
 * per attempt from {@code exam_submissions.started_at}, so two students starting
 * at different times get different - and equally valid - windows.
 */
@Entity
@Table(name = "exams")
@SQLDelete(
        sql = "UPDATE exams SET is_deleted = 1, deleted_at = CURRENT_TIMESTAMP WHERE exam_id = ?")
@SQLRestriction("is_deleted = 0")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Exam {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "exam_id")
    private Long examId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "course_id", nullable = false)
    private Course course;

    @Column(name = "title", nullable = false, length = 200)
    private String title;

    /** Sitting time in minutes; the deadline is {@code started_at + this}. */
    @Column(name = "duration_minutes", nullable = false)
    private Integer durationMinutes;

    /**
     * How many times a student may sit this paper.
     *
     * <p>The default of 1 in the schema is the common case: an exam that can be
     * retaken until it sticks measures nothing.
     */
    @Column(name = "max_attempts", nullable = false)
    @Builder.Default
    private Integer maxAttempts = 1;

    /** Whether students can see and start this paper. */
    @Column(name = "is_published", nullable = false)
    @Builder.Default
    private boolean published = false;

    @Column(name = "is_deleted", nullable = false)
    @Builder.Default
    private boolean deleted = false;

    @Column(name = "deleted_at")
    private LocalDateTime deletedAt;

    @ColumnDefault("CURRENT_TIMESTAMP")
    @Column(name = "created_at", nullable = false, insertable = false, updatable = false)
    private LocalDateTime createdAt;
}

package com.thesis.elearning.modules.course.entity;

import com.thesis.elearning.modules.auth.entity.User;
import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.MapsId;
import jakarta.persistence.Table;
import org.hibernate.annotations.ColumnDefault;
import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.Objects;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * A student's progress through one lesson.
 *
 * <h2>Grain</h2>
 * The inherited key is {@code (student_id, lesson_id)} - progress is tracked per
 * LESSON, not per material. The client currently keys its own progress cache on
 * {@code materialId}; that divergence is recorded and will need reconciling when
 * the learning endpoints are wired, but changing the key here would mean
 * dropping a composite primary key and re-modelling the table.
 */
@Entity
@Table(name = "learning_progress")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LearningProgress {

    @EmbeddedId private ProgressId id;

    @MapsId("studentId")
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "student_id", nullable = false)
    private User student;

    @MapsId("lessonId")
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "lesson_id", nullable = false)
    private Lesson lesson;

    @Column(name = "is_completed", nullable = false)
    @Builder.Default
    private boolean completed = false;

    /** Resume position, in seconds. */
    @Column(name = "last_watched_seconds", nullable = false)
    @Builder.Default
    private Integer lastWatchedSeconds = 0;

    @ColumnDefault("CURRENT_TIMESTAMP")
    @Column(name = "updated_at", nullable = false, insertable = false, updatable = false)
    private LocalDateTime updatedAt;

    /** Composite key matching the inherited table. */
    @Embeddable
    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ProgressId implements Serializable {

        private static final long serialVersionUID = 1L;

        @Column(name = "student_id")
        private Long studentId;

        @Column(name = "lesson_id")
        private Long lessonId;

        @Override
        public boolean equals(final Object other) {
            if (this == other) {
                return true;
            }
            if (!(other instanceof ProgressId that)) {
                return false;
            }
            return Objects.equals(studentId, that.studentId)
                    && Objects.equals(lessonId, that.lessonId);
        }

        @Override
        public int hashCode() {
            return Objects.hash(studentId, lessonId);
        }
    }
}

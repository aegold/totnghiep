package com.thesis.elearning.modules.course.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.SQLDelete;
import org.hibernate.annotations.SQLRestriction;

/**
 * A chapter within a course.
 *
 * <p>Soft-deleted exactly like {@link Course}. The inherited table has
 * {@code is_deleted} but no {@code deleted_at}, so the SQLDelete statement cannot
 * stamp a removal time here; that asymmetry is a property of the schema, not an
 * oversight.
 */
@Entity
@Table(name = "chapters")
@SQLDelete(sql = "UPDATE chapters SET is_deleted = 1 WHERE chapter_id = ?")
@SQLRestriction("is_deleted = 0")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Chapter {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "chapter_id")
    private Long chapterId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "course_id", nullable = false)
    private Course course;

    @Column(name = "title", nullable = false, length = 200)
    private String title;

    @Column(name = "order_index", nullable = false)
    private Integer orderIndex;

    @Column(name = "is_deleted", nullable = false)
    @Builder.Default
    private boolean deleted = false;

    /*
     * Deliberately NO createdAt field: unlike lessons, courses and materials,
     * the inherited `chapters` table has no created_at column. Declaring one
     * here makes `ddl-auto: validate` refuse to start, which is exactly how the
     * mismatch was found rather than being discovered at runtime.
     */
}
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
import org.hibernate.annotations.ColumnDefault;
import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * One retrievable unit of lesson content (video, PDF, document, or slides).
 *
 * <p>{@code title}, {@code duration_seconds}, {@code total_slides} and
 * {@code text_content} come from migration V2; the inherited table had only
 * {@code file_url}, which cannot express both a video URL and a document URL.
 */
@Entity
@Table(name = "materials")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Material {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "material_id")
    private Long materialId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "lesson_id", nullable = false)
    private Lesson lesson;

    /** One of VIDEO, PDF, DOCUMENT, SLIDES. */
    @Column(name = "material_type", nullable = false, length = 20)
    private String materialType;

    @Column(name = "title", nullable = false, length = 200)
    private String title;

    /** Playable URL for VIDEO; document URL for the rest. Nullable since V2. */
    @Column(name = "file_url", length = 500)
    private String fileUrl;

    /** Runtime length, used to resume playback and to show progress. */
    @Column(name = "duration_seconds")
    private Integer durationSeconds;

    @Column(name = "total_slides")
    private Integer totalSlides;

    /** Extracted plain text; also the RAG corpus for the tutor. */
    @Column(name = "text_content", columnDefinition = "LONGTEXT")
    private String textContent;

    /** Whether the tutor's retrieval index includes this material. */
    @Column(name = "is_indexed_for_ai", nullable = false)
    @Builder.Default
    private boolean indexedForAi = false;

    @ColumnDefault("CURRENT_TIMESTAMP")
    @Column(name = "created_at", nullable = false, insertable = false, updatable = false)
    private LocalDateTime createdAt;
}

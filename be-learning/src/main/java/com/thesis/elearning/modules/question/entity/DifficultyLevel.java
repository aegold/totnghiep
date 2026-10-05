package com.thesis.elearning.modules.question.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.ColumnDefault;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Read-only lookup of the three difficulty bands a question can carry.
 *
 * <p>Mapped onto the inherited {@code difficulty_levels} table, whose primary key
 * is {@code difficulty_id} rather than the conventional {@code id}.
 *
 * <p>Deliberately reference-only: the application never inserts or updates a row
 * here, it only resolves {@code questions.difficulty_id} for display. Seeding and
 * curating the band list belongs to {@code DataInitializer} or a migration.
 */
@Entity
@Table(name = "difficulty_levels")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DifficultyLevel {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "difficulty_id")
    private Integer difficultyId;

    /** Stable discriminator persisted in {@code questions.question_type}'s sibling column. */
    @Column(name = "level_code", nullable = false, length = 30)
    private String levelCode;

    /**
     * Human-readable Vietnamese label.
     *
     * <p>Mapped read-only. The column exists in MySQL and is NOT NULL, but the
     * application never writes it, so declaring it {@code insertable = false}
     * keeps Hibernate from naming it in an INSERT and lets the declared default
     * fill it. Without this mapping the generated H2 schema lacks the column
     * entirely, and a seeder or import that supplies it fails there while the same
     * statement fails on MySQL for the opposite reason - a table that no single
     * INSERT satisfies.
     */
    @ColumnDefault("'Kh\u00F4ng x\u00E1c \u0111\u1ECBnh'")
    @Column(name = "level_name", nullable = false, length = 50, insertable = false, updatable = false)
    private String levelName;
}

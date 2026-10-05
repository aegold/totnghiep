package com.thesis.elearning.modules.course.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import org.hibernate.annotations.ColumnDefault;
import java.time.LocalDateTime;
import java.util.LinkedHashSet;
import java.util.Set;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.SQLDelete;
import org.hibernate.annotations.SQLRestriction;

/**
 * A lesson within a chapter.
 *
 * <p>The {@code many-to-many} to {@link com.thesis.elearning.modules.knowledge.entity.KnowledgeNode}
 * maps the inherited {@code lesson_knowledge_nodes} join table, which is what
 * lets the graph answer "how many lessons teach this competency".
 */
@Entity
@Table(name = "lessons")
@SQLDelete(sql = "UPDATE lessons SET is_deleted = 1 WHERE lesson_id = ?")
@SQLRestriction("is_deleted = 0")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Lesson {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "lesson_id")
    private Long lessonId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "chapter_id", nullable = false)
    private Chapter chapter;

    @Column(name = "title", nullable = false, length = 200)
    private String title;

    @Column(name = "order_index", nullable = false)
    private Integer orderIndex;

    @Column(name = "is_deleted", nullable = false)
    @Builder.Default
    private boolean deleted = false;

    @ColumnDefault("CURRENT_TIMESTAMP")
    @Column(name = "created_at", nullable = false, insertable = false, updatable = false)
    private LocalDateTime createdAt;

    @ManyToMany(fetch = FetchType.LAZY)
    @jakarta.persistence.JoinTable(
            name = "lesson_knowledge_nodes",
            joinColumns =
                    @jakarta.persistence.JoinColumn(name = "lesson_id"),
            inverseJoinColumns =
                    @jakarta.persistence.JoinColumn(name = "node_id"))
    @Builder.Default
    private Set<com.thesis.elearning.modules.knowledge.entity.KnowledgeNode> knowledgeNodes =
            new LinkedHashSet<>();
}

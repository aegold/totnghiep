package com.thesis.elearning.modules.exam.entity;

import com.thesis.elearning.modules.auth.entity.User;
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

/**
 * One student's attempt at one exam.
 *
 * <h2>Why {@code submittedAt == null} means "still running"</h2>
 * There is no status column. An attempt is in flight precisely while it has a
 * start and no submission, so the pair ({@code started_at NOT NULL},
 * {@code submitted_at NULL}) carries the state. A separate enum would need a
 * column the inherited schema does not have and would duplicate information
 * already implied by these two.
 *
 * <p>It also makes the timer stateless: the deadline is recomputed from
 * {@code startedAt} on every request, so no timer has to be stored, no scheduled
 * task has to close the attempt, and a server restart in the middle of an exam
 * loses nothing.
 *
 * <h2>Late work is still work</h2>
 * An attempt is never discarded for arriving late. Grading and storage happen
 * regardless; lateness is derived from the timestamps at read time rather than
 * flagged in a column, which keeps it auditable without duplicating state.
 */
@Entity
@Table(name = "exam_submissions")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ExamSubmission {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "submission_id")
    private Long submissionId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "exam_id", nullable = false)
    private Exam exam;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "student_id", nullable = false)
    private User student;

    /** When the attempt began; the deadline is this plus the exam's duration. */
    @Column(name = "started_at", nullable = false)
    private LocalDateTime startedAt;

    /** Null while the attempt is still running. */
    @Column(name = "submitted_at")
    private LocalDateTime submittedAt;

    /**
     * Proctoring strikes observed by the client.
     *
     * <p>The client counts a tab switch, a context-menu attempt and a devtools
     * shortcut as comparable events, and the schema has one counter for them, so
     * the most numerous signal is written here rather than invented as several
     * columns. It is an advisory signal for a human reviewer, never a gate: the
     * server does not reject a submission on the strength of it.
     */
    @Column(name = "tab_switch_count", nullable = false)
    @Builder.Default
    private Integer tabSwitchCount = 0;

    /** Whether this attempt has been handed in. */
    public boolean isSubmitted() {
        return submittedAt != null;
    }
}

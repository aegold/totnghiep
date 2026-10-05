package com.thesis.elearning.modules.course.service;

import com.thesis.elearning.common.exception.AppException;
import com.thesis.elearning.common.exception.ErrorCode;
import com.thesis.elearning.modules.auth.entity.User;
import com.thesis.elearning.modules.course.dto.UpdateProgressRequest;
import com.thesis.elearning.modules.course.entity.LearningProgress;
import com.thesis.elearning.modules.course.entity.LearningProgress.ProgressId;
import com.thesis.elearning.modules.course.entity.Lesson;
import com.thesis.elearning.modules.course.repository.LearningProgressRepository;
import com.thesis.elearning.modules.course.repository.LessonRepository;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Records and reads a student's progress.
 *
 * <h2>Grain</h2>
 * The inherited key is {@code (student_id, lesson_id)}. The client currently
 * caches progress per material; that divergence is recorded and needs
 * reconciling when the learning endpoints are wired, but changing the key here
 * would mean discarding a composite primary key.
 */
@Service
public class LearningProgressService {

    private final LearningProgressRepository progressRepository;
    private final LessonRepository lessonRepository;

    public LearningProgressService(
            final LearningProgressRepository progressRepository,
            final LessonRepository lessonRepository) {
        this.progressRepository = progressRepository;
        this.lessonRepository = lessonRepository;
    }

    /**
     * Upserts a progress row.
     *
     * <p>Idempotent: repeated calls from the player's progress events overwrite
     * rather than accumulate, so a duplicated event cannot inflate the watched
     * time.
     */
    @Transactional
    public LearningProgress update(
            final Long studentId, final Long lessonId, final UpdateProgressRequest request) {

        final Lesson lesson =
                lessonRepository
                        .findById(lessonId)
                        .orElseThrow(
                                () ->
                                        new AppException(
                                                ErrorCode.RESOURCE_NOT_FOUND,
                                                "Không tìm thấy bài học"));

        final ProgressId id = new ProgressId(studentId, lessonId);
        final LearningProgress progress =
                progressRepository
                        .findById(id)
                        .orElseGet(
                                () -> {
                                    final LearningProgress fresh = new LearningProgress();
                                    fresh.setId(id);
                                    fresh.setStudent(User.builder().userId(studentId).build());
                                    fresh.setLesson(lesson);
                                    fresh.setCompleted(false);
                                    fresh.setLastWatchedSeconds(0);
                                    return fresh;
                                });

        if (request.lastWatchedSeconds() != null) {
            // Negative positions are meaningless; 0 is the safest floor.
            progress.setLastWatchedSeconds(Math.max(0, request.lastWatchedSeconds()));
        }
        if (request.completed() != null) {
            progress.setCompleted(request.completed() != 0);
        }

        return progressRepository.save(progress);
    }

    @Transactional(readOnly = true)
    public List<LearningProgress> findForStudent(final Long studentId) {
        return progressRepository.findByIdStudentId(studentId);
    }

    @Transactional(readOnly = true)
    public long countCompleted(final Long studentId) {
        return progressRepository.countByIdStudentIdAndCompletedTrue(studentId);
    }
}
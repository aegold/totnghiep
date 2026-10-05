package com.thesis.elearning.modules.course.service;

import com.thesis.elearning.common.exception.AppException;
import com.thesis.elearning.common.exception.ErrorCode;
import com.thesis.elearning.modules.course.dto.CourseOutlineDto.LessonDto;
import com.thesis.elearning.modules.course.dto.CourseOutlineDto.MaterialDto;
import com.thesis.elearning.modules.course.entity.Chapter;
import com.thesis.elearning.modules.course.entity.Course;
import com.thesis.elearning.modules.course.entity.Lesson;
import com.thesis.elearning.modules.course.entity.Material;
import com.thesis.elearning.modules.course.repository.ChapterRepository;
import com.thesis.elearning.modules.course.repository.CourseRepository;
import com.thesis.elearning.modules.course.repository.LessonRepository;
import com.thesis.elearning.modules.course.repository.MaterialRepository;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Chapter and lesson authoring, plus soft deletion. */
@Service
public class LessonService {

    static final String TYPE_VIDEO = "VIDEO";

    private final ChapterRepository chapterRepository;
    private final LessonRepository lessonRepository;
    private final MaterialRepository materialRepository;
    private final CourseRepository courseRepository;

    public LessonService(
            final ChapterRepository chapterRepository,
            final LessonRepository lessonRepository,
            final MaterialRepository materialRepository,
            final CourseRepository courseRepository) {
        this.chapterRepository = chapterRepository;
        this.lessonRepository = lessonRepository;
        this.materialRepository = materialRepository;
        this.courseRepository = courseRepository;
    }

    @Transactional
    public Chapter createChapter(final Long courseId, final String title, final int orderIndex) {
        // The Course proxy comes from CourseRepository: ChapterRepository
        // resolves its own type, so borrowing it here would be a type error.
        final Course course = courseRepository.getReferenceById(courseId);
        return chapterRepository.save(
                Chapter.builder()
                        .course(course)
                        .title(title.trim())
                        .orderIndex(orderIndex)
                        .deleted(false)
                        .build());
    }

    @Transactional
    public LessonDto createLesson(
            final Long chapterId, final String title, final int orderIndex) {
        final Chapter chapter =
                chapterRepository
                        .findById(chapterId)
                        .orElseThrow(
                                () ->
                                        new AppException(
                                                ErrorCode.RESOURCE_NOT_FOUND,
                                                "Không tìm thấy chương"));

        final Lesson lesson =
                lessonRepository.save(
                        Lesson.builder()
                                .chapter(chapter)
                                .title(title.trim())
                                .orderIndex(orderIndex)
                                .deleted(false)
                                .build());

        return toLessonDto(lesson);
    }

    @Transactional
    public MaterialDto createMaterial(
            final Long lessonId,
            final String materialType,
            final String title,
            final String fileUrl,
            final Integer durationSeconds) {

        final Lesson lesson =
                lessonRepository
                        .findById(lessonId)
                        .orElseThrow(
                                () ->
                                        new AppException(
                                                ErrorCode.RESOURCE_NOT_FOUND,
                                                "Không tìm thấy bài học"));

        final Material material =
                materialRepository.save(
                        Material.builder()
                                .lesson(lesson)
                                .materialType(materialType)
                                .title(title == null ? "" : title.trim())
                                .fileUrl(fileUrl)
                                .durationSeconds(durationSeconds)
                                .build());

        return toMaterialDto(material);
    }

    @Transactional(readOnly = true)
    public LessonDto findLesson(final Long lessonId) {
        return toLessonDto(requireLesson(lessonId));
    }

    /**
     * Soft-deletes a lesson.
     *
     * <p>As with courses the row is flagged rather than erased: a learner's
     * {@code learning_progress} and any graded answers stay resolvable for audit.
     */
    @Transactional
    public void softDeleteLesson(final Long lessonId) {
        lessonRepository.delete(requireLesson(lessonId));
    }

    /**
     * Soft-deletes a chapter.
     *
     * <p>Lessons are deliberately NOT cascaded to. The inherited
     * {@code chapters} table has {@code is_deleted} but no {@code deleted_at},
     * and destroying lessons would orphan their materials and progress rows.
     */
    @Transactional
    public void softDeleteChapter(final Long chapterId) {
        chapterRepository.findById(chapterId).ifPresent(chapterRepository::delete);
    }

    private Lesson requireLesson(final Long lessonId) {
        return lessonRepository
                .findById(lessonId)
                .orElseThrow(
                        () ->
                                new AppException(
                                        ErrorCode.RESOURCE_NOT_FOUND, "Không tìm thấy bài học"));
    }

    private static LessonDto toLessonDto(final Lesson lesson) {
        return new LessonDto(
                lesson.getLessonId(),
                lesson.getTitle(),
                lesson.getOrderIndex(),
                List.of());
    }

    /** Splits {@code file_url} into the video/document pair the client expects. */
    static MaterialDto toMaterialDto(final Material material) {
        final boolean isVideo = TYPE_VIDEO.equalsIgnoreCase(material.getMaterialType());
        return new MaterialDto(
                material.getMaterialId(),
                material.getTitle(),
                material.getMaterialType(),
                isVideo ? material.getFileUrl() : null,
                isVideo ? null : material.getFileUrl(),
                material.getDurationSeconds(),
                material.getTotalSlides(),
                material.getTextContent(),
                material.isIndexedForAi());
    }
}
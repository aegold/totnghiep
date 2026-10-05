package com.thesis.elearning.modules.course.service;

import com.thesis.elearning.common.exception.AppException;
import com.thesis.elearning.common.exception.ErrorCode;
import com.thesis.elearning.modules.auth.entity.User;
import com.thesis.elearning.modules.auth.repository.UserRepository;
import com.thesis.elearning.modules.course.dto.CreateCourseRequest;
import com.thesis.elearning.modules.course.dto.CourseOutlineDto;
import com.thesis.elearning.modules.course.dto.CourseOutlineDto.ChapterDto;
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
import com.thesis.elearning.modules.knowledge.entity.Subject;
import com.thesis.elearning.modules.knowledge.repository.SubjectRepository;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Course authoring and outline assembly. */
@Service
public class CourseService {

    private static final String TYPE_VIDEO = "VIDEO";

    private final CourseRepository courseRepository;
    private final ChapterRepository chapterRepository;
    private final LessonRepository lessonRepository;
    private final MaterialRepository materialRepository;
    private final SubjectRepository subjectRepository;
    private final UserRepository userRepository;

    public CourseService(
            final CourseRepository courseRepository,
            final ChapterRepository chapterRepository,
            final LessonRepository lessonRepository,
            final MaterialRepository materialRepository,
            final SubjectRepository subjectRepository,
            final UserRepository userRepository) {
        this.courseRepository = courseRepository;
        this.chapterRepository = chapterRepository;
        this.lessonRepository = lessonRepository;
        this.materialRepository = materialRepository;
        this.subjectRepository = subjectRepository;
        this.userRepository = userRepository;
    }

    @Transactional
    public CourseOutlineDto create(final CreateCourseRequest request, final Long teacherId) {
        final Subject subject =
                subjectRepository
                        .findById(request.subjectId())
                        .orElseThrow(
                                () ->
                                        new AppException(
                                                ErrorCode.RESOURCE_NOT_FOUND,
                                                "Không tìm thấy môn học"));

        final User teacher =
                userRepository
                        .findById(teacherId)
                        .orElseThrow(
                                () ->
                                        new AppException(
                                                ErrorCode.RESOURCE_NOT_FOUND,
                                                "Không tìm thấy giảng viên"));

        final Course course =
                courseRepository.save(
                        Course.builder()
                                .subject(subject)
                                .teacher(teacher)
                                .title(request.title().trim())
                                .description(
                                        request.description() == null
                                                ? null
                                                : request.description().trim())
                                .published(request.published())
                                .deleted(false)
                                .build());

        return outline(course.getCourseId());
    }

    /**
     * Soft-deletes a course.
     *
     * <p>{@code @SQLDelete} turns this into an UPDATE, so chapters, lessons,
     * materials, learning progress and authored questions all stay resolvable.
     * Nothing here touches {@code knowledge_nodes}: the curriculum graph is
     * shared across courses, and cascading into it would silently re-lock every
     * student enrolled in any other course referencing the same nodes.
     */
    @Transactional
    public void softDelete(final Long courseId) {
        final Course course =
                courseRepository
                        .findById(courseId)
                        .orElseThrow(
                                () ->
                                        new AppException(
                                                ErrorCode.RESOURCE_NOT_FOUND,
                                                "Không tìm thấy môn học"));
        courseRepository.delete(course);
    }

    /**
     * Assembles the four-level outline.
     *
     * <p>One query per level rather than a deep join fetch: the hierarchy is
     * small, and separate queries avoid the multiple-bag-fetch problem a single
     * join across chapters, lessons and materials would trigger.
     */
    @Transactional(readOnly = true)
    public CourseOutlineDto outline(final Long courseId) {
        final Course course = requireCourse(courseId);

        final List<ChapterDto> chapters =
                chapterRepository.findByCourseCourseIdOrderByOrderIndexAsc(courseId).stream()
                        .map(this::toChapterDto)
                        .toList();

        return new CourseOutlineDto(
                course.getCourseId(),
                course.getSubject() == null ? null : course.getSubject().getSubjectCode(),
                course.getTitle(),
                // The inherited schema has no term column on courses.
                null,
                course.isPublished(),
                chapters);
    }

    /** Summary list for an author; chapters are omitted to stay lightweight. */
    @Transactional(readOnly = true)
    public List<CourseOutlineDto> findByTeacher(final Long teacherId) {
        return courseRepository.findByTeacherUserIdOrderByTitleAsc(teacherId).stream()
                .map(
                        course ->
                                new CourseOutlineDto(
                                        course.getCourseId(),
                                        course.getSubject() == null
                                                ? null
                                                : course.getSubject().getSubjectCode(),
                                        course.getTitle(),
                                        null,
                                        course.isPublished(),
                                        List.of()))
                .toList();
    }

    private Course requireCourse(final Long courseId) {
        return courseRepository
                .findById(courseId)
                .orElseThrow(
                        () ->
                                new AppException(
                                        ErrorCode.RESOURCE_NOT_FOUND, "Không tìm thấy môn học"));
    }

    private ChapterDto toChapterDto(final Chapter chapter) {
        return new ChapterDto(
                chapter.getChapterId(),
                chapter.getTitle(),
                chapter.getOrderIndex(),
                lessonRepository
                        .findByChapterChapterIdOrderByOrderIndexAsc(chapter.getChapterId())
                        .stream()
                        .map(this::toLessonDto)
                        .toList());
    }

    private LessonDto toLessonDto(final Lesson lesson) {
        return new LessonDto(
                lesson.getLessonId(),
                lesson.getTitle(),
                lesson.getOrderIndex(),
                materialRepository
                        .findByLessonLessonIdOrderByMaterialIdAsc(lesson.getLessonId())
                        .stream()
                        .map(this::toMaterialDto)
                        .toList());
    }

    /** Splits {@code file_url} into the video/document pair the client expects. */
    private MaterialDto toMaterialDto(final Material material) {
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
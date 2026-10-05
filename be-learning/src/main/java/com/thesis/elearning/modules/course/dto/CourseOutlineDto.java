package com.thesis.elearning.modules.course.dto;

import java.util.List;

/**
 * Full course outline: Course -> Chapter -> Lesson -> Material.
 *
 * <h2>Why lessons are kept as their own level</h2>
 * The client's {@code CourseModule} holds materials directly and has no lesson
 * concept, flattening chapters and lessons into one list. That would lose the
 * distinction the database and the authoring UI rely on, so the outline mirrors
 * the real four-level hierarchy. Adapting it to the client's flatter shape is a
 * rendering concern and belongs in the client.
 */
public record CourseOutlineDto(
        Long courseId,
        String code,
        String title,
        String term,
        boolean published,
        List<ChapterDto> chapters) {

    /** One chapter and the lessons it contains. */
    public record ChapterDto(Long chapterId, String title, int orderIndex, List<LessonDto> lessons) {}

    /** One lesson and its materials. */
    public record LessonDto(Long lessonId, String title, int orderIndex, List<MaterialDto> materials) {}

    /**
     * One retrievable material.
     *
     * <p>{@code videoUrl} and {@code documentUrl} are projected from the single
     * {@code file_url} column according to the material type, because the client
     * treats a video as playable and everything else as a document.
     */
    public record MaterialDto(
            Long materialId,
            String title,
            String materialType,
            String videoUrl,
            String documentUrl,
            Integer durationSeconds,
            Integer totalSlides,
            String textContent,
            boolean indexedForAi) {}
}
import { BookOpen, ChevronRight, FileText, Video } from 'lucide-react';
import { useCallback, useMemo, useState, type ReactElement } from 'react';
import { useParams } from 'react-router-dom';

import { ChatSidebar } from '@/features/course-learning/ai-tutor/ChatSidebar';
import { ContentViewer } from '@/features/course-learning/components/ContentViewer';
import { WorkspaceLayout } from '@/features/course-learning/WorkspaceLayout';
import {
  MOCK_COURSE,
  MOCK_PROGRESS,
  MOCK_SUGGESTIONS,
} from '@/features/course-learning/mock/mockCourse';
import { useSSEChat } from '@/hooks/useSSEChat';
import type {
  CourseMaterial,
  MaterialProgress,
  MaterialSelection,
} from '@/features/course-learning/types';
import { cn } from '@/lib/utils';

/** Icon per material type in the outline rail. */
const TYPE_ICON = {
  VIDEO: Video,
  PDF: FileText,
  DOCUMENT: BookOpen,
  SLIDES: FileText,
} as const;

/**
 * The interactive learning workspace.
 *
 * Ties together the outline rail, the `ContentViewer`, and the streaming
 * `ChatSidebar`, all split by the resizable `WorkspaceLayout`.
 */
export function CourseWorkspacePage(): ReactElement {
  const params = useParams<{ courseId: string }>();
  const parsedId: number = Number.parseInt(params['courseId'] ?? '1', 10);

  const course = useMemo(
    () => ({
      ...MOCK_COURSE,
      courseId: Number.isInteger(parsedId) ? parsedId : MOCK_COURSE.courseId,
    }),
    [parsedId],
  );

  const [activeMaterialId, setActiveMaterialId] = useState<number>(101);
  const [progressMap, setProgressMap] = useState<Record<number, MaterialProgress>>(MOCK_PROGRESS);

  const chat = useSSEChat({ courseId: course.courseId });

  const activeMaterial: CourseMaterial | undefined = useMemo(
    () =>
      course.modules
        .flatMap((module) => module.materials)
        .find((material) => material.materialId === activeMaterialId),
    [course, activeMaterialId],
  );

  /** Persists progress; the video player already debounces these calls. */
  const handleProgress = useCallback(
    (update: { lastWatchedSeconds: number; percentComplete: number }): void => {
      setProgressMap((current) => ({
        ...current,
        [activeMaterialId]: {
          materialId: activeMaterialId,
          lastWatchedSeconds: update.lastWatchedSeconds,
          percentComplete: update.percentComplete,
          lastViewedAt: new Date().toISOString(),
        },
      }));
    },
    [activeMaterialId],
  );

  /** A selection in the material immediately seeds a grounded tutor prompt. */
  const handleAskAi = useCallback(
    (selection: MaterialSelection): void => {
      chat.setSelection(selection);
      void chat.send('Hãy giải thích phần văn bản đã đánh dấu này.', selection);
    },
    [chat],
  );

  const outline = (
    <nav className="w-full space-y-4 overflow-y-auto p-4" aria-label="Danh sách nội dung môn học">
      <div>
        <p className="text-[0.7rem] font-semibold uppercase tracking-wide text-slate-400">
          {course.code} · {course.term}
        </p>
        <h1 className="mt-0.5 text-sm font-semibold text-slate-900">{course.title}</h1>
      </div>

      {course.modules.map((module) => (
        <div key={module.moduleId}>
          <p className="mb-1.5 text-xs font-semibold text-slate-500">{module.title}</p>

          <ul className="space-y-1">
            {module.materials.map((material: CourseMaterial) => {
              const Icon = TYPE_ICON[material.materialType];
              const isActive: boolean = material.materialId === activeMaterialId;
              const percent: number = progressMap[material.materialId]?.percentComplete ?? 0;

              return (
                <li key={material.materialId}>
                  <button
                    type="button"
                    onClick={() => setActiveMaterialId(material.materialId)}
                    aria-current={isActive ? 'true' : undefined}
                    className={cn(
                      'flex w-full items-center gap-2 rounded-[--radius-field] px-2 py-1.5 text-left text-xs transition-colors',
                      isActive
                        ? 'bg-brand-50 font-medium text-brand-700'
                        : 'text-slate-600 hover:bg-slate-50',
                    )}
                  >
                    <Icon className="size-3.5 shrink-0" aria-hidden="true" />
                    <span className="min-w-0 flex-1 truncate">{material.title}</span>
                    {percent >= 1 ? (
                      <span className="text-[0.65rem] font-semibold text-emerald-600">xong</span>
                    ) : percent > 0 ? (
                      <span className="text-[0.65rem] tabular-nums text-slate-400">
                        {Math.round(percent * 100)}%
                      </span>
                    ) : null}
                    {isActive && <ChevronRight className="size-3 shrink-0" aria-hidden="true" />}
                  </button>
                </li>
              );
            })}
          </ul>
        </div>
      ))}
    </nav>
  );

  if (activeMaterial === undefined) {
    return (
      <div className="flex h-full items-center justify-center text-sm text-slate-500">
        Hãy chọn một tài liệu để bắt đầu học.
      </div>
    );
  }

  return (
    <WorkspaceLayout
      content={
        <div className="flex h-full min-w-0">
          {/* Outline rail (desktop) */}
          <aside className="hidden w-64 shrink-0 border-r border-slate-200 bg-white lg:block">
            {outline}
          </aside>

          <div className="flex min-w-0 flex-1 flex-col">
            <ContentViewer
              material={activeMaterial}
              progress={progressMap[activeMaterial.materialId] ?? null}
              onProgress={handleProgress}
              onAskAi={handleAskAi}
            />

            {/* Outline collapses below the material on smaller screens. */}
            <details className="shrink-0 border-t border-slate-200 bg-white lg:hidden">
              <summary className="cursor-pointer px-4 py-2 text-xs font-medium text-slate-600">
                Danh sách nội dung
              </summary>
              {outline}
            </details>
          </div>
        </div>
      }
      sidebar={
        <ChatSidebar
          messages={chat.messages}
          streamingContent={chat.streamingContent}
          citations={chat.citations}
          isStreaming={chat.isStreaming}
          error={chat.error}
          selectionContext={chat.activeSelection}
          suggestions={MOCK_SUGGESTIONS}
          onSend={(prompt: string) => void chat.send(prompt)}
          onStop={chat.abort}
          onClear={chat.clear}
          onDismissSelection={() => chat.setSelection(null)}
        />
      }
    />
  );
}

export default CourseWorkspacePage;
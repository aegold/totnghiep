import { BookOpen, FileText, Presentation, Video as VideoIcon } from 'lucide-react';
import { useCallback, useMemo, useRef, useState, type ReactElement } from 'react';

import { TutorMarkdown } from '@/components/markdown/TutorMarkdown';
import { PdfReader } from '@/features/course-learning/components/PdfReader';
import {
  VideoPlayer,
  formatTimecode,
} from '@/features/course-learning/components/VideoPlayer';
import {
  AskAiAffordance,
  useTextSelection,
} from '@/features/course-learning/hooks/useTextSelection';
import type {
  CourseMaterial,
  MaterialProgress,
  MaterialSelection,
} from '@/features/course-learning/types';
import { cn } from '@/lib/utils';
import { MATERIAL_TYPE_LABEL, MATERIAL_TYPE_NOUN } from '@/lib/i18n/labels';

export interface ContentViewerProps {
  material: CourseMaterial;
  /** Learner progress, powering video resume and the completion indicator. */
  progress?: MaterialProgress | null;
  /** Debounced persistence; the parent owns the network call. */
  onProgress?: (update: { lastWatchedSeconds: number; percentComplete: number }) => void;
  /** Raised when the learner clicks "Ask AI Tutor" on a selection. */
  onAskAi: (selection: MaterialSelection) => void;
  className?: string;
}

/** Icon representing each material type, shown in the header. */
const MATERIAL_ICON = {
  VIDEO: VideoIcon,
  PDF: FileText,
  DOCUMENT: BookOpen,
  SLIDES: Presentation,
} as const;

/**
 * Multimedia learning viewport.
 *
 * Dispatches on `material_type`:
 *  - VIDEO    -> custom HTML5 player with resume + debounced progress
 *  - PDF      -> pdf.js canvas with a selectable text layer
 *  - DOCUMENT -> rendered Markdown/HTML text
 *  - SLIDES   -> extracted text, presented as a reading pane
 *
 * All non-video modes share the selection -> "Ask AI Tutor" affordance.
 */
export function ContentViewer({
  material,
  progress,
  onProgress,
  onAskAi,
  className,
}: ContentViewerProps): ReactElement {
  const scrollRef = useRef<HTMLDivElement | null>(null);
  const contentRef = useRef<HTMLDivElement | null>(null);
  const [page, setPage] = useState<number>(1);

  const Icon = MATERIAL_ICON[material.materialType];

  const handleAsk = useCallback(
    (selection: MaterialSelection): void => {
      onAskAi(selection);
    },
    [onAskAi],
  );

  /*
   * Only PDF and slide viewers know their location; a plain document has no
   * paging, so no location is reported for it.
   */
  const resolveLocation = useCallback((): string | null => {
    if (material.materialType === 'PDF') {
      return 'trang';
    }
    if (material.materialType === 'SLIDES') {
      return 'slide';
    }
    return null;
  }, [material.materialType, page]);

  const { popover, dismiss } = useTextSelection({
    materialId: material.materialId,
    materialTitle: material.title,
    containerRef: contentRef,
    resolveLocation,
    onAsk: handleAsk,
  });

  const percent: number = progress?.percentComplete ?? 0;

  const header = useMemo(
    () => (
      <div className="flex shrink-0 items-center gap-3 border-b border-slate-200 bg-white px-4 py-3">
        <div className="flex size-9 shrink-0 items-center justify-center rounded-[--radius-field] bg-brand-50">
          <Icon className="size-4 text-brand-600" aria-hidden="true" />
        </div>

        <div className="min-w-0 flex-1">
          <h2 className="truncate text-sm font-semibold text-slate-900">{material.title}</h2>
          <p className="truncate text-xs text-slate-500">
            {MATERIAL_TYPE_LABEL[material.materialType]}
            {material.durationSeconds !== null && ` · ${formatTimecode(material.durationSeconds)}`}
            {percent > 0 && ` · đã học ${Math.round(percent * 100)}%`}
          </p>
        </div>

        {/* Progress is never colour-only: the numeric label sits alongside. */}
        {percent > 0 && (
          <div className="hidden w-28 shrink-0 sm:block">
            <div
              className="h-1.5 overflow-hidden rounded-full bg-slate-200"
              role="progressbar"
              aria-valuenow={Math.round(percent * 100)}
              aria-valuemin={0}
              aria-valuemax={100}
              aria-label="Tiến độ học tài liệu"
            >
              <div
                className="h-full rounded-full bg-brand-600"
                style={{ width: `${Math.round(percent * 100)}%` }}
              />
            </div>
          </div>
        )}
      </div>
    ),
    [Icon, material, percent],
  );

  const selectionAffordance = (
    <AskAiAffordance popover={popover} onAsk={handleAsk} onDismiss={dismiss} />
  );

  /** Shared empty state for a material whose file is not available yet. */
  const missingSource = (): ReactElement => (
    <div className={cn('flex h-full flex-col', className)}>
      {header}
      <div className="flex flex-1 items-center justify-center p-8 text-center">
        <p className="text-sm text-slate-500">
          {MATERIAL_TYPE_NOUN[material.materialType]} chưa có tệp nguồn.
        </p>
      </div>
    </div>
  );

  /* ---------------------------- Video branch ---------------------------- */
  if (material.materialType === 'VIDEO') {
    if (material.videoUrl === null) {
      return missingSource();
    }

    return (
      <div className={cn('flex h-full flex-col', className)}>
        <div ref={contentRef} className="flex min-h-0 flex-1 flex-col">
          <VideoPlayer
            src={material.videoUrl}
            title={material.title}
            lastWatchedSeconds={progress?.lastWatchedSeconds ?? 0}
            onProgress={(update) =>
              onProgress?.({
                lastWatchedSeconds: update.lastWatchedSeconds,
                percentComplete: update.percentComplete,
              })
            }
            onProgressFlush={(update) =>
              onProgress?.({
                lastWatchedSeconds: update.lastWatchedSeconds,
                percentComplete: update.percentComplete,
              })
            }
          />
        </div>

        {selectionAffordance}
      </div>
    );
  }

  /* ----------------------------- PDF branch ----------------------------- */
  if (material.materialType === 'PDF') {
    if (material.documentUrl === null) {
      return missingSource();
    }

    return (
      <div className={cn('flex h-full flex-col', className)}>
        <div
          ref={scrollRef}
          className="scrollbar-slim min-h-0 flex-1 overflow-y-auto bg-slate-100"
        >
          <div ref={contentRef}>
            <PdfReader
              src={material.documentUrl}
              title={material.title}
              onPageChange={setPage}
              scrollContainerRef={scrollRef}
            />
          </div>
        </div>

        {selectionAffordance}
      </div>
    );
  }

  /* ------------------------- Document / slides -------------------------- */
  return (
    <div className={cn('flex h-full flex-col', className)}>
      {header}

      <div
        ref={scrollRef}
        className="scrollbar-slim min-h-0 flex-1 overflow-y-auto bg-canvas"
      >
        <div ref={contentRef} className="mx-auto max-w-3xl px-6 py-8">
          {material.textContent !== null ? (
            <TutorMarkdown
              content={material.textContent}
              className="text-[0.95rem] leading-relaxed"
            />
          ) : (
            <div className="rounded-[--radius-card] border border-dashed border-slate-300 bg-white px-6 py-14 text-center">
              <p className="text-sm text-slate-500">
                Không thể trích xuất văn bản đọc được từ tài liệu này.
              </p>
            </div>
          )}
        </div>
      </div>

      {selectionAffordance}
    </div>
  );
}
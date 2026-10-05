import * as DialogPrimitive from '@radix-ui/react-dialog';
import { BookOpen, Check, CheckCircle2, Circle, Clock, Lock, X } from 'lucide-react';
import type { ReactElement } from 'react';

import { Badge } from '@/components/ui/badge';
import { Button } from '@/components/ui/button';
import { MASTERY_THRESHOLD } from '@/features/knowledge-graph/types';
import type {
  KnowledgeNodeDetail,
  KnowledgeNodeStatus,
} from '@/features/knowledge-graph/types';
import { humanizeToken } from '@/lib/utils';

export interface NodeDetailDrawerProps {
  /** `null` closes the drawer. */
  detail: KnowledgeNodeDetail | null;
  onOpenChange: (open: boolean) => void;
  onContinueLesson: (detail: KnowledgeNodeDetail) => void;
  onTakeDiagnostic: (detail: KnowledgeNodeDetail) => void;
  /** Used when a prerequisite is clicked, to walk the dependency chain. */
  onSelectNode: (nodeId: string) => void;
}

const STATUS_BADGE: Readonly<Record<KnowledgeNodeStatus, 'success' | 'warning' | 'neutral'>> = {
  MASTERED: 'success',
  IN_PROGRESS: 'warning',
  LOCKED: 'neutral',
};

/** Checkbox-style tick used in the prerequisite checklist. */
function PrereqCheck({ status }: { status: KnowledgeNodeStatus }): ReactElement {
  if (status === 'MASTERED') {
    return (
      <CheckCircle2 className="size-4 shrink-0 text-emerald-500" aria-hidden="true" />
    );
  }
  return <Circle className="size-4 shrink-0 text-slate-300" aria-hidden="true" />;
}

/**
 * Slide-over detail panel for a single skill.
 *
 * Built on the Radix `Dialog` primitives already in the design system (Stage 3)
 * rather than adding a drawer dependency — focus trapping, scroll locking, and
 * Escape handling come for free.
 *
 * ## Why the CTA is disabled rather than hidden when LOCKED
 * Hiding the button makes a locked skill look broken. Showing it disabled with
 * the blocking prerequisite named tells the learner exactly what to do next.
 */
export function NodeDetailDrawer({
  detail,
  onOpenChange,
  onContinueLesson,
  onTakeDiagnostic,
  onSelectNode,
}: NodeDetailDrawerProps): ReactElement {
  const isOpen: boolean = detail !== null;
  const status: KnowledgeNodeStatus = detail?.derivedStatus ?? 'LOCKED';
  const isLocked: boolean = status === 'LOCKED';

  return (
    <DialogPrimitive.Root open={isOpen} onOpenChange={onOpenChange}>
      <DialogPrimitive.Portal>
        <DialogPrimitive.Overlay className="fixed inset-0 z-50 animate-fade-in bg-slate-900/40 backdrop-blur-[1px]" />

        <DialogPrimitive.Content
          className="fixed inset-y-0 right-0 z-50 flex w-full max-w-md animate-slide-in flex-col border-l border-slate-200 bg-white shadow-overlay focus:outline-none"
          aria-describedby={undefined}
        >
          {detail === null ? (
            <div className="flex flex-1 items-center justify-center" />
          ) : (
            <>
              {/* ------------------------------ Header ---------------------------- */}
              <div className="shrink-0 border-b border-slate-200 p-5">
                <div className="flex items-start justify-between gap-3">
                  <div className="min-w-0">
                    <p className="text-[0.65rem] font-semibold uppercase tracking-wide text-slate-400">
                      {detail.topic}
                    </p>
                    <DialogPrimitive.Title className="mt-1 text-lg font-semibold leading-tight text-slate-900">
                      {detail.title}
                    </DialogPrimitive.Title>
                  </div>

                  <DialogPrimitive.Close
                    className="-m-1 shrink-0 rounded-md p-1.5 text-slate-400 transition-colors hover:bg-slate-100 hover:text-slate-700"
                    aria-label="Đóng bảng chi tiết"
                  >
                    <X className="size-4" aria-hidden="true" />
                  </DialogPrimitive.Close>
                </div>

                <div className="mt-3 flex flex-wrap items-center gap-2">
                  <Badge variant={STATUS_BADGE[status]}>{humanizeToken(status)}</Badge>

                  {!isLocked && (
                    <span className="text-xs font-medium tabular-nums text-slate-600">
                      {detail.masteryScore}% thành thạo
                      {status === 'MASTERED' && ` · cần tối thiểu ${MASTERY_THRESHOLD}%`}
                    </span>
                  )}
                </div>
              </div>

              {/* ------------------------------ Body ------------------------------ */}
              <div className="scrollbar-slim min-h-0 flex-1 space-y-6 overflow-y-auto p-5">
                <section>
                  <h3 className="text-xs font-semibold uppercase tracking-wide text-slate-500">
                    Giới thiệu về kỹ năng này
                  </h3>
                  <p className="mt-2 text-sm leading-relaxed text-slate-600">
                    {detail.description}
                  </p>
                </section>

                {/* Blocking reason — only rendered when locked. */}
                {isLocked && detail.blockedBy.length > 0 && (
                  <section className="rounded-[--radius-field] border border-amber-200 bg-amber-50 p-3">
                    <p className="flex items-center gap-1.5 text-xs font-semibold text-amber-900">
                      <Lock className="size-3.5" aria-hidden="true" />
                      Hãy nắm vững trước các kỹ năng sau
                    </p>
                    <ul className="mt-2 space-y-1">
                      {detail.blockedBy.map((title: string) => (
                        <li key={title} className="text-xs text-amber-800">
                          {title}
                        </li>
                      ))}
                    </ul>
                  </section>
                )}

                {/* Prerequisite checklist */}
                <section>
                  <h3 className="text-xs font-semibold uppercase tracking-wide text-slate-500">
                    Tiền kiến ({detail.prerequisiteDetails.length})
                  </h3>

                  {detail.prerequisiteDetails.length === 0 ? (
                    <p className="mt-2 text-sm text-slate-500">
                      Không có tiền kiến — kỹ năng này có thể học ngay từ đầu.
                    </p>
                  ) : (
                    <ul className="mt-2 space-y-1.5">
                      {detail.prerequisiteDetails.map((prerequisite) => (
                        <li key={prerequisite.id}>
                          <button
                            type="button"
                            onClick={() => onSelectNode(prerequisite.id)}
                            className="flex w-full items-center gap-2 rounded-[--radius-field] border border-slate-200 px-2.5 py-2 text-left transition-colors hover:border-brand-300 hover:bg-brand-50"
                          >
                            <PrereqCheck status={prerequisite.status} />
                            <span className="min-w-0 flex-1 truncate text-sm text-slate-700">
                              {prerequisite.title}
                            </span>
                            <span className="shrink-0 text-[0.65rem] font-medium text-slate-400">
                              {humanizeToken(prerequisite.status)}
                            </span>
                          </button>
                        </li>
                      ))}
                    </ul>
                  )}
                </section>

                {/* Associated lessons */}
                <section>
                  <h3 className="text-xs font-semibold uppercase tracking-wide text-slate-500">
                    Bài học ({detail.lessons.length})
                  </h3>

                  {detail.lessons.length === 0 ? (
                    <p className="mt-2 text-sm text-slate-500">
                      Chưa có bài học nào được liên kết với kỹ năng này.
                    </p>
                  ) : (
                    <ul className="mt-2 space-y-1.5">
                      {detail.lessons.map((lesson) => {
                        const isComplete: boolean = lesson.percentComplete >= 1;

                        return (
                          <li
                            key={lesson.lessonId}
                            className="flex items-center gap-2.5 rounded-[--radius-field] border border-slate-200 px-2.5 py-2"
                          >
                            {isComplete ? (
                              <CheckCircle2
                                className="size-4 shrink-0 text-emerald-500"
                                aria-hidden="true"
                              />
                            ) : (
                              <BookOpen
                                className="size-4 shrink-0 text-slate-300"
                                aria-hidden="true"
                              />
                            )}

                            <div className="min-w-0 flex-1">
                              <p className="truncate text-sm text-slate-700">{lesson.title}</p>
                              <p className="text-[0.65rem] text-slate-400">
                                {humanizeToken(lesson.materialType)}
                                {lesson.durationMinutes !== null &&
                                  ` · ${lesson.durationMinutes} phút`}
                                {lesson.percentComplete > 0 &&
                                  !isComplete &&
                                  ` · đã học ${Math.round(lesson.percentComplete * 100)}%`}
                              </p>
                            </div>

                            {isComplete && (
                              <Badge variant="success" className="shrink-0">
                                Xong
                              </Badge>
                            )}
                          </li>
                        );
                      })}
                    </ul>
                  )}
                </section>

                {/* Downstream impact */}
                {detail.unlocks.length > 0 && (
                  <section>
                    <h3 className="text-xs font-semibold uppercase tracking-wide text-slate-500">
                      Mở khoá ({detail.unlocks.length})
                    </h3>
                    <p className="mt-1.5 text-xs text-slate-500">
                      Nắm vững kỹ năng này sẽ mở khoá {detail.unlocks.length} kỹ năng phía sau.
                    </p>
                  </section>
                )}
              </div>

              {/* --------------------------- Footer / CTA -------------------------- */}
              <div className="shrink-0 space-y-2 border-t border-slate-200 p-5">
                {isLocked ? (
                  <>
                    <Button fullWidth size="lg" disabled>
                      <Lock aria-hidden="true" />
                      Chưa mở khoá
                    </Button>
                    <p className="flex items-start gap-1.5 text-xs text-slate-500">
                      <Clock className="mt-0.5 size-3 shrink-0" aria-hidden="true" />
                      Hãy nắm vững các tiền kiến ở trên để mở khoá kỹ năng này.
                    </p>
                  </>
                ) : (
                  <div className="flex gap-2">
                    <Button
                      variant="secondary"
                      fullWidth
                      onClick={() => onContinueLesson(detail)}
                      disabled={detail.lessons.length === 0}
                      title={
                        detail.lessons.length === 0 ? 'Chưa liên kết bài hỏi nào' : undefined
                      }
                    >
                      <BookOpen aria-hidden="true" />
                      Tiếp tục bài học
                    </Button>

                    <Button fullWidth onClick={() => onTakeDiagnostic(detail)}>
                      <Check aria-hidden="true" />
                      Làm bài kiểm tra chẩn đoán
                    </Button>
                  </div>
                )}
              </div>
            </>
          )}
        </DialogPrimitive.Content>
      </DialogPrimitive.Portal>
    </DialogPrimitive.Root>
  );
}
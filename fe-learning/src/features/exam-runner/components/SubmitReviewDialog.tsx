import { AlertTriangle, CheckCircle2, Flag, HelpCircle } from 'lucide-react';
import type { ReactElement } from 'react';

import { Button } from '@/components/ui/button';
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogHeader,
  DialogTitle,
} from '@/components/ui/dialog';
import { cn } from '@/lib/utils';

export interface SubmitReviewDialogProps {
  open: boolean;
  onOpenChange: (open: boolean) => void;
  /** Question numbers (1-based) that carry an answer. */
  answered: readonly number[];
  unanswered: readonly number[];
  flagged: readonly number[];
  isSubmitting: boolean;
  onConfirm: () => void;
  /** True when the exam ended by timeout or a proctoring strike. */
  forced?: boolean;
}

/** Compact, wrapped run of question numbers. */
function NumberList({
  numbers,
  emptyLabel,
  tone,
}: {
  numbers: readonly number[];
  emptyLabel: string;
  tone: string;
}): ReactElement {
  if (numbers.length === 0) {
    return <p className="text-sm text-slate-500">{emptyLabel}</p>;
  }

  return (
    <ul className="flex flex-wrap gap-1.5">
      {numbers.map((number: number) => (
        <li
          key={number}
          className={cn(
            'flex size-7 items-center justify-center rounded-md border text-xs font-medium',
            tone,
          )}
        >
          {number}
        </li>
      ))}
    </ul>
  );
}

/**
 * Non-blocking pre-submission confirmation.
 *
 * Deliberately a dialog the learner must dismiss rather than an instant submit,
 * so a mis-click cannot end an attempt. Shows exactly what will be recorded.
 */
export function SubmitReviewDialog({
  open,
  onOpenChange,
  answered,
  unanswered,
  flagged,
  isSubmitting,
  onConfirm,
  forced = false,
}: SubmitReviewDialogProps): ReactElement {
  const total: number = answered.length + unanswered.length;

  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent
        hideCloseButton={forced}
        onEscapeKeyDown={(event: KeyboardEvent) => {
          /* A forced submission must be acknowledged, not dismissed. */
          if (forced) {
            event.preventDefault();
          }
        }}
        footer={
          <>
            <Button
              variant="secondary"
              onClick={() => onOpenChange(false)}
              disabled={isSubmitting || forced}
            >
              Tiếp tục làm bài
            </Button>
            <Button variant="destructive" onClick={onConfirm} isLoading={isSubmitting}>
              Nộp bài thi
            </Button>
          </>
        }
      >
        <DialogHeader>
          <DialogTitle className="flex items-center gap-2">
            <AlertTriangle className="size-5 text-amber-600" aria-hidden="true" />
            {forced ? 'Đã hết giờ — nộp bài ngay' : 'Bạn có chắc muốn nộp bài thi?'}
          </DialogTitle>
          <DialogDescription>
            {forced
              ? 'Bài thi của bạn đã kết thúc tự động. Hãy xem lại tóm tắt, sau đó xác nhận để hoàn tất nộp bài.'
              : 'Sau khi nộp, bạn không thể quay lại bài thi này. Vui lòng xem lại tóm tắt bên dưới.'}
          </DialogDescription>
        </DialogHeader>

        <div className="space-y-5">
          {/* Readiness counts */}
          <div className="grid grid-cols-3 gap-3">
            <div className="rounded-[--radius-field] bg-emerald-50 p-3 text-center">
              <p className="text-xl font-semibold text-emerald-700">{answered.length}</p>
              <p className="text-xs text-emerald-700">Đã trả lời</p>
            </div>
            <div className="rounded-[--radius-field] bg-slate-100 p-3 text-center">
              <p className="text-xl font-semibold text-slate-700">{unanswered.length}</p>
              <p className="text-xs text-slate-600">Chưa trả lời</p>
            </div>
            <div className="rounded-[--radius-field] bg-amber-50 p-3 text-center">
              <p className="text-xl font-semibold text-amber-700">{flagged.length}</p>
              <p className="text-xs text-amber-700">Đã đánh dấu</p>
            </div>
          </div>

          {unanswered.length > 0 && (
            <div className="flex items-start gap-2.5 rounded-[--radius-field] border border-amber-200 bg-amber-50 p-3">
              <HelpCircle className="mt-0.5 size-4 shrink-0 text-amber-600" aria-hidden="true" />
              <p className="text-sm text-amber-900">
                {unanswered.length} câu sẽ được ghi nhận là trống.
              </p>
            </div>
          )}

          <div className="space-y-3">
            <div>
              <p className="mb-1.5 flex items-center gap-1.5 text-xs font-semibold uppercase tracking-wide text-slate-500">
                <CheckCircle2 className="size-3.5 text-emerald-500" aria-hidden="true" />
                Đã trả lời ({answered.length}/{total})
              </p>
              <NumberList
                numbers={answered}
                emptyLabel="Chưa trả lời câu nào."
                tone="border-emerald-200 bg-emerald-50 text-emerald-700"
              />
            </div>

            <div>
              <p className="mb-1.5 flex items-center gap-1.5 text-xs font-semibold uppercase tracking-wide text-slate-500">
                <HelpCircle className="size-3.5 text-slate-400" aria-hidden="true" />
                Chưa trả lời ({unanswered.length})
              </p>
              <NumberList
                numbers={unanswered}
                emptyLabel="Mọi câu hỏi đều đã có câu trả lời."
                tone="border-slate-200 bg-slate-100 text-slate-600"
              />
            </div>

            <div>
              <p className="mb-1.5 flex items-center gap-1.5 text-xs font-semibold uppercase tracking-wide text-slate-500">
                <Flag className="size-3.5 text-amber-500" aria-hidden="true" />
                Đã đánh dấu xem lại ({flagged.length})
              </p>
              <NumberList
                numbers={flagged}
                emptyLabel="Không có câu hỏi nào được đánh dấu."
                tone="border-amber-200 bg-amber-50 text-amber-700"
              />
            </div>
          </div>
        </div>
      </DialogContent>
    </Dialog>
  );
}
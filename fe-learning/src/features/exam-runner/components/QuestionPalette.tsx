import { Flag } from 'lucide-react';
import type { ReactElement } from 'react';

import { cn } from '@/lib/utils';
import { hasAnswer } from '@/features/question-engine';
import type { AnyQuestionPayload, AnyQuestionResponse } from '@/features/question-engine';

export interface QuestionPaletteProps {
  /** Questions in presentation order. */
  questions: readonly {
    versionId: number;
    payload: AnyQuestionPayload;
  }[];
  answers: ReadonlyMap<number, AnyQuestionResponse>;
  flagged: ReadonlySet<number>;
  currentIndex: number;
  onSelect: (index: number) => void;
  className?: string;
}

/**
 * At-a-glance navigator for the whole paper.
 *
 * Colour encoding (colour is never the ONLY signal — the flag glyph and an
 * accessible label repeat the state, so it works for colour-blind candidates):
 *  - green fill   -> answered
 *  - gray outline -> unanswered
 *  - amber ring   -> flagged for review
 *  - blue ring    -> currently active
 *
 * A flagged AND answered question shows both, since both facts matter.
 */
export function QuestionPalette({
  questions,
  answers,
  flagged,
  currentIndex,
  onSelect,
  className,
}: QuestionPaletteProps): ReactElement {
  const answeredCount: number = questions.filter((question) => {
    const response = answers.get(question.versionId);
    return response !== undefined && hasAnswer(question.payload, response);
  }).length;

  return (
    <nav className={cn('space-y-3', className)} aria-label="Bộ điều hướng câu hỏi">
      <div className="flex items-baseline justify-between">
        <h2 className="text-xs font-semibold uppercase tracking-wide text-slate-500">Câu hỏi</h2>
        <p className="text-xs font-medium text-slate-600" aria-live="polite">
          {answeredCount}/{questions.length}
        </p>
      </div>

      <ol className="grid grid-cols-5 gap-2">
        {questions.map((question, index: number) => {
          const response = answers.get(question.versionId);
          const isAnswered: boolean =
            response !== undefined && hasAnswer(question.payload, response);
          const isFlagged: boolean = flagged.has(question.versionId);
          const isActive: boolean = index === currentIndex;

          const stateLabel: string = [
            `Câu ${index + 1}`,
            isAnswered ? 'đã trả lời' : 'chưa trả lời',
            isFlagged ? 'đã đánh dấu xem lại' : '',
            isActive ? 'câu hỏi hiện tại' : '',
          ]
            .filter(Boolean)
            .join(', ');

          return (
            <li key={question.versionId}>
              <button
                type="button"
                onClick={() => onSelect(index)}
                aria-current={isActive ? 'true' : undefined}
                aria-label={stateLabel}
                className={cn(
                  'relative flex size-10 items-center justify-center rounded-[--radius-field] border text-sm font-semibold transition-all',
                  isAnswered
                    ? 'border-emerald-500 bg-emerald-500 text-white'
                    : 'border-slate-200 bg-white text-slate-600 hover:border-slate-400 hover:bg-slate-50',
                  /* Amber ring wins over the answered fill when both apply. */
                  isFlagged && 'ring-2 ring-amber-400 ring-offset-1',
                  isActive &&
                    'scale-105 border-brand-600 bg-brand-600 text-white ring-2 ring-brand-300 ring-offset-1',
                  isActive && isAnswered && 'bg-brand-600',
                )}
              >
                {index + 1}

                {isFlagged && (
                  <Flag
                    className={cn(
                      'absolute -right-1 -top-1 size-3.5 rounded-full bg-amber-400 p-0.5 text-white',
                      isActive && 'bg-brand-600',
                    )}
                    aria-hidden="true"
                  />
                )}
              </button>
            </li>
          );
        })}
      </ol>

      {/* Legend — text labels, not colour alone. */}
      <ul className="space-y-1.5 text-[0.7rem] text-slate-500">
        <li className="flex items-center gap-2">
          <span className="size-3 rounded border border-emerald-500 bg-emerald-500" aria-hidden="true" />
          Đã trả lời
        </li>
        <li className="flex items-center gap-2">
          <span className="size-3 rounded border border-slate-200 bg-white" aria-hidden="true" />
          Chưa trả lời
        </li>
        <li className="flex items-center gap-2">
          <span className="size-3 rounded border border-slate-200 bg-white ring-1 ring-amber-400" aria-hidden="true" />
          Đã đánh dấu xem lại
        </li>
        <li className="flex items-center gap-2">
          <span className="size-3 rounded border border-brand-600 bg-brand-600" aria-hidden="true" />
          Câu hỏi hiện tại
        </li>
      </ul>
    </nav>
  );
}
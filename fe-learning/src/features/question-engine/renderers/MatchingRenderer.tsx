import { Link2, Unlink } from 'lucide-react';
import { useState, type ReactElement } from 'react';

import { RichText } from '@/features/question-engine/components/RichText';
import type {
  MatchItem,
  MatchingPayload,
  MatchingResponse,
  QuestionProps,
} from '@/features/question-engine/types';
import { cn } from '@/lib/utils';

export type MatchingRendererProps = QuestionProps<MatchingPayload, MatchingResponse>;

/**
 * Renders a `MATCHING` question as two connected columns.
 *
 * Interaction model: select an unpaired item on the left, then click an item on
 * the right to link them. Clicking a paired left item releases it; "Clear all"
 * resets the question.
 *
 * Controlled: the mapping lives in `value.pairs` and each change emits a new
 * object via copy-on-write. `selectedLeftId` is transient UI state (which row
 * is armed), never part of the submitted answer.
 */
export function MatchingRenderer({
  payload,
  value,
  onChange,
  disabled = false,
}: MatchingRendererProps): ReactElement {
  const [selectedLeftId, setSelectedLeftId] = useState<string | null>(null);

  /** Right item lookup, so a left row can display its partner's text. */
  const rightItemById: ReadonlyMap<string, MatchItem> = new Map(
    payload.rightItems.map((item) => [item.id, item]),
  );

  /** Emits an immutable copy of the mapping. */
  const emit = (pairs: Record<string, string>): void => {
    onChange({ pairs: { ...pairs } });
  };

  /** Finds which left row currently owns a given right item. */
  const findOwner = (rightId: string): string | undefined =>
    Object.entries(value.pairs).find(([, mappedRightId]) => mappedRightId === rightId)?.[0];

  const handleSelectLeft = (leftId: string): void => {
    if (disabled) {
      return;
    }
    /* Clicking a paired row releases it and re-arms the row. */
    if (value.pairs[leftId] !== undefined) {
      const next: Record<string, string> = { ...value.pairs };
      delete next[leftId];
      emit(next);
    }
    setSelectedLeftId((current) => (current === leftId ? null : leftId));
  };

  const handleSelectRight = (rightId: string): void => {
    if (disabled || selectedLeftId === null) {
      return;
    }

    /*
     * A right item may serve only one left item. If it is already claimed, the
     * link is transferred and the previous owner is freed — never duplicated.
     */
    const previousOwner: string | undefined = findOwner(rightId);
    const next: Record<string, string> = { ...value.pairs };

    if (previousOwner !== undefined && previousOwner !== selectedLeftId) {
      delete next[previousOwner];
    }
    next[selectedLeftId] = rightId;

    emit(next);
    setSelectedLeftId(null);
  };

  const handleClearAll = (): void => {
    if (disabled) {
      return;
    }
    emit({});
    setSelectedLeftId(null);
  };

  const pairedCount: number = Object.keys(value.pairs).length;
  const isComplete: boolean =
    payload.leftItems.length > 0 && pairedCount === payload.leftItems.length;

  return (
    <div className="space-y-4">
      <RichText content={payload.prompt} />

      {/* Instructions + progress */}
      <div className="flex flex-wrap items-center justify-between gap-2 text-xs">
        <p className="text-slate-500">
          {selectedLeftId === null
            ? 'Chọn một mục ở cột bên trái, sau đó chọn mục tương ứng ở cột bên phải.'
            : 'Bây giờ hãy chọn mục tương ứng ở cột bên phải.'}
        </p>
        <div className="flex items-center gap-3">
          <span className={cn('font-medium', isComplete ? 'text-emerald-600' : 'text-slate-400')}>
            Đã nối {pairedCount}/{payload.leftItems.length} cặp
          </span>
          {pairedCount > 0 && !disabled && (
            <button
              type="button"
              onClick={handleClearAll}
              className="font-medium text-slate-500 underline underline-offset-2 transition-colors hover:text-slate-900"
            >
              Xóa tất cả
            </button>
          )}
        </div>
      </div>

      {/* Two-column board. On narrow screens the columns stack. */}
      <div className="grid gap-3 sm:grid-cols-[1fr_1.75rem_1fr] sm:items-start">
        {/* Left column */}
        <ul className="space-y-2">
          {payload.leftItems.map((item: MatchItem) => {
            const mappedRightId: string | undefined = value.pairs[item.id];
            const mappedRight: MatchItem | undefined =
              mappedRightId !== undefined ? rightItemById.get(mappedRightId) : undefined;
            const isArmed: boolean = selectedLeftId === item.id;

            return (
              <li key={item.id}>
                <button
                  type="button"
                  disabled={disabled}
                  aria-pressed={isArmed}
                  onClick={() => handleSelectLeft(item.id)}
                  className={cn(
                    'flex w-full items-center justify-between gap-2 rounded-[--radius-field] border p-3 text-left transition-all',
                    isArmed
                      ? 'border-brand-500 bg-brand-50 ring-1 ring-brand-500'
                      : mappedRight !== undefined
                        ? 'border-emerald-200 bg-emerald-50/50'
                        : 'border-slate-200 bg-white hover:border-slate-300 hover:bg-slate-50',
                    disabled && 'cursor-not-allowed opacity-60 hover:border-slate-200 hover:bg-white',
                  )}
                >
                  <RichText content={item.text} className="min-w-0" />
                  {mappedRight !== undefined ? (
                    <span className="flex shrink-0 items-center gap-1 text-xs text-emerald-700">
                      <Link2 className="size-3.5" aria-hidden="true" />
                      <span className="max-w-[8rem] truncate">{mappedRight.text}</span>
                    </span>
                  ) : (
                    <span className="shrink-0 text-xs text-slate-400">
                      {isArmed ? 'đã chọn' : ''}
                    </span>
                  )}
                </button>
              </li>
            );
          })}
        </ul>

        {/* Connector gutter — visual link indicator between the columns. */}
        <div aria-hidden="true" className="hidden flex-col gap-2 sm:flex">
          {payload.leftItems.map((item: MatchItem) => (
            <div key={item.id} className="flex h-[3.25rem] items-center">
              {value.pairs[item.id] !== undefined ? (
                <span className="h-px w-full bg-emerald-300" />
              ) : (
                <span className="size-1 rounded-full bg-slate-200" />
              )}
            </div>
          ))}
        </div>

        {/* Right column — inert until a left item is armed. */}
        <ul className="space-y-2">
          {payload.rightItems.map((item: MatchItem) => {
            const owner: string | undefined = findOwner(item.id);
            const isPaired: boolean = owner !== undefined;
            const isClickable: boolean = !disabled && selectedLeftId !== null && !isPaired;

            return (
              <li key={item.id}>
                <button
                  type="button"
                  disabled={disabled || selectedLeftId === null}
                  onClick={() => handleSelectRight(item.id)}
                  aria-label={isPaired ? `${item.text}, đã được nối` : `Nối với ${item.text}`}
                  className={cn(
                    'flex w-full items-center justify-between gap-2 rounded-[--radius-field] border p-3 text-left transition-all',
                    isPaired
                      ? 'border-emerald-200 bg-emerald-50/50'
                      : 'border-slate-200 bg-white',
                    isClickable && 'cursor-pointer hover:border-brand-400 hover:bg-brand-50',
                    (disabled || selectedLeftId === null) && !isPaired && 'cursor-not-allowed opacity-50',
                  )}
                >
                  <RichText content={item.text} className="min-w-0" />
                  {isPaired && <Unlink className="size-3.5 shrink-0 text-emerald-600" aria-hidden="true" />}
                </button>
              </li>
            );
          })}
        </ul>
      </div>

      {payload.hint !== undefined && <p className="text-xs text-slate-500">{payload.hint}</p>}
    </div>
  );
}
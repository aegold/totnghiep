import { Check } from 'lucide-react';
import { useId, type ReactElement } from 'react';

import { RichText } from '@/features/question-engine/components/RichText';
import type {
  MultipleChoicePayload,
  MultipleChoiceResponse,
  QuestionProps,
} from '@/features/question-engine/types';
import { cn } from '@/lib/utils';

export type MultipleChoiceRendererProps = QuestionProps<
  MultipleChoicePayload,
  MultipleChoiceResponse
>;

/**
 * Renders a `MULTIPLE_CHOICE` question.
 *
 * - `allowMultiple: false` -> radio group (single selection)
 * - `allowMultiple: true`  -> checkbox group (independent toggles)
 *
 * Controlled: the selection lives in `value`, and every interaction emits a
 * brand-new `selectedOptionIds` array without touching the previous one.
 */
export function MultipleChoiceRenderer({
  payload,
  value,
  onChange,
  disabled = false,
  idPrefix,
}: MultipleChoiceRendererProps): ReactElement {
  const generatedId = useId();
  const groupName: string = idPrefix ?? `mc-${generatedId}`;

  const selected: ReadonlySet<string> = new Set(value.selectedOptionIds);

  /** Emits an immutable update: a new array, never a push into the old one. */
  const emit = (nextSelectedIds: readonly string[]): void => {
    onChange({ selectedOptionIds: [...nextSelectedIds] });
  };

  const handleSelect = (optionId: string): void => {
    if (disabled) {
      return;
    }

    if (payload.allowMultiple) {
      if (selected.has(optionId)) {
        emit(value.selectedOptionIds.filter((id: string) => id !== optionId));
      } else {
        /* Append preserves the author's option order regardless of click order. */
        const ordered: string[] = payload.options
          .map((option) => option.id)
          .filter((id: string) => id === optionId || selected.has(id));
        emit(ordered);
      }
      return;
    }

    /* Single choice: re-clicking the active option clears it. */
    if (selected.has(optionId)) {
      emit([]);
    } else {
      emit([optionId]);
    }
  };

  /** Roving focus + arrow-key navigation for the single-choice variant. */
  const handleKeyDown = (event: React.KeyboardEvent<HTMLDivElement>): void => {
    if (disabled || payload.allowMultiple || event.key !== 'ArrowDown' && event.key !== 'ArrowUp') {
      return;
    }

    event.preventDefault();
    const currentIndex: number = payload.options.findIndex((option) =>
      selected.has(option.id),
    );
    const step: number = event.key === 'ArrowDown' ? 1 : -1;
    const from: number = currentIndex === -1 ? 0 : currentIndex;
    const nextIndex: number = (from + step + payload.options.length) % payload.options.length;
    const nextOption = payload.options[nextIndex];

    if (nextOption !== undefined) {
      emit([nextOption.id]);
      document.getElementById(`${groupName}-${nextOption.id}`)?.focus();
    }
  };

  return (
    <div className="space-y-4">
      <RichText content={payload.prompt} />

      <div
        role={payload.allowMultiple ? 'group' : 'radiogroup'}
        aria-label={
          payload.allowMultiple
            ? 'Chọn tất cả các phương án đúng'
            : 'Chọn một phương án đúng'
        }
        className="space-y-2.5"
        onKeyDown={handleKeyDown}
      >
        {payload.options.map((option) => {
          const isSelected: boolean = selected.has(option.id);
          const inputId: string = `${groupName}-${option.id}`;

          return (
            <label
              key={option.id}
              htmlFor={inputId}
              className={cn(
                'group flex cursor-pointer items-start gap-3 rounded-[--radius-field] border p-3.5 transition-all',
                isSelected
                  ? 'border-brand-500 bg-brand-50/60 ring-1 ring-brand-500'
                  : 'border-slate-200 bg-white hover:border-slate-300 hover:bg-slate-50',
                disabled && 'cursor-not-allowed opacity-60 hover:border-slate-200 hover:bg-white',
              )}
            >
              <input
                id={inputId}
                type={payload.allowMultiple ? 'checkbox' : 'radio'}
                name={groupName}
                value={option.id}
                checked={isSelected}
                disabled={disabled}
                onChange={() => handleSelect(option.id)}
                className="peer sr-only"
              />

              {/* Custom indicator; the real input stays focusable via `peer`. */}
              <span
                aria-hidden="true"
                className={cn(
                  'mt-0.5 flex size-[1.15rem] shrink-0 items-center justify-center border-2 text-white transition-all',
                  payload.allowMultiple ? 'rounded-[0.3rem]' : 'rounded-full',
                  isSelected
                    ? 'border-brand-600 bg-brand-600'
                    : 'border-slate-300 bg-white group-hover:border-slate-400',
                  'peer-focus-visible:outline peer-focus-visible:outline-2 peer-focus-visible:outline-offset-2 peer-focus-visible:outline-brand-500',
                )}
              >
                {isSelected &&
                  (payload.allowMultiple ? (
                    <Check className="size-3" strokeWidth={3} />
                  ) : (
                    <span className="size-1.5 rounded-full bg-white" />
                  ))}
              </span>

              <span className="min-w-0 flex-1">
                <RichText content={option.text} />
              </span>
            </label>
          );
        })}
      </div>

      {payload.hint !== undefined && (
        <p className="text-xs text-slate-500">{payload.hint}</p>
      )}
    </div>
  );
}
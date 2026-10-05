import { useId, useMemo, type ReactElement, type ReactNode } from 'react';

import { RichText } from '@/features/question-engine/components/RichText';
import type {
  BlankDefinition,
  FillInBlankPayload,
  FillInBlankResponse,
  QuestionProps,
} from '@/features/question-engine/types';
import { cn } from '@/lib/utils';

export type FillInBlankRendererProps = QuestionProps<
  FillInBlankPayload,
  FillInBlankResponse
>;

/**
 * Matches answer slots in a prompt, e.g. `{{capital}}`.
 *
 * The class excludes `}` so a stray brace or nested LaTeX cannot swallow
 * the whole match.
 */
const BLANK_PATTERN = /(\{\{[^}]+\}\})/g;

/** A prompt is split into literal Markdown runs and blank placeholders. */
export type PromptSegment =
  | { kind: 'text'; content: string }
  | { kind: 'blank'; blankId: string };

/**
 * Splits a prompt into renderable segments.
 *
 * Exported as a pure function so the parsing rules can be unit-tested without
 * mounting the component.
 */
export const parsePromptSegments = (prompt: string): PromptSegment[] => {
  const segments: PromptSegment[] = [];
  let lastIndex: number = 0;

  /*
   * `BLANK_PATTERN` is global, and `String.matchAll` throws on a regex whose
   * `lastIndex` is non-zero — so a fresh instance is created per call.
   */
  for (const match of prompt.matchAll(new RegExp(BLANK_PATTERN.source, 'g'))) {
    const token: string = match[0];
    const start: number = match.index;

    if (start > lastIndex) {
      segments.push({ kind: 'text', content: prompt.slice(lastIndex, start) });
    }

    segments.push({ kind: 'blank', blankId: token.slice(2, -2).trim() });
    lastIndex = start + token.length;
  }

  if (lastIndex < prompt.length) {
    segments.push({ kind: 'text', content: prompt.slice(lastIndex) });
  }

  return segments;
};

/**
 * Renders a `FILL_IN_BLANK` question.
 *
 * The prompt is tokenised on `{{blank_id}}`; each token becomes an inline input
 * while surrounding literal text is rendered as Markdown, so a blank can sit
 * mid-sentence or beside a LaTeX expression.
 *
 * Controlled: answers are read from `value.answers` and every keystroke emits a
 * fresh object, leaving the previous response untouched.
 */
export function FillInBlankRenderer({
  payload,
  value,
  onChange,
  disabled = false,
  idPrefix,
}: FillInBlankRendererProps): ReactElement {
  const generatedId = useId();
  const baseId: string = idPrefix ?? `fib-${generatedId}`;

  const segments: PromptSegment[] = useMemo(
    () => parsePromptSegments(payload.prompt),
    [payload.prompt],
  );

  /** Index of blank definitions so a slot can show its hint. */
  const blanksById: ReadonlyMap<string, BlankDefinition> = useMemo(
    () => new Map(payload.blanks.map((blank) => [blank.id, blank])),
    [payload.blanks],
  );

  const embeddedIds: ReadonlySet<string> = useMemo(
    () =>
      new Set(
        segments
          .filter((segment) => segment.kind === 'blank')
          .map((segment) => segment.blankId),
      ),
    [segments],
  );

  /** Blanks referenced in the prompt but absent from `payload.blanks`. */
  const undeclaredIds: string[] = useMemo(
    () => [...embeddedIds].filter((id) => !blanksById.has(id)),
    [embeddedIds, blanksById],
  );

  /** Blanks declared but never embedded — rendered below the prompt. */
  const orphanBlanks: BlankDefinition[] = useMemo(
    () => payload.blanks.filter((blank) => !embeddedIds.has(blank.id)),
    [payload.blanks, embeddedIds],
  );

  const handleChange = (blankId: string, answer: string): void => {
    if (disabled) {
      return;
    }
    /* Copy-on-write: the incoming `value.answers` object is never mutated. */
    onChange({ answers: { ...value.answers, [blankId]: answer } });
  };

  /** Renders a text run, collapsing whitespace so inputs stay aligned. */
  const renderText = (content: string, key: string): ReactNode => {
    if (content.trim().length === 0) {
      return /\s{2,}/.test(content) ? <span key={key}>{' '}</span> : null;
    }

    return (
      <span key={key} className="inline">
        <RichText content={content} className="inline" />
      </span>
    );
  };

  return (
    <div className="space-y-4">
      {/* Prompt, with each {{blank_id}} replaced by an inline input. */}
      <div className="flex flex-wrap items-baseline gap-x-1.5 gap-y-2 text-sm text-slate-700">
        {segments.map((segment: PromptSegment, index: number) => {
          if (segment.kind === 'text') {
            return renderText(segment.content, `text-${index}`);
          }

          const definition: BlankDefinition | undefined = blanksById.get(segment.blankId);
          const isDeclared: boolean = definition !== undefined;
          const inputId: string = `${baseId}-${segment.blankId}`;

          return (
            <span key={`blank-${index}`} className="inline-flex flex-col align-bottom">
              <input
                id={inputId}
                type="text"
                value={value.answers[segment.blankId] ?? ''}
                disabled={disabled}
                placeholder={definition?.hint ?? '…'}
                aria-label={
                  isDeclared
                    ? `Ô trống ${index + 1}${
                        definition?.hint !== undefined ? `: ${definition.hint}` : ''
                      }`
                    : `Ô trống ${index + 1}, mã chưa khai báo "${segment.blankId}"`
                }
                aria-invalid={!isDeclared}
                onChange={(event) => handleChange(segment.blankId, event.target.value)}
                className={cn(
                  'h-8 w-32 rounded-md border bg-white px-2 text-sm text-slate-900 shadow-subtle transition-colors',
                  'placeholder:text-slate-400 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-brand-500/30',
                  'disabled:cursor-not-allowed disabled:bg-slate-50 disabled:text-slate-400',
                  isDeclared
                    ? 'border-slate-300 hover:border-slate-400'
                    : 'border-dashed border-amber-400 bg-amber-50/50',
                )}
              />
              {definition?.hint !== undefined && (
                <span className="mt-0.5 text-[0.65rem] leading-none text-slate-400">
                  {definition.hint}
                </span>
              )}
            </span>
          );
        })}
      </div>

      {/* Authoring diagnostics for malformed payloads. */}
      {undeclaredIds.length > 0 && (
        <p className="text-xs text-amber-700">
          Đề bài tham chiếu ô trống không có trong danh sách:{' '}
          {undeclaredIds.map((id: string) => `{{${id}}}`).join(', ')}
        </p>
      )}

      {/* Blanks declared but never embedded in the prompt. */}
      {orphanBlanks.map((blank: BlankDefinition) => {
        const inputId: string = `${baseId}-${blank.id}`;
        return (
          <div key={blank.id} className="space-y-1.5">
            <label htmlFor={inputId} className="text-sm font-medium text-slate-700">
              {blank.hint ?? `Blank: ${blank.id}`}
            </label>
            <input
              id={inputId}
              type="text"
              value={value.answers[blank.id] ?? ''}
              disabled={disabled}
              placeholder={blank.hint ?? '…'}
              onChange={(event) => handleChange(blank.id, event.target.value)}
              className="h-9 w-full rounded-[--radius-field] border border-slate-300 bg-white px-3 text-sm shadow-subtle transition-colors placeholder:text-slate-400 focus-visible:border-brand-400 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-brand-500/25 disabled:cursor-not-allowed disabled:bg-slate-50"
            />
          </div>
        );
      })}

      {payload.hint !== undefined && <p className="text-xs text-slate-500">{payload.hint}</p>}
    </div>
  );
}
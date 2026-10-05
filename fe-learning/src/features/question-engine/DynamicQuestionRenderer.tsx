import { HelpCircle, type LucideIcon } from 'lucide-react';
import type { ComponentType, ReactElement } from 'react';

import { FillInBlankRenderer } from '@/features/question-engine/renderers/FillInBlankRenderer';
import { MatchingRenderer } from '@/features/question-engine/renderers/MatchingRenderer';
import { MultipleChoiceRenderer } from '@/features/question-engine/renderers/MultipleChoiceRenderer';
import { OrderingRenderer } from '@/features/question-engine/renderers/OrderingRenderer';
import type {
  AnyQuestionPayload,
  AnyQuestionResponse,
  FillInBlankPayload,
  FillInBlankResponse,
  MatchingPayload,
  MatchingResponse,
  MultipleChoicePayload,
  MultipleChoiceResponse,
  OrderingPayload,
  OrderingResponse,
  QuestionProps,
  QuestionType,
  QuestionTypeMap,
} from '@/features/question-engine/types';
import { QuestionType as QT } from '@/features/question-engine/types';
import { cn } from '@/lib/utils';

/**
 * The registry: one entry per `questionType`.
 *
 * Each renderer is stored under its own key with its own payload/response pair,
 * so a lookup is type-safe without casts. Registering a new question type means
 * adding exactly one entry here plus one case in `dispatch` — nothing else in
 * the application needs to change.
 */
interface RendererEntry<T extends QuestionType> {
  Renderer: ComponentType<QuestionProps<
    QuestionTypeMap[T]['payload'],
    QuestionTypeMap[T]['response']
  >>;
  /** Short label for error surfaces and developer tooling. */
  label: string;
  /** Shown by the fallback UI so content authors can see what was expected. */
  icon: LucideIcon;
}

const REGISTRY: { readonly [T in QuestionType]: RendererEntry<T> } = {
  [QT.MULTIPLE_CHOICE]: {
    Renderer: MultipleChoiceRenderer,
    label: 'Trắc nghiệm',
    icon: HelpCircle,
  },
  [QT.FILL_IN_BLANK]: {
    Renderer: FillInBlankRenderer,
    label: 'Điền vào ô trống',
    icon: HelpCircle,
  },
  [QT.MATCHING]: {
    Renderer: MatchingRenderer,
    label: 'Nối cặp tương ứng',
    icon: HelpCircle,
  },
  [QT.ORDERING]: {
    Renderer: OrderingRenderer,
    label: 'Sắp xếp theo thứ tự',
    icon: HelpCircle,
  },
};

/** Whether the engine can render this type. */
export const canRender = (questionType: string): boolean =>
  Object.prototype.hasOwnProperty.call(REGISTRY, questionType);

/**
 * Rendered when `questionType` is unknown — e.g. a question version authored
 * for a build newer than this one.
 *
 * This is deliberately a presentational fallback, not a crash: an unsupported
 * question must never take down an entire exam for the learner.
 */
export function UnsupportedQuestion({
  questionType,
  className,
}: {
  questionType: string;
  className?: string;
}): ReactElement {
  return (
    <div
      role="note"
      className={cn(
        'flex items-start gap-3 rounded-[--radius-field] border border-amber-200 bg-amber-50 p-4',
        className,
      )}
    >
      <HelpCircle className="mt-0.5 size-5 shrink-0 text-amber-600" aria-hidden="true" />
      <div className="min-w-0">
        <p className="text-sm font-medium text-amber-900">Không thể hiển thị câu hỏi</p>
        <p className="mt-1 text-sm text-amber-800">
          Câu hỏi này sử dụng một loại không được hỗ trợ{' '}
          <code className="rounded bg-amber-100 px-1 py-0.5 font-mono text-xs">
            {questionType}
          </code>
          . Bạn có thể bỏ qua và quay lại sau. Vui lòng liên hệ quản trị viên nếu lỗi vẫn tiếp diễn.
        </p>
      </div>
    </div>
  );
}

export interface DynamicQuestionRendererProps {
  questionType: string;
  payload: AnyQuestionPayload;
  value: AnyQuestionResponse;
  onChange: (next: AnyQuestionResponse) => void;
  disabled?: boolean;
  /** Namespaces generated DOM ids when several questions share a page. */
  idPrefix?: string;
  className?: string;
}

/**
 * Resolves a renderer from the registry.
 *
 * Exported separately so tests (and alternative hosts) can exercise dispatch
 * without rendering React.
 */
export function resolveRenderer(questionType: string): ComponentType<
  QuestionProps<AnyQuestionPayload, AnyQuestionResponse>
> | null {
  if (!canRender(questionType)) {
    return null;
  }

  /*
   * Sound narrowing: `canRender` proved the key exists on `REGISTRY`, so every
   * entry is a component accepting its own payload/response pair. The widened
   * signature is only used to look the component up — `dispatch` narrows again
   * before rendering.
   */
  const entry: RendererEntry<QuestionType> | undefined = REGISTRY[
    questionType as QuestionType
  ] as RendererEntry<QuestionType> | undefined;

  return (entry?.Renderer ?? null) as ComponentType<
    QuestionProps<AnyQuestionPayload, AnyQuestionResponse>
  > | null;
}

/**
 * Dispatches to the correct renderer, narrowing the payload/response union to
 * the matched member. This is the single place where a type-specific renderer
 * receives type-specific data.
 */
function dispatch(props: DynamicQuestionRendererProps): ReactElement | null {
  const { questionType, payload, value, onChange, disabled, idPrefix } = props;

  switch (questionType) {
    case QT.MULTIPLE_CHOICE: {
      const typed: MultipleChoiceResponse = value as MultipleChoiceResponse;
      return (
        <MultipleChoiceRenderer
          payload={payload as MultipleChoicePayload}
          value={typed}
          onChange={onChange as (next: MultipleChoiceResponse) => void}
          disabled={disabled}
          idPrefix={idPrefix}
        />
      );
    }

    case QT.FILL_IN_BLANK: {
      const typed: FillInBlankResponse = value as FillInBlankResponse;
      return (
        <FillInBlankRenderer
          payload={payload as FillInBlankPayload}
          value={typed}
          onChange={onChange as (next: FillInBlankResponse) => void}
          disabled={disabled}
          idPrefix={idPrefix}
        />
      );
    }

    case QT.MATCHING: {
      const typed: MatchingResponse = value as MatchingResponse;
      return (
        <MatchingRenderer
          payload={payload as MatchingPayload}
          value={typed}
          onChange={onChange as (next: MatchingResponse) => void}
          disabled={disabled}
        />
      );
    }

    case QT.ORDERING: {
      const typed: OrderingResponse = value as OrderingResponse;
      return (
        <OrderingRenderer
          payload={payload as OrderingPayload}
          value={typed}
          onChange={onChange as (next: OrderingResponse) => void}
          disabled={disabled}
        />
      );
    }

    default:
      return null;
  }
}

/**
 * Renders any supported question from its polymorphic `content_payload`.
 *
 * ## Zero coupling
 * This component depends on nothing but the renderers and the type contracts —
 * no router, no exam layout, no modal, no store. A host supplies `value` and
 * `onChange` and receives back a `student_response` document.
 *
 * Usage:
 * ```tsx
 * const [response, setResponse] = useState(createEmptyResponse(type, payload));
 * <DynamicQuestionRenderer
 *   questionType={type}
 *   payload={payload}
 *   value={response}
 *   onChange={setResponse}
 * />
 * ```
 */
export function DynamicQuestionRenderer(props: DynamicQuestionRendererProps): ReactElement {
  const { questionType, className } = props;

  /* Unknown type -> accessible fallback instead of a blank/broken question. */
  if (!canRender(questionType)) {
    return <UnsupportedQuestion questionType={questionType} className={className} />;
  }

  const rendered: ReactElement | null = dispatch(props);

  /* Defensive: a registry entry must always produce an element. */
  if (rendered === null) {
    return <UnsupportedQuestion questionType={questionType} className={className} />;
  }

  return <div className={className}>{rendered}</div>;
}

export default DynamicQuestionRenderer;
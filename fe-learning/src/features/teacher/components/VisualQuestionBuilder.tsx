import { zodResolver } from '@hookform/resolvers/zod';
import { Plus, Trash2, X } from 'lucide-react';
import { useCallback, useMemo, useState, type ReactElement } from 'react';
import { useFieldArray, useForm, useWatch } from 'react-hook-form';

import { Badge } from '@/components/ui/badge';
import { Button } from '@/components/ui/button';
import { Card } from '@/components/ui/card';
import { Label } from '@/components/ui/input';
import {
  DynamicQuestionRenderer,
  QuestionType,
  createEmptyResponse,
} from '@/features/question-engine';
import {
  formatQuestionPayloads,
  questionFormSchema,
  type QuestionFormValues,
  type QuestionPayloadPair,
} from '@/features/teacher/schemas/questionFormSchema';
import { cn, humanizeToken } from '@/lib/utils';

type BuilderTab = 'author' | 'preview';

/** Per-type blank form values, used when switching the discriminator. */
const DEFAULTS_BY_TYPE: Record<QuestionType, QuestionFormValues> = {
  [QuestionType.MULTIPLE_CHOICE]: {
    questionType: QuestionType.MULTIPLE_CHOICE,
    prompt: '',
    hint: '',
    allowMultiple: false,
    options: [
      { id: 'opt_a', text: '', isCorrect: true, explanation: '' },
      { id: 'opt_b', text: '', isCorrect: false, explanation: '' },
    ],
    gradingNote: '',
  },
  [QuestionType.FILL_IN_BLANK]: {
    questionType: QuestionType.FILL_IN_BLANK,
    prompt: 'Đáp án là {{answer}}.',
    hint: '',
    blanks: [{ id: 'answer', hint: '', acceptedAnswers: [''], pattern: '' }],
    gradingNote: '',
  },
  [QuestionType.MATCHING]: {
    questionType: QuestionType.MATCHING,
    prompt: 'Ghép mỗi mục ở cột bên trái với mục tương ứng ở cột bên phải.',
    hint: '',
    leftItems: [
      { id: 'l1', text: '' },
      { id: 'l2', text: '' },
    ],
    rightItems: [
      { id: 'r1', text: '' },
      { id: 'r2', text: '' },
    ],
    pairs: [{ leftId: '', rightId: '', explanation: '' }],
    explainEachMatch: false,
    gradingNote: '',
  },
  [QuestionType.ORDERING]: {
    questionType: QuestionType.ORDERING,
    prompt: 'Hãy sắp xếp các sự kiện sau theo thứ tự đúng.',
    hint: '',
    items: [
      { id: 'i1', text: '' },
      { id: 'i2', text: '' },
      { id: 'i3', text: '' },
    ],
    correctOrder: ['i1', 'i2', 'i3'],
    gradingNote: '',
  },
};

export interface VisualQuestionBuilderProps {
  initialValues?: QuestionFormValues;
  onSubmit?: (payloads: QuestionPayloadPair) => void;
}

/** Field-level error text, or `null` when the field is valid. */
function FieldError({ message }: { message: string | undefined }): ReactElement | null {
  if (message === undefined) {
    return null;
  }
  return (
    <p role="alert" className="mt-1 text-xs text-danger">
      {message}
    </p>
  );
}

/** Shared input class so every control in the builder looks identical. */
const FIELD_CLASS =
  'h-9 w-full rounded-[--radius-field] border border-slate-300 px-3 text-sm shadow-subtle focus-visible:border-brand-400 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-brand-500/25 disabled:bg-slate-50';

/**
 * Error shape for one field array.
 * `superRefine` reports array-level problems on `message`, item-level problems
 * by numeric index.
 */
/**
 * Errors for one array item. Fields are listed explicitly rather than indexed,
 * because a string index signature would force `message` to share its type.
 * Only the fields the UI actually reads are declared.
 */
type ItemError = {
  message?: string;
  text?: { message?: string };
  id?: { message?: string };
  acceptedAnswers?: { message?: string };
};

/**
 * Error shape for one field array.
 * `superRefine` reports array-level problems on `message`, item-level problems
 * by numeric index.
 */
type ArrayFieldError = { message?: string; [index: number]: ItemError | undefined } | undefined;

/**
 * Errors narrowed to the ACTIVE branch.
 *
 * `formState.errors` is a union across the discriminator, so `branchErrors.options`
 * is not a valid property until the branch is known. Rather than sprinkle
 * `as never` across the whole component, the narrowing happens here — once,
 * with a documented reason — and the UI reads `branchErrors` thereafter.
 */
type BranchErrors = {
  prompt?: { message?: string };
  hint?: { message?: string };
  gradingNote?: { message?: string };
  options?: ArrayFieldError;
  blanks?: ArrayFieldError;
  pairs?: ArrayFieldError;
  leftItems?: ArrayFieldError;
  rightItems?: ArrayFieldError;
  items?: ArrayFieldError;
  correctOrder?: ArrayFieldError;
};

/**
 * Visual question authoring tool.
 *
 * ## Controlled by a discriminated union
 * `useForm` is typed against `questionFormSchema`, so `zodResolver` validates
 * ONLY the active branch. Switching question type calls `reset()` with that
 * branch's defaults — mandatory, because a discriminated union cannot carry
 * stale fields from a previous branch, and keeping them would submit an MCQ
 * payload containing `blanks`.
 *
 * ## Data separation
 * The preview tab renders `content_payload` through the Stage 2 engine, so an
 * instructor sees exactly what a student will see. The private solution is
 * shown in a separate, clearly-badged panel.
 */
export function VisualQuestionBuilder({
  initialValues,
  onSubmit,
}: VisualQuestionBuilderProps): ReactElement {
  const [tab, setTab] = useState<BuilderTab>('author');

  const {
    control,
    register,
    handleSubmit,
    reset,
    watch,
    setValue,
    formState: { errors },
  } = useForm<QuestionFormValues>({
    resolver: zodResolver(questionFormSchema),
    mode: 'onChange',
    defaultValues: initialValues ?? DEFAULTS_BY_TYPE[QuestionType.MULTIPLE_CHOICE],
  });

  const questionType = useWatch({ control, name: 'questionType' });
  const values = watch();

  const options = useFieldArray({ control, name: 'options' });
  const blanks = useFieldArray({ control, name: 'blanks' });
  const leftItems = useFieldArray({ control, name: 'leftItems' });
  const rightItems = useFieldArray({ control, name: 'rightItems' });
  const pairs = useFieldArray({ control, name: 'pairs' });
  const items = useFieldArray({ control, name: 'items' });

  /** See `BranchErrors`: one documented narrowing, reused by every section. */
  const branchErrors = errors as BranchErrors;

  const handleTypeChange = (next: QuestionType): void => {
    /* Discard fields from the previous branch; they are invalid here. */
    reset(DEFAULTS_BY_TYPE[next]);
  };

  /** Serialised pair, or `null` while the form is invalid. */
  const serialised: QuestionPayloadPair | null = useMemo(() => {
    const parsed = questionFormSchema.safeParse(values);
    return parsed.success ? formatQuestionPayloads(parsed.data) : null;
  }, [values]);

  const previewResponse = useMemo(
    () =>
      serialised === null
        ? null
        : createEmptyResponse(questionType as QuestionType, serialised.content_payload as never),
    [serialised, questionType],
  );

  const handleValid = useCallback(
    (parsed: QuestionFormValues): void => {
      onSubmit?.(formatQuestionPayloads(parsed));
    },
    [onSubmit],
  );

  return (
    <div className="space-y-5">
      {/* ------------------------- Type selector ------------------------- */}
      <Card className="p-5">
        <div className="flex flex-wrap items-end gap-4">
          <div className="min-w-[14rem] flex-1">
            <Label htmlFor="questionType">Loại câu hỏi</Label>
            <select
              id="questionType"
              value={questionType}
              onChange={(event) => handleTypeChange(event.target.value as QuestionType)}
              className={cn(FIELD_CLASS, 'mt-1.5')}
            >
              {Object.values(QuestionType).map((type: QuestionType) => (
                <option key={type} value={type}>
                  {humanizeToken(type)}
                </option>
              ))}
            </select>
          </div>

          <div
            className="flex gap-1 rounded-[--radius-field] bg-slate-100 p-1"
            role="tablist"
            aria-label="Chế độ soạn thảo"
          >
            {(['author', 'preview'] as const).map((candidate: BuilderTab) => (
              <button
                key={candidate}
                type="button"
                role="tab"
                aria-selected={tab === candidate}
                onClick={() => setTab(candidate)}
                className={cn(
                  'rounded px-3 py-1.5 text-xs font-medium transition-colors',
                  tab === candidate ? 'bg-white text-slate-900 shadow-subtle' : 'text-slate-500',
                )}
              >
                {candidate === 'author' ? 'Soạn thảo' : 'Xem trước'}
              </button>
            ))}
          </div>
        </div>

        <FieldError message={errors.questionType?.message} />
      </Card>

      {tab === 'preview' ? (
        <PreviewPanel
          serialised={serialised}
          questionType={questionType}
          response={previewResponse}
        />
      ) : (
        <form onSubmit={handleSubmit(handleValid)} className="space-y-5">
          {/* --------------------------- Prompt ---------------------------- */}
          <Card className="p-5">
            <Label htmlFor="prompt">Nội dung câu hỏi</Label>
            <p className="mt-0.5 text-xs text-slate-500">
              Hỗ trợ Markdown và LaTeX. Với câu điền vào ô trống, hãy chèn các ô theo cú pháp{' '}
              <code className="rounded bg-slate-100 px-1 font-mono">{'{{blank_name}}'}</code>.
            </p>
            <textarea
              id="prompt"
              rows={4}
              {...register('prompt')}
              className="mt-2 w-full rounded-[--radius-field] border border-slate-300 px-3 py-2 text-sm shadow-subtle focus-visible:border-brand-400 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-brand-500/25"
              aria-invalid={branchErrors.prompt !== undefined}
            />
            <FieldError message={branchErrors.prompt?.message} />

            <div className="mt-3">
              <Label htmlFor="hint">Gợi ý (không bắt buộc, công khai)</Label>
              <input
                id="hint"
                type="text"
                {...register('hint')}
                className={cn(FIELD_CLASS, 'mt-1.5')}
              />
              <FieldError message={branchErrors.hint?.message} />
            </div>
          </Card>

          {/* ---------------------- Type-specific form --------------------- */}
          {questionType === QuestionType.MULTIPLE_CHOICE && (
            <Card className="space-y-4 p-5">
              <label className="flex items-center gap-2 text-sm font-medium text-slate-700">
                <input
                  type="checkbox"
                  checked={
                    values.questionType === QuestionType.MULTIPLE_CHOICE
                      ? values.allowMultiple
                      : false
                  }
                  onChange={(event) => setValue('allowMultiple', event.target.checked)}
                  className="size-4 rounded border-slate-300 text-brand-600"
                />
                Cho phép nhiều đáp án đúng
              </label>

              {branchErrors.options?.message !== undefined && (
                <p role="alert" className="text-xs text-danger">
                  {branchErrors.options.message}
                </p>
              )}

              <div className="space-y-3">
                {options.fields.map((field, index) => (
                  <div
                    key={field.id}
                    className="rounded-[--radius-field] border border-slate-200 p-3"
                  >
                    <div className="flex items-start gap-2">
                      <input
                        type="checkbox"
                        checked={
                          values.questionType === QuestionType.MULTIPLE_CHOICE
                            ? (values.options[index]?.isCorrect ?? false)
                            : false
                        }
                        onChange={(event) =>
                          setValue(`options.${index}.isCorrect`, event.target.checked, {
                            shouldValidate: true,
                          })
                        }
                        className="mt-2 size-4 shrink-0 rounded border-slate-300 text-emerald-600"
                        aria-label={`Đánh dấu phương án ${index + 1} là đúng`}
                      />

                      <div className="min-w-0 flex-1 space-y-2">
                        <input
                          type="text"
                          placeholder={`Nội dung phương án ${index + 1}`}
                          {...register(`options.${index}.text` as const)}
                          className={FIELD_CLASS}
                        />
                        <FieldError
                          message={branchErrors.options?.[index]?.text?.message as string | undefined}
                        />

                        {/* Amber tint marks this as private authoring data. */}
                        <input
                          type="text"
                          placeholder="Phần giải thích riêng (không hiển thị cho sinh viên)"
                          {...register(`options.${index}.explanation` as const)}
                          className={cn(FIELD_CLASS, 'bg-amber-50/50 text-xs')}
                        />
                      </div>

                      <button
                        type="button"
                        onClick={() => options.remove(index)}
                        disabled={options.fields.length <= 2}
                        className="mt-1 rounded-md p-1.5 text-slate-400 hover:bg-slate-100 hover:text-danger disabled:opacity-30"
                        aria-label={`Xoá phương án ${index + 1}`}
                      >
                        <Trash2 className="size-4" aria-hidden="true" />
                      </button>
                    </div>
                  </div>
                ))}
              </div>

              <Button
                type="button"
                variant="secondary"
                size="sm"
                onClick={() =>
                  options.append({
                    id: `opt_${String.fromCharCode(97 + options.fields.length)}`,
                    text: '',
                    isCorrect: false,
                    explanation: '',
                  })
                }
              >
                <Plus aria-hidden="true" />
                Thêm phương án
              </Button>
            </Card>
          )}

          {questionType === QuestionType.FILL_IN_BLANK && (
            <Card className="space-y-3 p-5">
              <div className="flex items-center justify-between">
                <Label>Các ô trống</Label>
                <Button
                  type="button"
                  variant="secondary"
                  size="sm"
                  onClick={() =>
                    blanks.append({ id: '', hint: '', acceptedAnswers: [''], pattern: '' })
                  }
                >
                  <Plus aria-hidden="true" />
                  Thêm ô trống
                </Button>
              </div>

              {branchErrors.blanks?.message !== undefined && (
                <p role="alert" className="text-xs text-danger">
                  {branchErrors.blanks.message}
                </p>
              )}

              {blanks.fields.map((field, index) => (
                <div key={field.id} className="rounded-[--radius-field] border border-slate-200 p-3">
                  <div className="flex items-center gap-2">
                    <input
                      type="text"
                      placeholder="blank_name"
                      {...register(`blanks.${index}.id` as const)}
                      className={cn(FIELD_CLASS, 'font-mono')}
                    />
                    <button
                      type="button"
                      onClick={() => blanks.remove(index)}
                      disabled={blanks.fields.length <= 1}
                      className="rounded-md p-1.5 text-slate-400 hover:bg-slate-100 hover:text-danger disabled:opacity-30"
                      aria-label={`Xoá ô trống ${index + 1}`}
                    >
                      <Trash2 className="size-4" aria-hidden="true" />
                    </button>
                  </div>
                  <FieldError message={branchErrors.blanks?.[index]?.id?.message as string | undefined} />

                  {/* Private: the answer key. Amber tint signals this. */}
                  <label className="mt-2 block text-xs font-medium text-slate-600">
                    Dáp án được chấp nhận (riêng tư)
                  </label>
                  <input
                    type="text"
                    placeholder="8.314, 8.31"
                    {...register(`blanks.${index}.acceptedAnswers.0` as const)}
                    className={cn(FIELD_CLASS, 'mt-1 bg-amber-50/50 text-xs')}
                  />
                  <FieldError
                    message={branchErrors.blanks?.[index]?.acceptedAnswers?.message as string | undefined}
                  />

                  <label className="mt-2 block text-xs font-medium text-slate-600">
                    Biểu thức quy chuẩn (không bắt buộc, riêng tư)
                  </label>
                  <input
                    type="text"
                    placeholder="^8\.\d+$"
                    {...register(`blanks.${index}.pattern` as const)}
                    className={cn(FIELD_CLASS, 'mt-1 bg-amber-50/50 font-mono text-xs')}
                  />
                </div>
              ))}
            </Card>
          )}

          {questionType === QuestionType.MATCHING && (
            <Card className="space-y-4 p-5">
              <div className="grid gap-4 sm:grid-cols-2">
                <div>
                  <Label>Các mục bên trái (công khai)</Label>
                  {leftItems.fields.map((field, index) => (
                    <div key={field.id} className="mt-1.5 flex gap-1.5">
                      <input
                        type="text"
                        placeholder={`Left ${index + 1}`}
                        {...register(`leftItems.${index}.text` as const)}
                        className={FIELD_CLASS}
                      />
                      <button
                        type="button"
                        onClick={() => leftItems.remove(index)}
                        disabled={leftItems.fields.length <= 2}
                        className="rounded-md px-2 text-slate-400 hover:text-danger disabled:opacity-30"
                        aria-label={`Remove left item ${index + 1}`}
                      >
                        <X className="size-4" aria-hidden="true" />
                      </button>
                    </div>
                  ))}
                  <Button
                    type="button"
                    variant="ghost"
                    size="sm"
                    className="mt-1.5"
                    onClick={() =>
                      leftItems.append({ id: `l${leftItems.fields.length + 1}`, text: '' })
                    }
                  >
                    <Plus aria-hidden="true" />
                    Add left
                  </Button>
                </div>

                <div>
                  <Label>Các mục bên phải (công khai)</Label>
                  {rightItems.fields.map((field, index) => (
                    <div key={field.id} className="mt-1.5 flex gap-1.5">
                      <input
                        type="text"
                        placeholder={`Right ${index + 1}`}
                        {...register(`rightItems.${index}.text` as const)}
                        className={FIELD_CLASS}
                      />
                      <button
                        type="button"
                        onClick={() => rightItems.remove(index)}
                        disabled={rightItems.fields.length <= 2}
                        className="rounded-md px-2 text-slate-400 hover:text-danger disabled:opacity-30"
                        aria-label={`Remove right item ${index + 1}`}
                      >
                        <X className="size-4" aria-hidden="true" />
                      </button>
                    </div>
                  ))}
                  <Button
                    type="button"
                    variant="ghost"
                    size="sm"
                    className="mt-1.5"
                    onClick={() =>
                      rightItems.append({ id: `r${rightItems.fields.length + 1}`, text: '' })
                    }
                  >
                    <Plus aria-hidden="true" />
                    Add right
                  </Button>
                </div>
              </div>

              {/* Private: the correct pairing. */}
              <div>
                <Label>Các cặp nối đúng (riêng tư)</Label>
                {branchErrors.pairs?.message !== undefined && (
                  <p role="alert" className="mt-1 text-xs text-danger">
                    {branchErrors.pairs.message}
                  </p>
                )}

                {pairs.fields.map((field, index) => (
                  <div
                    key={field.id}
                    className="mt-2 grid items-center gap-2 rounded-[--radius-field] border border-amber-200 bg-amber-50/40 p-2 sm:grid-cols-[1fr_auto_1fr_auto]"
                  >
                    <select
                      {...register(`pairs.${index}.leftId` as const)}
                      className={FIELD_CLASS}
                      aria-label={`Mục bên trái của cặp ${index + 1}`}
                    >
                      <option value="">Chọn mục bên trái…</option>
                      {(values.questionType === QuestionType.MATCHING ? values.leftItems : []).map(
                        (item) => (
                          <option key={item.id} value={item.id}>
                            {item.text || item.id}
                          </option>
                        ),
                      )}
                    </select>

                    <span className="hidden text-slate-400 sm:block" aria-hidden="true">
                      →
                    </span>

                    <select
                      {...register(`pairs.${index}.rightId` as const)}
                      className={FIELD_CLASS}
                      aria-label={`Mục bên phải của cặp ${index + 1}`}
                    >
                      <option value="">Chọn mục bên phải…</option>
                      {(values.questionType === QuestionType.MATCHING ? values.rightItems : []).map(
                        (item) => (
                          <option key={item.id} value={item.id}>
                            {item.text || item.id}
                          </option>
                        ),
                      )}
                    </select>

                    <button
                      type="button"
                      onClick={() => pairs.remove(index)}
                      className="rounded-md p-1.5 text-slate-400 hover:text-danger"
                      aria-label={`Xoá cặp ${index + 1}`}
                    >
                      <X className="size-4" aria-hidden="true" />
                    </button>
                  </div>
                ))}

                <Button
                  type="button"
                  variant="secondary"
                  size="sm"
                  className="mt-2"
                  onClick={() => pairs.append({ leftId: '', rightId: '', explanation: '' })}
                >
                  <Plus aria-hidden="true" />
                  Thêm cặp
                </Button>
              </div>
            </Card>
          )}

          {questionType === QuestionType.ORDERING && (
            <Card className="space-y-3 p-5">
              <Label>Các mục (công khai, hiển thị theo thứ tự xáo trộn)</Label>

              {items.fields.map((field, index) => (
                <div key={field.id} className="flex gap-1.5">
                  <input
                    type="text"
                    placeholder={`Mục ${index + 1}`}
                    {...register(`items.${index}.text` as const)}
                    className={FIELD_CLASS}
                  />
                  <button
                    type="button"
                    onClick={() => items.remove(index)}
                    disabled={items.fields.length <= 2}
                    className="rounded-md px-2 text-slate-400 hover:text-danger disabled:opacity-30"
                    aria-label={`Xoá mục ${index + 1}`}
                  >
                    <X className="size-4" aria-hidden="true" />
                  </button>
                </div>
              ))}

              <Button
                type="button"
                variant="ghost"
                size="sm"
                onClick={() => items.append({ id: `i${items.fields.length + 1}`, text: '' })}
              >
                <Plus aria-hidden="true" />
                Thêm mục
              </Button>

              {branchErrors.correctOrder?.message !== undefined && (
                <p role="alert" className="mt-2 text-xs text-danger">
                  {branchErrors.correctOrder.message}
                </p>
              )}

              {/* Private: the authored sequence. */}
              <div className="rounded-[--radius-field] border border-amber-200 bg-amber-50/40 p-3">
                <Label>Thứ tự đúng (riêng tư)</Label>
                <ol className="mt-2 space-y-1.5">
                  {(values.questionType === QuestionType.ORDERING ? values.correctOrder : []).map(
                    (_itemId, index) => (
                      <li key={`order-${index}`} className="flex items-center gap-2">
                        <span className="flex size-6 shrink-0 items-center justify-center rounded bg-slate-200 text-xs font-semibold text-slate-600">
                          {index + 1}
                        </span>
                        <input
                          type="text"
                          {...register(`correctOrder.${index}` as const)}
                          className={cn(FIELD_CLASS, 'bg-white')}
                          aria-label={`Mục ở vị trí ${index + 1}`}
                        />
                      </li>
                    ),
                  )}
                </ol>
              </div>
            </Card>
          )}

          {/* ----------------------- Private grading note -------------------- */}
          <Card className="border-amber-200 bg-amber-50/40 p-5">
            <Label htmlFor="gradingNote">
              Grading note (private, never sent to students)
            </Label>
            <textarea
              id="gradingNote"
              rows={2}
              {...register('gradingNote')}
              className="mt-1.5 w-full rounded-[--radius-field] border border-amber-200 bg-white px-3 py-2 text-sm shadow-subtle focus-visible:border-amber-400 focus-visible:outline-none"
            />
          </Card>

          <div className="flex items-center justify-end gap-2">
            <Badge variant={serialised === null ? 'warning' : 'success'}>
              {serialised === null ? 'Chưa hoàn tất' : 'Sẵn sàng xuất bản'}
            </Badge>
            <Button type="submit" disabled={serialised === null}>
              Lưu câu hỏi
            </Button>
          </div>
        </form>
      )}
    </div>
  );
}

/* -------------------------------------------------------------------------- */
/*  Preview                                                                     */
/* -------------------------------------------------------------------------- */

interface PreviewPanelProps {
  serialised: QuestionPayloadPair | null;
  questionType: QuestionType;
  response: unknown;
}

/**
 * Live student-facing preview.
 *
 * Renders ONLY `content_payload` through the Stage 2 engine, which is the
 * whole point: an instructor sees precisely what ships to a student, with no
 * route by which the answer key could appear. Inputs are disabled so the
 * preview never mutates form state.
 */
function PreviewPanel({
  serialised,
  questionType,
  response,
}: PreviewPanelProps): ReactElement {
  if (serialised === null || response === null) {
    return (
      <Card className="p-8 text-center">
        <p className="text-sm text-slate-500">
          Hãy hoàn tất các trường bắt buộc để xem bản xem trước trực tiếp.
        </p>
      </Card>
    );
  }

  return (
    <div className="space-y-5">
      <Card className="p-5">
        <div className="mb-4 flex items-center gap-2">
          <Badge variant="default">Góc nhìn của sinh viên</Badge>
          <p className="text-xs text-slate-500">
            Chỉ được hiển thị từ <code className="rounded bg-slate-100 px-1 font-mono">content_payload</code>{' '}
            — đáp án không bao giờ được gửi tới thành phần này.
          </p>
        </div>

        <div className="pointer-events-none select-none">
          <DynamicQuestionRenderer
            questionType={questionType}
            payload={serialised.content_payload}
            value={response as never}
            onChange={() => undefined}
            disabled
            idPrefix="author-preview"
          />
        </div>
      </Card>

      {/* Instructor-only: the private document, clearly badged. */}
      <Card className="border-amber-200 bg-amber-50/40 p-5">
        <div className="mb-3 flex items-center gap-2">
          <Badge variant="warning">Riêng tư — không hiển thị cho sinh viên</Badge>
        </div>
        <p className="mb-2 text-xs font-medium text-amber-900">solution_payload</p>
        <pre className="scrollbar-slim overflow-x-auto rounded-[--radius-field] bg-white p-3 font-mono text-[11px] text-slate-700">
          {JSON.stringify(serialised.solution_payload, null, 2)}
        </pre>
      </Card>

      {/* The exact bytes that will be POSTed. */}
      <Card className="p-5">
        <p className="mb-2 text-xs font-medium text-slate-600">
          content_payload (nội dung gửi đi)
        </p>
        <pre className="scrollbar-slim overflow-x-auto rounded-[--radius-field] bg-slate-50 p-3 font-mono text-[11px] text-slate-700">
          {JSON.stringify(serialised.content_payload, null, 2)}
        </pre>
      </Card>
    </div>
  );
}
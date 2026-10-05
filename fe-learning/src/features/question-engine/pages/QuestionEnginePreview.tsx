import { useCallback, useState, type ReactElement } from 'react';

import { Badge } from '@/components/ui/badge';
import { Button } from '@/components/ui/button';
import { Card, CardContent } from '@/components/ui/card';
import DynamicQuestionRenderer from '@/features/question-engine/DynamicQuestionRenderer';
import { createEmptyResponse } from '@/features/question-engine/factories';
import {
  MOCK_QUESTION_ENTRIES,
  UNSUPPORTED_QUESTION_PAYLOAD,
} from '@/features/question-engine/mockQuestions';
import type {
  AnyQuestionPayload,
  AnyQuestionResponse,
  QuestionType,
} from '@/features/question-engine/types';
import { cn } from '@/lib/utils';

interface PreviewEntry {
  id: number;
  title: string;
  questionType: QuestionType;
  payload: AnyQuestionPayload;
}

/** Adds the deliberately-unsupported fixture to the mock list. */
const PREVIEW_ENTRIES: readonly PreviewEntry[] = [
  ...MOCK_QUESTION_ENTRIES,
  {
    id: 999,
    title: 'Loại không hỗ trợ (xem trước phương án dự phòng)',
    /*
     * `as unknown` first: the fixture intentionally does NOT satisfy any
     * renderer contract, which is precisely what this entry demonstrates.
     */
    questionType: 'ESSAY_WITH_RUBRIC' as QuestionType,
    payload: UNSUPPORTED_QUESTION_PAYLOAD as unknown as AnyQuestionPayload,
  },
];

/**
 * Developer preview for the Question Engine.
 *
 * Demonstrates that the engine is fully self-contained: this page owns the
 * response state and the engine only ever sees `{ payload, value, onChange }`.
 * The same component would work identically inside an exam runner or a modal.
 */
export function QuestionEnginePreview(): ReactElement {
  const [responses, setResponses] = useState<Record<number, AnyQuestionResponse>>({});
  const [disabled, setDisabled] = useState<boolean>(false);

  const handleChange = useCallback(
    (id: number) => (next: AnyQuestionResponse) => {
      /* Replace one entry with a NEW object; never mutate the previous state. */
      setResponses((current) => ({ ...current, [id]: next }));
    },
    [],
  );

  const reset = useCallback((): void => setResponses({}), []);

  const resetOne = useCallback((entry: PreviewEntry): void => {
    setResponses((current) => {
      const next = { ...current };
      delete next[entry.id];
      return next;
    });
  }, []);

  return (
    <div className="space-y-6">
      <div className="flex flex-wrap items-center justify-between gap-3">
        <div>
          <h2 className="text-lg font-semibold tracking-tight text-slate-900">
            Xem trước Trình soạn câu hỏi
          </h2>
          <p className="mt-0.5 text-sm text-slate-500">
            Mọi bộ hiển thị đều được điều khiển — trạng thái đáp án nằm ở trang này, không nằm trong
            engine.
          </p>
        </div>

        <div className="flex items-center gap-2">
          <Button
            variant="secondary"
            size="sm"
            onClick={() => setDisabled((current) => !current)}
            aria-pressed={disabled}
          >
            {disabled ? 'Bật nhập liệu' : 'Tắt nhập liệu'}
          </Button>
          <Button variant="ghost" size="sm" onClick={reset}>
            Đặt lại tất cả
          </Button>
        </div>
      </div>

      {PREVIEW_ENTRIES.map((entry: PreviewEntry) => {
        const response: AnyQuestionResponse =
          responses[entry.id] ?? createEmptyResponse(entry.questionType, entry.payload);

        return (
          <Card key={entry.id}>
            <CardContent className="space-y-4 pt-5">
              <div className="flex flex-wrap items-start justify-between gap-2">
                <div className="min-w-0">
                  <p className="text-sm font-semibold text-slate-900">{entry.title}</p>
                  <div className="mt-1 flex flex-wrap items-center gap-2">
                    <Badge variant="neutral" className="font-mono text-[10px]">
                      {entry.questionType}
                    </Badge>
                    <span className="text-[11px] text-slate-400">
                      #{entry.id}
                    </span>
                  </div>
                </div>
                <Button variant="ghost" size="sm" onClick={() => resetOne(entry)}>
                  Đặt lại
                </Button>
              </div>

              <DynamicQuestionRenderer
                questionType={entry.questionType}
                payload={entry.payload}
                value={response}
                onChange={handleChange(entry.id)}
                disabled={disabled}
                idPrefix={`preview-${entry.id}`}
              />

              {/* Live view of the JSON that would be written to `student_response`. */}
              <details className="group rounded-[--radius-field] bg-slate-50 p-2.5">
                <summary className="cursor-pointer text-xs font-medium text-slate-500 select-none">
                  <span className="group-open:hidden">Hiện</span>
                  <span className="hidden group-open:inline">Ẩn</span>{' '}
                  <code className="rounded bg-slate-200 px-1 py-0.5 text-[10px]">
                    student_response
                  </code>
                </summary>
                <pre
                  className={cn(
                    'scrollbar-slim mt-2 overflow-x-auto whitespace-pre-wrap break-all',
                    'rounded bg-white p-2.5 font-mono text-[11px] text-slate-700',
                  )}
                >
                  {JSON.stringify(response, null, 2)}
                </pre>
              </details>
            </CardContent>
          </Card>
        );
      })}
    </div>
  );
}

export default QuestionEnginePreview;
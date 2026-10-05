import { ArrowDown, Eraser, Send, Sparkles, Square, X } from 'lucide-react';
import {
  useCallback,
  useLayoutEffect,
  useRef,
  useState,
  type FormEvent,
  type ReactElement,
} from 'react';

import { ChatMessageItem } from '@/features/course-learning/ai-tutor/ChatMessageItem';
import type {
  ChatMessage,
  MaterialSelection,
  TutorCitation,
  TutorSuggestion,
} from '@/features/course-learning/types';
import { cn } from '@/lib/utils';

export interface ChatSidebarProps {
  messages: readonly ChatMessage[];
  /** Live tokens for the in-flight assistant turn; not yet in `messages`. */
  streamingContent: string;
  /** Citations for the in-flight turn. */
  citations: readonly TutorCitation[];
  isStreaming: boolean;
  error: string | null;
  /** Selection currently grounding the next prompt. */
  selectionContext: MaterialSelection | null;
  suggestions: readonly TutorSuggestion[];
  onSend: (prompt: string) => void;
  onStop: () => void;
  onClear: () => void;
  onDismissSelection: () => void;
  onClose?: () => void;
}

/** Starter prompts shown before the learner has asked anything. */
const QUICK_PROMPTS: readonly string[] = [
  'Tóm tắt ý chính của tài liệu này',
  'Giải thích lại theo cách dễ hiểu hơn',
  'Tôi nên tập trung vào nội dung nào cho kỳ thi?',
] as const;

/** Distance from the bottom, in px, still treated as "at the bottom". */
const STICKY_THRESHOLD_PX: number = 80;

/** Placeholder turn rendered while tokens stream in. */
const streamingPlaceholderId = 'streaming-placeholder';

export function ChatSidebar({
  messages,
  streamingContent,
  citations,
  isStreaming,
  error,
  selectionContext,
  suggestions,
  onSend,
  onStop,
  onClear,
  onDismissSelection,
  onClose,
}: ChatSidebarProps): ReactElement {
  const [draft, setDraft] = useState<string>('');
  const scrollRef = useRef<HTMLDivElement | null>(null);
  /**
   * Whether auto-scroll is engaged. Released when the learner scrolls up to
   * read earlier turns, so a stream of tokens does not yank them back down.
   */
  const isPinnedRef = useRef<boolean>(true);

  const lastAssistantId: string | undefined = [...messages]
    .reverse()
    .find((message: ChatMessage) => message.role === 'assistant')?.id;

  /** The user turn preceding the latest answer, used to offer a retry. */
  const retryPrompt: string | undefined = (() => {
    if (messages.length < 2) {
      return undefined;
    }
    const previous = messages[messages.length - 2];
    return previous !== undefined && previous.role === 'user' ? previous.content : undefined;
  })();

  /* Scroll on every token batch, but only while the learner is at the bottom. */
  useLayoutEffect(() => {
    const node = scrollRef.current;
    if (node === null || !isPinnedRef.current) {
      return;
    }
    node.scrollTop = node.scrollHeight;
  }, [streamingContent, messages]);

  const handleScroll = useCallback((): void => {
    const node = scrollRef.current;
    if (node === null) {
      return;
    }
    isPinnedRef.current =
      node.scrollHeight - node.scrollTop - node.clientHeight <= STICKY_THRESHOLD_PX;
  }, []);

  const scrollToLatest = useCallback((): void => {
    const node = scrollRef.current;
    if (node === null) {
      return;
    }
    isPinnedRef.current = true;
    node.scrollTo({ top: node.scrollHeight, behavior: 'smooth' });
  }, []);

  const submit = useCallback(
    (text: string): void => {
      const trimmed: string = text.trim();
      if (trimmed.length === 0 || isStreaming) {
        return;
      }
      setDraft('');
      /* Re-pin so the incoming turn is visible straight away. */
      isPinnedRef.current = true;
      onSend(trimmed);
    },
    [isStreaming, onSend],
  );

  const handleSubmit = (event: FormEvent<HTMLFormElement>): void => {
    event.preventDefault();
    submit(draft);
  };

  const isPinned: boolean = isPinnedRef.current;
  const showScrollButton: boolean = !isPinned;

  return (
    <div className="flex h-full flex-col bg-white">
      {/* Header */}
      <header className="flex shrink-0 items-center gap-2 border-b border-slate-200 px-4 py-3">
        <Sparkles className="size-4 shrink-0 text-brand-600" aria-hidden="true" />
        <div className="min-w-0 flex-1">
          <h2 className="text-sm font-semibold text-slate-900">Trợ lý ôn tập AI</h2>
          <p className="truncate text-[0.7rem] text-slate-500">
            Câu trả lời dựa trên tài liệu môn học
          </p>
        </div>

        {messages.length > 0 && (
          <button
            type="button"
            onClick={onClear}
            className="rounded-md p-1.5 text-slate-400 transition-colors hover:bg-slate-100 hover:text-slate-700"
            aria-label="Xoá cuộc trò chuyện"
            title="Xoá cuộc trò chuyện"
          >
            <Eraser className="size-4" aria-hidden="true" />
          </button>
        )}

        {onClose !== undefined && (
          <button
            type="button"
            onClick={onClose}
            className="rounded-md p-1.5 text-slate-400 transition-colors hover:bg-slate-100 hover:text-slate-700"
            aria-label="Ẩn trợ giảng AI"
            title="Ẩn trợ giảng AI"
          >
            <X className="size-4" aria-hidden="true" />
          </button>
        )}
      </header>

      {/* Selection context banner */}
      {selectionContext !== null && (
        <div className="shrink-0 border-b border-amber-200 bg-amber-50 px-4 py-2.5">
          <div className="flex items-start gap-2">
            <p className="min-w-0 flex-1 text-[0.7rem] leading-relaxed text-amber-900">
              <span className="font-semibold">Context:</span>{' '}
              <span className="italic">
                &quot;{selectionContext.text.slice(0, 120)}
                {selectionContext.text.length > 120 ? '…' : ''}&quot;
              </span>
              <span className="text-amber-700">
                {' — '}
                {selectionContext.materialTitle}
                {selectionContext.location !== null && ` (${selectionContext.location})`}
              </span>
            </p>
            <button
              type="button"
              onClick={onDismissSelection}
              className="shrink-0 rounded p-0.5 text-amber-700 transition-colors hover:bg-amber-100"
              aria-label="Bỏ ngữ cảnh trích dẫn"
            >
              <X className="size-3.5" aria-hidden="true" />
            </button>
          </div>
        </div>
      )}

      {/* Transcript */}
      <div
        ref={scrollRef}
        onScroll={handleScroll}
        className="scrollbar-slim relative min-h-0 flex-1 space-y-4 overflow-y-auto px-4 py-4"
      >
        {messages.length === 0 && !isStreaming ? (
          <EmptyState onSelect={submit} />
        ) : (
          <>
            {messages.map((message: ChatMessage) => (
              <ChatMessageItem
                key={message.id}
                message={message}
                {...(message.id === lastAssistantId && retryPrompt !== undefined
                  ? { onRetry: () => submit(retryPrompt) }
                  : {})}
              />
            ))}

            {isStreaming && (
              <ChatMessageItem
                message={{
                  id: streamingPlaceholderId,
                  role: 'assistant',
                  content: streamingContent,
                  citations: [...citations],
                  wasAborted: false,
                  createdAt: Date.now(),
                }}
                isStreaming
              />
            )}
          </>
        )}

        {showScrollButton && (
          <button
            type="button"
            onClick={scrollToLatest}
            className="sticky bottom-2 ml-auto flex w-fit items-center gap-1 rounded-full border border-slate-200 bg-white px-2.5 py-1 text-[0.7rem] font-medium text-slate-600 shadow-overlay transition-colors hover:bg-slate-50"
          >
            <ArrowDown className="size-3" aria-hidden="true" />
            Jump to latest
          </button>
        )}
      </div>

      {/* Error */}
      {error !== null && (
        <p
          role="alert"
          className="shrink-0 border-t border-red-200 bg-red-50 px-4 py-2 text-xs text-red-700"
        >
          {error}
        </p>
      )}

      {/* Follow-up chips */}
      {!isStreaming && suggestions.length > 0 && messages.length > 0 && (
        <div className="shrink-0 border-t border-slate-100 px-4 py-2">
          <div className="flex flex-wrap gap-1.5">
            {suggestions.map((suggestion: TutorSuggestion) => (
              <button
                key={suggestion.id}
                type="button"
                onClick={() => submit(suggestion.prompt)}
                className="rounded-full border border-brand-200 bg-brand-50 px-2.5 py-1 text-[0.7rem] font-medium text-brand-700 transition-colors hover:bg-brand-100"
              >
                {suggestion.label}
              </button>
            ))}
          </div>
        </div>
      )}

      {/* Composer */}
      <form onSubmit={handleSubmit} className="shrink-0 border-t border-slate-200 p-3">
        <div className="flex items-end gap-2">
          <textarea
            value={draft}
            onChange={(event) => setDraft(event.target.value)}
            onKeyDown={(event) => {
              /* Enter sends; Shift+Enter inserts a newline. */
              if (event.key === 'Enter' && !event.shiftKey) {
                event.preventDefault();
                submit(draft);
              }
            }}
            rows={1}
            placeholder={
              isStreaming ? 'Trợ giảng đang trả lời…' : 'Hỏi một câu hỏi về tài liệu này'
            }
            aria-label="Gửi câu hỏi tới trợ giảng AI"
            className={cn(
              'max-h-32 min-h-[2.25rem] flex-1 resize-none rounded-[--radius-field] border border-slate-300 px-3 py-2 text-sm shadow-subtle',
              'placeholder:text-slate-400 focus-visible:border-brand-400 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-brand-500/25',
              isStreaming && 'bg-slate-50',
            )}
          />

          {isStreaming ? (
            <button
              type="button"
              onClick={onStop}
              className="flex size-9 shrink-0 items-center justify-center rounded-[--radius-field] bg-slate-800 text-white transition-colors hover:bg-slate-900"
              aria-label="Ngừng tạo câu trả lời"
            >
              <Square className="size-4 fill-current" aria-hidden="true" />
            </button>
          ) : (
            <button
              type="submit"
              disabled={draft.trim().length === 0}
              className="flex size-9 shrink-0 items-center justify-center rounded-[--radius-field] bg-brand-600 text-white transition-colors hover:bg-brand-700 disabled:opacity-40"
              aria-label="Gửi tin nhắn"
            >
              <Send className="size-4" aria-hidden="true" />
            </button>
          )}
        </div>
      </form>
    </div>
  );
}

/** First-run state: explains the tutor and offers starter prompts. */
function EmptyState({ onSelect }: { onSelect: (prompt: string) => void }): ReactElement {
  return (
    <div className="flex h-full flex-col items-center justify-center px-2 text-center">
      <div className="flex size-11 items-center justify-center rounded-2xl bg-brand-50">
        <Sparkles className="size-5 text-brand-600" aria-hidden="true" />
      </div>
      <p className="mt-3 text-sm font-medium text-slate-700">Hỏi về tài liệu này</p>
      <p className="mt-1 text-xs text-slate-500">
        Câu trả lời sẽ dẫn đúng slide hoặc trang mà nội dung đến từ đó.
      </p>

      <div className="mt-4 flex w-full flex-col gap-1.5">
        {QUICK_PROMPTS.map((prompt: string) => (
          <button
            key={prompt}
            type="button"
            onClick={() => onSelect(prompt)}
            className="rounded-[--radius-field] border border-slate-200 bg-white px-3 py-2 text-left text-xs text-slate-600 transition-colors hover:border-brand-300 hover:bg-brand-50 hover:text-brand-800"
          >
            {prompt}
          </button>
        ))}
      </div>
    </div>
  );
}
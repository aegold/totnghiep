import { BookMarked, Bot, ChevronDown, Copy, RefreshCw, User } from 'lucide-react';
import { memo, useState, type ReactElement } from 'react';

import { TutorMarkdown } from '@/components/markdown/TutorMarkdown';
import type { ChatMessage, TutorCitation } from '@/features/course-learning/types';
import { cn } from '@/lib/utils';

/** Collapsible provenance badge for one retrieved chunk. */
function CitationBadge({ citation }: { citation: TutorCitation }): ReactElement {
  const [isExpanded, setIsExpanded] = useState<boolean>(false);

  return (
    <div className="overflow-hidden rounded-[--radius-field] border border-slate-200 bg-white">
      <button
        type="button"
        onClick={() => setIsExpanded((open: boolean) => !open)}
        aria-expanded={isExpanded}
        className="flex w-full items-center gap-1.5 px-2 py-1 text-left text-xs font-medium text-slate-700 transition-colors hover:bg-slate-50"
      >
        <BookMarked className="size-3 shrink-0 text-brand-500" aria-hidden="true" />
        <span className="min-w-0 flex-1 truncate">
          {citation.label}
          {citation.location !== null && (
            <span className="text-slate-400"> · {citation.location}</span>
          )}
        </span>
        {citation.score !== null && (
          <span className="shrink-0 rounded bg-slate-100 px-1 py-0.5 font-mono text-[0.65rem] text-slate-500">
            {citation.score.toFixed(2)}
          </span>
        )}
        <ChevronDown
          className={cn(
            'size-3.5 shrink-0 text-slate-400 transition-transform',
            isExpanded && 'rotate-180',
          )}
          aria-hidden="true"
        />
      </button>

      {isExpanded && (
        <div className="border-t border-slate-100 px-2.5 py-2">
          {citation.documentTitle !== null && (
            <p className="mb-1 text-[0.7rem] font-medium text-slate-500">
              {citation.documentTitle}
            </p>
          )}
          {citation.snippet.length > 0 ? (
            <blockquote className="border-l-2 border-brand-200 pl-2 text-xs italic leading-relaxed text-slate-600">
              {citation.snippet}
            </blockquote>
          ) : (
            <p className="text-xs text-slate-400">Chưa có bản xem trước.</p>
          )}
        </div>
      )}
    </div>
  );
}

export interface ChatMessageItemProps {
  message: ChatMessage;
  /** True for the in-flight assistant turn. */
  isStreaming?: boolean;
  onRetry?: () => void;
}

/**
 * Renders one chat turn.
 *
 * User turns are plain text in an aligned bubble. Assistant turns are rendered
 * as Markdown with KaTeX and syntax highlighting, followed by collapsible
 * citation badges — the provenance the RAG policy requires.
 */
export const ChatMessageItem = memo(function ChatMessageItem({
  message,
  isStreaming = false,
  onRetry,
}: ChatMessageItemProps): ReactElement {
  const [copied, setCopied] = useState<boolean>(false);
  const isUser: boolean = message.role === 'user';

  const handleCopy = (): void => {
    void navigator.clipboard.writeText(message.content).then(() => {
      setCopied(true);
      window.setTimeout(() => setCopied(false), 1_600);
    });
  };

  if (isUser) {
    return (
      <div className="flex justify-end gap-2.5">
        <div className="max-w-[85%] rounded-[--radius-card] rounded-br-sm bg-brand-600 px-3.5 py-2.5 text-sm text-white">
          <p className="whitespace-pre-wrap break-words">{message.content}</p>
        </div>
        <span
          className="mt-0.5 flex size-7 shrink-0 items-center justify-center rounded-full bg-slate-200"
          aria-hidden="true"
        >
          <User className="size-3.5 text-slate-600" />
        </span>
      </div>
    );
  }

  const hasContent: boolean = message.content.trim().length > 0;

  return (
    <div className="flex gap-2.5">
      <span
        className="mt-0.5 flex size-7 shrink-0 items-center justify-center rounded-full bg-brand-100"
        aria-hidden="true"
      >
        <Bot className="size-3.5 text-brand-700" />
      </span>

      <div className="min-w-0 max-w-[92%] flex-1">
        <div className="rounded-[--radius-card] rounded-bl-sm border border-slate-200 bg-white px-3.5 py-2.5">
          {hasContent ? (
            <TutorMarkdown content={message.content} isStreaming={isStreaming} />
          ) : (
            /* Waiting for the first token. */
            <div className="flex items-center gap-1.5 py-1" role="status">
              <span className="sr-only">Trợ giảng đang suy nghĩ…</span>
              {[0, 1, 2].map((dot: number) => (
                <span
                  key={dot}
                  className="size-1.5 animate-bounce rounded-full bg-slate-300"
                  style={{ animationDelay: `${dot * 120}ms` }}
                />
              ))}
            </div>
          )}

          {isStreaming && hasContent && (
            <span
              className="ml-0.5 inline-block h-3.5 w-0.5 animate-pulse bg-brand-500 align-text-bottom"
              aria-hidden="true"
            />
          )}
        </div>

        {/* The learner must know an answer they stopped is incomplete. */}
        {message.wasAborted && !isStreaming && (
          <p className="mt-1.5 flex items-center gap-1.5 text-xs text-amber-700">
            <span className="size-1.5 rounded-full bg-amber-500" aria-hidden="true" />
            Generation stopped — this answer may be incomplete.
          </p>
        )}

        {!isStreaming && message.citations.length > 0 && (
          <div className="mt-2 space-y-1.5">
            <p className="text-[0.7rem] font-semibold uppercase tracking-wide text-slate-400">
              {message.citations.length === 1
                ? '1 source'
                : `${message.citations.length} sources`}
            </p>
            {message.citations.map((citation: TutorCitation) => (
              <CitationBadge key={citation.sourceId} citation={citation} />
            ))}
          </div>
        )}

        {!isStreaming && hasContent && (
          <div className="mt-1.5 flex items-center gap-1">
            <button
              type="button"
              onClick={handleCopy}
              className="flex items-center gap-1 rounded px-1.5 py-1 text-[0.7rem] text-slate-500 transition-colors hover:bg-slate-100 hover:text-slate-800"
            >
              <Copy className="size-3" aria-hidden="true" />
              {copied ? 'Đã sao chép' : 'Sao chép'}
            </button>

            {onRetry !== undefined && (
              <button
                type="button"
                onClick={onRetry}
                className="flex items-center gap-1 rounded px-1.5 py-1 text-[0.7rem] text-slate-500 transition-colors hover:bg-slate-100 hover:text-slate-800"
              >
                <RefreshCw className="size-3" aria-hidden="true" />
                Retry
              </button>
            )}
          </div>
        )}
      </div>
    </div>
  );
});
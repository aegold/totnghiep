import { memo, type ReactElement } from 'react';
import ReactMarkdown from 'react-markdown';
import rehypeKatex from 'rehype-katex';
import remarkGfm from 'remark-gfm';
import remarkMath from 'remark-math';

import { markdownComponents } from '@/components/markdown/markdownComponents';
import { cn } from '@/lib/utils';

import 'katex/dist/katex.min.css';
import 'highlight.js/styles/github-dark.css';

export interface TutorMarkdownProps {
  /** Markdown with GFM tables, `$...$` inline math, `$$...$$` display math. */
  content: string;
  className?: string;
  /**
   * True while tokens are still arriving.
   *
   * Half-written fences and unbalanced `$` delimiters make the Markdown parser
   * re-shuffle the whole document on nearly every frame, which reads as a
   * flicker. While streaming, the text is shown verbatim instead.
   */
  isStreaming?: boolean;
}

/**
 * Markdown renderer for AI Tutor answers: GFM + KaTeX + syntax highlighting.
 *
 * Memoised because a finished message never changes, so scrolling the chat
 * does not re-parse the whole transcript.
 */
export const TutorMarkdown = memo(function TutorMarkdown({
  content,
  className,
  isStreaming = false,
}: TutorMarkdownProps): ReactElement {
  /* Degrade to plain text mid-stream; see the `isStreaming` doc comment. */
  if (isStreaming) {
    return (
      <div
        className={cn('whitespace-pre-wrap break-words text-sm leading-relaxed text-slate-700', className)}
      >
        {content}
      </div>
    );
  }

  return (
    <div className={cn('text-sm text-slate-700', className)}>
      <ReactMarkdown
        remarkPlugins={[remarkMath, remarkGfm]}
        rehypePlugins={[rehypeKatex]}
        components={markdownComponents}
        /* Raw HTML is never enabled, so untrusted output cannot inject nodes. */
        skipHtml
      >
        {content}
      </ReactMarkdown>
    </div>
  );
});
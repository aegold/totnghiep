import { memo, type ReactElement } from 'react';
import ReactMarkdown from 'react-markdown';
import rehypeKatex from 'rehype-katex';
import remarkMath from 'remark-math';

import 'katex/dist/katex.min.css';

import { markdownComponents } from '@/components/markdown/markdownComponents';
import { cn } from '@/lib/utils';

import 'katex/dist/katex.min.css';

export interface RichTextProps {
  /** Markdown + `$...$` / `$$...$$` LaTeX source. */
  content: string;
  className?: string;
}

/**
 * Renders prompt/hint/option text with Markdown and KaTeX support.
 *
 * Shares its AST overrides with the AI Tutor via `components/markdown`, so
 * question content and tutor answers render identically. The component map now
 * also syntax-highlights fenced code, which tutor answers rely on.
 *
 * Memoised because question payloads are immutable and usually re-render when
 * an unrelated part of the page changes.
 */
export const RichText = memo(function RichText({
  content,
  className,
}: RichTextProps): ReactElement {
  return (
    <div className={cn('text-sm text-slate-700', className)}>
      <ReactMarkdown
        remarkPlugins={[remarkMath]}
        rehypePlugins={[rehypeKatex]}
        components={markdownComponents}
        /* Raw HTML is never enabled, so untrusted content cannot inject nodes. */
        skipHtml
      >
        {content}
      </ReactMarkdown>
    </div>
  );
});
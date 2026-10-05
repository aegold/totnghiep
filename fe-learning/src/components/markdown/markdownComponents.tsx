import type { Components } from 'react-markdown';

import { CodeBlock } from '@/components/markdown/CodeBlock';
import { cn } from '@/lib/utils';

/**
 * Markdown AST overrides shared by the Question Engine and the AI Tutor.
 *
 * Extracted so both surfaces render identical typography and KaTeX, without
 * the AI Tutor feature importing anything from `question-engine`.
 *
 * `react-markdown` is used WITHOUT `rehype-raw`, so raw HTML in content is
 * parsed as text and can never execute — untrusted tutor output is safe by
 * construction, which is why there is no `dangerouslySetInnerHTML` anywhere.
 */
export const markdownComponents: Components = {
  p: ({ className, children, ...props }) => (
    <p className={cn('leading-relaxed', className)} {...props}>
      {children}
    </p>
  ),
  strong: ({ className, children, ...props }) => (
    <strong className={cn('font-semibold text-slate-900', className)} {...props}>
      {children}
    </strong>
  ),
  em: ({ className, children, ...props }) => (
    <em className={cn('italic', className)} {...props}>
      {children}
    </em>
  ),
  ul: ({ className, children, ...props }) => (
    <ul className={cn('my-2 list-disc space-y-1 pl-5', className)} {...props}>
      {children}
    </ul>
  ),
  ol: ({ className, children, ...props }) => (
    <ol className={cn('my-2 list-decimal space-y-1 pl-5', className)} {...props}>
      {children}
    </ol>
  ),
  li: ({ className, children, ...props }) => (
    <li className={cn('leading-relaxed', className)} {...props}>
      {children}
    </li>
  ),

  /*
   * Fenced blocks arrive as `pre > code`. Rendering them through `CodeBlock`
   * gives the tutor and question prompts identical syntax highlighting.
   */
  pre: ({ className, children, ...props }) => (
    <pre className={cn('my-3 overflow-x-auto', className)} {...props}>
      {children}
    </pre>
  ),
  code: ({ className, children, ...props }) => {
    /*
     * `react-markdown` does not expose a "isFenced" flag, but fenced blocks
     * always carry a `language-*` class. Inline code never does.
     */
    const languageClass: string | undefined = className
      ?.split(/\s+/)
      .find((token: string) => token.startsWith('language-'));

    if (languageClass !== undefined) {
      return (
        <CodeBlock
          language={languageClass.replace('language-', '')}
          code={String(children).replace(/\n$/, '')}
        />
      );
    }

    return (
      <code
        className={cn('rounded bg-slate-100 px-1 py-0.5 font-mono text-[0.85em] text-slate-800', className)}
        {...props}
      >
        {children}
      </code>
    );
  },

  blockquote: ({ className, children, ...props }) => (
    <blockquote
      className={cn('border-l-2 border-slate-300 pl-3 text-slate-600 italic', className)}
      {...props}
    >
      {children}
    </blockquote>
  ),
  a: ({ className, children, ...props }) => (
    <a
      className={cn('text-brand-600 underline underline-offset-2', className)}
      /* Tutor output can contain links; force safe rel/target. */
      rel="noopener noreferrer nofollow"
      {...props}
    >
      {children}
    </a>
  ),
  h1: ({ className, children, ...props }) => (
    <h1 className={cn('mt-4 text-lg font-semibold text-slate-900', className)} {...props}>
      {children}
    </h1>
  ),
  h2: ({ className, children, ...props }) => (
    <h2 className={cn('mt-3.5 text-base font-semibold text-slate-900', className)} {...props}>
      {children}
    </h2>
  ),
  h3: ({ className, children, ...props }) => (
    <h3 className={cn('mt-3 text-sm font-semibold text-slate-900', className)} {...props}>
      {children}
    </h3>
  ),
  h4: ({ className, children, ...props }) => (
    <h4 className={cn('mt-2.5 text-sm font-semibold text-slate-900', className)} {...props}>
      {children}
    </h4>
  ),
  table: ({ className, children, ...props }) => (
    <div className="my-3 overflow-x-auto">
      <table className={cn('w-full border-collapse text-sm', className)} {...props}>
        {children}
      </table>
    </div>
  ),
  th: ({ className, children, ...props }) => (
    <th
      className={cn('border border-slate-200 bg-slate-50 px-3 py-2 text-left font-semibold', className)}
      {...props}
    >
      {children}
    </th>
  ),
  td: ({ className, children, ...props }) => (
    <td className={cn('border border-slate-200 px-3 py-2', className)} {...props}>
      {children}
    </td>
  ),
  hr: ({ className, ...props }) => <hr className={cn('my-4 border-slate-200', className)} {...props} />,
};
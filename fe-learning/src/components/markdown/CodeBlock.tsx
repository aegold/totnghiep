import { Check, Copy } from 'lucide-react';
import { memo, useEffect, useState, type ReactElement } from 'react';

import { cn } from '@/lib/utils';

/**
 * Languages registered up front. Kept deliberately small: each one adds to the
 * bundle, and tutor answers overwhelmingly use these.
 */
const SUPPORTED_LANGUAGES: readonly string[] = [
  'python',
  'javascript',
  'typescript',
  'java',
  'cpp',
  'sql',
  'bash',
  'json',
] as const;

/**
 * highlight.js core plus a handful of languages.
 *
 * Imported dynamically so the ~200 kB highlighter is fetched only when a code
 * block actually appears — most tutor answers contain none.
 */
type HighlightFn = (code: string, language: 'python') => { value: string };

let highlighterPromise: Promise<HighlightFn | null> | null = null;

/** Loads and caches the highlighter; concurrent callers share one promise. */
const loadHighlighter = async (): Promise<HighlightFn | null> => {
  if (highlighterPromise === null) {
    highlighterPromise = import('highlight.js/lib/core')
      .then(async (core) => {
        const [python, javascript, typescript, java, cpp, sql, bash, json] = await Promise.all([
          import('highlight.js/lib/languages/python'),
          import('highlight.js/lib/languages/javascript'),
          import('highlight.js/lib/languages/typescript'),
          import('highlight.js/lib/languages/java'),
          import('highlight.js/lib/languages/cpp'),
          import('highlight.js/lib/languages/sql'),
          import('highlight.js/lib/languages/bash'),
          import('highlight.js/lib/languages/json'),
        ]);

        core.default.registerLanguage('python', python.default);
        core.default.registerLanguage('javascript', javascript.default);
        core.default.registerLanguage('typescript', typescript.default);
        core.default.registerLanguage('java', java.default);
        core.default.registerLanguage('cpp', cpp.default);
        core.default.registerLanguage('sql', sql.default);
        core.default.registerLanguage('bash', bash.default);
        core.default.registerLanguage('json', json.default);
        core.default.registerAliases(['js', 'ts', 'py', 'sh', 'shell'], {
          languageName: 'javascript',
        });

        return core.default.highlight.bind(core.default) as HighlightFn;
      })
      .catch(() => null);
  }

  return highlighterPromise;
};

export interface CodeBlockProps {
  language: string;
  code: string;
  className?: string;
}

/**
 * Fenced code block with syntax highlighting and a copy button.
 *
 * Degrades to plain monospaced text until the highlighter resolves, and falls
 * back permanently for an unknown language rather than failing to render.
 */
export const CodeBlock = memo(function CodeBlock({
  language,
  code,
  className,
}: CodeBlockProps): ReactElement {
  const [html, setHtml] = useState<string>('');
  const [copied, setCopied] = useState<boolean>(false);

  const normalised: string = language.toLowerCase();

  useEffect(() => {
    /* Plain text first, so the block never renders empty. */
    if (!SUPPORTED_LANGUAGES.includes(normalised)) {
      setHtml('');
      return;
    }

    let cancelled = false;

    void loadHighlighter().then((highlight) => {
      if (highlight === null || cancelled) {
        return;
      }
      try {
        const result = highlight(code, normalised as 'python');
        if (!cancelled) {
          setHtml(result.value);
        }
      } catch {
        /* Malformed source must never break the message. */
        if (!cancelled) {
          setHtml('');
        }
      }
    });

    return () => {
      cancelled = true;
    };
  }, [code, normalised]);

  const handleCopy = (): void => {
    void navigator.clipboard.writeText(code).then(() => {
      setCopied(true);
      window.setTimeout(() => setCopied(false), 1_600);
    });
  };

  return (
    <div
      className={cn(
        'group relative my-3 overflow-hidden rounded-[--radius-field] border border-slate-700 bg-slate-900',
        className,
      )}
    >
      <div className="flex items-center justify-between border-b border-slate-700/70 px-3 py-1.5">
        <span className="font-mono text-[0.7rem] uppercase tracking-wide text-slate-400">
          {normalised}
        </span>

        <button
          type="button"
          onClick={handleCopy}
          className="flex items-center gap-1 rounded px-1.5 py-1 text-[0.7rem] font-medium text-slate-400 transition-colors hover:bg-slate-800 hover:text-slate-200"
          aria-label="Sao chép mã vào bộ nhớ tạm"
        >
          {copied ? <Check className="size-3" aria-hidden="true" /> : <Copy className="size-3" aria-hidden="true" />}
          {copied ? 'Copied' : 'Copy'}
        </button>
      </div>

      <pre className="scrollbar-slim overflow-x-auto p-3 text-[0.8rem] leading-relaxed">
        {/*
         * `dangerouslySetInnerHTML` is safe here and ONLY here: `html` is
         * highlight.js output derived from the code string itself. It is
         * escaped by the highlighter and never contains authored HTML.
         */}
        {html !== '' ? (
          <code
            className={`hljs language-${normalised}`}
            dangerouslySetInnerHTML={{ __html: html }}
          />
        ) : (
          <code className="font-mono text-slate-100">{code}</code>
        )}
      </pre>
    </div>
  );
});
import { ChevronLeft, ChevronRight, FileWarning, Loader2 } from 'lucide-react';
import { useCallback, useEffect, useRef, useState, type ReactElement } from 'react';

import { Button } from '@/components/ui/button';

/**
 * PDF reader built on pdf.js.
 *
 * ## Why pdf.js and not an `<iframe>`
 * Browsers render PDFs in a plugin whose DOM is not reachable from the page,
 * which makes text selection impossible — silently breaking the "select a
 * passage -> Ask AI Tutor" flow and citation anchoring. pdf.js renders each page
 * to a canvas and overlays a real, selectable DOM text layer.
 *
 * ## Bundle impact
 * pdf.js is large (~300 kB gzipped) and is dynamically imported, so it is only
 * fetched when a learner actually opens a PDF — never on first load.
 */

/* --------------------------- pdf.js module shim --------------------------- */

/**
 * pdf.js ships ESM with no bundled type declarations. The surface used here is
 * small and fully enumerated below, so it is declared locally rather than
 * turning off type checking with `any` or a suppression comment.
 */
interface PdfViewport {
  width: number;
  height: number;
}

interface PdfTextItem {
  str?: string;
  transform?: number[];
  width?: number;
  height?: number;
  hasEOL?: boolean;
}

interface PdfPageProxy {
  getViewport: (options: { scale: number }) => PdfViewport;
  getTextContent: () => Promise<{ items: PdfTextItem[] }>;
  render: (options: {
    canvasContext: CanvasRenderingContext2D;
    viewport: PdfViewport;
  }) => { promise: Promise<void>; cancel?: () => void };
}

interface PdfDocumentProxy {
  numPages: number;
  getPage: (pageNumber: number) => Promise<PdfPageProxy>;
  destroy: () => Promise<void>;
}

interface PdfJsModule {
  GlobalWorkerOptions: { workerSrc: string };
  getDocument: (options: { url: string }) => { promise: Promise<PdfDocumentProxy> };
}

/** Lazily loads pdf.js and configures its worker; shared across all readers. */
let pdfModulePromise: Promise<PdfJsModule> | null = null;

const loadPdfJs = async (): Promise<PdfJsModule> => {
  if (pdfModulePromise === null) {
    pdfModulePromise = import('pdfjs-dist')
      .then(async (module) => {
        const worker = await import('pdfjs-dist/build/pdf.worker.min.mjs?url');
        const lib = (module.default ?? module) as unknown as PdfJsModule;
        lib.GlobalWorkerOptions.workerSrc = worker.default;
        return lib;
      })
      .catch((error: unknown) => {
        /* Reset so a later attempt can retry after a transient failure. */
        pdfModulePromise = null;
        throw error;
      });
  }
  return pdfModulePromise;
};

export interface PdfReaderProps {
  src: string;
  title: string;
  /** 1-based page the learner should land on. */
  initialPage?: number;
  /** Reports the visible page so citations can reference it. */
  onPageChange?: (page: number) => void;
  /** Scrolls the owning container; set when the reader is not the scroller. */
  scrollContainerRef?: React.RefObject<HTMLElement | null>;
  className?: string;
}

/** Everything needed to position one span of the pdf.js text layer. */
interface SpanStyle {
  left: number;
  top: number;
  fontSize: number;
  scaleX: number;
}

/**
 * Converts a pdf.js text item into absolute CSS.
 *
 * pdf.js supplies `transform = [scaleX, skewY, skewX, scaleY, x, y]` in unscaled
 * PDF units; `viewport.convertToViewportPoint` does the perspective maths, but
 * for the axis-aligned text these documents use, `x`/`y` scaled by the viewport
 * factor is equivalent and far cheaper per span.
 */
const toSpanStyle = (item: PdfTextItem, scale: number): SpanStyle => {
  const transform: number[] = item.transform ?? [1, 0, 0, 1, 0, 0];
  const height: number = item.height ?? 10;

  return {
    left: (transform[4] ?? 0) * scale,
    top: ((transform[5] ?? 0) - height) * scale,
    fontSize: height * scale,
    scaleX: transform[0] ?? 1,
  };
};

/** One rendered page: its CSS size and its text-layer spans. */
interface PdfPageView {
  pageNumber: number;
  width: number;
  height: number;
  spans: { key: string; text: string; style: SpanStyle; breakAfter: boolean }[];
}

/** Renders one page into the supplied canvas and returns its text layout. */
const renderPage = async (
  doc: PdfDocumentProxy,
  pageNumber: number,
  canvas: HTMLCanvasElement,
): Promise<PdfPageView> => {
  const page = await doc.getPage(pageNumber);
  const base = page.getViewport({ scale: 1 });
  /* Cap the width so a wide page does not render a huge canvas. */
  const scale: number = Math.min(2, 900 / base.width);
  const viewport = page.getViewport({ scale });

  canvas.width = viewport.width;
  canvas.height = viewport.height;

  const context = canvas.getContext('2d');
  if (context === null) {
    throw new Error('Không lấy được ngữ cảnh Canvas 2D.');
  }

  const task = page.render({ canvasContext: context, viewport });
  await task.promise;

  const textContent = await page.getTextContent();

  return {
    pageNumber,
    width: viewport.width,
    height: viewport.height,
    spans: textContent.items
      .filter((item: PdfTextItem) => typeof item.str === 'string' && item.str.length > 0)
      .map((item: PdfTextItem, index: number) => ({
        key: `${pageNumber}-${index}`,
        text: item.str ?? '',
        style: toSpanStyle(item, scale),
        breakAfter: item.hasEOL === true,
      })),
  };
};

export function PdfReader({
  src,
  title,
  initialPage = 1,
  onPageChange,
  scrollContainerRef,
  className,
}: PdfReaderProps): ReactElement {
  const [numPages, setNumPages] = useState<number>(0);
  const [pageViews, setPageViews] = useState<ReadonlyMap<number, PdfPageView>>(new Map());
  const [currentPage, setCurrentPage] = useState<number>(initialPage);
  const [error, setError] = useState<string | null>(null);
  const [isLoading, setIsLoading] = useState<boolean>(true);

  const docRef = useRef<PdfDocumentProxy | null>(null);
  /** Canvas per page, registered by ref callbacks as pages mount. */
  const canvasRefs = useRef<Map<number, HTMLCanvasElement>>(new Map());

  useEffect(
    () => () => {
      const doc = docRef.current;
      docRef.current = null;
      void doc?.destroy().catch(() => undefined);
    },
    [],
  );

  useEffect(() => {
    let cancelled = false;

    const load = async (): Promise<void> => {
      try {
        const pdfjs = await loadPdfJs();
        const doc = await pdfjs.getDocument({ url: src }).promise;

        if (cancelled) {
          void doc.destroy();
          return;
        }

        docRef.current = doc;
        setNumPages(doc.numPages);
        setIsLoading(false);
      } catch (caught: unknown) {
        if (!cancelled) {
          setError(caught instanceof Error ? caught.message : 'Không tải được tệp PDF này.');
          setIsLoading(false);
        }
      }
    };

    void load();

    return () => {
      cancelled = true;
    };
  }, [src]);

  const goToPage = useCallback(
    (page: number): void => {
      const clamped: number = Math.max(1, Math.min(numPages || 1, page));
      setCurrentPage(clamped);
      onPageChange?.(clamped);

      /* Scroll the owning container so keyboard paging actually moves. */
      const target = canvasRefs.current.get(clamped);
      const scroller = scrollContainerRef?.current ?? null;

      if (target !== undefined && scroller !== null) {
        scroller.scrollTo({ top: target.offsetTop - 16, behavior: 'smooth' });
      }
    },
    [numPages, onPageChange, scrollContainerRef],
  );

  /* Arrow keys page through the document. */
  const handleKeyDown = useCallback(
    (event: React.KeyboardEvent<HTMLDivElement>): void => {
      if (event.key === 'ArrowRight' || event.key === 'PageDown') {
        event.preventDefault();
        goToPage(currentPage + 1);
      } else if (event.key === 'ArrowLeft' || event.key === 'PageUp') {
        event.preventDefault();
        goToPage(currentPage - 1);
      }
    },
    [currentPage, goToPage],
  );

  /** Renders a page once its canvas has mounted. */
  const attachCanvas = useCallback(
    (pageNumber: number) => (node: HTMLCanvasElement | null): void => {
      const doc = docRef.current;
      if (node === null || doc === null) {
        return;
      }

      canvasRefs.current.set(pageNumber, node);

      if (pageViews.has(pageNumber)) {
        return;
      }

      void renderPage(doc, pageNumber, node)
        .then((view: PdfPageView) => {
          setPageViews((current) => {
            const next = new Map(current);
            next.set(pageNumber, view);
            return next;
          });
        })
        .catch((caught: unknown) => {
          setError(caught instanceof Error ? caught.message : 'Không hiển thị được trang này.');
        });
    },
    [pageViews],
  );

  if (error !== null && pageViews.size === 0) {
    return (
      <div className="flex h-full flex-col items-center justify-center gap-3 p-8 text-center">
        <FileWarning className="size-8 text-danger" aria-hidden="true" />
        <p className="text-sm text-slate-600">{error}</p>
      </div>
    );
  }

  if (isLoading) {
    return (
      <div className="flex h-full items-center justify-center" role="status">
        <Loader2 className="size-7 animate-spin text-brand-600" aria-hidden="true" />
        <span className="sr-only">Đang tải tài liệu PDF…</span>
      </div>
    );
  }

  const pageNumbers: number[] = Array.from(
    { length: numPages },
    (_unused, index: number) => index + 1,
  );

  return (
    <div
      className={className}
      role="document"
      aria-label={title}
      tabIndex={0}
      onKeyDown={handleKeyDown}
    >
      {/* Page controls */}
      <div className="sticky top-0 z-10 flex items-center justify-between gap-3 border-b border-slate-200 bg-white px-4 py-2">
        <p className="truncate text-sm font-medium text-slate-700">{title}</p>

        <div className="flex items-center gap-1.5">
          <Button
            variant="ghost"
            size="icon-sm"
            onClick={() => goToPage(currentPage - 1)}
            disabled={currentPage <= 1}
            aria-label="Trang trước"
          >
            <ChevronLeft aria-hidden="true" />
          </Button>

          <span className="text-xs tabular-nums text-slate-600" aria-live="polite">
            Trang {currentPage}/{numPages}
          </span>

          <Button
            variant="ghost"
            size="icon-sm"
            onClick={() => goToPage(currentPage + 1)}
            disabled={currentPage >= numPages}
            aria-label="Trang sau"
          >
            <ChevronRight aria-hidden="true" />
          </Button>
        </div>
      </div>

      {/* Pages: canvas with a transparent text layer for selection. */}
      <div className="space-y-5 p-4">
        {pageNumbers.map((pageNumber: number) => {
          const view: PdfPageView | undefined = pageViews.get(pageNumber);

          return (
            <div
              key={pageNumber}
              className="relative mx-auto w-fit"
              style={view !== undefined ? { width: view.width } : { minWidth: 320, minHeight: 200 }}
            >
              <canvas
                ref={attachCanvas(pageNumber)}
                className="rounded border border-slate-200 shadow-subtle"
                aria-label={`Trang ${pageNumber}`}
              />

              {view !== undefined && (
                /*
                 * Real, selectable DOM positioned over the canvas glyphs.
                 * `text-transparent` keeps the visual output identical while
                 * preserving selection, find-in-page, and screen-reader access.
                 */
                <div
                  className="absolute left-0 top-0 select-text text-transparent"
                  style={{ width: view.width, height: view.height }}
                  aria-label={`Lớp văn bản trang ${pageNumber}`}
                >
                  {view.spans.map((span) => (
                    <span key={span.key}>
                      <span
                        style={{
                          position: 'absolute',
                          left: `${span.style.left}px`,
                          top: `${span.style.top}px`,
                          fontSize: `${span.style.fontSize}px`,
                          fontFamily: 'sans-serif',
                          transform: `scaleX(${span.style.scaleX})`,
                          transformOrigin: '0 0',
                          whiteSpace: 'pre',
                        }}
                      >
                        {span.text}
                      </span>
                      {span.breakAfter && <br />}
                    </span>
                  ))}
                </div>
              )}
            </div>
          );
        })}
      </div>
    </div>
  );
}
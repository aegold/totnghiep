import { useCallback, useEffect, useRef, useState, type ReactElement } from 'react';

import { LEARNING_LIMITS } from '@/features/course-learning/types';
import type { MaterialSelection } from '@/features/course-learning/types';

/** Where the "Ask AI Tutor" affordance should be anchored on screen. */
export interface SelectionPopoverState {
  text: string;
  /** Viewport coordinates for the tooltip. */
  top: number;
  left: number;
  selection: MaterialSelection;
}

export interface UseTextSelectionOptions {
  /** Material the selection belongs to, echoed into the selection payload. */
  materialId: number;
  materialTitle: string;
  /** Restricts selection to this element; selections elsewhere are ignored. */
  containerRef: React.RefObject<HTMLElement | null>;
  /** 1-based page/slide, when the viewer knows it. */
  resolveLocation?: () => string | null;
  onAsk: (selection: MaterialSelection) => void;
}

export interface UseTextSelectionResult {
  popover: SelectionPopoverState | null;
  dismiss: () => void;
}

/** Below this many characters a "selection" is almost certainly a stray click. */
const MIN_SELECTION_CHARS: number = 3;

/**
 * Detects a text selection inside the learning material and exposes an
 * "Ask AI Tutor" affordance anchored to it.
 *
 * ## The scoping problem this solves
 * `window.getSelection()` is global. Without an explicit containment check,
 * selecting text in the AI sidebar — or anywhere else on the page — would also
 * fire this. The anchor node is therefore tested against the container ref on
 * every `mouseup`.
 *
 * The listener defers to the next tick because the browser only finalises a
 * selection after the mouseup handler returns.
 */
export function useTextSelection(options: UseTextSelectionOptions): UseTextSelectionResult {
  const { materialId, materialTitle, containerRef, resolveLocation } = options;

  const [popover, setPopover] = useState<SelectionPopoverState | null>(null);
  const popoverRef = useRef<SelectionPopoverState | null>(null);

  const dismiss = useCallback((): void => {
    popoverRef.current = null;
    setPopover(null);
  }, []);

  useEffect(() => {
    const clear = (): void => {
      if (popoverRef.current !== null) {
        popoverRef.current = null;
        setPopover(null);
      }
    };

    const handleMouseUp = (): void => {
      /* Wait for the browser to settle the selection. */
      window.setTimeout(() => {
        const selection = window.getSelection();
        const container = containerRef.current;

        if (selection === null || container === null || selection.isCollapsed) {
          return;
        }

        const text: string = selection.toString().trim();

        if (text.length < MIN_SELECTION_CHARS) {
          return;
        }

        /*
         * Containment check: the selection must start inside the material.
         * `anchorNode` may be a text node, so compare against its parent.
         */
        const anchorNode: Node | null = selection.anchorNode;
        const anchorElement: Node | null =
          anchorNode?.nodeType === Node.TEXT_NODE ? anchorNode.parentNode : anchorNode;

        if (anchorElement === null || !container.contains(anchorElement)) {
          return;
        }

        const rect: DOMRect = selection.getRangeAt(0).getBoundingClientRect();

        if (rect.width === 0 && rect.height === 0) {
          return;
        }

        const truncated: string =
          text.length > LEARNING_LIMITS.maxSelectionChars
            ? `${text.slice(0, LEARNING_LIMITS.maxSelectionChars)}…`
            : text;

        const next: SelectionPopoverState = {
          text: truncated,
          /* Centre the tooltip above the selection. */
          top: rect.top - 44,
          left: rect.left + rect.width / 2,
          selection: {
            text: truncated,
            materialId,
            materialTitle,
            location: resolveLocation?.() ?? null,
          },
        };

        popoverRef.current = next;
        setPopover(next);
      }, 0);
    };

    /* Clicking outside the affordance dismisses it. */
    const handleMouseDown = (event: MouseEvent): void => {
      const target = event.target;
      if (
        !(target instanceof HTMLElement) ||
        target.closest('[data-selection-affordance="true"]') === null
      ) {
        clear();
      }
    };

    document.addEventListener('mouseup', handleMouseUp);
    document.addEventListener('mousedown', handleMouseDown);

    return () => {
      document.removeEventListener('mouseup', handleMouseUp);
      document.removeEventListener('mousedown', handleMouseDown);
    };
  }, [containerRef, materialId, materialTitle, resolveLocation]);

  return { popover, dismiss };
}

export interface AskAiAffordanceProps {
  popover: SelectionPopoverState | null;
  onAsk: (selection: MaterialSelection) => void;
  onDismiss: () => void;
}

/**
 * Floating "Ask AI Tutor" tooltip.
 *
 * Positioned with `position: fixed` using viewport coordinates, so it is
 * unaffected by the scroll container and by the resizable split pane.
 */
export function AskAiAffordance({
  popover,
  onAsk,
  onDismiss,
}: AskAiAffordanceProps): ReactElement | null {
  if (popover === null) {
    return null;
  }

  return (
    <div
      data-selection-affordance="true"
      role="dialog"
      aria-label="Hỏi trợ giảng AI về phần văn bản đã chọn"
      style={{ top: `${popover.top}px`, left: `${popover.left}px` }}
      className="fixed z-[70] -translate-x-1/2 -translate-y-full animate-fade-in"
    >
      <div className="flex items-center gap-1 rounded-lg bg-slate-900 p-1 shadow-overlay">
        <button
          type="button"
          onClick={() => onAsk(popover.selection)}
          className="rounded-md px-2.5 py-1.5 text-xs font-medium text-white transition-colors hover:bg-slate-700"
        >
          Hỏi trợ giảng AI
        </button>
        <button
          type="button"
          onClick={onDismiss}
          className="rounded-md px-2 py-1.5 text-xs text-slate-400 transition-colors hover:bg-slate-700 hover:text-slate-200"
          aria-label="Đóng"
        >
          <span aria-hidden="true">✕</span>
        </button>
      </div>

      {/* Pointer tail. */}
      <div className="mx-auto h-1.5 w-1.5 -translate-y-0.5 rotate-45 bg-slate-900" aria-hidden="true" />
    </div>
  );
}
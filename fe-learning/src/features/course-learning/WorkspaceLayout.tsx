import { GripVertical, PanelRightClose, PanelRightOpen } from 'lucide-react';
import {
  useCallback,
  useEffect,
  useRef,
  useState,
  type PointerEvent as ReactPointerEvent,
  type ReactElement,
  type ReactNode,
} from 'react';

import { useIsDesktop } from '@/hooks/useMediaQuery';
import { cn } from '@/lib/utils';

/** Default split: 65% material / 35% tutor, per the workspace spec. */
const DEFAULT_CONTENT_PERCENT = 65;

/** Neither pane may be squeezed below this. */
const MIN_CONTENT_PERCENT = 25;
const MIN_TUTOR_PERCENT = 22;

/** Pixels moved per arrow-key press on the separator. */
const KEYBOARD_STEP_PERCENT = 4;

export interface WorkspaceLayoutProps {
  /** Left pane: the learning material viewport. */
  content: ReactNode;
  /** Right pane: the AI study copilot. */
  sidebar: ReactNode;
  /** Accessible name for the split, announced to screen readers. */
  label?: string;
  /** Called whenever the split changes, so the parent can persist it. */
  onRatioChange?: (contentPercent: number) => void;
  /** Initially collapsed (focus mode). */
  defaultCollapsed?: boolean;
  className?: string;
}

/**
 * Resizable split-pane workspace.
 *
 * ## Why hand-rolled rather than a split-pane library
 * The behaviour needed is one divider, and the accessibility contract is the
 * part libraries most often skip. Here the separator is a real
 * `role="separator"` carrying `aria-valuenow`, so it is announced and operable
 * from the keyboard: arrows nudge, Home/End jump to the extremes, Enter or
 * double-click resets to 65/35.
 *
 * Below the `lg` breakpoint the panes stack, because a 35% column on a phone is
 * unusable.
 */
export function WorkspaceLayout({
  content,
  sidebar,
  label = 'Không gian học tập',
  onRatioChange,
  defaultCollapsed = false,
  className,
}: WorkspaceLayoutProps): ReactElement {
  const isDesktop: boolean = useIsDesktop();

  const [contentPercent, setContentPercent] = useState<number>(DEFAULT_CONTENT_PERCENT);
  const [isCollapsed, setIsCollapsed] = useState<boolean>(defaultCollapsed);
  const [isDragging, setIsDragging] = useState<boolean>(false);

  const containerRef = useRef<HTMLDivElement | null>(null);
  /** Mirrors the ratio so pointermove does not re-bind on every render. */
  const ratioRef = useRef<number>(DEFAULT_CONTENT_PERCENT);

  const applyRatio = useCallback(
    (percent: number): void => {
      const maxContent = 100 - MIN_TUTOR_PERCENT;
      const next: number = Math.min(maxContent, Math.max(MIN_CONTENT_PERCENT, percent));

      ratioRef.current = next;
      setContentPercent(next);
      onRatioChange?.(next);
    },
    [onRatioChange],
  );

  /* Pointer dragging. `setPointerCapture` keeps the drag alive when the cursor
     leaves the separator, which is otherwise an abrupt stop. */
  const handlePointerDown = (event: ReactPointerEvent<HTMLDivElement>): void => {
    if (!isDesktop) {
      return;
    }
    event.currentTarget.setPointerCapture(event.pointerId);
    setIsDragging(true);
  };

  const handlePointerMove = (event: ReactPointerEvent<HTMLDivElement>): void => {
    const container = containerRef.current;
    if (container === null || !isDragging) {
      return;
    }

    const rect = container.getBoundingClientRect();
    if (rect.width === 0) {
      return;
    }

    applyRatio(((event.clientX - rect.left) / rect.width) * 100);
  };

  const endDrag = (event: ReactPointerEvent<HTMLDivElement>): void => {
    if (event.currentTarget.hasPointerCapture(event.pointerId)) {
      event.currentTarget.releasePointerCapture(event.pointerId);
    }
    setIsDragging(false);
  };

  const handleKeyDown = (event: React.KeyboardEvent<HTMLDivElement>): void => {
    let next: number | null = null;

    switch (event.key) {
      case 'ArrowLeft':
        next = ratioRef.current - KEYBOARD_STEP_PERCENT;
        break;
      case 'ArrowRight':
        next = ratioRef.current + KEYBOARD_STEP_PERCENT;
        break;
      case 'Home':
        next = MIN_CONTENT_PERCENT;
        break;
      case 'End':
        next = 100 - MIN_TUTOR_PERCENT;
        break;
      case 'Enter':
        /* Double-click equivalent: reset to the default split. */
        next = DEFAULT_CONTENT_PERCENT;
        break;
      default:
        return;
    }

    event.preventDefault();
    applyRatio(next);
  };

  const resetRatio = (): void => {
    setIsCollapsed(false);
    applyRatio(DEFAULT_CONTENT_PERCENT);
  };

  /* Suppress text selection while a drag is in progress. */
  useEffect(() => {
    if (!isDragging) {
      return;
    }
    const previous = document.body.style.userSelect;
    document.body.style.userSelect = 'none';
    return () => {
      document.body.style.userSelect = previous;
    };
  }, [isDragging]);

  /* Below `lg` the panes stack; the ratio and collapse state are irrelevant. */
  if (!isDesktop) {
    return (
      <div className={cn('flex h-full flex-col', className)}>
        <div className="min-h-[60vh] flex-1 overflow-hidden">{content}</div>
        {!isCollapsed && (
          <div className="h-[40vh] shrink-0 border-t border-slate-200">{sidebar}</div>
        )}
      </div>
    );
  }

  const showSidebar: boolean = !isCollapsed;

  return (
    <div
      ref={containerRef}
      className={cn('relative flex h-full w-full overflow-hidden', className)}
    >
      {/* Material pane */}
      <div
        className="min-w-0 overflow-hidden"
        style={{
          flexBasis: showSidebar ? `${contentPercent}%` : '100%',
          flexGrow: 0,
          flexShrink: 0,
        }}
      >
        {content}
      </div>

      {/* Divider */}
      {showSidebar && (
        <div
          role="separator"
          tabIndex={0}
          aria-orientation="vertical"
          aria-label={`Thay đổi độ rộng ${label}`}
          aria-valuenow={Math.round(contentPercent)}
          aria-valuemin={MIN_CONTENT_PERCENT}
          aria-valuemax={100 - MIN_TUTOR_PERCENT}
          aria-valuetext={`Tài liệu ${Math.round(contentPercent)}%, trợ giảng AI ${Math.round(100 - contentPercent)}%`}
          onPointerDown={handlePointerDown}
          onPointerMove={handlePointerMove}
          onPointerUp={endDrag}
          onPointerCancel={endDrag}
          onKeyDown={handleKeyDown}
          onDoubleClick={resetRatio}
          className={cn(
            'group relative z-10 flex w-1.5 shrink-0 cursor-col-resize items-center justify-center bg-slate-200 transition-colors',
            'hover:bg-brand-400 focus-visible:bg-brand-500 focus-visible:outline-none',
            isDragging && 'bg-brand-500',
          )}
        >
          {/* Widened invisible hit area, so the divider is easy to grab. */}
          <span className="absolute inset-y-0 -left-1.5 -right-1.5" aria-hidden="true" />
          <GripVertical
            className="size-3 text-slate-500 opacity-0 transition-opacity group-hover:opacity-100 group-focus-visible:opacity-100"
            aria-hidden="true"
          />
        </div>
      )}

      {/* Tutor pane */}
      {showSidebar && (
        <div className="min-w-0 flex-1 overflow-hidden border-l border-slate-200">{sidebar}</div>
      )}

      {/* Restore control shown in focus mode */}
      {!showSidebar && (
        <button
          type="button"
          onClick={() => setIsCollapsed(false)}
          className="absolute right-4 top-4 z-20 flex items-center gap-1.5 rounded-[--radius-field] border border-slate-200 bg-white px-2.5 py-1.5 text-xs font-medium text-slate-600 shadow-raised transition-colors hover:bg-slate-50"
          aria-label="Hiện trợ giảng AI"
        >
          <PanelRightOpen className="size-4" aria-hidden="true" />
          Trợ giảng AI
        </button>
      )}
    </div>
  );
}

/** Header button that toggles focus mode. */
export function FocusModeToggle({
  isCollapsed,
  onToggle,
}: {
  isCollapsed: boolean;
  onToggle: () => void;
}): ReactElement {
  return (
    <button
      type="button"
      onClick={onToggle}
      className="flex items-center gap-1.5 rounded-[--radius-field] border border-slate-200 bg-white px-2.5 py-1.5 text-xs font-medium text-slate-600 shadow-subtle transition-colors hover:bg-slate-50"
      aria-pressed={isCollapsed}
    >
      {isCollapsed ? (
        <PanelRightOpen className="size-4" aria-hidden="true" />
      ) : (
        <PanelRightClose className="size-4" aria-hidden="true" />
      )}
      {isCollapsed ? 'Hiện trợ giảng' : 'Chế độ tập trung'}
    </button>
  );
}
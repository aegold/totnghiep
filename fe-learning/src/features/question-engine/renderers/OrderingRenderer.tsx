import { ArrowDown, ArrowUp, GripVertical } from 'lucide-react';
import { useState, type DragEvent, type ReactElement } from 'react';

import { RichText } from '@/features/question-engine/components/RichText';
import type {
  OrderedItem,
  OrderingPayload,
  OrderingResponse,
  QuestionProps,
} from '@/features/question-engine/types';
import { cn } from '@/lib/utils';

export type OrderingRendererProps = QuestionProps<OrderingPayload, OrderingResponse>;

/**
 * Immutable array move: returns a NEW array with the item relocated.
 * The input array is never mutated, so React's change detection stays correct
 * and earlier responses retained in state keep their original value.
 */
export const moveItem = (order: readonly string[], from: number, to: number): string[] => {
  if (from === to || from < 0 || to < 0 || from >= order.length || to >= order.length) {
    return [...order];
  }

  const next: string[] = [...order];
  const [moved] = next.splice(from, 1);
  /* `moved` is defined because `from` was bounds-checked above. */
  next.splice(to, 0, moved as string);
  return next;
};

/**
 * Renders an `ORDERING` question.
 *
 * Two equivalent input methods are offered, because drag-and-drop alone is
 * unusable on touch screens and for keyboard/assistive-tech users:
 *  1. Native HTML5 drag-and-drop between rows.
 *  2. Explicit Move up / Move down buttons, which are keyboard reachable.
 *
 * Controlled: the sequence lives in `value.submittedOrder` and every move emits
 * a fresh array. While the response is empty the authored order is displayed, so
 * the item pool is never blank on first render.
 */
export function OrderingRenderer({
  payload,
  value,
  onChange,
  disabled = false,
}: OrderingRendererProps): ReactElement {
  const [draggedIndex, setDraggedIndex] = useState<number | null>(null);
  const [dragOverIndex, setDragOverIndex] = useState<number | null>(null);

  const itemById: ReadonlyMap<string, OrderedItem> = new Map(
    payload.items.map((item) => [item.id, item]),
  );

  /**
   * Ids currently in play, falling back to the authored order until the learner
   * interacts. Ids absent from the payload are filtered out defensively.
   */
  const currentOrder: string[] = (value.submittedOrder.length > 0
    ? value.submittedOrder
    : payload.items.map((item) => item.id)
  ).filter((id: string) => itemById.has(id));

  const emitOrder = (order: string[]): void => {
    if (disabled) {
      return;
    }
    onChange({ submittedOrder: [...order] });
  };

  const move = (from: number, to: number): void => {
    emitOrder(moveItem(currentOrder, from, to));
  };

  const handleDragStart = (index: number, event: DragEvent<HTMLLIElement>): void => {
    if (disabled) {
      event.preventDefault();
      return;
    }
    setDraggedIndex(index);
    event.dataTransfer.effectAllowed = 'move';
    /* Required by Firefox; without it the drag never starts. */
    event.dataTransfer.setData('text/plain', String(index));
  };

  const handleDragOver = (index: number, event: DragEvent<HTMLLIElement>): void => {
    if (disabled || draggedIndex === null) {
      return;
    }
    /* Without preventDefault the element is not a valid drop target. */
    event.preventDefault();
    event.dataTransfer.dropEffect = 'move';
    setDragOverIndex(index);
  };

  const handleDrop = (index: number, event: DragEvent<HTMLLIElement>): void => {
    if (disabled) {
      return;
    }
    event.preventDefault();

    const raw: string = event.dataTransfer.getData('text/plain');
    const from: number = draggedIndex ?? Number.parseInt(raw, 10);

    if (Number.isInteger(from) && from !== index) {
      move(from, index);
    }
    setDraggedIndex(null);
    setDragOverIndex(null);
  };

  const handleDragEnd = (): void => {
    setDraggedIndex(null);
    setDragOverIndex(null);
  };

  return (
    <div className="space-y-4">
      <RichText content={payload.prompt} />

      <p className="text-xs text-slate-500">
        Kéo thả các hàng để sắp xếp, hoặc dùng các nút di chuyển. Thứ tự của bạn được lưu tự động.
      </p>

      <ol className="space-y-2">
        {currentOrder.map((itemId: string, index: number) => {
          const item: OrderedItem | undefined = itemById.get(itemId);
          if (item === undefined) {
            return null;
          }

          const isFirst: boolean = index === 0;
          const isLast: boolean = index === currentOrder.length - 1;
          const isDragging: boolean = draggedIndex === index;
          const isDropTarget: boolean = dragOverIndex === index && draggedIndex !== index;

          return (
            <li
              key={itemId}
              draggable={!disabled}
              onDragStart={(event) => handleDragStart(index, event)}
              onDragOver={(event) => handleDragOver(index, event)}
              onDrop={(event) => handleDrop(index, event)}
              onDragEnd={handleDragEnd}
              className={cn(
                'flex items-center gap-2.5 rounded-[--radius-field] border bg-white p-2.5 transition-all',
                isDragging ? 'opacity-40' : 'border-slate-200',
                isDropTarget && 'border-brand-500 ring-1 ring-brand-500',
                !disabled && 'cursor-grab active:cursor-grabbing',
              )}
            >
              {/* Position badge doubles as the visual anchor for order. */}
              <span
                aria-label={`Position ${index + 1} of ${currentOrder.length}`}
                className="flex size-7 shrink-0 items-center justify-center rounded-md bg-slate-100 text-xs font-semibold text-slate-600"
              >
                {index + 1}
              </span>

              <GripVertical className="size-4 shrink-0 text-slate-300" aria-hidden="true" />

              <span className="min-w-0 flex-1">
                <RichText content={item.text} />
              </span>

              {/* Keyboard-accessible reordering. */}
              <span className="flex shrink-0 items-center gap-1">
                <button
                  type="button"
                  disabled={disabled || isFirst}
                  onClick={() => move(index, index - 1)}
                  aria-label={`Di chuyển "${item.text}" lên trên`}
                  className="rounded-md p-1.5 text-slate-500 transition-colors hover:bg-slate-100 hover:text-slate-900 disabled:cursor-not-allowed disabled:opacity-30 disabled:hover:bg-transparent"
                >
                  <ArrowUp className="size-4" aria-hidden="true" />
                </button>
                <button
                  type="button"
                  disabled={disabled || isLast}
                  onClick={() => move(index, index + 1)}
                  aria-label={`Di chuyển "${item.text}" xuống dưới`}
                  className="rounded-md p-1.5 text-slate-500 transition-colors hover:bg-slate-100 hover:text-slate-900 disabled:cursor-not-allowed disabled:opacity-30 disabled:hover:bg-transparent"
                >
                  <ArrowDown className="size-4" aria-hidden="true" />
                </button>
              </span>
            </li>
          );
        })}
      </ol>

      {payload.hint !== undefined && <p className="text-xs text-slate-500">{payload.hint}</p>}
    </div>
  );
}
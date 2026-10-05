import { Handle, Position, type NodeProps } from '@xyflow/react';
import { Check, Lock } from 'lucide-react';
import { memo, type ReactElement } from 'react';

import { MASTERY_THRESHOLD } from '@/features/knowledge-graph/types';
import type {
  KnowledgeNodeStatus,
  /* Aliased: the component below shares the `SkillNode` name. */
  SkillNode as SkillNodeType,
} from '@/features/knowledge-graph/types';
import { cn } from '@/lib/utils';

/**
 * Visual language for the three mastery states.
 *
 * Colour is never the ONLY signal: each state also carries a distinct icon
 * (lock / pulse / check) and a text label, so the map works with any form of
 * colour vision and is announced correctly to screen readers.
 */
export interface NodeVisuals {
  container: string;
  title: string;
  badge: string;
  /** Accessible label prefix, e.g. "Locked". */
  label: string;
}

export const NODE_VISUALS: Readonly<Record<KnowledgeNodeStatus, NodeVisuals>> = {
  LOCKED: {
    container: 'bg-slate-100 border-slate-200',
    title: 'text-slate-500',
    badge: 'bg-slate-200 text-slate-500',
    label: 'Locked',
  },
  IN_PROGRESS: {
    container: 'bg-white border-amber-300',
    title: 'text-slate-900',
    badge: 'bg-amber-100 text-amber-700',
    label: 'In progress',
  },
  MASTERED: {
    container: 'bg-white border-emerald-300',
    title: 'text-slate-900',
    badge: 'bg-emerald-100 text-emerald-700',
    label: 'Mastered',
  },
};

/** Circular progress ring showing mastery toward the 80% threshold. */
function ProgressRing({
  score,
  isSelected,
}: {
  score: number;
  isSelected: boolean;
}): ReactElement {
  const radius: number = 15;
  const circumference: number = 2 * Math.PI * radius;
  /* Progress is measured toward the threshold, not toward 100. */
  const fraction: number = Math.max(0, Math.min(1, score / MASTERY_THRESHOLD));
  const dash: number = circumference * fraction;

  return (
    <svg
      className="size-9 shrink-0 -rotate-90"
      viewBox="0 0 36 36"
      role="img"
      aria-label={`${score}% mastery, ${MASTERY_THRESHOLD}% required`}
    >
      <circle
        cx="18"
        cy="18"
        r={radius}
        fill="none"
        strokeWidth="3"
        className={isSelected ? 'stroke-amber-100' : 'stroke-slate-100'}
      />
      <circle
        cx="18"
        cy="18"
        r={radius}
        fill="none"
        strokeWidth="3"
        strokeLinecap="round"
        strokeDasharray={`${dash} ${circumference}`}
        className="stroke-amber-500 transition-[stroke-dasharray] duration-500"
      />
    </svg>
  );
}

/**
 * Custom React Flow node for one skill.
 *
 * ## Handle conventions
 * `target` sits on TOP and `source` on the BOTTOM so edges flow downward from
 * prerequisite to dependent, matching dagre's `'TB'` rank direction.
 *
 * Both handles are `opacity-0`, never `display: none` — React Flow cannot route
 * an edge to a handle that is not laid out, and `display: none` removes it.
 *
 * Memoised because React Flow re-renders every node whenever any node's
 * selection changes; at 50+ nodes that is the difference between a smooth map
 * and a stuttering one.
 */
export const SkillNode = memo(function SkillNode({
  data,
  selected,
}: NodeProps<SkillNodeType>): ReactElement {
  const status: KnowledgeNodeStatus =
    (data['derivedStatus'] as KnowledgeNodeStatus | undefined) ??
    (data.status as KnowledgeNodeStatus);

  const visuals: NodeVisuals = NODE_VISUALS[status];
  const isLocked: boolean = status === 'LOCKED';
  const score: number = Number.isFinite(data.masteryScore) ? data.masteryScore : 0;

  return (
    <div
      className={cn(
        /* `nodrag` stops a click-drag from moving a computed node. */
        'nodrag nopan w-[220px] rounded-[--radius-card] border-2 p-3 shadow-subtle transition-all',
        visuals.container,
        selected === true && 'ring-2 ring-brand-500 ring-offset-2',
        /* Desaturation reads as "not yet available". */
        isLocked && 'opacity-75 saturate-0',
      )}
      aria-label={`${visuals.label}: ${data.title}, mastery ${score}%`}
    >
      {/* Incoming prerequisite edge. */}
      <Handle
        type="target"
        position={Position.Top}
        isConnectable={false}
        className="!size-2.5 !border-2 !border-white !bg-slate-300 opacity-0"
      />

      <div className="flex items-start gap-2.5">
        <div className="min-w-0 flex-1">
          <p className="text-[0.65rem] font-medium uppercase tracking-wide text-slate-400">
            {data.topic}
          </p>
          <p
            className={cn(
              'mt-0.5 line-clamp-2 text-sm font-semibold leading-snug',
              visuals.title,
            )}
          >
            {data.title}
          </p>
        </div>

        {/* Status affordance — a distinct icon per state. */}
        {status === 'MASTERED' && (
          <span
            className={cn('flex size-6 shrink-0 items-center justify-center rounded-full', visuals.badge)}
            aria-hidden="true"
          >
            <Check className="size-3.5" strokeWidth={3} />
          </span>
        )}

        {status === 'LOCKED' && (
          <span
            className={cn('flex size-6 shrink-0 items-center justify-center rounded-full', visuals.badge)}
            aria-hidden="true"
          >
            <Lock className="size-3" />
          </span>
        )}

        {status === 'IN_PROGRESS' && (
          <span
            className="relative flex shrink-0 items-center justify-center"
            aria-hidden="true"
          >
            {/* Pulsing halo communicates "actively working on this". */}
            <span className="absolute size-6 animate-ping rounded-full bg-amber-400 opacity-40" />
            <span
              className={cn('relative flex size-6 items-center justify-center rounded-full', visuals.badge)}
            >
              <span className="size-2 rounded-full bg-amber-500" />
            </span>
          </span>
        )}
      </div>

      {/* Progress + footer */}
      <div className="mt-2.5 flex items-center gap-2">
        {status === 'IN_PROGRESS' && <ProgressRing score={score} isSelected={selected === true} />}

        <div className="min-w-0 flex-1">
          <p
            className={cn(
              'truncate text-[0.65rem] font-medium',
              isLocked ? 'text-slate-400' : 'text-slate-500',
            )}
          >
            {status === 'MASTERED'
              ? `Mastered · ${score}%`
              : status === 'LOCKED'
                ? visuals.label
                : `${score}% of ${MASTERY_THRESHOLD}%`}
          </p>

          {data.relatedLessonCount > 0 && (
            <p className="truncate text-[0.6rem] text-slate-400">
              {data.relatedLessonCount} lesson{data.relatedLessonCount === 1 ? '' : 's'}
            </p>
          )}
        </div>
      </div>

      {/* Outgoing dependent edges. */}
      <Handle
        type="source"
        position={Position.Bottom}
        isConnectable={false}
        className="!size-2.5 !border-2 !border-white !bg-brand-400 opacity-0"
      />
    </div>
  );
});
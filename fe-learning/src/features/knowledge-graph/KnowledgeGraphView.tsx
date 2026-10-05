import {
  Background,
  BackgroundVariant,
  Controls,
  MarkerType,
  MiniMap,
  Panel,
  ReactFlow,
  ReactFlowProvider,
  useReactFlow,
  type Edge as FlowEdge,
  type Node as FlowNode,
  type NodeTypes,
} from '@xyflow/react';
import { AlertTriangle, Search, X } from 'lucide-react';
import { useCallback, useEffect, useMemo, useState, type ReactElement } from 'react';

import { Badge } from '@/components/ui/badge';
import { NodeDetailDrawer } from '@/features/knowledge-graph/components/NodeDetailDrawer';
import { SkillNode } from '@/features/knowledge-graph/components/SkillNode';
import {
  MOCK_KNOWLEDGE_NODES,
  MOCK_LESSONS_BY_NODE,
  MOCK_PREREQUISITE_EDGES,
} from '@/features/knowledge-graph/mock/mockKnowledgeGraph';
import { computeLayout } from '@/features/knowledge-graph/utils/layoutEngine';
import {
  deriveAllStatuses,
  type KnowledgeNodeData,
  type KnowledgeNodeDetail,
  type KnowledgeNodeStatus,
  type RelatedLesson,
  type SkillEdge,
  type SkillNode as SkillNodeType,
} from '@/features/knowledge-graph/types';
import { cn } from '@/lib/utils';

/*
 * REQUIRED: React Flow ships no styles of its own beyond this file.
 * Without it, EDGES SILENTLY DO NOT RENDER.
 */
import '@xyflow/react/dist/style.css';

/**
 * Node-type registry.
 *
 * MUST be module scope, never built inside the component. React Flow compares
 * this object by reference; recreating it on every render remounts every node,
 * which destroys selection state and tanks performance at 50+ nodes.
 */
const NODE_TYPES: NodeTypes = { skill: SkillNode };

/** MiniMap tints, mirroring `NODE_VISUALS` so the legend stays consistent. */
const MINIMAP_COLOURS: Record<KnowledgeNodeStatus, string> = {
  LOCKED: '#cbd5e1',
  IN_PROGRESS: '#fbbf24',
  MASTERED: '#34d399',
};

const SEARCHABLE_FIELDS = ['title', 'topic', 'description'] as const;

/** Builds the drawer payload for one node. */
const buildDetail = (
  node: KnowledgeNodeData,
  allNodes: ReadonlyMap<string, KnowledgeNodeData>,
  statuses: ReadonlyMap<string, KnowledgeNodeStatus>,
  lessonsByNodeId: Readonly<Record<string, readonly RelatedLesson[]>>,
): KnowledgeNodeDetail => {
  const derivedStatus: KnowledgeNodeStatus = statuses.get(node.id) ?? 'LOCKED';

  const prerequisiteDetails = node.prerequisites
    .map((id: string) => {
      const prerequisite = allNodes.get(id);
      return {
        id,
        title: prerequisite?.title ?? 'Kỹ năng đã lưu trữ',
        /*
         * An unknown id is treated as satisfied — `deriveNodeStatus` already
         * decided the node is not blocked by it, so the drawer must agree.
         */
        status: statuses.get(id) ?? ('MASTERED' as KnowledgeNodeStatus),
      };
    })
    /* Unmet requirements first: they are what the learner must act on. */
    .sort((a, b) => Number(a.status !== 'MASTERED') - Number(b.status !== 'MASTERED'));

  const blockedBy = prerequisiteDetails
    .filter((prerequisite) => prerequisite.status !== 'MASTERED')
    .map((prerequisite) => prerequisite.title);

  const unlocks = [...allNodes.values()]
    .filter((candidate) => candidate.prerequisites.includes(node.id))
    .map((candidate) => candidate.id);

  return {
    ...node,
    derivedStatus,
    prerequisiteDetails,
    unlocks,
    blockedBy,
    lessons: [...(lessonsByNodeId[node.id] ?? [])],
  };
};

export interface KnowledgeGraphViewProps {
  nodes?: readonly KnowledgeNodeData[];
  lessonsByNodeId?: Readonly<Record<string, readonly RelatedLesson[]>>;
  prerequisiteEdges?: readonly { source: string; target: string }[];
  onContinueLesson?: (detail: KnowledgeNodeDetail) => void;
  onTakeDiagnostic?: (detail: KnowledgeNodeDetail) => void;
}

export function KnowledgeGraphView({
  nodes = MOCK_KNOWLEDGE_NODES,
  lessonsByNodeId = MOCK_LESSONS_BY_NODE,
  prerequisiteEdges = MOCK_PREREQUISITE_EDGES,
  onContinueLesson,
  onTakeDiagnostic,
}: KnowledgeGraphViewProps): ReactElement {
  const [searchTerm, setSearchTerm] = useState<string>('');
  const [selectedId, setSelectedId] = useState<string | null>(null);
  const { fitView } = useReactFlow();

  const allNodes: ReadonlyMap<string, KnowledgeNodeData> = useMemo(
    () => new Map(nodes.map((node) => [node.id, node])),
    [nodes],
  );

  const statuses: ReadonlyMap<string, KnowledgeNodeStatus> = useMemo(
    () => deriveAllStatuses(nodes),
    [nodes],
  );

  /*
   * Layout runs ONCE per data change, never per render. Running dagre in the
   * render body would recompute the whole graph on every selection change and
   * visibly stutter at 50+ nodes.
   */
  const layout = useMemo(
    () => computeLayout(nodes, prerequisiteEdges),
    [nodes, prerequisiteEdges],
  );

  /** Ids matching the current search; empty when no search is active. */
  const matchedIds: ReadonlySet<string> = useMemo(() => {
    const query: string = searchTerm.trim().toLowerCase();
    if (query.length === 0) {
      return new Set<string>();
    }

    return new Set(
      nodes
        .filter((node) =>
          SEARCHABLE_FIELDS.some((field) => node[field].toLowerCase().includes(query)),
        )
        .map((node) => node.id),
    );
  }, [searchTerm, nodes]);

  const isSearching: boolean = searchTerm.trim().length > 0;

  const flowNodes: SkillNodeType[] = useMemo(
    () =>
      nodes.map((node) => {
        const position = layout.positions.get(node.id) ?? { x: 0, y: 0 };
        const status: KnowledgeNodeStatus = statuses.get(node.id) ?? 'LOCKED';
        const isMatch: boolean = !isSearching || matchedIds.has(node.id);

        return {
          id: node.id,
          type: 'skill',
          position,
          data: { ...node, derivedStatus: status },
          selected: node.id === selectedId,
          /* Dim non-matches rather than removing them, to preserve context. */
          className: cn('transition-opacity', isSearching && !isMatch && 'opacity-20'),
          ariaLabel: `${node.title}, ${status.toLowerCase()}`,
        };
      }),
    [nodes, layout.positions, statuses, isSearching, matchedIds, selectedId],
  );

  const flowEdges: SkillEdge[] = useMemo(() => {
    const known: ReadonlySet<string> = new Set(nodes.map((node) => node.id));

    return prerequisiteEdges
      /* Drop dangling edges; React Flow warns and renders nothing for them. */
      .filter((edge) => known.has(edge.source) && known.has(edge.target))
      .map((edge) => {
        const dependent = statuses.get(edge.target) ?? 'LOCKED';
        const isSatisfied = statuses.get(edge.source) === 'MASTERED';
        const colour: string = isSatisfied ? '#a5b4fc' : '#e2e8f0';

        return {
          id: `${edge.source}->${edge.target}`,
          source: edge.source,
          target: edge.target,
          data: { prerequisiteId: edge.source },
          type: 'smoothstep',
          /* Only animate edges that are actually unblocking something. */
          animated: isSatisfied && dependent !== 'LOCKED',
          style: {
            stroke: colour,
            strokeWidth: 1.5,
            /* Dashed reads as "requirement not yet met". */
            strokeDasharray: isSatisfied ? undefined : '4 4',
          },
          markerEnd: {
            type: MarkerType.ArrowClosed,
            width: 14,
            height: 14,
            color: colour,
          },
        };
      });
  }, [prerequisiteEdges, nodes, statuses]);

  const detail: KnowledgeNodeDetail | null = useMemo(() => {
    if (selectedId === null) {
      return null;
    }
    const node = allNodes.get(selectedId);
    return node === undefined
      ? null
      : buildDetail(node, allNodes, statuses, lessonsByNodeId);
  }, [selectedId, allNodes, statuses, lessonsByNodeId]);

  const counts: Record<KnowledgeNodeStatus, number> = useMemo(() => {
    const tally: Record<KnowledgeNodeStatus, number> = {
      MASTERED: 0,
      IN_PROGRESS: 0,
      LOCKED: 0,
    };
    for (const status of statuses.values()) {
      tally[status] += 1;
    }
    return tally;
  }, [statuses]);

  /** Frame a lone search match so the learner is not left hunting for it. */
  useEffect(() => {
    if (matchedIds.size !== 1) {
      return undefined;
    }
    const [firstMatch] = [...matchedIds];
    const timer = window.setTimeout(() => {
      fitView({ nodes: [{ id: String(firstMatch) }], duration: 400, maxZoom: 1 });
    }, 120);
    return () => window.clearTimeout(timer);
  }, [matchedIds, fitView]);

  /** Selecting a node frames it — used by the drawer's prerequisite links. */
  const handleSelectNode = useCallback(
    (nodeId: string): void => {
      setSelectedId(nodeId);
      window.setTimeout(() => {
        fitView({ nodes: [{ id: nodeId }], duration: 400, maxZoom: 1.1 });
      }, 120);
    },
    [fitView],
  );

  const clearSelection = useCallback((): void => setSelectedId(null), []);

  return (
    <div className="flex h-full w-full flex-col bg-canvas">
      <ReactFlow
        nodes={flowNodes as unknown as FlowNode[]}
        edges={flowEdges as unknown as FlowEdge[]}
        nodeTypes={NODE_TYPES}
        onNodeClick={(_event: unknown, node: { id: string }) => setSelectedId(node.id)}
        onPaneClick={clearSelection}
        fitView
        fitViewOptions={{ padding: 0.2, maxZoom: 1 }}
        minZoom={0.2}
        maxZoom={1.8}
        /* Positions are computed; dragging would fight the layout engine. */
        nodesDraggable={false}
        nodesConnectable={false}
        elementsSelectable
        /* Backspace must not silently delete the learner's skill tree. */
        deleteKeyCode={null}
        /* Wheel scrolls the page; Ctrl+wheel still zooms. */
        zoomOnScroll={false}
        panOnScroll
        panOnDrag
        selectionOnDrag={false}
        proOptions={{ hideAttribution: true }}
        className="bg-canvas"
      >
        <Background variant={BackgroundVariant.Dots} gap={22} size={1.5} color="#e2e8f0" />
        <Controls showInteractive={false} position="bottom-right" />
        <MiniMap
          pannable
          zoomable
          position="bottom-left"
          nodeColor={(node: FlowNode): string => {
            const status = (node.data as { derivedStatus?: KnowledgeNodeStatus }).derivedStatus;
            return MINIMAP_COLOURS[status ?? 'LOCKED'];
          }}
          nodeStrokeWidth={0}
          maskColor="rgb(248 250 252 / 0.75)"
          className="!bg-white"
        />

        {/* Search + status tally */}
        <Panel position="top-left" className="!m-0 !p-4">
          <div className="flex items-center gap-2">
            <div className="relative">
              <Search
                className="pointer-events-none absolute left-2.5 top-1/2 size-4 -translate-y-1/2 text-slate-400"
                aria-hidden="true"
              />
              <input
                type="search"
                value={searchTerm}
                onChange={(event) => setSearchTerm(event.target.value)}
                placeholder="Tìm kiếm kỹ năng…"
                aria-label="Tìm kiếm kỹ năng"
                className="h-9 w-56 rounded-[--radius-field] border border-slate-200 bg-white pl-8 pr-8 text-sm shadow-subtle focus-visible:border-brand-400 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-brand-500/25"
              />
              {isSearching && (
                <button
                  type="button"
                  onClick={() => setSearchTerm('')}
                  className="absolute right-2 top-1/2 -translate-y-1/2 rounded p-0.5 text-slate-400 hover:text-slate-700"
                  aria-label="Xoá nội dung tìm kiếm"
                >
                  <X className="size-3.5" aria-hidden="true" />
                </button>
              )}
            </div>

            <div className="hidden items-center gap-1.5 sm:flex">
              <Badge variant="success">{counts.MASTERED} đã thành thạo</Badge>
              <Badge variant="warning">{counts.IN_PROGRESS} đang học</Badge>
              <Badge variant="neutral">{counts.LOCKED} chưa mở khoá</Badge>
            </div>
          </div>

          {/* Cycle warning: an upstream data bug, surfaced rather than hidden. */}
          {layout.cycleDetected && (
            <p
              role="alert"
              className="mt-2 flex max-w-md items-start gap-1.5 rounded-[--radius-field] border border-amber-200 bg-amber-50 p-2 text-xs text-amber-800"
            >
              <AlertTriangle className="mt-0.5 size-3.5 shrink-0" aria-hidden="true" />
              {layout.cycleIds.length > 0
                ? `Phát hiện vòng lặp tiền kiến (${layout.cycleIds.join(' → ')}). Đang hiển thị bố cục đơn giản hơn.`
                : 'Một số kỹ năng không thể được bố trí tự động. Đang hiển thị bố cục đơn giản hơn.'}
            </p>
          )}
        </Panel>
      </ReactFlow>

      <NodeDetailDrawer
        detail={detail}
        onOpenChange={(open: boolean) => {
          if (!open) {
            clearSelection();
          }
        }}
        onContinueLesson={onContinueLesson ?? ((): void => undefined)}
        onTakeDiagnostic={onTakeDiagnostic ?? ((): void => undefined)}
        onSelectNode={handleSelectNode}
      />
    </div>
  );
}

/**
 * Public entry point.
 *
 * `ReactFlowProvider` must wrap the component that calls `useReactFlow`, so it
 * lives here rather than inside the view.
 */
export function KnowledgeGraphViewProvider(props: KnowledgeGraphViewProps): ReactElement {
  return (
    <ReactFlowProvider>
      <KnowledgeGraphView {...props} />
    </ReactFlowProvider>
  );
}

export default KnowledgeGraphViewProvider;
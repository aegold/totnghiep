import dagre from '@dagrejs/dagre';
import { Position, type Edge } from '@xyflow/react';

import { findPrerequisiteCycle } from '@/features/knowledge-graph/types';
import type { KnowledgeNodeData } from '@/features/knowledge-graph/types';

/**
 * DAG layout engine.
 *
 * Wraps `@dagrejs/dagre` to compute top-to-bottom positions for the skill tree.
 * Deliberately free of React so it can be unit-tested directly.
 *
 * ## The centre-origin trap
 * dagre reports a node's position at its CENTRE. React Flow positions nodes by
 * their top-left corner by default (`nodeOrigin = [0, 0]`). Returning dagre's
 * numbers unchanged offsets the whole graph by half a node on both axes — nodes
 * drift down-right and the bounding box is wrong. `computeLayout` subtracts
 * half the node dimensions to compensate.
 */

/** Default rendered node box; must match `SkillNode`'s real dimensions. */
export const DEFAULT_NODE_WIDTH = 220;
export const DEFAULT_NODE_HEIGHT = 88;

export interface LayoutOptions {
  nodeWidth?: number;
  nodeHeight?: number;
  /** `'TB'` renders prerequisites above dependents (the learning direction). */
  rankDir?: 'TB' | 'BT' | 'LR' | 'RL';
  /** Horizontal gap between nodes in the same rank. */
  nodesep?: number;
  /** Vertical gap between ranks. */
  ranksep?: number;
  marginx?: number;
  marginy?: number;
}

export interface LayoutResult {
  /** Node id -> top-left position in flow coordinates. */
  positions: ReadonlyMap<string, { x: number; y: number }>;
  /**
   * True when the graph could not be laid out by dagre and the fallback ran.
   * A cycle is an upstream data bug; the caller should surface it.
   */
  cycleDetected: boolean;
  /** Ids involved in the cycle, when one was found. */
  cycleIds: readonly string[];
}

/**
 * Minimal structural view of a node.
 * Avoids depending on the React Flow `Node` generic, which is irrelevant here.
 */
export interface LayoutNode {
  id: string;
  prerequisites: string[];
}

/** Position at which a node's outgoing edge leaves, per rank direction. */
export const toHandlePosition = (rankDir: LayoutOptions['rankDir']): Position => {
  switch (rankDir) {
    case 'BT':
      return Position.Top;
    case 'LR':
      return Position.Left;
    case 'RL':
      return Position.Right;
    case 'TB':
    default:
      /* Prerequisites flow downward into the dependent below. */
      return Position.Bottom;
  }
};

/**
 * Fallback layout for a cyclic or otherwise un-layable graph.
 *
 * Packs nodes into a grid ordered by prerequisite depth. It is not beautiful,
 * but it renders every node at a distinct, stable position — far better than a
 * thrown error blanking the whole view.
 */
const fallbackLayout = (
  nodes: readonly LayoutNode[],
  nodeWidth: number,
  nodeHeight: number,
): ReadonlyMap<string, { x: number; y: number }> => {
  /** Longest prerequisite chain above `id`, cycle-guarded. */
  const depthOf = (id: string, seen: ReadonlySet<string>): number => {
    if (seen.has(id)) {
      return 0;
    }
    const next: ReadonlySet<string> = new Set([...seen, id]);
    const node = nodes.find((candidate) => candidate.id === id);
    const parents: string[] = node?.prerequisites ?? [];

    return parents.length === 0
      ? 0
      : 1 + Math.max(...parents.map((parent) => depthOf(parent, next)));
  };

  /* Deepest first, so prerequisites sit above their dependents. */
  const ordered = [...nodes].sort(
    (a, b) => depthOf(b.id, new Set()) - depthOf(a.id, new Set()),
  );

  const positions = new Map<string, { x: number; y: number }>();
  const columnsPerRow: number = Math.max(1, Math.ceil(Math.sqrt(ordered.length)));

  ordered.forEach((node: LayoutNode, index: number) => {
    const row: number = Math.floor(index / columnsPerRow);
    const column: number = index % columnsPerRow;
    positions.set(node.id, {
      x: column * (nodeWidth + 48),
      y: row * (nodeHeight + 64),
    });
  });

  return positions;
};

/**
 * Computes top-left positions for every node.
 *
 * @param nodes - skill nodes carrying their prerequisite ids
 * @param prerequisiteEdges - edges oriented `prerequisite -> dependent`
 * @returns a position per node, plus cycle diagnostics
 */
export function computeLayout(
  nodes: readonly LayoutNode[],
  prerequisiteEdges: ReadonlyArray<Edge | { source: string; target: string }>,
  options: LayoutOptions = {},
): LayoutResult {
  const {
    nodeWidth = DEFAULT_NODE_WIDTH,
    nodeHeight = DEFAULT_NODE_HEIGHT,
    rankDir = 'TB',
    nodesep = 56,
    ranksep = 96,
    marginx = 24,
    marginy = 24,
  } = options;

  if (nodes.length === 0) {
    return { positions: new Map(), cycleDetected: false, cycleIds: [] };
  }

  /*
   * Detect a cycle BEFORE calling dagre: `dagre.layout()` throws on cyclic
   * input, which would blank the entire graph view.
   */
  const cycleIds: string[] = findPrerequisiteCycle(nodes as readonly KnowledgeNodeData[]);

  if (cycleIds.length > 0) {
    return {
      positions: fallbackLayout(nodes, nodeWidth, nodeHeight),
      cycleDetected: true,
      cycleIds,
    };
  }

  try {
    const graph = new dagre.graphlib.Graph();
    graph.setDefaultEdgeLabel(() => ({}));
    graph.setGraph({ rankdir: rankDir, nodesep, ranksep, marginx, marginy });

    for (const node of nodes) {
      graph.setNode(node.id, { width: nodeWidth, height: nodeHeight });
    }

    /*
     * Only wire edges whose endpoints both exist. A dangling prerequisite id is
     * tolerated by `deriveNodeStatus`; dagre would throw on it here.
     */
    const known: ReadonlySet<string> = new Set(nodes.map((node) => node.id));

    for (const edge of prerequisiteEdges) {
      if (known.has(edge.source) && known.has(edge.target)) {
        graph.setEdge(edge.source, edge.target);
      }
    }

    dagre.layout(graph);

    const positions = new Map<string, { x: number; y: number }>();

    for (const node of nodes) {
      const laidOut: { x: number; y: number } | undefined = graph.node(node.id);

      if (laidOut === undefined) {
        continue;
      }

      /* dagre reports the centre; React Flow expects the top-left corner. */
      positions.set(node.id, {
        x: laidOut.x - nodeWidth / 2,
        y: laidOut.y - nodeHeight / 2,
      });
    }

    return { positions, cycleDetected: false, cycleIds: [] };
  } catch (error: unknown) {
    /*
     * Last-resort guard: a layout engine must never be able to white-screen the
     * view, whatever dagre throws at it.
     */
    console.error('[layoutEngine] dagre layout failed; using fallback.', error);
    return {
      positions: fallbackLayout(nodes, nodeWidth, nodeHeight),
      cycleDetected: true,
      cycleIds: [],
    };
  }
}
import type { Edge, Node } from '@xyflow/react';

/**
 * Knowledge Graph — domain contracts.
 *
 * ## Data model
 *  - `knowledge_nodes`         -> atomic competencies (one row per skill)
 *  - `knowledge_prerequisites` -> prerequisite A is required for B
 *  - `lesson_knowledge_nodes`  -> many-to-many link to lessons
 *
 * ## Adaptive rule
 * A node is unlocked only when EVERY prerequisite is MASTERED, where mastery
 * means a diagnostic score of at least 80%.
 */

/** Mastery threshold, in percent, at which a skill counts as mastered. */
export const MASTERY_THRESHOLD = 80;

/**
 * The three visual/behavioural states of a skill.
 *
 * NOTE: this is DERIVED, never trusted from the API. See `deriveNodeStatus`.
 */
export type KnowledgeNodeStatus = 'LOCKED' | 'IN_PROGRESS' | 'MASTERED';

/**
 * Raw node as authored in the CMS and served by the backend.
 *
 * `status` is accepted for display before derivation and by hosts that already
 * hold a server-computed value, but the UI always derives its own — see the
 * rationale on `deriveNodeStatus`.
 */
export interface KnowledgeNodeData {
  /**
   * React Flow v12 constrains node data to `Record<string, unknown>`, so the
   * interface needs an index signature to be usable as `Node<...>` data.
   */
  [key: string]: unknown;

  /** `knowledge_nodes.id` */
  id: string;
  title: string;
  description: string;
  status: KnowledgeNodeStatus;
  /** 0–100. `>= MASTERY_THRESHOLD` means mastered. */
  masteryScore: number;
  /** Ids of `knowledge_nodes` that must be mastered first. */
  prerequisites: string[];
  /** Count from `lesson_knowledge_nodes`. */
  relatedLessonCount: number;
  /** Curriculum grouping, used for section headers and MiniMap grouping. */
  topic: string;
}

/** A lesson attached to a node via `lesson_knowledge_nodes`. */
export interface RelatedLesson {
  lessonId: number;
  title: string;
  materialType: 'VIDEO' | 'PDF' | 'DOCUMENT' | 'SLIDES';
  /** 0–1; `>= 1` counts as complete. */
  percentComplete: number;
  durationMinutes: number | null;
}

/**
 * A skill plus everything the drawer needs. Built once from the node list plus
 * lessons, so the drawer never fetches on open.
 */
export interface KnowledgeNodeDetail extends KnowledgeNodeData {
  /** Derived status, denormalised so the drawer cannot disagree with the map. */
  derivedStatus: KnowledgeNodeStatus;
  /** Resolved prerequisite titles, in declaration order. */
  prerequisiteDetails: { id: string; title: string; status: KnowledgeNodeStatus }[];
  /** Ids of nodes that list this one as a prerequisite. */
  unlocks: string[];
  lessons: RelatedLesson[];
  /** Titles of unmet prerequisites; empty unless `derivedStatus` is `LOCKED`. */
  blockedBy: string[];
}

/* -------------------------------------------------------------------------- */
/*  React Flow types                                                           */
/* -------------------------------------------------------------------------- */

/**
 * React Flow node bound to our data.
 *
 * The second generic (`'skill'`) names the node type, which is what lets
 * `NodeProps` infer `data` as `KnowledgeNodeData` inside the custom component.
 */
export type SkillNode = Node<KnowledgeNodeData, 'skill'>;

/** Edge carrying which prerequisite relationship it represents. */
export type SkillEdge = Edge<{ prerequisiteId: string }>;

/** Diagram shape consumed by `KnowledgeGraphView`. */
export interface KnowledgeGraphData {
  nodes: KnowledgeNodeData[];
  lessonsByNodeId: Record<string, RelatedLesson[]>;
}

/* -------------------------------------------------------------------------- */
/*  Status derivation                                                          */
/* -------------------------------------------------------------------------- */

const clampScore = (score: number): number =>
  Number.isFinite(score) ? Math.min(100, Math.max(0, score)) : 0;

/**
 * Derives a node's status from its mastery score and its prerequisites.
 *
 * ## Why derive rather than trust `status` from the API
 * If the server-sent status and the prerequisite graph disagree, the UI can show
 * a node as unlocked while a prerequisite beside it is visibly LOCKED.
 * Students notice that contradiction and stop trusting the map. Deriving here
 * makes the invariant unbreakable: a node can never be unlocked unless every
 * prerequisite is mastered.
 *
 * Order matters — mastery is checked FIRST, so a mastered skill whose
 * prerequisites were later un-mastered (e.g. a prerequisite re-assigned)
 * still reads as MASTERED. Rewarding mastery already earned is the fairer
 * behaviour, and matches how the academic committee defines progression.
 *
 * @param node - the node being classified
 * @param allNodes - every node, used to resolve prerequisites recursively
 */
export const deriveNodeStatus = (
  node: KnowledgeNodeData,
  allNodes: ReadonlyMap<string, KnowledgeNodeData>,
): KnowledgeNodeStatus => {
  const score: number = clampScore(node.masteryScore);

  /* Already earned it — never take mastery away. */
  if (score >= MASTERY_THRESHOLD) {
    return 'MASTERED';
  }

  const isUnlocked: boolean = node.prerequisites.every((id: string) => {
    const prerequisite = allNodes.get(id);

    /*
     * A dangling prerequisite id (deleted upstream) must not permanently lock
     * the node — there is nothing the learner could ever do about it, so it
     * counts as satisfied rather than trapping them.
     */
    if (prerequisite === undefined) {
      return true;
    }

    return deriveNodeStatus(prerequisite, allNodes) === 'MASTERED';
  });

  return isUnlocked ? 'IN_PROGRESS' : 'LOCKED';
};

/** Derives status for every node in one call, keyed by id. */
export const deriveAllStatuses = (
  nodes: readonly KnowledgeNodeData[],
): ReadonlyMap<string, KnowledgeNodeStatus> => {
  const byId: ReadonlyMap<string, KnowledgeNodeData> = new Map(
    nodes.map((node) => [node.id, node]),
  );

  const statuses = new Map<string, KnowledgeNodeStatus>();

  for (const node of nodes) {
    statuses.set(node.id, deriveNodeStatus(node, byId));
  }

  return statuses;
};

/**
 * Detects a cycle in the prerequisite graph.
 *
 * Iterative DFS with an explicit stack: recursion depth would equal the longest
 * prerequisite chain, which a deep curriculum can push past the JS call stack.
 * A cycle is an upstream data-integrity bug that makes `dagre.layout()` throw,
 * so the layout engine must detect it before calling dagre.
 *
 * @returns the ids participating in a cycle, or an empty array when acyclic.
 */
export const findPrerequisiteCycle = (
  nodes: readonly KnowledgeNodeData[],
): string[] => {
  const adjacency = new Map<string, string[]>(
    nodes.map((node) => [node.id, node.prerequisites]),
  );

  const WHITE = 0;
  const GREY = 1;
  const BLACK = 2;
  const colour = new Map<string, number>(nodes.map((node) => [node.id, WHITE]));

  for (const node of nodes) {
    if (colour.get(node.id) !== WHITE) {
      continue;
    }

    /* `path` mirrors the call stack so a cycle can be reconstructed. */
    const path: string[] = [node.id];
    const stack: { id: string; index: number }[] = [{ id: node.id, index: 0 }];
    colour.set(node.id, GREY);

    while (stack.length > 0) {
      const frame = stack[stack.length - 1] as { id: string; index: number };
      const edges: string[] = adjacency.get(frame.id) ?? [];

      if (frame.index >= edges.length) {
        colour.set(frame.id, BLACK);
        stack.pop();
        path.pop();
        continue;
      }

      const next: string | undefined = edges[frame.index];
      frame.index += 1;

      if (next === undefined || !adjacency.has(next)) {
        continue;
      }

      const state: number | undefined = colour.get(next);

      if (state === GREY) {
        /* Back edge: everything from `next` onward is the cycle. */
        const start: number = path.indexOf(next);
        return start === -1 ? [next] : path.slice(start);
      }

      if (state === WHITE) {
        colour.set(next, GREY);
        path.push(next);
        stack.push({ id: next, index: 0 });
      }
    }
  }

  return [];
};
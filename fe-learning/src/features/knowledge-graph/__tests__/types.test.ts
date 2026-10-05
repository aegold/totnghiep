import { describe, expect, it } from 'vitest';

import {
  deriveAllStatuses,
  deriveNodeStatus,
  findPrerequisiteCycle,
  MASTERY_THRESHOLD,
  type KnowledgeNodeData,
} from '@/features/knowledge-graph/types';

/** Builds a node with sensible defaults so each test states only what matters. */
const node = (
  id: string,
  prerequisites: string[],
  masteryScore: number,
): KnowledgeNodeData => ({
  id,
  title: id,
  description: '',
  /* Deliberately contradictory — derivation must ignore this field. */
  status: 'LOCKED',
  masteryScore,
  prerequisites,
  relatedLessonCount: 0,
  topic: 'Test',
});

const index = (nodes: KnowledgeNodeData[]): Map<string, KnowledgeNodeData> =>
  new Map(nodes.map((n) => [n.id, n]));

describe('deriveNodeStatus', () => {
  it('masters a node at exactly the threshold', () => {
    const nodes = [node('a', [], MASTERY_THRESHOLD)];
    expect(deriveNodeStatus(nodes[0] as KnowledgeNodeData, index(nodes))).toBe('MASTERED');
  });

  it('does not master one point below the threshold', () => {
    const nodes = [node('a', [], MASTERY_THRESHOLD - 1)];
    expect(deriveNodeStatus(nodes[0] as KnowledgeNodeData, index(nodes))).toBe('IN_PROGRESS');
  });

  it('locks a node whose prerequisite is not mastered', () => {
    const nodes = [node('prereq', [], 40), node('target', ['prereq'], 0)];
    const map = index(nodes);

    expect(deriveNodeStatus(nodes[1] as KnowledgeNodeData, map)).toBe('LOCKED');
  });

  it('unlocks a node once every prerequisite is mastered', () => {
    const nodes = [
      node('p1', [], 90),
      node('p2', [], 95),
      node('target', ['p1', 'p2'], 0),
    ];
    const map = index(nodes);

    expect(deriveNodeStatus(nodes[2] as KnowledgeNodeData, map)).toBe('IN_PROGRESS');
  });

  it('resolves prerequisites transitively through several levels', () => {
    /* c unlocked only if b is mastered, b only if a is. */
    const nodes = [
      node('a', [], 100),
      node('b', ['a'], 100),
      node('c', ['b'], 0),
    ];
    const map = index(nodes);

    expect(deriveNodeStatus(nodes[2] as KnowledgeNodeData, map)).toBe('IN_PROGRESS');
  });

  it('locks a node when a transitive prerequisite is unmet', () => {
    /*
     * `b` depends on `a`, and `c` depends on `b`. Because `b` has NOT met the
     * threshold, `c` is locked — and crucially `c` must not read `b`'s raw
     * score, only its derived status.
     */
    const nodes = [
      node('a', [], 10),
      node('b', ['a'], 50),
      node('c', ['b'], 0),
    ];
    const map = index(nodes);

    expect(deriveNodeStatus(nodes[2] as KnowledgeNodeData, map)).toBe('LOCKED');
  });

  it('does not permanently lock on a dangling prerequisite id', () => {
    /*
     * The prerequisite was deleted upstream. There is nothing the learner could
     * ever do about it, so the node must remain reachable.
     */
    const nodes = [node('orphan', ['deleted-upstream'], 30)];

    expect(deriveNodeStatus(nodes[0] as KnowledgeNodeData, index(nodes))).toBe('IN_PROGRESS');
  });

  it('never revokes mastery that was already earned', () => {
    const nodes = [node('prereq', [], 10), node('earned', ['prereq'], 92)];
    const map = index(nodes);

    expect(deriveNodeStatus(nodes[1] as KnowledgeNodeData, map)).toBe('MASTERED');
  });

  it('ignores the server-supplied status field entirely', () => {
    /* Claims MASTERED at 10% — derivation must not be fooled. */
    const liar: KnowledgeNodeData = { ...node('a', [], 10), status: 'MASTERED' };

    expect(deriveNodeStatus(liar, index([liar]))).toBe('IN_PROGRESS');
  });

  it('clamps out-of-range scores', () => {
    const nodes = [node('high', [], 250), node('low', [], -40)];
    const map = index(nodes);

    expect(deriveNodeStatus(nodes[0] as KnowledgeNodeData, map)).toBe('MASTERED');
    expect(deriveNodeStatus(nodes[1] as KnowledgeNodeData, map)).toBe('IN_PROGRESS');
  });
});

describe('deriveAllStatuses', () => {
  it('derives every node in one pass', () => {
    const nodes = [
      node('a', [], 95),
      node('b', ['a'], 50),
      node('c', ['b'], 0),
      node('orphan', ['nope'], 0),
    ];

    const statuses = deriveAllStatuses(nodes);

    expect(statuses.get('a')).toBe('MASTERED');
    /* Unlocked by `a`, but only 50% itself. */
    expect(statuses.get('b')).toBe('IN_PROGRESS');
    /* Still blocked, because `b` is not mastered. */
    expect(statuses.get('c')).toBe('LOCKED');
    expect(statuses.get('orphan')).toBe('IN_PROGRESS');
  });
});

describe('findPrerequisiteCycle', () => {
  it('returns empty for an acyclic graph', () => {
    const nodes = [node('a', [], 0), node('b', ['a'], 0), node('c', ['b'], 0)];

    expect(findPrerequisiteCycle(nodes)).toEqual([]);
  });

  it('detects a direct self-loop', () => {
    expect(findPrerequisiteCycle([node('a', ['a'], 0)])).toEqual(['a']);
  });

  it('detects a two-node cycle', () => {
    expect(findPrerequisiteCycle([node('a', ['b'], 0), node('b', ['a'], 0)])).toEqual([
      'a',
      'b',
    ]);
  });

  it('detects a longer cycle', () => {
    const nodes = [
      node('a', ['c'], 0),
      node('b', ['a'], 0),
      node('c', ['b'], 0),
    ];

    expect(findPrerequisiteCycle(nodes)).toHaveLength(3);
  });

  it('ignores dangling ids when checking for cycles', () => {
    expect(findPrerequisiteCycle([node('a', ['ghost'], 0)])).toEqual([]);
  });

  it('handles a 5000-node chain without blowing the call stack', () => {
    /*
     * Regression guard: a recursive DFS would overflow here, since recursion
     * depth equals the longest prerequisite chain.
     */
    const nodes: KnowledgeNodeData[] = Array.from({ length: 5_000 }, (_unused, index) =>
      node(`n${index}`, index === 0 ? [] : [`n${index - 1}`], 0),
    );

    expect(findPrerequisiteCycle(nodes)).toEqual([]);
  });
});
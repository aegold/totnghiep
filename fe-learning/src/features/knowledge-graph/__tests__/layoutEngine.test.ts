import { describe, expect, it } from 'vitest';

import {
  computeLayout,
  DEFAULT_NODE_HEIGHT,
  DEFAULT_NODE_WIDTH,
  toHandlePosition,
  type LayoutNode,
} from '@/features/knowledge-graph/utils/layoutEngine';

const node = (id: string, prerequisites: string[] = []): LayoutNode => ({
  id,
  prerequisites,
});

/** Edges oriented prerequisite -> dependent. */
const edges = (pairs: [string, string][]): { source: string; target: string }[] =>
  pairs.map(([source, target]) => ({ source, target }));

describe('computeLayout', () => {
  it('assigns a position to every node', () => {
    const nodes = [node('a'), node('b', ['a']), node('c', ['b'])];

    const { positions } = computeLayout(nodes, edges([['a', 'b'], ['b', 'c']]));

    expect(positions.size).toBe(3);
    for (const id of ['a', 'b', 'c']) {
      expect(positions.has(id)).toBe(true);
    }
  });

  it('places prerequisites above their dependents (rankdir TB)', () => {
    const nodes = [node('a'), node('b', ['a']), node('c', ['b'])];

    const { positions } = computeLayout(nodes, edges([['a', 'b'], ['b', 'c']]));

    const a = positions.get('a');
    const b = positions.get('b');
    const c = positions.get('c');

    /* Strictly increasing Y: a -> b -> c flows downward. */
    expect(a?.y).toBeLessThan(b?.y ?? 0);
    expect(b?.y).toBeLessThan(c?.y ?? 0);
  });

  it('keeps siblings on the same rank', () => {
    const nodes = [node('root'), node('left', ['root']), node('right', ['root'])];

    const { positions } = computeLayout(nodes, edges([['root', 'left'], ['root', 'right']]));

    /* Same rank => identical Y, different X. */
    expect(positions.get('left')?.y).toBe(positions.get('right')?.y);
    expect(positions.get('left')?.x).not.toBe(positions.get('right')?.x);
  });

  it('converts dagre centre coordinates to top-left origin', () => {
    const nodes = [node('only')];

    const { positions } = computeLayout(nodes, []);

    const position = positions.get('only');
    expect(position).toBeDefined();

    /*
     * With a single node dagre centres it at (width/2, height/2) plus the
     * margin. Converting to top-left must subtract exactly half the node.
     */
    expect(position?.x).toBe(24);
    expect(position?.y).toBe(24);
  });

  it('respects a custom node size', () => {
    const nodes = [node('only')];

    const { positions } = computeLayout(nodes, [], {
      nodeWidth: 300,
      nodeHeight: 120,
      marginx: 0,
      marginy: 0,
    });

    expect(positions.get('only')?.x).toBe(0);
    expect(positions.get('only')?.y).toBe(0);
  });

  it('falls back instead of throwing when the graph has a cycle', () => {
    const nodes = [node('a', ['b']), node('b', ['a'])];

    const result = computeLayout(nodes, edges([['a', 'b'], ['b', 'a']]));

    expect(result.cycleDetected).toBe(true);
    expect(result.cycleIds.length).toBeGreaterThan(0);
    /* Every node must still get a position, or the view would be blank. */
    expect(result.positions.size).toBe(2);
  });

  it('still positions every node in a cyclic graph', () => {
    const nodes = [node('a', ['b']), node('b', ['a']), node('c', ['a'])];

    const { positions } = computeLayout(nodes, []);

    expect(positions.size).toBe(3);
  });

  it('ignores edges referencing unknown nodes', () => {
    const nodes = [node('a'), node('b', ['ghost'])];

    const { positions, cycleDetected } = computeLayout(nodes, [
      { source: 'ghost', target: 'b' },
    ]);

    expect(cycleDetected).toBe(false);
    expect(positions.size).toBe(2);
  });

  it('handles an empty graph', () => {
    const result = computeLayout([], []);

    expect(result.positions.size).toBe(0);
    expect(result.cycleDetected).toBe(false);
  });

  it('handles isolated nodes with no edges', () => {
    const nodes = [node('a'), node('b'), node('c')];

    const { positions, cycleDetected } = computeLayout(nodes, []);

    expect(cycleDetected).toBe(false);
    expect(positions.size).toBe(3);
  });

  it('lays out 50+ nodes without colliding positions', () => {
    const nodes: LayoutNode[] = Array.from({ length: 60 }, (_unused, index) =>
      node(`n${index}`, index === 0 ? [] : [`n${index - 1}`]),
    );

    const pairs = nodes.slice(1).map((n, index) => [`n${index}`, n.id] as [string, string]);
    const { positions } = computeLayout(nodes, edges(pairs));

    expect(positions.size).toBe(60);

    /* Positions must be unique, or nodes would render on top of each other. */
    const keys = [...positions.values()].map((p) => `${Math.round(p.x)}:${Math.round(p.y)}`);
    expect(new Set(keys).size).toBe(keys.length);
  });

  it('exports node dimensions that match the rendered node', () => {
    expect(DEFAULT_NODE_WIDTH).toBe(220);
    expect(DEFAULT_NODE_HEIGHT).toBe(88);
  });
});

describe('toHandlePosition', () => {
  it('maps each rank direction to the outgoing handle side', () => {
    expect(toHandlePosition('TB')).toBe('bottom');
    expect(toHandlePosition('BT')).toBe('top');
    expect(toHandlePosition('LR')).toBe('left');
    expect(toHandlePosition('RL')).toBe('right');
  });

  it('defaults to bottom for an unspecified direction', () => {
    expect(toHandlePosition(undefined)).toBe('bottom');
  });
});
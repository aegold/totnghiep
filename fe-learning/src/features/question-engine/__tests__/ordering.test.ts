import { describe, expect, it } from 'vitest';

import { moveItem } from '@/features/question-engine/renderers/OrderingRenderer';

const BASE: readonly string[] = ['a', 'b', 'c', 'd'];

describe('moveItem', () => {
  it('moves an item forward', () => {
    expect(moveItem(BASE, 0, 2)).toEqual(['b', 'c', 'a', 'd']);
  });

  it('moves an item backward', () => {
    expect(moveItem(BASE, 3, 1)).toEqual(['a', 'd', 'b', 'c']);
  });

  it('never mutates the source array', () => {
    const source: string[] = [...BASE];
    const result: string[] = moveItem(source, 0, 3);

    expect(source).toEqual(['a', 'b', 'c', 'd']);
    expect(result).not.toBe(source);
  });

  it('returns a new array even when the move is a no-op', () => {
    const result: string[] = moveItem(BASE, 1, 1);

    expect(result).toEqual([...BASE]);
    expect(result).not.toBe(BASE);
  });

  it('preserves length and full membership', () => {
    const result: string[] = moveItem(BASE, 0, 3);

    expect(result).toHaveLength(BASE.length);
    expect([...result].sort()).toEqual([...BASE].sort());
  });

  it('ignores out-of-range indices instead of corrupting the array', () => {
    expect(moveItem(BASE, -1, 2)).toEqual([...BASE]);
    expect(moveItem(BASE, 0, 99)).toEqual([...BASE]);
    expect(moveItem(BASE, 9, 0)).toEqual([...BASE]);
  });

  it('handles an empty list', () => {
    expect(moveItem([], 0, 1)).toEqual([]);
  });
});
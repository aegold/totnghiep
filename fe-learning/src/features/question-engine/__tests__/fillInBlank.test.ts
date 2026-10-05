import { describe, expect, it } from 'vitest';

import { parsePromptSegments } from '@/features/question-engine/renderers/FillInBlankRenderer';

describe('parsePromptSegments', () => {
  it('extracts blanks and keeps the surrounding text', () => {
    const segments = parsePromptSegments('The capital of France is {{city}}.');

    expect(segments).toEqual([
      { kind: 'text', content: 'The capital of France is ' },
      { kind: 'blank', blankId: 'city' },
      { kind: 'text', content: '.' },
    ]);
  });

  it('handles several blanks in one prompt', () => {
    const segments = parsePromptSegments('H{{two}}O has mass {{mass}} g/mol');

    expect(segments.filter((s) => s.kind === 'blank')).toEqual([
      { kind: 'blank', blankId: 'two' },
      { kind: 'blank', blankId: 'mass' },
    ]);
  });

  it('trims whitespace inside the token', () => {
    expect(parsePromptSegments('{{ spaced }}')).toEqual([
      { kind: 'blank', blankId: 'spaced' },
    ]);
  });

  it('is not confused by LaTeX braces in the surrounding text', () => {
    const segments = parsePromptSegments('$\\frac{x^2}{2} + {{constant}}$');

    expect(segments).toContainEqual({ kind: 'blank', blankId: 'constant' });
    expect(segments[0]).toEqual({ kind: 'text', content: '$\\frac{x^2}{2} + ' });
  });

  it('returns a single text segment when there are no blanks', () => {
    expect(parsePromptSegments('No blanks here.')).toEqual([
      { kind: 'text', content: 'No blanks here.' },
    ]);
  });

  it('is reusable across calls (no sticky lastIndex leak)', () => {
    const prompt = 'a {{x}} b {{y}} c';

    /* Regression: a shared global regex would drift on the second call. */
    expect(parsePromptSegments(prompt)).toEqual(parsePromptSegments(prompt));
    expect(parsePromptSegments(prompt)).toHaveLength(5);
  });

  it('returns nothing for an empty prompt', () => {
    expect(parsePromptSegments('')).toEqual([]);
  });
});
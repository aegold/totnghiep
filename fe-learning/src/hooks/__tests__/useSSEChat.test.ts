import { act, renderHook } from '@testing-library/react';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';

import { useSSEChat } from '@/hooks/useSSEChat';
import type { TutorCitation } from '@/features/course-learning/types';

/** Builds an SSE wire payload from events. */
const wire = (events: readonly unknown[]): string =>
  events.map((event) => `data: ${JSON.stringify(event)}\n\n`).join('');

const citation: TutorCitation = {
  sourceId: 's1',
  label: 'Slide 14',
  documentTitle: 'Lecture 2',
  location: 'Lecture 2',
  snippet: 'Master theorem case 2.',
  score: 0.91,
};

/**
 * A `fetch` stub whose stream yields the supplied chunks, honouring the abort
 * signal the way a real network would.
 */
const streamFetch = (chunks: readonly string[]): typeof fetch => {
  const encoder = new TextEncoder();
  /* Copied to a mutable queue: the stream consumes it. */
  const queue: string[] = [...chunks];

  return vi.fn(async (_input: RequestInfo | URL, init?: RequestInit) => {
    const signal: AbortSignal | null | undefined = init?.signal;

    const body = new ReadableStream<Uint8Array>({
      async pull(controller): Promise<void> {
        if (signal?.aborted === true) {
          controller.error(new DOMException('Aborted', 'AbortError'));
          return;
        }
        /* Yield so `read()` resolves asynchronously, as in a real stream. */
        await Promise.resolve();

        const next: string | undefined = queue.shift();
        if (next === undefined) {
          controller.close();
          return;
        }
        controller.enqueue(encoder.encode(next));
      },
    });

    return new Response(body, {
      status: 200,
      headers: { 'Content-Type': 'text/event-stream' },
    });
  }) as unknown as typeof fetch;
};

beforeEach(() => {
  /* rAF drives token batching; run it synchronously so state lands immediately. */
  vi.stubGlobal('requestAnimationFrame', (callback: FrameRequestCallback): number => {
    callback(0);
    return 0;
  });
  vi.stubGlobal('cancelAnimationFrame', (): void => undefined);
});

afterEach(() => {
  vi.unstubAllGlobals();
});

describe('useSSEChat', () => {
  it('streams tokens and commits a single, non-duplicated assistant message', async () => {
    const fetchImpl = streamFetch([
      wire([
        { type: 'token', content: 'The ' },
        { type: 'token', content: 'Master ' },
        { type: 'token', content: 'theorem.' },
        { type: 'done' },
      ]),
    ]);

    const { result } = renderHook(() => useSSEChat({ courseId: 1, fetchImpl }));

    await act(async () => {
      await result.current.send('Explain the master theorem');
    });

    const assistant = result.current.messages.filter((m) => m.role === 'assistant');

    expect(assistant).toHaveLength(1);
    /*
     * Regression guard: `streamed` and the live-display buffer both hold every
     * token, so concatenating them duplicated the entire answer.
     */
    expect(assistant[0]?.content).toBe('The Master theorem.');
    expect(result.current.isStreaming).toBe(false);
  });

  it('records the user turn alongside the assistant turn', async () => {
    const fetchImpl = streamFetch([
      wire([{ type: 'token', content: 'Yes.' }, { type: 'done' }]),
    ]);

    const { result } = renderHook(() => useSSEChat({ courseId: 1, fetchImpl }));

    await act(async () => {
      await result.current.send('What is T(n)?');
    });

    expect(result.current.messages.map((m) => m.role)).toEqual(['user', 'assistant']);
  });

  it('attaches citations, de-duplicated by sourceId', async () => {
    const fetchImpl = streamFetch([
      wire([
        { type: 'token', content: 'Answer' },
        { type: 'citation', source: citation },
        /* Same source repeated — must not produce two badges. */
        { type: 'citation', source: citation },
        { type: 'sources', sources: [citation] },
        { type: 'done' },
      ]),
    ]);

    const { result } = renderHook(() => useSSEChat({ courseId: 1, fetchImpl }));

    await act(async () => {
      await result.current.send('q');
    });

    const assistant = result.current.messages[1];
    expect(assistant?.citations).toHaveLength(1);
    expect(assistant?.citations[0]?.label).toBe('Slide 14');
  });

  it('reassembles a stream whose chunks split mid-event', async () => {
    const full = wire([
      { type: 'token', content: 'Assembled from fragments' },
      { type: 'done' },
    ]);

    /* Five-character fragments: a hostile chunking pattern. */
    const fragments: string[] = [];
    for (let i = 0; i < full.length; i += 5) {
      fragments.push(full.slice(i, i + 5));
    }

    const { result } = renderHook(() =>
      useSSEChat({ courseId: 1, fetchImpl: streamFetch(fragments) }),
    );

    await act(async () => {
      await result.current.send('q');
    });

    expect(result.current.messages[1]?.content).toBe('Assembled from fragments');
  });

  it('surfaces a service error event without committing a message', async () => {
    const fetchImpl = streamFetch([
      wire([{ type: 'error', message: 'No course material found.' }]),
    ]);

    const { result } = renderHook(() => useSSEChat({ courseId: 1, fetchImpl }));

    await act(async () => {
      await result.current.send('q');
    });

    expect(result.current.error).toBe('No course material found.');
    expect(result.current.status).toBe('error');
    expect(result.current.messages.filter((m) => m.role === 'assistant')).toHaveLength(0);
  });

  it('reports a 401 to the host instead of silently retrying', async () => {
    const onUnauthorized = vi.fn();
    const fetchImpl = vi.fn(async () => new Response(null, { status: 401 })) as unknown as typeof fetch;

    const { result } = renderHook(() =>
      useSSEChat({ courseId: 1, fetchImpl, onUnauthorized }),
    );

    await act(async () => {
      await result.current.send('q');
    });

    expect(onUnauthorized).toHaveBeenCalledTimes(1);
    /* The message is user-facing, so it is asserted in its rendered form. */
    expect(result.current.error).toContain('Phiên làm việc đã hết hạn');
  });

  it('clears the transcript', async () => {
    const fetchImpl = streamFetch([
      wire([{ type: 'token', content: 'hi' }, { type: 'done' }]),
    ]);

    const { result } = renderHook(() => useSSEChat({ courseId: 1, fetchImpl }));

    await act(async () => {
      await result.current.send('q');
    });
    expect(result.current.messages.length).toBeGreaterThan(0);

    act(() => {
      result.current.clear();
    });

    expect(result.current.messages).toEqual([]);
    expect(result.current.citations).toEqual([]);
  });
});
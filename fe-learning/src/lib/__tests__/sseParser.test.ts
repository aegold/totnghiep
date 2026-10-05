import { describe, expect, it } from 'vitest';

import { parseSSEFrame, SSEParser } from '@/lib/sseParser';

/** Builds a well-formed SSE frame. */
const frame = (payload: unknown): string =>
  `data: ${JSON.stringify(payload)}\n\n`;

const tokenEvent = (content: string): string =>
  frame({ type: 'token', content });

describe('parseSSEFrame', () => {
  it('parses a token event', () => {
    expect(parseSSEFrame('data: {"type":"token","content":"hi"}')).toEqual({
      type: 'token',
      content: 'hi',
    });
  });

  it('recognises the [DONE] sentinel', () => {
    expect(parseSSEFrame('data: [DONE]')).toEqual({ type: 'done' });
  });

  it('ignores comment/keep-alive lines', () => {
    expect(parseSSEFrame(': keep-alive')).toBeNull();
    expect(parseSSEFrame('')).toBeNull();
  });

  it('ignores non-data fields such as event: and id:', () => {
    expect(parseSSEFrame('event: message\nid: 7\ndata: {"type":"done"}')).toEqual({
      type: 'done',
    });
  });

  it('returns null for malformed JSON rather than throwing', () => {
    expect(parseSSEFrame('data: {not json')).toBeNull();
  });

  it('returns null for an unrecognised event type', () => {
    expect(parseSSEFrame('data: {"type":"mystery"}')).toBeNull();
  });

  it('drops a citation missing its required sourceId', () => {
    expect(parseSSEFrame('data: {"type":"citation","source":{"label":"Slide 1"}}')).toBeNull();
  });

  it('fills sensible defaults on a sparse citation', () => {
    const parsed = parseSSEFrame(
      'data: {"type":"citation","source":{"sourceId":"s1"}}',
    );

    expect(parsed).toEqual({
      type: 'citation',
      source: {
        sourceId: 's1',
        label: 's1',
        documentTitle: null,
        location: null,
        snippet: '',
        score: null,
      },
    });
  });
});

describe('SSEParser — framing', () => {
  it('parses two events arriving in one chunk', () => {
    const parser = new SSEParser();
    const events = parser.feed(tokenEvent('a') + tokenEvent('b'));

    expect(events).toEqual([
      { type: 'token', content: 'a' },
      { type: 'token', content: 'b' },
    ]);
  });

  it('emits nothing when a chunk ends mid-event', () => {
    const parser = new SSEParser();
    const full = tokenEvent('hello');

    /* Cut the frame in half — no terminator has arrived. */
    expect(parser.feed(full.slice(0, 20))).toEqual([]);

    /* The remainder completes it. */
    expect(parser.feed(full.slice(20))).toEqual([{ type: 'token', content: 'hello' }]);
  });

  it('reassembles an event split across many tiny chunks', () => {
    const parser = new SSEParser();
    const full = tokenEvent('reconstructed');
    const events = [];

    /* One character per chunk: the worst possible framing. */
    for (const char of full) {
      events.push(...parser.feed(char));
    }

    expect(events).toEqual([{ type: 'token', content: 'reconstructed' }]);
  });

  it('handles a chunk boundary that lands exactly between events', () => {
    const parser = new SSEParser();
    const first = tokenEvent('one');
    const second = tokenEvent('two');

    expect(parser.feed(first).map((e) => e.type)).toEqual(['token']);
    expect(parser.feed(second).map((e) => e.type)).toEqual(['token']);
  });

  it('decodes a multi-byte character split across two byte chunks', () => {
    const parser = new SSEParser();
    const encoded = new TextEncoder().encode(tokenEvent('π ≈ 3.14 ✓'));

    /* Find the 0xCF byte of U+03C0 and split there. */
    const piStart = encoded.findIndex((byte: number) => byte === 0xcf);
    expect(piStart).toBeGreaterThan(0);

    const head = encoded.slice(0, piStart + 1);
    const tail = encoded.slice(piStart + 1);

    const events = [...parser.feed(head), ...parser.feed(tail)];

    /*
     * Without `stream: true` the decoder would emit U+FFFD here, corrupting
     * the answer permanently.
     */
    expect(events).toHaveLength(1);
    expect(events[0]).toEqual({ type: 'token', content: 'π ≈ 3.14 ✓' });
  });

  it('flushes a truncated final event on stream end', () => {
    const parser = new SSEParser();
    const full = tokenEvent('cut off here');

    /* No trailing blank line — the server disconnected mid-write. */
    expect(parser.feed(full.slice(0, full.length - 2))).toEqual([]);
    expect(parser.flush()).toEqual([{ type: 'token', content: 'cut off here' }]);
  });

  it('returns nothing from flush when the buffer is empty', () => {
    const parser = new SSEParser();
    parser.feed(tokenEvent('done'));
    expect(parser.flush()).toEqual([]);
  });

  it('collects a mixed sequence of tokens, citations, sources and done', () => {
    const parser = new SSEParser();
    const payload =
      tokenEvent('The') +
      tokenEvent(' answer') +
      frame({
        type: 'citation',
        source: { sourceId: 's1', label: 'Slide 14', snippet: 'x' },
      }) +
      frame({
        type: 'sources',
        sources: [{ sourceId: 's1', label: 'Slide 14' }],
      }) +
      frame({ type: 'done', finishReason: 'stop' });

    const events = parser.feed(payload);

    expect(events.map((event) => event.type)).toEqual([
      'token',
      'token',
      'citation',
      'sources',
      'done',
    ]);
    expect(events[4]).toEqual({ type: 'done', finishReason: 'stop' });
  });
});
/**
 * Contract test: real FastAPI output -> real client parser.
 *
 * The unit tests in `sseParser.test.ts` feed hand-written frames, so they prove
 * the parser works but NOT that the Python service produces frames it accepts.
 * A mismatch there is silent: `parseSSEFrame` returns `null` for anything it
 * does not recognise, so tokens vanish and citations disappear with no console
 * error.
 *
 * `ai-learning/samples/sse-sample.sse` is a byte-for-byte capture of
 * `POST /api/v1/ai/tutor/chat-stream`. Regenerate it after any change to
 * `services/sse.py`:
 *
 *   curl -N -X POST http://localhost:8000/api/v1/ai/tutor/chat-stream \
 *     -H 'Content-Type: application/json' \
 *     -d '{"courseId":1,"message":"Dia chi IPv4 la gi?","history":[],
 *          "selectedText":"Bai 2: Dia chi IPv4 va Chia Subnet","materialId":1}' \
 *     -o ai-learning/samples/sse-sample.sse
 */

import { readFileSync } from 'node:fs';
import { resolve } from 'node:path';

import { describe, expect, it } from 'vitest';

import { SSEParser } from '@/lib/sseParser';
import type { TutorStreamEvent } from '@/features/course-learning/types';

const SAMPLE_PATH = resolve(__dirname, '../../../../ai-learning/samples/sse-sample.sse');

/** Parses the capture in one shot. */
const parseAll = (raw: string): TutorStreamEvent[] => {
  const parser = new SSEParser();
  return [...parser.feed(raw), ...parser.flush()];
};

/**
 * Parses the capture split at `size` bytes, which is what a network actually
 * does: chunk boundaries land mid-UTF-8-character and mid-JSON.
 */
const parseChunked = (raw: string, size: number): TutorStreamEvent[] => {
  const parser = new SSEParser();
  const bytes = new TextEncoder().encode(raw);
  const events: TutorStreamEvent[] = [];
  for (let offset = 0; offset < bytes.length; offset += size) {
    events.push(...parser.feed(bytes.slice(offset, offset + size)));
  }
  events.push(...parser.flush());
  return events;
};

const capture = (): string => {
  try {
    return readFileSync(SAMPLE_PATH, 'utf8');
  } catch {
    throw new Error(
      `Missing SSE capture at ${SAMPLE_PATH}. Regenerate it - see the header of this file.`,
    );
  }
};

describe('FastAPI SSE capture parses through the client parser', () => {
  const events = parseAll(capture());

  it('yields at least one event', () => {
    expect(events.length).toBeGreaterThan(10);
  });

  it('produces no unrecognised frames', () => {
    // Every parsed event is one of the documented variants; a payload the
    // parser rejected would simply be missing from this array.
    const types = new Set(events.map((event) => event.type));
    for (const type of types) {
      expect(['token', 'citation', 'sources', 'done', 'error']).toContain(type);
    }
  });

  it('starts with citations so badges exist before prose', () => {
    expect(events.at(0)?.type).toBe('citation');
  });

  it('emits citations with a renderable provenance', () => {
    const citations = events.filter((event) => event.type === 'citation');
    expect(citations.length).toBeGreaterThan(0);
    for (const event of citations) {
      if (event.type !== 'citation') continue;
      // `parseCitation` drops anything without a string sourceId, so this is
      // the field whose absence would silently erase the badge.
      expect(typeof event.source.sourceId).toBe('string');
      expect(event.source.sourceId.length).toBeGreaterThan(0);
      expect(typeof event.source.label).toBe('string');
      expect(typeof event.source.snippet).toBe('string');
    }
  });

  it('points citations at the real course material', () => {
    const sources = events.filter((event) => event.type === 'sources');
    expect(sources.length).toBeGreaterThan(0);
    const last = sources.at(-1);
    if (last === undefined || last.type !== 'sources') {
      throw new Error('expected a sources event');
    }
    expect(last.sources.at(0)?.documentTitle).toBe('slides_ch1.pdf');
  });

  it('streams the answer as many small tokens', () => {
    const tokens = events.filter((event) => event.type === 'token');
    expect(tokens.length).toBeGreaterThan(20);
    for (const event of tokens) {
      if (event.type !== 'token') continue;
      expect(typeof event.content).toBe('string');
      expect(event.content.length).toBeGreaterThan(0);
    }
  });

  it('reassembles into coherent Vietnamese prose', () => {
    const answer = events
      .filter((event) => event.type === 'token')
      .map((event) => (event.type === 'token' ? event.content : ''))
      .join('');
    expect(answer).toContain('Chào bạn');
    expect(answer.length).toBeGreaterThan(200);
  });

  it('ends with done so the hook returns to idle', () => {
    expect(events.at(-1)?.type).toBe('done');
  });

  it('never emits an error frame', () => {
    expect(events.filter((event) => event.type === 'error')).toHaveLength(0);
  });
});

describe('the capture survives hostile network chunking', () => {
  // The regression this guards: the Python side must not depend on a chunk
  // boundary lining up with a frame boundary.
  it.each([1, 3, 7, 64, 1024])('produces identical events at %i-byte chunks', (size) => {
    const raw = capture();
    expect(parseChunked(raw, size)).toEqual(parseAll(raw));
  });

  it('reassembles multi-byte Vietnamese characters split across chunks', () => {
    const raw = capture();
    const answer = parseChunked(raw, 3)
      .filter((event) => event.type === 'token')
      .map((event) => (event.type === 'token' ? event.content : ''))
      .join('');
    expect(answer).toContain('Chào bạn');
    expect(answer).not.toContain('�');
  });
});
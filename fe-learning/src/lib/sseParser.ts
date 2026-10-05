import type { TutorCitation, TutorStreamEvent } from '@/features/course-learning/types';

/**
 * Incremental Server-Sent Events parser.
 *
 * ## Why this is not a one-liner
 * A network chunk has no relationship to an SSE event boundary. A single chunk
 * can contain half an event, three events, or cut a multi-byte UTF-8 character
 * in half. Three independent problems therefore have to be solved:
 *
 *  1. **Encoding** — `TextDecoder` must run in streaming mode, or a `π` or emoji
 *     split across a chunk boundary decodes to U+FFFD.
 *  2. **Framing** — events are separated by a blank line (`\n\n`). Any trailing
 *     partial event must be carried over to the next chunk instead of being
 *     parsed early or dropped.
 *  3. **Payload** — only `data:` lines carry the JSON; `event:`, `id:`, and
 *     `retry:` lines are metadata and must be skipped.
 *
 * Exported as a standalone class so the logic can be unit-tested without a
 * network or a DOM.
 */

/** Separators tolerated by the SSE spec. */
const EVENT_BOUNDARY = /\r?\n\r?\n/;

/** Terminator some frameworks send instead of a `done` event. */
const DONE_SENTINEL = '[DONE]';

const isRecord = (value: unknown): value is Record<string, unknown> =>
  typeof value === 'object' && value !== null;

/** Validates a citation coming off the wire before it reaches the UI. */
const parseCitation = (raw: unknown): TutorCitation | null => {
  if (!isRecord(raw) || typeof raw['sourceId'] !== 'string') {
    return null;
  }

  return {
    sourceId: raw['sourceId'],
    label: typeof raw['label'] === 'string' ? raw['label'] : raw['sourceId'],
    documentTitle:
      typeof raw['documentTitle'] === 'string' ? raw['documentTitle'] : null,
    location: typeof raw['location'] === 'string' ? raw['location'] : null,
    snippet: typeof raw['snippet'] === 'string' ? raw['snippet'] : '',
    score: typeof raw['score'] === 'number' ? raw['score'] : null,
  };
};

/**
 * Converts one decoded frame into an event.
 * Returns `null` for keep-alives, comments, and unrecognised payloads.
 */
export const parseSSEFrame = (frame: string): TutorStreamEvent | null => {
  const trimmed: string = frame.trim();

  if (trimmed.length === 0 || trimmed.startsWith(':')) {
    /* Comment / keep-alive heartbeat. */
    return null;
  }

  /* Concatenate every `data:` line; multi-line payloads are valid SSE. */
  const dataLines: string[] = [];
  for (const line of trimmed.split(/\r?\n/)) {
    if (line.startsWith('data:')) {
      dataLines.push(line.slice(5).trimStart());
    }
  }

  if (dataLines.length === 0) {
    return null;
  }

  const payload: string = dataLines.join('\n');

  if (payload === DONE_SENTINEL) {
    return { type: 'done' };
  }

  let parsed: unknown;
  try {
    parsed = JSON.parse(payload);
  } catch {
    /* Malformed frame: skip rather than abort the whole stream. */
    return null;
  }

  if (!isRecord(parsed)) {
    return null;
  }

  switch (parsed['type']) {
    case 'token':
      return typeof parsed['content'] === 'string'
        ? { type: 'token', content: parsed['content'] }
        : null;

    case 'citation': {
      const source: TutorCitation | null = parseCitation(parsed['source']);
      return source === null ? null : { type: 'citation', source };
    }

    case 'sources': {
      if (!Array.isArray(parsed['sources'])) {
        return null;
      }
      const sources: TutorCitation[] = parsed['sources']
        .map(parseCitation)
        .filter((source: TutorCitation | null): source is TutorCitation => source !== null);
      return { type: 'sources', sources };
    }

    case 'done':
      return {
        type: 'done',
        ...(typeof parsed['finishReason'] === 'string'
          ? { finishReason: parsed['finishReason'] }
          : {}),
      };

    case 'error':
      return {
        type: 'error',
        message: typeof parsed['message'] === 'string' ? parsed['message'] : 'Tutor unavailable.',
      };

    default:
      return null;
  }
};

export class SSEParser {
  /**
   * Streaming decoder. `stream: true` is essential: it holds back incomplete
   * multi-byte sequences so a character split across chunks is reassembled.
   */
  private readonly decoder = new TextDecoder('utf-8');

  /** Bytes decoded but not yet forming a complete `\n\n`-delimited event. */
  private buffer = '';

  /** Feeds raw bytes (or a string, for tests) and returns complete events. */
  public feed(chunk: Uint8Array | string): TutorStreamEvent[] {
    this.buffer +=
      typeof chunk === 'string' ? chunk : this.decoder.decode(chunk, { stream: true });

    const events: TutorStreamEvent[] = [];
    const parts: string[] = this.buffer.split(EVENT_BOUNDARY);

    /*
     * The final part is either an incomplete event or an empty string. It is
     * always carried over — flushing it here is what corrupts a stream whose
     * chunk boundary lands mid-JSON.
     */
    this.buffer = parts.pop() ?? '';

    for (const part of parts) {
      const event: TutorStreamEvent | null = parseSSEFrame(part);
      if (event !== null) {
        events.push(event);
      }
    }

    return events;
  }

  /**
   * Flushes on stream end.
   *
   * A server that closes mid-event leaves an unterminated tail; draining it
   * means a truncated answer is shown rather than silently discarded.
   */
  public flush(): TutorStreamEvent[] {
    const tail: string = this.buffer;
    this.buffer = '';

    /* Complete any dangling multi-byte sequence before discarding state. */
    this.decoder.decode();

    if (tail.trim().length === 0) {
      return [];
    }

    const event: TutorStreamEvent | null = parseSSEFrame(tail);
    return event === null ? [] : [event];
  }
}
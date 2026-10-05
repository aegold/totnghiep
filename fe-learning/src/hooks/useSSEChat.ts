import { useCallback, useEffect, useRef, useState } from 'react';

import { SSEParser } from '@/lib/sseParser';
import { authStoreApi } from '@/stores/authStore';
import type {
  ChatMessage,
  MaterialSelection,
  TutorChatRequest,
  TutorCitation,
  TutorStreamEvent,
} from '@/features/course-learning/types';

/**
 * Streaming chat against the FastAPI RAG tutor.
 *
 * ## Why `fetch` and not the Axios client
 * Axios buffers an entire response before resolving, which defeats streaming
 * outright. The browser `fetch` API exposes a `ReadableStream` we can consume
 * token by token.
 *
 * The trade-off: `fetch` bypasses the Axios interceptors from Stage 1, so this
 * hook attaches the bearer token itself and CANNOT silently refresh an expired
 * token mid-stream. A 401 therefore surfaces as an actionable error instead of
 * retrying invisibly behind the learner's back.
 */

export const TUTOR_STREAM_PATH = '/ai/tutor/chat-stream';

/**
 * Where the FastAPI RAG service lives.
 *
 * Deliberately separate from `API_BASE_URL`: the tutor runs on its own
 * microservice (:8000) rather than behind Spring Boot, so the browser talks to
 * it directly and exercises its CORS configuration. The literal fallback keeps
 * the Vitest suite deterministic without requiring the service to be running.
 */
export const AI_TUTOR_BASE_URL: string =
  (import.meta.env.VITE_AI_BASE_URL as string | undefined) ??
  'http://localhost:8000/api/v1';

export type ChatStatus = 'idle' | 'streaming' | 'error';

export interface UseSSEChatOptions {
  courseId: number;
  initialSelection?: MaterialSelection | null;
  onUnauthorized?: () => void;
  /** Injected in tests; defaults to the global `fetch`. */
  fetchImpl?: typeof fetch;
}

export interface UseSSEChatResult {
  messages: ChatMessage[];
  /** Tokens for the in-flight assistant turn; not yet in `messages`. */
  streamingContent: string;
  citations: TutorCitation[];
  status: ChatStatus;
  error: string | null;
  /** Selection seeding the next prompt. */
  activeSelection: MaterialSelection | null;
  isStreaming: boolean;
  send: (prompt: string, selection?: MaterialSelection | null) => Promise<void>;
  abort: () => void;
  clear: () => void;
  setSelection: (selection: MaterialSelection | null) => void;
}

/** Monotonic id for transcript entries. */
let messageCounter = 0;
const nextMessageId = (): string => {
  messageCounter += 1;
  return `msg-${Date.now()}-${messageCounter}`;
};

export function useSSEChat(options: UseSSEChatOptions): UseSSEChatResult {
  const { courseId, initialSelection = null, onUnauthorized } = options;

  const [messages, setMessages] = useState<ChatMessage[]>([]);
  const [streamingContent, setStreamingContent] = useState<string>('');
  const [citations, setCitations] = useState<TutorCitation[]>([]);
  const [status, setStatus] = useState<ChatStatus>('idle');
  const [error, setError] = useState<string | null>(null);
  const [activeSelection, setActiveSelection] = useState<MaterialSelection | null>(
    initialSelection,
  );

  /** Aborts the in-flight request on unmount or on "Stop". */
  const abortRef = useRef<AbortController | null>(null);

  /**
   * Tokens accumulate here and are flushed to state once per animation frame.
   * Writing to state per token would schedule a React render for every
   * fragment and pin the main thread for the whole generation.
   */
  const pendingTokensRef = useRef<string>('');
  const frameRef = useRef<number | null>(null);

  const flushTokens = useCallback((): void => {
    if (frameRef.current !== null) {
      return;
    }
    frameRef.current = window.requestAnimationFrame(() => {
      frameRef.current = null;
      if (pendingTokensRef.current.length > 0) {
        const batch: string = pendingTokensRef.current;
        pendingTokensRef.current = '';
        setStreamingContent((current: string) => current + batch);
      }
    });
  }, []);

  const cancelFrame = useCallback((): void => {
    if (frameRef.current !== null) {
      window.cancelAnimationFrame(frameRef.current);
      frameRef.current = null;
    }
  }, []);

  /* Abort any in-flight stream when the component unmounts. */
  useEffect(
    () => () => {
      abortRef.current?.abort();
      cancelFrame();
    },
    [cancelFrame],
  );

  /** Commits the streamed turn into the transcript. */
  const commitAssistantMessage = useCallback(
    (wasAborted: boolean, streamed: string, sources: TutorCitation[]): void => {
      cancelFrame();

      /*
       * `streamed` is the authoritative transcript text and already contains
       * every token. `pendingTokensRef` holds the same tokens for live display
       * only, so it must NOT be concatenated here — that would double the
       * answer. It is simply discarded, since `streamingContent` is cleared.
       */
      const full: string = streamed;
      pendingTokensRef.current = '';

      if (full.trim().length === 0) {
        return;
      }

      setMessages((current: ChatMessage[]) => [
        ...current,
        {
          id: nextMessageId(),
          role: 'assistant',
          content: full,
          citations: sources,
          wasAborted,
          createdAt: Date.now(),
        },
      ]);
      setStreamingContent('');
    },
    [cancelFrame],
  );

  /**
   * Applies one event and reports what the reader should do next.
   *
   * Returning a distinct `error` outcome (rather than a bare boolean) matters:
   * a service-side error sets `status` to `'error'`, and the caller must not
   * overwrite that with `'idle'` when the stream unwinds.
   */
  const handleEvent = useCallback(
    (
      event: TutorStreamEvent,
      onCitations: (incoming: TutorCitation[]) => void,
    ): 'continue' | 'done' | 'error' => {
      switch (event.type) {
        case 'token':
          pendingTokensRef.current += event.content;
          flushTokens();
          return 'continue';

        case 'citation':
          onCitations([event.source]);
          return 'continue';

        case 'sources':
          onCitations(event.sources);
          return 'continue';

        case 'done':
          return 'done';

        case 'error':
          setError(event.message);
          setStatus('error');
          return 'error';

        default: {
          /* An unknown event type is ignored rather than treated as fatal. */
          const exhaustive: never = event;
          void exhaustive;
          return 'continue';
        }
      }
    },
    [flushTokens],
  );

  const abort = useCallback((): void => {
    abortRef.current?.abort();
  }, []);

  const clear = useCallback((): void => {
    abortRef.current?.abort();
    cancelFrame();
    pendingTokensRef.current = '';
    setMessages([]);
    setStreamingContent('');
    setCitations([]);
    setError(null);
    setStatus('idle');
  }, [cancelFrame]);

  const setSelection = useCallback((selection: MaterialSelection | null): void => {
    setActiveSelection(selection);
  }, []);

  const send = useCallback(
    async (
      prompt: string,
      selection: MaterialSelection | null = activeSelection,
    ): Promise<void> => {
      const trimmed: string = prompt.trim();
      if (trimmed.length === 0 || status === 'streaming') {
        return;
      }

      /* Any previous stream is finished; start clean. */
      abortRef.current?.abort();
      pendingTokensRef.current = '';
      setStreamingContent('');
      setCitations([]);
      setError(null);
      setStatus('streaming');

      const userMessage: ChatMessage = {
        id: nextMessageId(),
        role: 'user',
        content: trimmed,
        citations: [],
        wasAborted: false,
        createdAt: Date.now(),
      };

      setMessages((current: ChatMessage[]) => [...current, userMessage]);

      /*
       * Citations are collected locally rather than read back from state: the
       * state copy lags by a render, and the transcript must be complete the
       * instant the stream ends.
       */
      let collected: TutorCitation[] = [];
      const addCitations = (incoming: TutorCitation[]): void => {
        if (incoming.length === 0) {
          return;
        }
        const seen = new Set(collected.map((citation: TutorCitation) => citation.sourceId));
        collected = [
          ...collected,
          ...incoming.filter((citation: TutorCitation) => !seen.has(citation.sourceId)),
        ];
        setCitations(collected);
      };

      const body: TutorChatRequest = {
        courseId,
        message: trimmed,
        history: [...messages, userMessage].map((message: ChatMessage) => ({
          role: message.role,
          content: message.content,
        })),
        selectedText: selection?.text ?? null,
        materialId: selection?.materialId ?? null,
      };

      const controller = new AbortController();
      abortRef.current = controller;

      const fetchImpl: typeof fetch = options.fetchImpl ?? window.fetch.bind(window);
      const accessToken: string | null = authStoreApi.getAccessToken();

      let streamed: string = '';

      try {
        const response: Response = await fetchImpl(`${AI_TUTOR_BASE_URL}${TUTOR_STREAM_PATH}`, {
          method: 'POST',
          headers: {
            'Content-Type': 'application/json',
            Accept: 'text/event-stream',
            ...(accessToken !== null ? { Authorization: `Bearer ${accessToken}` } : {}),
          },
          body: JSON.stringify(body),
          signal: controller.signal,
          /* Session cookies ride along, matching the REST client. */
          credentials: 'include',
        });

        if (response.status === 401) {
          /* No refresh interceptor on fetch — surface it for the host to handle. */
          setError('Phiên làm việc đã hết hạn. Vui lòng đăng nhập lại để tiếp tục.');
          setStatus('error');
          onUnauthorized?.();
          return;
        }

        if (!response.ok) {
          setError(`Tutor request failed (${response.status}).`);
          setStatus('error');
          return;
        }

        const stream = response.body;
        if (stream === null) {
          setError('Trợ giảng trả về nội dung rỗng.');
          setStatus('error');
          return;
        }

        const reader: ReadableStreamDefaultReader<Uint8Array> = stream.getReader();
        const parser = new SSEParser();
        let outcome: 'continue' | 'done' | 'error' = 'continue';

        for (;;) {
          const { done, value } = await reader.read();

          if (done) {
            break;
          }

          if (value !== undefined) {
            for (const event of parser.feed(value)) {
              if (event.type === 'token') {
                streamed += event.content;
              }
              const result = handleEvent(event, addCitations);
              if (result !== 'continue') {
                outcome = result;
                break;
              }
            }
          }

          if (outcome !== 'continue') {
            break;
          }
        }

        /* Drain a truncated tail so a partial answer is never discarded. */
        if (outcome === 'continue') {
          for (const event of parser.flush()) {
            if (event.type === 'token') {
              streamed += event.content;
            }
            const result = handleEvent(event, addCitations);
            if (result !== 'continue') {
              outcome = result;
            }
          }
        }

        commitAssistantMessage(controller.signal.aborted, streamed, collected);

        /*
         * Only return to idle on a clean finish. Overwriting 'error' here would
         * silently swallow a service-side failure.
         */
        if (outcome !== 'error') {
          setStatus('idle');
        }
        setActiveSelection(null);
      } catch (caught: unknown) {
        /*
         * An abort is a user action, not a failure: keep whatever streamed so
         * far and return to idle without an error banner.
         */
        if (controller.signal.aborted) {
          commitAssistantMessage(true, streamed, collected);
          setStatus('idle');
          return;
        }

        commitAssistantMessage(false, streamed, collected);
        setError(
          caught instanceof Error ? caught.message : 'Không thể kết nối tới dịch vụ trợ giảng AI.',
        );
        setStatus('error');
      } finally {
        abortRef.current = null;
      }
    },
    [
      courseId,
      messages,
      status,
      activeSelection,
      handleEvent,
      commitAssistantMessage,
      onUnauthorized,
      options.fetchImpl,
    ],
  );

  return {
    messages,
    streamingContent,
    citations,
    status,
    error,
    activeSelection,
    isStreaming: status === 'streaming',
    send,
    abort,
    clear,
    setSelection,
  };
}
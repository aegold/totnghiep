"""SSE frame serialisation matching `fe-learning/src/lib/sseParser.ts`.

Three constraints come straight from the client parser and must not drift:

1. Frames are separated by a blank line. It splits on /\\r?\\n\\r?\\n/, so every
   frame here ends with exactly `\\n\\n`.
2. Only lines beginning with `data:` are read, and the payload is taken as
   everything after the colon with leading whitespace stripped.
3. The payload is parsed with `JSON.parse`, so each frame must be a single line
   of JSON. `json.dumps` never emits a raw newline, which keeps Vietnamese
   diacritics intact via `ensure_ascii=False` without breaking framing.
"""

from __future__ import annotations

import json
from typing import Any

EVENT_BOUNDARY = "\n\n"


def encode(payload: dict[str, Any]) -> str:
    """Serialise one event into a complete SSE frame."""
    return f"data: {json.dumps(payload, ensure_ascii=False)}{EVENT_BOUNDARY}"


def token(content: str) -> str:
    """An incremental answer fragment."""
    return encode({"type": "token", "content": content})


def citation(source: dict[str, Any]) -> str:
    """A single retrieved source, emitted before the answer text."""
    return encode({"type": "citation", "source": source})


def sources(items: list[dict[str, Any]]) -> str:
    """The final, authoritative source list."""
    return encode({"type": "sources", "sources": items})


def done(finish_reason: str = "stop") -> str:
    """Terminates the stream so the hook returns to idle."""
    return encode({"type": "done", "finishReason": finish_reason})


def error(message: str) -> str:
    """Surfaces a failure to the chat panel instead of hanging the stream."""
    return encode({"type": "error", "message": message})


def keepalive() -> str:
    """Comment heartbeat; `parseSSEFrame` discards frames starting with ':'."""
    return f": keepalive{EVENT_BOUNDARY}"
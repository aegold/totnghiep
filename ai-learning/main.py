"""AI Tutor microservice - FastAPI RAG service for the Adaptive LMS.

Serves `POST /api/v1/ai/tutor/chat-stream`, which the React client consumes
through `useSSEChat` / `SSEParser`. The wire contract is documented in
`fe-learning/src/features/course-learning/types.ts` and is asserted in
`tests/test_sse_contract.py`.
"""

from __future__ import annotations

import asyncio
import logging
from contextlib import asynccontextmanager
from typing import Any, AsyncIterator

from fastapi import FastAPI, Request
from fastapi.middleware.cors import CORSMiddleware
from fastapi.responses import StreamingResponse
from pydantic import BaseModel, Field

from services import sse
from services.config import get_settings
from services.llm import build_provider_chain, stream_answer
from services.rag_service import RagService

logging.basicConfig(
    level=logging.INFO,
    format="%(asctime)s  %(levelname)-7s %(name)s  %(message)s",
)
logger = logging.getLogger("ai-learning")

ALLOWED_ORIGINS = [
    "http://localhost:5173",
    "http://127.0.0.1:5173",
]

# Between the first token and the last, a stalled upstream would leave the
# learner's cursor frozen with no explanation.
KEEPALIVE_INTERVAL_SECONDS = 15.0


class ChatRequest(BaseModel):
    """Mirrors `TutorChatRequest` in fe-learning/types.ts.

    `selected_text` is the field the client actually sends; the brief's
    `highlighted_text` does not exist in the TypeScript type, so accepting it
    would only ever be dead weight.
    """

    courseId: int = Field(ge=0)  # noqa: N815 - mirrors the wire contract
    message: str = Field(min_length=1)
    history: list[dict[str, Any]] = Field(default_factory=list)
    selectedText: str | None = None  # noqa: N815
    materialId: int | None = None  # noqa: N815


_rag = RagService()


@asynccontextmanager
async def lifespan(_: FastAPI) -> AsyncIterator[None]:
    chunk_count = _rag.ensure_indexed()
    logger.info("RAG ready with %d chunks", chunk_count)
    yield


app = FastAPI(
    title="AI Tutor Microservice",
    version="1.0.0",
    description="RAG-grounded Vietnamese tutor for the Adaptive LMS.",
    lifespan=lifespan,
)

app.add_middleware(
    CORSMiddleware,
    allow_origins=ALLOWED_ORIGINS,
    allow_credentials=True,
    allow_methods=["GET", "POST", "OPTIONS"],
    allow_headers=["*"],
    expose_headers=["Content-Type", "Cache-Control"],
)


@app.get("/api/v1/health")
async def health() -> dict[str, Any]:
    """Liveness probe used by start-all.bat's readiness gate."""
    settings = get_settings()
    providers = [provider.name for provider in build_provider_chain(settings)]
    return {
        "status": "UP",
        "service": "ai-tutor",
        "chunks": _rag.chunk_count,
        "providers": providers,
    }


@app.post("/api/v1/ai/tutor/chat-stream")
async def chat_stream(request: ChatRequest) -> StreamingResponse:
    """Stream a grounded answer as Server-Sent Events.

    The Spring Boot bearer token is accepted but not validated: this service
    holds no user data and issues no credentials, so it deliberately never
    answers 401 - doing so would surface in the chat panel as a session error.
    """
    chunks = _rag.retrieve(
        request.message, request.selectedText
    )
    if chunks:
        logger.info(
            "Retrieved %d chunk(s) for course %s", len(chunks), request.courseId
        )
    else:
        # Not an error: the learner may be asking without selecting anything.
        logger.info("No chunk retrieved; answering from selection/history only.")

    chain = build_provider_chain()
    citations = [chunk.to_citation() for chunk in chunks]

    async def event_source() -> AsyncIterator[str]:
        # Citations first so the badges exist before any prose renders.
        for source in citations:
            yield sse.citation(source)

        last_beat = asyncio.get_event_loop().time()
        try:
            generator = stream_answer(
                request.message, chunks, request.selectedText, request.history, chain
            )
            while True:
                try:
                    piece = await asyncio.wait_for(
                        generator.__anext__(), timeout=KEEPALIVE_INTERVAL_SECONDS
                    )
                except StopAsyncIteration:
                    break
                except asyncio.TimeoutError:
                    yield sse.keepalive()
                    continue

                now = asyncio.get_event_loop().time()
                if now - last_beat > KEEPALIVE_INTERVAL_SECONDS:
                    yield sse.keepalive()
                    last_beat = now
                if piece:
                    yield sse.token(piece)
        except Exception as exc:  # pragma: no cover - defensive
            logger.exception("Stream failed: %s", exc)
            yield sse.error(
                "Trợ giảng AI gặp sự cố khi xử lý câu hỏi. Vui lòng thử lại."
            )

        if citations:
            yield sse.sources(citations)
        yield sse.done()

    return StreamingResponse(
        event_source(),
        media_type="text/event-stream",
        headers={
            "Cache-Control": "no-cache",
            "Connection": "keep-alive",
            # Without this, nginx-style proxies may buffer the whole stream and
            # the UI would appear to hang until generation completed.
            "X-Accel-Buffering": "no",
        },
    )
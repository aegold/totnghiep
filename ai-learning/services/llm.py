"""LLM providers and the fallback chain that guards the live demo.

Order: Gemini -> OpenAI -> Local Heuristic Streamer.

Every provider failure mode - missing key, DNS failure, timeout, HTTP 429, an
exhausted quota, a malformed stream - is caught and falls through to the next
option. A failure *after* tokens have already been emitted does not switch
providers, because replaying from a different model would duplicate text in the
learner's transcript; instead the stream is closed cleanly.
"""

from __future__ import annotations

import logging
from typing import Any, AsyncIterator, Protocol

import httpx

from .config import Settings, get_settings
from .heuristic import LocalHeuristicStreamer
from .rag_service import RetrievedChunk

logger = logging.getLogger(__name__)

SYSTEM_PROMPT = """Bạn là trợ giảng ảo của học phần Mạng máy tính và An toàn hệ thống.

Nguyên tắc bắt buộc:
1. Chỉ giải thích dựa trên nội dung tài liệu được cung cấp trong phần ngữ cảnh.
2. Không bịa đặt số liệu, tên giao thức, hoặc chi tiết không có trong ngữ cảnh.
3. Nếu ngữ cảnh không đủ để trả lời, hãy nói thẳng rằng tài liệu chưa đề cập và gợi ý cách học.
4. Trả lời bằng tiếng Việt, giọng điệu trang trọng, khuyến khích và mang tính học thuật.
5. Cấu trúc câu trả lời bằng Markdown: tiêu đề con (###), gạch đầu dòng, và in đậm cho thuật ngữ chính.
6. Nếu người học bôi đen một đoạn, hãy tập trung giải thích chính xác đoạn đó trước.
"""


def build_messages(
    question: str, chunks: list[RetrievedChunk], selected_text: str | None, history: list[dict[str, Any]]
) -> list[dict[str, str]]:
    """Assemble the chat payload sent to the external provider."""
    context_parts: list[str] = []
    if chunks:
        joined = "\n\n".join(
            f"[{chunk.source_file} - trang {chunk.page_number}]\n{chunk.text}"
            for chunk in chunks
        )
        context_parts.append(f"### Ngữ cảnh từ tài liệu\n{joined}")
    else:
        context_parts.append(
            "### Ngữ cảnh từ tài liệu\n(Không truy xuất được đoạn nào liên quan.)"
        )
    if selected_text:
        context_parts.append(
            f"### Đoạn người học đã bôi đen\n{selected_text.strip()}"
        )

    messages: list[dict[str, str]] = [{"role": "system", "content": SYSTEM_PROMPT}]
    for turn in history[-6:]:
        # `history` is client-supplied and only validated as a list of objects,
        # so a stray string must be skipped rather than raise a 500.
        if not isinstance(turn, dict):
            continue
        role = turn.get("role")
        content = turn.get("content")
        if role in ("user", "assistant") and isinstance(content, str) and content:
            messages.append({"role": role, "content": content})
    messages.append(
        {
            "role": "user",
            "content": "\n\n".join(context_parts)
            + f"\n\n### Câu hỏi\n{question.strip()}",
        }
    )
    return messages


class Provider(Protocol):
    """A source of answer tokens."""

    name: str

    async def stream(
        self, question: str, chunks: list[RetrievedChunk], selected_text: str | None,
        history: list[dict[str, Any]],
    ) -> AsyncIterator[str]: ...


class GeminiProvider:
    """Google Gemini via the streaming REST endpoint."""

    name = "gemini"
    endpoint = "https://generativelanguage.googleapis.com/v1beta/models/{model}:streamGenerateContent?alt=sse"

    def __init__(self, settings: Settings) -> None:
        self._settings = settings

    async def stream(
        self, question: str, chunks: list[RetrievedChunk], selected_text: str | None,
        history: list[dict[str, Any]],
    ) -> AsyncIterator[str]:
        messages = build_messages(question, chunks, selected_text, history)
        # Gemini separates the system turn from the conversation turns.
        system = "\n\n".join(m["content"] for m in messages if m["role"] == "system")
        turns = [{"role": "model" if m["role"] == "assistant" else "user",
                  "parts": [{"text": m["content"]}]}
                 for m in messages if m["role"] != "system"]

        url = self.endpoint.format(model=self._settings.gemini_model)
        payload = {
            "systemInstruction": {"parts": [{"text": system}]},
            "contents": turns,
            "generationConfig": {"temperature": 0.4, "maxOutputTokens": 2048},
        }
        headers = {"x-goog-api-key": self._settings.gemini_api_key}

        async with httpx.AsyncClient(timeout=httpx.Timeout(30.0, connect=8.0)) as client:
            async with client.stream("POST", url, json=payload, headers=headers) as response:
                if response.status_code != 200:
                    detail = (await response.aread()).decode("utf-8", "replace")[:200]
                    raise RuntimeError(
                        f"Gemini HTTP {response.status_code}: {detail}"
                    )
                async for line in response.aiter_lines():
                    if not line.startswith("data:"):
                        continue
                    piece = _extract_gemini_piece(line[5:].strip())
                    if piece:
                        yield piece


def _extract_gemini_piece(payload: str) -> str:
    """Pull the text delta out of one Gemini SSE frame, or '' if absent."""
    import json

    try:
        parsed = json.loads(payload)
    except json.JSONDecodeError:
        return ""
    candidates = parsed.get("candidates") or []
    for candidate in candidates:
        parts = (candidate.get("content") or {}).get("parts") or []
        for part in parts:
            text = part.get("text")
            if isinstance(text, str) and text:
                return text
    return ""
class OpenAIProvider:
    """OpenAI chat completions via SSE."""

    name = "openai"
    endpoint = "https://api.openai.com/v1/chat/completions"

    def __init__(self, settings: Settings) -> None:
        self._settings = settings

    async def stream(
        self, question: str, chunks: list[RetrievedChunk], selected_text: str | None,
        history: list[dict[str, Any]],
    ) -> AsyncIterator[str]:
        payload = {
            "model": self._settings.openai_model,
            "messages": build_messages(question, chunks, selected_text, history),
            "stream": True,
            "temperature": 0.4,
        }
        headers = {
            "Authorization": f"Bearer {self._settings.openai_api_key}",
            "Content-Type": "application/json",
        }

        async with httpx.AsyncClient(timeout=httpx.Timeout(30.0, connect=8.0)) as client:
            async with client.stream(
                "POST", self.endpoint, json=payload, headers=headers
            ) as response:
                if response.status_code != 200:
                    detail = (await response.aread()).decode("utf-8", "replace")[:200]
                    raise RuntimeError(f"OpenAI HTTP {response.status_code}: {detail}")
                async for line in response.aiter_lines():
                    if not line.startswith("data:"):
                        continue
                    raw = line[5:].strip()
                    if raw == "[DONE]":
                        break
                    piece = _extract_openai_piece(raw)
                    if piece:
                        yield piece


def _extract_openai_piece(payload: str) -> str:
    """Pull `choices[0].delta.content` out of one OpenAI SSE frame."""
    import json

    try:
        parsed = json.loads(payload)
    except json.JSONDecodeError:
        return ""
    for choice in parsed.get("choices") or []:
        content = (choice.get("delta") or {}).get("content")
        if isinstance(content, str) and content:
            return content
    return ""


class LocalProvider:
    """Wraps the heuristic streamer behind the same Provider interface."""

    name = "local"

    def __init__(self) -> None:
        self._streamer = LocalHeuristicStreamer()

    async def stream(
        self, question: str, chunks: list[RetrievedChunk], selected_text: str | None,
        history: list[dict[str, Any]],
    ) -> AsyncIterator[str]:
        async for piece in self._streamer.stream(question, chunks, selected_text):
            yield piece


def build_provider_chain(settings: Settings | None = None) -> list[Provider]:
    """Ordered providers, honouring an explicit AI_PROVIDER override."""
    settings = settings or get_settings()
    local = LocalProvider()
    if settings.provider == "local":
        return [local]
    chain: list[Provider] = []
    if settings.provider in ("auto", "gemini") and settings.gemini_api_key:
        chain.append(GeminiProvider(settings))
    if settings.provider in ("auto", "openai") and settings.openai_api_key:
        chain.append(OpenAIProvider(settings))
    # The local streamer terminates the chain, so fallback is unconditional.
    return [*chain, local]


async def stream_answer(
    question: str,
    chunks: list[RetrievedChunk],
    selected_text: str | None,
    history: list[dict[str, Any]],
    chain: list[Provider],
) -> AsyncIterator[str]:
    """Yield answer tokens, degrading through the chain on any failure.

    Raises nothing: the local provider is the last link and cannot fail, so a
    caller streaming this generator always receives a complete answer.
    """
    for index, provider in enumerate(chain):
        emitted = False
        try:
            async for piece in provider.stream(question, chunks, selected_text, history):
                emitted = True
                yield piece
            logger.info("Answer served by provider '%s'", provider.name)
            return
        except Exception as exc:
            if emitted:
                # Switching models now would repeat text already on screen.
                logger.warning(
                    "Provider '%s' failed mid-stream after emitting tokens (%s); "
                    "closing the answer cleanly.",
                    provider.name,
                    exc,
                )
                yield "\n\n_(Câu trả lời bị ngắt do mất kết nối với dịch vụ AI.)_"
                return
            logger.warning(
                "Provider '%s' unavailable (%s); trying the next provider.",
                provider.name,
                exc,
            )
            if index == len(chain) - 1:
                yield (
                    "\n\n_(Không có dịch vụ AI nào phản hồi. Trợ giảng đã dùng "
                    "chế độ dự phòng cục bộ.)_"
                )
                return
    # Unreachable in practice: LocalProvider always yields at least once.
    yield ""
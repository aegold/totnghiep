"""Local Heuristic Streamer - the guaranteed fallback.

This is the component that makes the thesis defense survivable. When no API key
is configured, or Gemini/OpenAI is unreachable, rate-limited, or quota-exhausted,
the tutor still answers - in formal academic Vietnamese, grounded in whatever
retrieval and selection context exists - instead of returning HTTP 500.

It deliberately composes its answer from the retrieved passages and the
learner's highlighted text rather than emitting a fixed string, so the response
still tracks the question that was actually asked.
"""

from __future__ import annotations

import asyncio
import re
from typing import AsyncIterator

from .rag_service import RetrievedChunk

# A learner's sentence rarely ends on a word boundary, so split on whitespace
# but keep the trailing space attached - otherwise the UI re-renders words with
# a visible gap and, mid-stream, Markdown flickers.
_SPLIT = re.compile(r"(\S+\s*)")

_MAX_SNIPPET = 320


def _first_sentences(text: str, limit: int = 2) -> str:
    """Take the opening sentences of a passage as a quotable excerpt."""
    flattened = re.sub(r"\s+", " ", text).strip()
    parts = re.split(r"(?<=[.!?])\s+", flattened)
    return " ".join(parts[:limit]).strip()


def _digest(chunks: list[RetrievedChunk]) -> str:
    """One representative sentence per retrieved page, de-duplicated."""
    seen: set[str] = set()
    lines: list[str] = []
    for chunk in chunks:
        sentence = _first_sentences(chunk.text)
        if not sentence:
            continue
        fingerprint = sentence[:60].lower()
        if fingerprint in seen:
            continue
        seen.add(fingerprint)
        lines.append(f"- {sentence[:_MAX_SNIPPET]}")
    return "\n".join(lines)


class LocalHeuristicStreamer:
    """Produces a grounded, Vietnamese, academic-style answer offline."""

    #: Revealed in the answer so the examiner can see the fallback engaged.
    banner = (
        "> **Chế độ nội bộ:** Trợ giảng đang hoạt động ở chế độ dự phòng cục bộ "
        "(không gọi được dịch vụ AI bên ngoài), nên câu trả lời được tổng hợp "
        "trực tiếp từ nội dung tài liệu bạn đã chọn."
    )

    def compose(
        self, question: str, chunks: list[RetrievedChunk], selected_text: str | None
    ) -> str:
        """Build the full answer as a single string."""
        question = question.strip() or "nội dung này"
        selection = (selected_text or "").strip()
        digest = _digest(chunks)

        sections: list[str] = ["Chào bạn! Câu hỏi của bạn về **" + question + "** rất phù hợp với chủ đề đang học. Dưới đây là phần tổng hợp."]

        if selection:
            sections.append(
                "### 1. Phần bạn đang tập trung\n"
                f"Bạn đã đánh dấu đoạn: “{selection[:_MAX_SNIPPET]}”. "
                "Đây là ý chính cần nắm: hãy đọc nó theo ba bước — xác định thuật ngữ "
                "chuyên ngành, hiểu vai trò của nó trong hệ thống, rồi liên hệ với "
                "tình huống thực tế mà bạn đã học trước đó."
            )

        if digest:
            body = "\n".join(
                f"### {index}. Nội dung liên quan từ tài liệu\n{line}"
                for index, line in enumerate(digest.splitlines(), start=1)
            )
            sections.append(body)
        elif not selection:
            sections.append(
                "### 1. Gợi ý ôn tập\n"
                "Hiện kho tài liệu chưa có đoạn nội dung nào khớp với câu hỏi này. "
                "Bạn hãy thử **bôi đen** đoạn slide liên quan rồi gửi lại câu hỏi — "
                "trợ giảng sẽ phân tích chính xác phần đó."
            )

        sections.append(
            "### Gợi ý tiếp theo\n"
            "- Đọc lại phần vừa nêu và tự diễn đạt bằng lời của bạn.\n"
            "- Thử hỏi với một từ khóa cụ thể hơn, ví dụ tên một giao thức hoặc một "
            "lớp trong mô hình phân tầng.\n"
            "- Nếu còn vướng mắc, bôi đen đúng câu đang gây khó hiểu để nhận được "
            "giải thích sâu hơn."
        )

        return "\n\n".join([self.banner, *sections])

    async def stream(
        self, question: str, chunks: list[RetrievedChunk], selected_text: str | None
    ) -> AsyncIterator[str]:
        """Yield the composed answer in word-sized deltas with a natural cadence."""
        answer = self.compose(question, chunks, selected_text)
        for piece in _SPLIT.findall(answer):
            if not piece:
                continue
            yield piece
            # Match the perceived pace of a real model closely enough that the
            # UI's streaming indicator and cursor look correct.
            await asyncio.sleep(0.012)
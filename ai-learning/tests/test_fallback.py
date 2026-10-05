"""Tests for the provider fallback chain - the thesis-defense safety net.

The invariant under test: no failure mode reachable during a live demo may
produce an HTTP 500 or a truncated answer.
"""

from __future__ import annotations

import pytest

from services.config import Settings
from services.heuristic import LocalHeuristicStreamer
from services.llm import LocalProvider, build_provider_chain, build_messages, stream_answer
from services.rag_service import RetrievedChunk


def _chunk() -> RetrievedChunk:
    return RetrievedChunk(
        source_id="slides_ch1#p1#c0",
        text="Chuong 1: Tong quan ve Mang may tinh. Bai 1: Mo hinh OSI.",
        source_file="slides_ch1.pdf",
        page_number=1,
        score=0.8,
    )


class _ExplodingProvider:
    """Raises before yielding anything, like a quota or DNS failure."""

    name = "exploding"

    def __init__(self, exc: Exception | None = None) -> None:
        self._exc = exc or RuntimeError("quota exhausted")

    async def stream(self, question, chunks, selected_text, history):
        raise self._exc
        yield ""  # pragma: no cover - makes this an async generator


class _PartialProvider:
    """Yields one token, then fails - the mid-stream disconnect case."""

    name = "partial"

    async def stream(self, question, chunks, selected_text, history):
        yield "Mo hinh OSI"
        raise ConnectionError("upstream reset")


async def _collect(generator) -> str:
    return "".join([piece async for piece in generator])


class TestChainComposition:
    def test_chain_always_ends_with_the_local_provider(self, settings: Settings) -> None:
        chain = build_provider_chain(settings)
        assert isinstance(chain[-1], LocalProvider)

    def test_no_keys_yields_local_only(self, settings: Settings) -> None:
        assert [p.name for p in build_provider_chain(settings)] == ["local"]

    def test_gemini_key_adds_gemini_before_local(self, make_settings) -> None:
        chain = build_provider_chain(make_settings(gemini_api_key="key"))
        assert [p.name for p in chain] == ["gemini", "local"]

    def test_both_keys_ordered_gemini_then_openai(self, make_settings) -> None:
        chain = build_provider_chain(
            make_settings(gemini_api_key="key", openai_api_key="key")
        )
        assert [p.name for p in chain] == ["gemini", "openai", "local"]

    def test_explicit_provider_override_forces_local(self, make_settings) -> None:
        chain = build_provider_chain(
            make_settings(gemini_api_key="key", provider="local")
        )
        assert [p.name for p in chain] == ["local"]

    def test_gemini_is_excluded_when_only_openai_is_set(self, make_settings) -> None:
        chain = build_provider_chain(make_settings(openai_api_key="key"))
        assert [p.name for p in chain] == ["openai", "local"]

    def test_gemini_only_override_ignores_the_openai_key(self, make_settings) -> None:
        chain = build_provider_chain(
            make_settings(gemini_api_key="key", openai_api_key="key", provider="gemini")
        )
        assert [p.name for p in chain] == ["gemini", "local"]


class TestFallbackBehaviour:
    @pytest.mark.asyncio
    async def test_falls_through_when_every_remote_provider_fails(self) -> None:
        chain = [_ExplodingProvider(), LocalProvider()]
        answer = await _collect(stream_answer("Hỏi về IPv4", [], None, [], chain))
        assert answer.strip(), "the local provider must always answer"

    @pytest.mark.asyncio
    async def test_local_only_never_raises(self) -> None:
        answer = await _collect(
            stream_answer("Hỏi", [_chunk()], None, [], [LocalProvider()])
        )
        assert len(answer) > 0

    @pytest.mark.asyncio
    async def test_network_error_is_absorbed(self) -> None:
        chain = [_ExplodingProvider(ConnectionError("dns")), LocalProvider()]
        assert await _collect(stream_answer("Hỏi", [], None, [], chain))

    @pytest.mark.asyncio
    async def test_quota_error_is_absorbed(self) -> None:
        chain = [_ExplodingProvider(RuntimeError("429 quota")), LocalProvider()]
        assert await _collect(stream_answer("Hỏi", [], None, [], chain))

    @pytest.mark.asyncio
    async def test_midstream_failure_does_not_duplicate_text(self) -> None:
        """Switching models after tokens landed would repeat them on screen."""
        chain = [_PartialProvider(), LocalProvider()]
        answer = await _collect(stream_answer("Hỏi", [], None, [], chain))
        assert answer.startswith("Mo hinh OSI")
        assert "Mo hinh OSI" not in answer[10:]

    @pytest.mark.asyncio
    async def test_midstream_failure_still_terminates_cleanly(self) -> None:
        chain = [_PartialProvider(), LocalProvider()]
        answer = await _collect(stream_answer("Hỏi", [], None, [], chain))
        assert answer.strip()


class TestHeuristicComposer:
    def test_answers_in_vietnamese_academic_tone(self) -> None:
        answer = LocalHeuristicStreamer().compose("Địa chỉ IPv4", [_chunk()], None)
        assert "Chào bạn" in answer
        assert "###" in answer, "must be structured Markdown"

    def test_reflects_the_highlighted_selection(self) -> None:
        answer = LocalHeuristicStreamer().compose(
            "Giải thích thêm", [_chunk()], "Mo hinh OSI"
        )
        assert "Mo hinh OSI" in answer

    def test_quotes_retrieved_context(self) -> None:
        answer = LocalHeuristicStreamer().compose("Tóm tắt", [_chunk()], None)
        assert "Mo hinh OSI" in answer

    def test_discloses_that_the_fallback_is_active(self) -> None:
        """An examiner should be able to see the fallback engaged."""
        answer = LocalHeuristicStreamer().compose("Hỏi", [_chunk()], None)
        assert "Chế độ nội bộ" in answer

    def test_handles_no_context_at_all(self) -> None:
        answer = LocalHeuristicStreamer().compose("Câu hỏi lạ", [], None)
        assert "bôi đen" in answer, "should coach the learner to select text"

    @pytest.mark.asyncio
    async def test_streams_multiple_deltas_not_one_blob(self) -> None:
        deltas = [
            piece
            async for piece in LocalHeuristicStreamer().stream(
                "Địa chỉ IPv4", [_chunk()], None
            )
        ]
        assert len(deltas) > 20
        assert all(deltas)

    @pytest.mark.asyncio
    async def test_streamed_deltas_reassemble_the_composed_answer(self) -> None:
        streamer = LocalHeuristicStreamer()
        streamed = await _collect(streamer.stream("Địa chỉ IPv4", [_chunk()], None))
        assert streamed == streamer.compose("Địa chỉ IPv4", [_chunk()], None)


class TestPromptConstruction:
    def test_includes_retrieved_context(self) -> None:
        messages = build_messages("Hỏi", [_chunk()], None, [])
        assert "slides_ch1.pdf" in messages[-1]["content"]

    def test_includes_the_highlighted_selection(self) -> None:
        messages = build_messages("Hỏi", [], "Mo hinh OSI", [])
        assert "Mo hinh OSI" in messages[-1]["content"]

    def test_says_so_when_retrieval_is_empty(self) -> None:
        messages = build_messages("Hỏi", [], None, [])
        assert "Không truy xuất được" in messages[-1]["content"]

    def test_keeps_recent_history_only(self) -> None:
        history = [{"role": "user", "content": f"cau hoi {i}"} for i in range(20)]
        messages = build_messages("Hỏi", [], None, history)
        turns = [m for m in messages if m["role"] != "system"]
        assert len(turns) == 7, "6 history turns plus the current question"

    def test_system_prompt_is_first_and_constraints_grounding(self) -> None:
        messages = build_messages("Hỏi", [_chunk()], None, [])
        assert messages[0]["role"] == "system"
        assert "Chỉ giải thích dựa trên nội dung tài liệu" in messages[0]["content"]

    def test_malformed_history_entries_are_ignored(self) -> None:
        history = [{"role": "system", "content": "x"}, {"content": "no role"}, "junk"]
        messages = build_messages("Hỏi", [], None, history)
        assert len(messages) == 2
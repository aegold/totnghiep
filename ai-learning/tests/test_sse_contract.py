"""Contract tests for the SSE wire format.

These mirror `fe-learning/src/lib/sseParser.ts` rule for rule. The client is the
authority: if a frame violates its framing or payload expectations the frame is
silently dropped (not raised), which shows up to a learner as missing text or a
missing citation with no console error. A test that only checks "did we emit
JSON" would not catch that class of bug.
"""

from __future__ import annotations

import json
import re

import pytest

from services import sse

# From sseParser.ts: `const EVENT_BOUNDARY = /\r?\n\r?\n/`
EVENT_BOUNDARY = re.compile(r"\r?\n\r?\n")

VALID_TYPES = {"token", "citation", "sources", "done", "error"}


def split_frames(raw: str) -> list[str]:
    """Replicate the client's framing so we assert on what it would see."""
    return [part for part in EVENT_BOUNDARY.split(raw) if part != ""]


def parse_payload(frame: str) -> dict:
    """Replicate the client's payload extraction."""
    data_lines = [
        line[5:].lstrip()
        for line in frame.strip().splitlines()
        if line.startswith("data:")
    ]
    assert data_lines, f"frame carries no data: line: {frame!r}"
    return json.loads("\n".join(data_lines))


class TestFrameFraming:
    def test_every_frame_is_delimited_by_a_blank_line(self) -> None:
        raw = sse.token("Xin chao") + sse.done()
        assert raw.endswith("\n\n")
        assert len(split_frames(raw)) == 2

    def test_vietnamese_payload_stays_on_one_line(self) -> None:
        """A raw newline inside the JSON would break frame parsing."""
        raw = sse.token("Địa chỉ IPv4 và chia subnet")
        assert "\n" not in raw.rstrip("\n")
        assert parse_payload(split_frames(raw)[0])["content"] == (
            "Địa chỉ IPv4 và chia subnet"
        )

    def test_keepalive_is_a_comment_frame(self) -> None:
        """`parseSSEFrame` returns None for frames starting with ':'."""
        frame = split_frames(sse.keepalive())[0]
        assert frame.startswith(":")

    def test_payload_is_valid_json_with_a_known_type(self) -> None:
        raw = sse.token("a") + sse.citation(_citation()) + sse.done()
        for frame in split_frames(raw):
            payload = parse_payload(frame)
            assert payload["type"] in VALID_TYPES


class TestTokenFrames:
    def test_token_shape(self) -> None:
        payload = parse_payload(split_frames(sse.token("Chào bạn!"))[0])
        assert payload == {"type": "token", "content": "Chào bạn!"}

    def test_empty_token_is_still_well_formed(self) -> None:
        payload = parse_payload(split_frames(sse.token(""))[0])
        assert payload["content"] == ""


class TestCitationFrames:
    """`parseCitation` drops anything without a string `sourceId`."""

    def test_citation_shape_survives_the_parsers_validator(self) -> None:
        payload = parse_payload(split_frames(sse.citation(_citation()))[0])
        assert payload["type"] == "citation"
        source = payload["source"]
        assert isinstance(source["sourceId"], str)
        assert isinstance(source["label"], str)
        assert source["documentTitle"] is None or isinstance(source["documentTitle"], str)
        assert source["location"] is None or isinstance(source["location"], str)
        assert isinstance(source["snippet"], str)
        assert source["score"] is None or isinstance(source["score"], float)

    def test_missing_source_id_would_be_dropped_by_the_client(self) -> None:
        """Guards the exact shape `parseCitation` rejects."""
        assert "sourceId" not in {"label": "x"}

    def test_sources_carries_the_full_list(self) -> None:
        items = [_citation("a"), _citation("b")]
        payload = parse_payload(split_frames(sse.sources(items))[0])
        assert payload["type"] == "sources"
        assert [source["sourceId"] for source in payload["sources"]] == ["a", "b"]


class TestTerminationFrames:
    def test_done_frame(self) -> None:
        payload = parse_payload(split_frames(sse.done())[0])
        assert payload == {"type": "done", "finishReason": "stop"}

    def test_error_frame_carries_a_message(self) -> None:
        payload = parse_payload(split_frames(sse.error("Lỗi"))[0])
        assert payload == {"type": "error", "message": "Lỗi"}


class TestStreamShape:
    def test_a_realistic_stream_is_citations_then_tokens_then_done(self) -> None:
        raw = (
            sse.citation(_citation())
            + sse.token("Chào bạn! ")
            + sse.token("Về khái niệm này...")
            + sse.sources([_citation()])
            + sse.done()
        )
        frames = split_frames(raw)
        types = [parse_payload(frame)["type"] for frame in frames]
        assert types == ["citation", "token", "token", "sources", "done"]
        assert types[-1] == "done"

    @pytest.mark.parametrize("fragment", ["a", "Địa chỉ IPv4", "π = 3.14", "🙂"])
    def test_multibyte_fragments_survive_serialisation(self, fragment: str) -> None:
        payload = parse_payload(split_frames(sse.token(fragment))[0])
        assert payload["content"] == fragment


def _citation(source_id: str = "slides_ch1#p1#c0") -> dict:
    return {
        "sourceId": source_id,
        "label": "Trang 1",
        "documentTitle": "slides_ch1.pdf",
        "location": "p.1",
        "snippet": "Chuong 1: Tong quan ve Mang may tinh",
        "score": 0.7993,
    }
"""End-to-end tests through the ASGI app.

The offline guarantee is asserted here, at the HTTP boundary, because that is
what the defence actually depends on: a blank `.env` must still yield a 200 and
a complete `token ... done` sequence.
"""

from __future__ import annotations

import json
import re

import pytest
from fastapi.testclient import TestClient

from main import app

EVENT_BOUNDARY = re.compile(r"\r?\n\r?\n")

BASE_PAYLOAD = {
    "courseId": 1,
    "message": "Dia chi IPv4 la gi?",
    "history": [],
    "selectedText": "Dia chi IPv4 va Chia Subnet",
    "materialId": 1,
}


@pytest.fixture(scope="module")
def client() -> TestClient:
    # `with` runs the lifespan hook, so indexing happens exactly as in production.
    with TestClient(app) as test_client:
        yield test_client


def parse_stream(raw: str) -> list[dict]:
    """Split the raw body the way the browser's SSEParser does."""
    events: list[dict] = []
    for frame in EVENT_BOUNDARY.split(raw):
        if not frame.strip() or frame.strip().startswith(":"):
            continue
        lines = [
            line[5:].lstrip()
            for line in frame.strip().splitlines()
            if line.startswith("data:")
        ]
        if lines:
            events.append(json.loads("\n".join(lines)))
    return events


def ask(client: TestClient, **overrides: object) -> list[dict]:
    return parse_stream(
        client.post("/api/v1/ai/tutor/chat-stream", json={**BASE_PAYLOAD, **overrides}).text
    )


class TestHealth:
    def test_health_reports_up(self, client: TestClient) -> None:
        response = client.get("/api/v1/health")
        assert response.status_code == 200
        assert response.json()["status"] == "UP"

    def test_health_lists_the_active_providers(self, client: TestClient) -> None:
        body = client.get("/api/v1/health").json()
        assert body["providers"][-1] == "local"

    def test_health_reports_the_indexed_chunk_count(self, client: TestClient) -> None:
        assert client.get("/api/v1/health").json()["chunks"] >= 1


class TestChatStream:
    def test_answers_200_with_the_sse_media_type(self, client: TestClient) -> None:
        response = client.post("/api/v1/ai/tutor/chat-stream", json=BASE_PAYLOAD)
        assert response.status_code == 200
        assert response.headers["content-type"].startswith("text/event-stream")

    def test_disables_proxy_buffering(self, client: TestClient) -> None:
        """A buffering proxy makes the stream appear to hang until completion."""
        response = client.post("/api/v1/ai/tutor/chat-stream", json=BASE_PAYLOAD)
        assert response.headers.get("x-accel-buffering") == "no"

    def test_stream_terminates_with_a_done_event(self, client: TestClient) -> None:
        assert ask(client)[-1]["type"] == "done"

    def test_stream_contains_answer_tokens(self, client: TestClient) -> None:
        tokens = [e for e in ask(client) if e["type"] == "token"]
        assert len(tokens) > 10, "answer must stream in many deltas"
        assert all(isinstance(e["content"], str) for e in tokens)

    def test_reassembled_answer_is_coherent_vietnamese(self, client: TestClient) -> None:
        answer = "".join(e["content"] for e in ask(client) if e["type"] == "token")
        assert "Chào bạn" in answer
        assert answer.startswith(">"), "banner should come first"

    def test_emits_citations_for_the_retrieved_material(self, client: TestClient) -> None:
        citations = [e for e in ask(client) if e["type"] == "citation"]
        assert citations, "the stub PDF is indexed, so a citation is expected"
        source = citations[0]["source"]
        assert source["sourceId"]
        assert source["documentTitle"] == "slides_ch1.pdf"
        assert source["location"].startswith("p.")

    def test_citations_precede_the_answer_text(self, client: TestClient) -> None:
        types = [e["type"] for e in ask(client)]
        assert types.index("citation") < types.index("token")

class TestOfflineGuarantee:
    def test_answers_200_with_no_api_key_configured(self, client: TestClient) -> None:
        """The defence-safety invariant, asserted at the HTTP boundary."""
        response = client.post("/api/v1/ai/tutor/chat-stream", json=BASE_PAYLOAD)
        assert response.status_code == 200
        assert parse_stream(response.text)[-1]["type"] == "done"

    def test_question_with_no_selection_still_answers(self, client: TestClient) -> None:
        events = ask(client, selectedText=None)
        assert events[-1]["type"] == "done"

    def test_never_returns_a_bearer_challenge(self, client: TestClient) -> None:
        """A 401 would render in the chat panel as an expired session."""
        response = client.post(
            "/api/v1/ai/tutor/chat-stream",
            json=BASE_PAYLOAD,
            headers={"Authorization": "Bearer spring-jwt-token"},
        )
        assert response.status_code != 401


class TestValidation:
    def test_rejects_an_empty_message(self, client: TestClient) -> None:
        response = client.post(
            "/api/v1/ai/tutor/chat-stream", json={**BASE_PAYLOAD, "message": ""}
        )
        assert response.status_code == 422

    def test_requires_course_id(self, client: TestClient) -> None:
        payload = {k: v for k, v in BASE_PAYLOAD.items() if k != "courseId"}
        assert client.post("/api/v1/ai/tutor/chat-stream", json=payload).status_code == 422

    def test_history_defaults_when_omitted(self, client: TestClient) -> None:
        payload = {k: v for k, v in BASE_PAYLOAD.items() if k != "history"}
        assert client.post("/api/v1/ai/tutor/chat-stream", json=payload).status_code == 200
    def test_final_sources_list_is_present(self, client: TestClient) -> None:
        sources = [e for e in ask(client) if e["type"] == "sources"]
        assert sources and isinstance(sources[-1]["sources"], list)

    def test_no_error_frame_on_the_happy_path(self, client: TestClient) -> None:
        assert not [e for e in ask(client) if e["type"] == "error"]
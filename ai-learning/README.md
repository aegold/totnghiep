# AI Tutor Microservice (`ai-learning`)

FastAPI RAG service for the Adaptive LMS. Answers questions about course
materials with citations, streamed over Server-Sent Events.

Runs on **port 8000**, alongside the Spring Boot API (8080) and the Vite
frontend (5173).

---

## Why the design is shaped this way

**The offline fallback is the point, not a nice-to-have.** A thesis defence
cannot be allowed to fail because the venue's wifi dropped or a Gemini quota
ran out. So every provider failure - missing key, DNS error, timeout, HTTP 429,
exhausted quota, malformed stream - falls through to `LocalHeuristicStreamer`,
which composes a grounded Vietnamese answer from the retrieved passages and the
learner's highlighted text. The service never answers `500`.

**No neural embeddings are downloaded.** `sentence-transformers` pulls Torch
(no dependable Python 3.13 wheel, ~2.5 GB) and ChromaDB's default embedding
function fetches a model from a CDN on first use. Both break the offline
guarantee, so `services/embedding.py` uses a deterministic hashed
character-n-gram model instead, paired with an explicit lexical-overlap score.
Same reasoning made ChromaDB optional: `services/vector_store.py` falls back to
a JSON-backed numpy store if the import fails.

**Vietnamese diacritics are folded before matching.** Course slides are often
exported without diacritics (`Dia chi IPv4`) while learners type with them
(`Địa chỉ IPv4`). `fold_vietnamese` normalises both sides - including `đ/ơ/ư`,
which have no canonical NFD decomposition and must be mapped by hand. Without
this, lexical overlap between the two spellings drops by half.

---

## Wire contract

The React client is the authority. The contract is defined in
`fe-learning/src/features/course-learning/types.ts` and parsed by
`fe-learning/src/lib/sseParser.ts`.

`POST /api/v1/ai/tutor/chat-stream`

Request (`TutorChatRequest`):

```json
{
  "courseId": 1,
  "message": "Địa chỉ IPv4 là gì?",
  "history": [],
  "selectedText": "Bài 2: Địa chỉ IPv4 và Chia Subnet",
  "materialId": 1
}
```

Response frames - one JSON object per `data:` line, terminated by a blank line:

```
data: {"type":"citation","source":{"sourceId":"...","label":"Trang 1","documentTitle":"slides_ch1.pdf","location":"p.1","snippet":"...","score":0.81}}

data: {"type":"token","content":"Chào bạn! "}

data: {"type":"sources","sources":[...]}

data: {"type":"done","finishReason":"stop"}
```

Order is `citation` -> `token`* -> `sources` -> `done`, so citation badges exist
before any prose renders. `: keepalive` comment frames may appear between
tokens; the client discards them.

### Citation format

Citations are **structured objects**, not rendered strings. `parseCitation` in
the client drops any source lacking a string `sourceId`, so a
`"[Nguồn: Trang X, slides_ch1.pdf]"` string would silently vanish rather than
display.

---

## Authentication

The Spring Boot bearer token is forwarded by `useSSEChat` but **not validated**
here. This service stores no user data and issues no credentials, so it
deliberately never returns `401` - that would surface in the chat panel as a
session-expired error. Anything reachable on port 8000 can call the tutor.

---

## Configuration (`.env`)

| Variable | Default | Purpose |
| --- | --- | --- |
| `PORT` | `8000` | Listen port |
| `AI_PROVIDER` | `auto` | `auto` / `gemini` / `openai` / `local` |
| `GEMINI_API_KEY` | *(blank)* | Blank means Gemini is skipped entirely |
| `GEMINI_MODEL` | `gemini-2.0-flash` | |
| `OPENAI_API_KEY` | *(blank)* | |
| `OPENAI_MODEL` | `gpt-4o-mini` | |
| `MATERIALS_DIR` | `../be-learning/uploads/materials` | PDFs to ingest |
| `CHROMA_PERSIST_DIR` | `./data/chromadb` | Vector index |
| `CHUNK_SIZE` / `CHUNK_OVERLAP` | `450` / `50` | Chunking window |
| `TOP_K` | `3` | Retrieved passages per question |

The checked-in `.env` has **no keys**, so a fresh clone runs fully offline.

---

## Running

```bash
# From the repository root - starts backend, frontend, and this service together
start-all.bat

# Or standalone
cd ai-learning
.venv\Scripts\python.exe -m uvicorn main:app --port 8000
```

- Health: `http://localhost:8000/api/v1/health`
- OpenAPI: `http://localhost:8000/docs`

`stop-all.bat` closes the `LMS_AI_SERVICE` window, frees port 8000, and sweeps
project-scoped Python processes.

---

## Tests

```bash
.venv\Scripts\python.exe -m pytest
```

Covers the SSE framing rules, the fallback chain (including mid-stream failure),
Vietnamese folding, chunking, and the HTTP-level offline guarantee.

The **cross-service contract** is asserted from the other side, in
`fe-learning/src/lib/__tests__/sseContract.integration.test.ts`, which replays a
byte-for-byte capture of this service's output (`samples/sse-sample.sse`)
through the real client parser - including at 1-byte chunk boundaries, where
multi-byte Vietnamese characters straddle the split.

Regenerate the capture after changing `services/sse.py`; the header of that test
file has the `curl` command.

---

## Known limitation

`be-learning/uploads/materials/slides_ch1.pdf` is currently a one-page stub of
about five lines, so the corpus indexes to a **single chunk**. Top-3 retrieval
therefore returns one result, and answers lean heavily on `selectedText`. The
retriever returns `min(top_k, available)` rather than padding, because
inventing sources to reach three would be fabricated provenance.

Dropping a real multi-page slide deck into that folder requires no code change -
the index rebuilds automatically on the next start.
"""Vector storage with a guaranteed-working local fallback.

ChromaDB is the intended backend and is used whenever it imports cleanly. If it
does not - a missing native dependency, a read-only volume at defense time - the
service degrades to a JSON-backed numpy store rather than failing to start. The
chunk corpus here is small (tens to low thousands of chunks), so the fallback is
entirely adequate.
"""

from __future__ import annotations

import json
import logging
from pathlib import Path
from typing import Any, Protocol

from .embedding import cosine

logger = logging.getLogger(__name__)

COLLECTION_NAME = "adaptive_learning_materials"


class VectorStore(Protocol):
    """Minimal surface `rag_service` depends on."""

    def count(self) -> int: ...

    def add(
        self,
        ids: list[str],
        embeddings: list[list[float]],
        documents: list[str],
        metadatas: list[dict[str, Any]],
    ) -> None: ...

    def query(self, embedding: list[float], top_k: int) -> list[tuple[str, float]]: ...

    def document(self, chunk_id: str) -> str | None: ...

    def metadata(self, chunk_id: str) -> dict[str, Any] | None: ...


class ChromaStore:
    """ChromaDB-backed store.

    Embeddings are always supplied explicitly so ChromaDB never instantiates
    its default embedding function - that model is downloaded from a CDN on
    first use, which would defeat the offline guarantee.
    """

    def __init__(self, persist_dir: Path) -> None:
        import chromadb
        from chromadb.config import Settings as ChromaSettings

        self._client = chromadb.PersistentClient(
            path=str(persist_dir),
            settings=ChromaSettings(anonymized_telemetry=False, allow_reset=True),
        )
        self._collection = self._client.get_or_create_collection(
            name=COLLECTION_NAME, metadata={"hnsw:space": "cosine"}
        )

    def count(self) -> int:
        return int(self._collection.count())

    def add(
        self,
        ids: list[str],
        embeddings: list[list[float]],
        documents: list[str],
        metadatas: list[dict[str, Any]],
    ) -> None:
        # Chroma rejects metadata values that are not str/int/float/bool.
        safe = [{key: str(value) for key, value in meta.items()} for meta in metadatas]
        self._collection.add(
            ids=ids, embeddings=embeddings, documents=documents, metadatas=safe
        )

    def query(self, embedding: list[float], top_k: int) -> list[tuple[str, float]]:
        result = self._collection.query(
            query_embeddings=[embedding], n_results=top_k, include=["distances"]
        )
        ids = (result.get("ids") or [[]])[0]
        distances = (result.get("distances") or [[]])[0]
        # Chroma reports cosine distance: 1 - similarity.
        return [
            (str(chunk_id), max(0.0, 1.0 - float(distance)))
            for chunk_id, distance in zip(ids, distances)
        ]

    def document(self, chunk_id: str) -> str | None:
        fetched = self._collection.get(ids=[chunk_id], include=["documents"])
        documents = fetched.get("documents") or []
        return documents[0] if documents else None

    def metadata(self, chunk_id: str) -> dict[str, Any] | None:
        fetched = self._collection.get(ids=[chunk_id], include=["metadatas"])
        metadatas = fetched.get("metadatas") or []
        return metadatas[0] if metadatas else None
class NumpyStore:
    """JSON-persisted linear-scan store used when ChromaDB is unavailable."""

    def __init__(self, persist_dir: Path) -> None:
        self._path = persist_dir.parent / "numpy_store.json"
        self._index: dict[str, dict[str, Any]] = {}
        self._embeddings: dict[str, list[float]] = {}
        if self._path.exists():
            try:
                payload = json.loads(self._path.read_text(encoding="utf-8"))
                self._index = payload.get("index", {})
                self._embeddings = payload.get("embeddings", {})
            except (json.JSONDecodeError, OSError) as exc:
                logger.warning("Numpy store corrupt, rebuilding: %s", exc)

    def count(self) -> int:
        return len(self._index)

    def add(
        self,
        ids: list[str],
        embeddings: list[list[float]],
        documents: list[str],
        metadatas: list[dict[str, Any]],
    ) -> None:
        for chunk_id, vector, document, metadata in zip(
            ids, embeddings, documents, metadatas
        ):
            self._index[chunk_id] = {"document": document, "metadata": metadata}
            self._embeddings[chunk_id] = vector
        self._flush()

    def query(self, embedding: list[float], top_k: int) -> list[tuple[str, float]]:
        scored = [
            (chunk_id, cosine(embedding, stored))
            for chunk_id, stored in self._embeddings.items()
        ]
        scored.sort(key=lambda pair: pair[1], reverse=True)
        return scored[:top_k]

    def document(self, chunk_id: str) -> str | None:
        record = self._index.get(chunk_id)
        return record["document"] if record else None

    def metadata(self, chunk_id: str) -> dict[str, Any] | None:
        record = self._index.get(chunk_id)
        return record["metadata"] if record else None

    def _flush(self) -> None:
        self._path.parent.mkdir(parents=True, exist_ok=True)
        self._path.write_text(
            json.dumps(
                {"index": self._index, "embeddings": self._embeddings},
                ensure_ascii=False,
            ),
            encoding="utf-8",
        )


def get_store(persist_dir: Path) -> VectorStore:
    """Build the best available store, logging which backend won."""
    try:
        store = ChromaStore(persist_dir)
        logger.info("Vector store: ChromaDB at %s", persist_dir)
        return store
    except Exception as exc:  # pragma: no cover - environment dependent
        logger.warning(
            "ChromaDB unavailable (%s); falling back to the local numpy store.", exc
        )
        return NumpyStore(persist_dir)
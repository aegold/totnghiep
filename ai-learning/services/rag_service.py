"""Document ingestion and retrieval for the AI Tutor.

Pipeline: pypdf -> character chunking -> vector store -> hybrid retrieval
(dense cosine + lexical overlap) -> citation objects.

`ensure_indexed()` runs at startup, so the first learner question never pays
the indexing cost.
"""

from __future__ import annotations

import logging
import re
import threading
from dataclasses import dataclass
from pathlib import Path
from typing import Any

from pypdf import PdfReader

from .config import Settings, get_settings
from .embedding import embed, lexical_overlap
from .vector_store import VectorStore, get_store

logger = logging.getLogger(__name__)

# Weights for the hybrid score. Dense similarity alone mis-ranks short chunks;
# exact term overlap is a strong signal for technical coursework.
DENSE_WEIGHT = 0.6
LEXICAL_WEIGHT = 0.4

_WHITESPACE = re.compile(r"[ \t]+")


@dataclass(frozen=True)
class RetrievedChunk:
    """One retrieved passage plus the provenance the UI renders as a badge."""

    source_id: str
    text: str
    source_file: str
    page_number: int
    score: float

    def to_citation(self) -> dict[str, Any]:
        """Shape matching `TutorCitation` in fe-learning/types.ts.

        The wire format is a structured object - the parser rejects anything
        without a string `sourceId`, so a rendered "[Nguon: ...]" string would
        be silently dropped rather than displayed.
        """
        return {
            "sourceId": self.source_id,
            "label": f"Trang {self.page_number}",
            "documentTitle": self.source_file,
            "location": f"p.{self.page_number}",
            "snippet": self.text[:220],
            "score": round(self.score, 4),
        }


@dataclass(frozen=True)
class _RawChunk:
    """Pre-embedding unit of text."""

    source_id: str
    text: str
    source_file: str
    page_number: int


def _clean(text: str) -> str:
    """Collapse whitespace so chunk offsets stay meaningful."""
    return _WHITESPACE.sub(" ", text.replace("\n", " ")).strip()


def chunk_page(
    text: str, page_number: int, source_file: str, size: int, overlap: int
) -> list[_RawChunk]:
    """Split one page into overlapping windows.

    A page shorter than `size` yields a single chunk rather than an empty list -
    the stub PDF in this repo is one page of a few lines, and dropping it would
    leave the tutor with nothing to ground on.
    """
    cleaned = _clean(text)
    if not cleaned:
        return []
    stem = Path(source_file).stem
    if len(cleaned) <= size:
        return [
            _RawChunk(f"{stem}#p{page_number}#c0", cleaned, source_file, page_number)
        ]

    step = max(1, size - overlap)
    chunks: list[_RawChunk] = []
    for index, start in enumerate(range(0, len(cleaned), step)):
        window = cleaned[start : start + size].strip()
        if len(window) < 40:
            break
        chunks.append(
            _RawChunk(f"{stem}#p{page_number}#c{index}", window, source_file, page_number)
        )
        if start + size >= len(cleaned):
            break
    return chunks
class RagService:
    """Indexes course materials once, then answers similarity queries."""

    def __init__(self, settings: Settings | None = None) -> None:
        self._settings = settings or get_settings()
        self._store: VectorStore = get_store(self._settings.chroma_dir)
        self._lock = threading.Lock()
        self._indexed = False

    @property
    def chunk_count(self) -> int:
        return self._store.count()

    def ensure_indexed(self, force: bool = False) -> int:
        """Index every PDF in MATERIALS_DIR when the store is empty."""
        with self._lock:
            if self._indexed and not force:
                return self._store.count()
            if self._store.count() > 0 and not force:
                self._indexed = True
                logger.info("Vector store already holds %d chunks", self._store.count())
                return self._store.count()
            self._rebuild()
            self._indexed = True
            return self._store.count()

    def _rebuild(self) -> None:
        materials_dir: Path = self._settings.materials_dir
        if not materials_dir.is_dir():
            logger.warning(
                "MATERIALS_DIR %s does not exist; the tutor will run without "
                "retrieved context and rely on the learner's selection.",
                materials_dir,
            )
            return

        raw: list[_RawChunk] = []
        for pdf in sorted(materials_dir.glob("*.pdf")):
            try:
                reader = PdfReader(str(pdf))
            except Exception as exc:
                logger.warning("Skipping unreadable PDF %s: %s", pdf.name, exc)
                continue
            for page_index, page in enumerate(reader.pages):
                raw.extend(
                    chunk_page(
                        page.extract_text() or "",
                        page_index + 1,
                        pdf.name,
                        self._settings.chunk_size,
                        self._settings.chunk_overlap,
                    )
                )
            logger.info("Read %s (%d pages)", pdf.name, len(reader.pages))

        if not raw:
            logger.warning("No extractable text found under %s", materials_dir)
            return

        self._store.add(
            ids=[chunk.source_id for chunk in raw],
            embeddings=[embed(chunk.text) for chunk in raw],
            documents=[chunk.text for chunk in raw],
            metadatas=[
                {"source_file": chunk.source_file, "page_number": chunk.page_number}
                for chunk in raw
            ],
        )
        logger.info("Stored %d chunks in the vector store", len(raw))

    def retrieve(
        self, query: str, selected_text: str | None = None, top_k: int | None = None
    ) -> list[RetrievedChunk]:
        """Return the best passages for `query`.

        The learner's highlighted text is folded into the query: when they ask
        "giai thich them" with a phrase selected, that phrase is the strongest
        available signal about what they mean.

        Returns fewer than `top_k` when the corpus is smaller than the request -
        padding the list would fabricate provenance.
        """
        limit = top_k or self._settings.top_k
        search_text = query.strip()
        if selected_text and selected_text.strip():
            search_text = f"{selected_text.strip()} {search_text}".strip()
        if not search_text:
            return []

        candidates = self._store.query(embed(search_text), limit)
        results: list[RetrievedChunk] = []
        for chunk_id, dense_score in candidates:
            document = self._store.document(chunk_id) or ""
            metadata = self._store.metadata(chunk_id) or {}
            try:
                page = int(metadata.get("page_number", 1))
            except (TypeError, ValueError):
                page = 1
            combined = DENSE_WEIGHT * dense_score + LEXICAL_WEIGHT * lexical_overlap(
                search_text, document
            )
            results.append(
                RetrievedChunk(
                    source_id=chunk_id,
                    text=document,
                    source_file=str(metadata.get("source_file", "tai-lieu")),
                    page_number=page,
                    score=combined,
                )
            )
        results.sort(key=lambda chunk: chunk.score, reverse=True)
        return results
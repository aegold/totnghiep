"""Test configuration.

Every test runs against a temporary vector store and forces the local provider,
so the suite is hermetic, fast, and unaffected by whatever key happens to be in
the developer's `.env` or by network availability.
"""

from __future__ import annotations

import os
import sys
import tempfile
from pathlib import Path

import pytest

SERVICE_ROOT = Path(__file__).resolve().parent.parent
sys.path.insert(0, str(SERVICE_ROOT))

_TMP_DIR = tempfile.mkdtemp(prefix="ai-learning-tests-")

os.environ["AI_PROVIDER"] = "local"
os.environ["GEMINI_API_KEY"] = ""
os.environ["OPENAI_API_KEY"] = ""
os.environ["CHROMA_PERSIST_DIR"] = str(Path(_TMP_DIR) / "chromadb")
os.environ["MATERIALS_DIR"] = str(SERVICE_ROOT / ".." / "be-learning" / "uploads" / "materials")

# Settings caches on first read; clear it so the overrides above take effect.
from services.config import Settings, get_settings  # noqa: E402

get_settings.cache_clear()


@pytest.fixture
def rag(rag_service_factory):
    """A RagService over the real course materials in an isolated store."""
    return rag_service_factory()


@pytest.fixture
def rag_service_factory():
    """Builds independent RagService instances sharing one temp store."""
    from services.config import get_settings as _get_settings
    from services.rag_service import RagService

    def _factory() -> RagService:
        return RagService(_get_settings())

    return _factory


@pytest.fixture
def make_settings():
    """Builds Settings variants; the dataclass is frozen so tests cannot mutate."""
    import dataclasses

    base = Settings(
        port=8000,
        gemini_api_key="",
        openai_api_key="",
        gemini_model="gemini-2.0-flash",
        openai_model="gpt-4o-mini",
        provider="auto",
        materials_dir=SERVICE_ROOT / ".." / "be-learning" / "uploads" / "materials",
        chroma_dir=Path(_TMP_DIR) / "chromadb",
        chunk_size=450,
        chunk_overlap=50,
        top_k=3,
    )

    def _factory(**overrides: object) -> Settings:
        return dataclasses.replace(base, **overrides)  # type: ignore[arg-type]

    return _factory


@pytest.fixture
def settings() -> Settings:
    """A fresh, key-free Settings."""
    return Settings(
        port=8000,
        gemini_api_key="",
        openai_api_key="",
        gemini_model="gemini-2.0-flash",
        openai_model="gpt-4o-mini",
        provider="auto",
        materials_dir=SERVICE_ROOT / ".." / "be-learning" / "uploads" / "materials",
        chroma_dir=Path(_TMP_DIR) / "chromadb",
        chunk_size=450,
        chunk_overlap=50,
        top_k=3,
    )
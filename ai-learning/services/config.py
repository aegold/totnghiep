"""Runtime configuration loaded from `.env`.

Every setting has a safe default so the service starts even when `.env` is
missing entirely - a thesis defense must never hinge on a config file.
"""

from __future__ import annotations

import os
from dataclasses import dataclass
from functools import lru_cache
from pathlib import Path

from dotenv import load_dotenv

BASE_DIR = Path(__file__).resolve().parent.parent
load_dotenv(BASE_DIR / ".env")


def _abs_path(raw: str, default: Path) -> Path:
    """Resolve a possibly-relative path against the service root."""
    candidate = Path(raw)
    return candidate if candidate.is_absolute() else (BASE_DIR / candidate).resolve()


@dataclass(frozen=True)
class Settings:
    port: int
    gemini_api_key: str
    openai_api_key: str
    gemini_model: str
    openai_model: str
    provider: str
    materials_dir: Path
    chroma_dir: Path
    chunk_size: int
    chunk_overlap: int
    top_k: int


@lru_cache(maxsize=1)
def get_settings() -> Settings:
    return Settings(
        port=int(os.getenv("PORT", "8000")),
        gemini_api_key=os.getenv("GEMINI_API_KEY", "").strip(),
        openai_api_key=os.getenv("OPENAI_API_KEY", "").strip(),
        gemini_model=os.getenv("GEMINI_MODEL", "gemini-2.0-flash"),
        openai_model=os.getenv("OPENAI_MODEL", "gpt-4o-mini"),
        provider=os.getenv("AI_PROVIDER", "auto").strip().lower(),
        materials_dir=_abs_path(
            os.getenv("MATERIALS_DIR", "../be-learning/uploads/materials"),
            BASE_DIR / "../be-learning/uploads/materials",
        ),
        chroma_dir=_abs_path(
            os.getenv("CHROMA_PERSIST_DIR", "./data/chromadb"), BASE_DIR / "data/chromadb"
        ),
        chunk_size=int(os.getenv("CHUNK_SIZE", "450")),
        chunk_overlap=int(os.getenv("CHUNK_OVERLAP", "50")),
        top_k=int(os.getenv("TOP_K", "3")),
    )


def has_any_provider_key(settings: Settings) -> bool:
    """True when at least one external provider is configured."""
    return bool(settings.gemini_api_key or settings.openai_api_key)
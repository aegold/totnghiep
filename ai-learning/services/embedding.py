"""Deterministic, fully offline text embeddings.

Why not `sentence-transformers`? It drags in Torch (no dependable Python 3.13
wheel, ~2.5 GB) and ChromaDB's bundled ONNX model downloads from a CDN on first
use. Both break the hard requirement that the tutor works with the network
cable pulled.

Instead this is a hashed character n-gram model: stable across processes, needs
no downloads, and captures the sub-word morphology that Vietnamese technical
terms need ("mạng"/"mạng máy tính", " chia"/"chia subnet"). It is weaker than a
transformer at pure semantics, so `rag_service` pairs it with an explicit
lexical overlap score - retrieval quality on a small corpus depends more on that
hybrid than on the embedding itself.
"""

from __future__ import annotations

import hashlib
import math
import re
import unicodedata
from collections import Counter

import numpy as np

DIMENSIONS = 256

# Vietnamese technical writing mixes Latin words, digits, and hyphenated terms.
_TOKEN_PATTERN = re.compile(r"[0-9a-zA-ZÀ-ỹ]+", re.UNICODE)

# Course slides are frequently exported without diacritics ("Dia chi IPv4"),
# while learners type them ("Địa chỉ IPv4"). Folding both sides to the same
# key is what keeps retrieval working across that mismatch.
#
# These four letters have no canonical decomposition, so NFD alone leaves them
# intact and they must be mapped by hand: đ is not d+stroke, and the horn on
# ơ/ư is a distinct codepoint rather than a combining accent.
_UNDECOMPOSABLE = {
    "đ": "d", "Đ": "D",
    "ơ": "o", "Ơ": "O",
    "ư": "u", "Ư": "U",
}
_HORN_TRANSLATION = str.maketrans(_UNDECOMPOSABLE)

# Stopwords removed before lexical scoring; they match every query and would
# otherwise inflate similarity uniformly.
_STOPWORDS = frozenset(
    """
    va cac cua co la cho mot nhung trong nay do de duoc khong nhu ve voi
    la ngi gi con nua thoi nhung vi o mot tu den se ra roi nua may
    """.split()
)


def fold_vietnamese(text: str) -> str:
    """Strip diacritics so accented and unaccented spellings compare equal."""
    translated = text.translate(_HORN_TRANSLATION)
    decomposed = unicodedata.normalize("NFD", translated)
    # Drop the combining marks left behind by NFD.
    stripped = "".join(char for char in decomposed if not unicodedata.combining(char))
    return unicodedata.normalize("NFC", stripped)


def tokenize(text: str) -> list[str]:
    """Lowercase, diacritic-folded word tokens with stopwords removed."""
    return [
        token
        for token in _TOKEN_PATTERN.findall(fold_vietnamese(text.lower()))
        if token not in _STOPWORDS and len(token) > 1
    ]


def _ngrams(text: str) -> list[str]:
    """Word-level and character-level features, hashed into one feature space."""
    normalized = " ".join(_TOKEN_PATTERN.findall(fold_vietnamese(text.lower())))
    padded = f" {normalized} "
    features: list[str] = []
    for token in normalized.split():
        features.extend(f"w:{token}")
        # Character trigrams give partial credit to morphological variants.
        padded_token = f"<{token}>"
        features.extend(
            f"c:{padded_token[i : i + 3]}" for i in range(len(padded_token) - 2)
        )
    # Word bigrams keep local phrase ordering meaningful.
    words = normalized.split()
    features.extend(f"b:{a}_{b}" for a, b in zip(words, words[1:]))
    del padded
    return features


def embed(text: str) -> list[float]:
    """Embed one string into a unit-length dense vector."""
    vector = np.zeros(DIMENSIONS, dtype=np.float32)
    counts = Counter(_ngrams(text))
    if not counts:
        return vector.tolist()

    for feature, count in counts.items():
        # Stable per-process hash: built-in hash() is salted per interpreter
        # run, which would silently invalidate a persisted index.
        digest = hashlib.blake2b(feature.encode("utf-8"), digest_size=8).digest()
        bucket = int.from_bytes(digest, "big") % DIMENSIONS
        # Signed hashing reduces the collision bias of unsigned assignment.
        sign = 1.0 if digest[0] % 2 == 0 else -1.0
        vector[bucket] += sign * (1.0 + math.log(count))

    norm = float(np.linalg.norm(vector))
    if norm > 0.0:
        vector /= norm
    return vector.tolist()


def cosine(left: list[float], right: list[float]) -> float:
    """Cosine similarity; both inputs are expected to be unit length."""
    if not left or not right:
        return 0.0
    a = np.asarray(left, dtype=np.float32)
    b = np.asarray(right, dtype=np.float32)
    denom = float(np.linalg.norm(a) * np.linalg.norm(b))
    if denom == 0.0:
        return 0.0
    return float(np.dot(a, b) / denom)


def lexical_overlap(query: str, document: str) -> float:
    """Fraction of the query's meaningful tokens present in the document."""
    query_tokens = set(tokenize(query))
    if not query_tokens:
        return 0.0
    document_tokens = set(tokenize(document))
    return len(query_tokens & document_tokens) / len(query_tokens)
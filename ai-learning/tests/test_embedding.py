"""Tests for the offline embedding layer."""

from __future__ import annotations

import pytest

from services.embedding import (
    cosine,
    embed,
    fold_vietnamese,
    lexical_overlap,
    tokenize,
)


class TestDiacriticFolding:
    @pytest.mark.parametrize(
        ("accented", "plain"),
        [
            ("Địa chỉ IPv4", "Dia chi IPv4"),
            ("Mô hình OSI", "Mo hinh OSI"),
            ("Bảo mật", "Bao mat"),
            ("Đường dẫn", "Duong dan"),
            ("Người dùng", "Nguoi dung"),
        ],
    )
    def test_accented_and_plain_forms_agree(self, accented: str, plain: str) -> None:
        assert fold_vietnamese(accented) == plain

    def test_undecomposable_letters_are_mapped(self) -> None:
        """đ/ơ/ư have no NFD decomposition and need explicit mapping."""
        assert fold_vietnamese("đ") == "d"
        assert fold_vietnamese("Đ") == "D"
        assert fold_vietnamese("ơ") == "o"
        assert fold_vietnamese("Ơ") == "O"
        assert fold_vietnamese("ư") == "u"
        assert fold_vietnamese("Ư") == "U"

    def test_tokenizer_normalises_to_plain_ascii(self) -> None:
        assert tokenize("Địa chỉ IPv4 và chia subnet") == [
            "dia",
            "chi",
            "ipv4",
            "chia",
            "subnet",
        ]

    def test_folding_does_not_strip_non_vietnamese_text(self) -> None:
        assert fold_vietnamese("TCP/IP v4") == "TCP/IP v4"


class TestEmbedding:
    def test_is_deterministic_across_calls(self) -> None:
        """A persisted index is only reusable if hashing is stable per process."""
        assert embed("Dia chi IPv4") == embed("Dia chi IPv4")

    def test_vector_is_unit_length(self) -> None:
        vector = embed("Moi hinh OSI")
        assert abs(sum(value * value for value in vector) - 1.0) < 1e-5

    def test_empty_text_yields_a_zero_vector(self) -> None:
        assert all(value == 0.0 for value in embed(""))

    def test_identical_text_scores_one(self) -> None:
        assert cosine(embed("Dia chi IPv4"), embed("Dia chi IPv4")) == pytest.approx(1.0)

    def test_unrelated_text_scores_below_one(self) -> None:
        score = cosine(embed("Dia chi IPv4"), embed("Mo hinh OSI va kien truc TCP/IP"))
        assert score < 0.9

    def test_accented_query_matches_unaccented_document(self) -> None:
        """The real corpus case: slides lack diacritics, learners type them."""
        query = embed("Địa chỉ IPv4 và chia subnet")
        document = embed("Bai 2: Dia chi IPv4 va Chia Subnet")
        assert cosine(query, document) > 0.95


class TestLexicalOverlap:
    def test_perfect_overlap(self) -> None:
        assert lexical_overlap("dia chi ipv4", "Dia chi IPv4") == pytest.approx(1.0)

    def test_partial_overlap_is_proportional(self) -> None:
        score = lexical_overlap("dia chi ipv4", "Dia chi")
        assert 0.0 < score < 1.0

    def test_no_overlap(self) -> None:
        assert lexical_overlap("dia chi ipv4", "mo hinh osi") == 0.0

    def test_empty_query_is_neutral(self) -> None:
        assert lexical_overlap("", "bat ky") == 0.0

    def test_stopwords_do_not_inflate_the_score(self) -> None:
        assert lexical_overlap("cua la cho", "khong lien quan") == 0.0
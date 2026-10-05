"""Tests for PDF chunking and retrieval behaviour."""

from __future__ import annotations

import pytest

from services.rag_service import RagService, chunk_page

LONG_PAGE = "Mang may tinh la mot he thong phan tan. " * 40


class TestChunking:
    def test_short_page_becomes_a_single_chunk(self) -> None:
        """The real slides_ch1.pdf is one short page; it must not vanish."""
        chunks = chunk_page("Chuong 1: Tong quan", 1, "slides_ch1.pdf", 450, 50)
        assert len(chunks) == 1
        assert chunks[0].page_number == 1

    def test_empty_page_yields_nothing(self) -> None:
        assert chunk_page("   \n  ", 1, "slides_ch1.pdf", 450, 50) == []

    def test_long_page_is_split(self) -> None:
        chunks = chunk_page(LONG_PAGE, 1, "slides_ch1.pdf", 450, 50)
        assert len(chunks) > 1

    def test_chunks_respect_the_size_budget(self) -> None:
        for chunk in chunk_page(LONG_PAGE, 1, "slides_ch1.pdf", 450, 50):
            assert len(chunk.text) <= 450

    def test_consecutive_chunks_overlap(self) -> None:
        chunks = chunk_page(LONG_PAGE, 1, "slides_ch1.pdf", 450, 50)
        first_tail = chunks[0].text[-50:]
        assert first_tail[:25] in chunks[1].text, "overlap keeps context continuous"

    def test_source_ids_are_unique_and_carry_provenance(self) -> None:
        chunks = chunk_page(LONG_PAGE, 3, "slides_ch1.pdf", 450, 50)
        ids = [chunk.source_id for chunk in chunks]
        assert len(ids) == len(set(ids))
        assert all(chunk_id.startswith("slides_ch1#p3#") for chunk_id in ids)

    def test_whitespace_is_collapsed(self) -> None:
        chunks = chunk_page("A    B\n\nC\tD", 1, "x.pdf", 450, 50)
        assert "\n" not in chunks[0].text
        assert "  " not in chunks[0].text


class TestRetrieval:
    def test_indexes_the_course_material(self, rag: RagService) -> None:
        assert rag.ensure_indexed() >= 1

    def test_retrieves_the_slide_for_a_matching_question(self, rag: RagService) -> None:
        rag.ensure_indexed()
        results = rag.retrieve("Dia chi IPv4 la gi?")
        assert results
        assert results[0].source_file == "slides_ch1.pdf"

    def test_accented_query_matches_unaccented_slides(self, rag: RagService) -> None:
        rag.ensure_indexed()
        results = rag.retrieve("Địa chỉ IPv4 và chia subnet")
        assert results and results[0].score > 0.4

    def test_selection_text_boosts_relevance(self, rag: RagService) -> None:
        rag.ensure_indexed()
        without = rag.retrieve("Giai thich them")[0].score
        with_selection = rag.retrieve("Giai thich them", "Dia chi IPv4")[0].score
        assert with_selection > without

    def test_empty_query_returns_nothing(self, rag: RagService) -> None:
        rag.ensure_indexed()
        assert rag.retrieve("") == []

    def test_returns_fewer_than_top_k_on_a_small_corpus(self, rag: RagService) -> None:
        """Padding to top_k would fabricate provenance for absent chunks."""
        rag.ensure_indexed()
        assert len(rag.retrieve("bat ky", None, top_k=3)) <= rag.chunk_count

    def test_results_are_ranked_by_descending_score(self, rag: RagService) -> None:
        rag.ensure_indexed()
        scores = [chunk.score for chunk in rag.retrieve("mo hinh OSI", top_k=3)]
        assert scores == sorted(scores, reverse=True)

    def test_citations_match_the_frontend_contract(self, rag: RagService) -> None:
        citation = rag.retrieve("Dia chi IPv4")[0].to_citation()
        assert isinstance(citation["sourceId"], str) and citation["sourceId"]
        assert citation["label"].startswith("Trang ")
        assert citation["location"].startswith("p.")
        assert 0.0 <= citation["score"] <= 1.0
        assert len(citation["snippet"]) <= 220

    def test_reindexing_is_idempotent(self, rag: RagService) -> None:
        """A second pass must not duplicate chunks in the store."""
        first = rag.ensure_indexed()
        second = rag.ensure_indexed()
        assert first == second
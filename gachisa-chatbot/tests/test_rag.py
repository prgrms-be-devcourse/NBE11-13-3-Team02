import math

import pytest

from app.rag import FAQ_DIR, FaqIndex, load_chunks


def test_FAQ_문서가_소제목_단위로_쪼개진다():
    chunks = load_chunks()

    assert len(chunks) > 10
    # 제목은 "문서명 > 소제목" 형태라 검색 결과에서 출처를 알 수 있다.
    assert all(" > " in c.title for c in chunks)
    assert all(c.text.startswith("## ") for c in chunks)


def test_FAQ_청크에_중복_제목이_없다():
    titles = [c.title for c in load_chunks()]

    assert len(titles) == len(set(titles))


def test_실제_FAQ_디렉터리를_읽는다():
    assert FAQ_DIR.is_dir()
    assert list(FAQ_DIR.glob("*.md"))


class _StubEmbedder:
    """지정한 벡터를 순서대로 돌려주는 임베딩 스텁."""

    def __init__(self, doc_vectors: list[list[float]], query_vector: list[float]) -> None:
        self._doc_vectors = doc_vectors
        self._query_vector = query_vector
        self.task_types: list[str] = []
        self.aio = type(
            "aio", (), {"models": type("models", (), {"embed_content": self._embed})()}
        )()

    async def _embed(self, *, model, contents, config):
        self.task_types.append(config.task_type)
        vectors = self._doc_vectors if len(contents) > 1 else [self._query_vector]
        return type(
            "R", (), {"embeddings": [type("E", (), {"values": v})() for v in vectors]}
        )()


@pytest.fixture
def faq_dir(tmp_path):
    (tmp_path / "a.md").write_text(
        "# 배송 안내\n\n## 배송지는 언제 등록하나요\n모집 중일 때만 됩니다.\n\n"
        "## 반품은 어떻게 하나요\n배송 중일 때 신청합니다.\n",
        encoding="utf-8",
    )
    return tmp_path


async def test_검색은_유사도가_높은_순으로_돌려준다(faq_dir):
    # 두 문서를 직교 벡터로 두고, 질의를 두 번째 문서 쪽으로 정렬시킨다.
    client = _StubEmbedder(doc_vectors=[[1.0, 0.0], [0.0, 1.0]], query_vector=[0.0, 2.0])
    index = await FaqIndex.build(client, faq_dir)

    results = await index.search("반품하고 싶어요", top_k=2)

    assert "반품" in results[0]["title"]
    assert results[0]["relevance"] > results[1]["relevance"]


async def test_문서와_질의에_다른_task_type을_쓴다(faq_dir):
    client = _StubEmbedder(doc_vectors=[[1.0, 0.0], [0.0, 1.0]], query_vector=[1.0, 0.0])
    index = await FaqIndex.build(client, faq_dir)

    await index.search("배송지")

    assert client.task_types == ["RETRIEVAL_DOCUMENT", "RETRIEVAL_QUERY"]


async def test_임베딩을_정규화해_유사도가_1을_넘지_않는다(faq_dir):
    # 크기가 큰 벡터를 줘도 정규화되면 내적은 코사인 값이라 1 이하여야 한다.
    client = _StubEmbedder(doc_vectors=[[3.0, 4.0], [0.0, 9.0]], query_vector=[6.0, 8.0])
    index = await FaqIndex.build(client, faq_dir)

    results = await index.search("배송지", top_k=2)

    assert math.isclose(results[0]["relevance"], 1.0, abs_tol=1e-3)
    assert all(r["relevance"] <= 1.0 for r in results)


async def test_top_k보다_많은_결과를_돌려주지_않는다(faq_dir):
    client = _StubEmbedder(doc_vectors=[[1.0, 0.0], [0.0, 1.0]], query_vector=[1.0, 1.0])
    index = await FaqIndex.build(client, faq_dir)

    assert len(await index.search("아무거나", top_k=1)) == 1

import logging
import math
import re
from dataclasses import dataclass
from pathlib import Path
from typing import Any

from google.genai import types

from app.config import get_settings

logger = logging.getLogger(__name__)

FAQ_DIR = Path(__file__).parent / "faq"
# 기본값은 Settings.gemini_embed_model 이다. 여기 상수는 그 기본값을 한곳에 두기
# 위한 것이고, 실제로 쓰는 값은 설정에서 온다.
EMBED_MODEL = get_settings().gemini_embed_model
# 3072차원 전체는 이 정도 규모에 과하다. 축소해도 검색 품질 차이가 거의 없다.
EMBED_DIM = 768


@dataclass(frozen=True)
class FaqChunk:
    title: str
    text: str


def load_chunks(directory: Path = FAQ_DIR) -> list[FaqChunk]:
    """`## ` 소제목 단위로 자른다. FAQ는 소제목 하나가 곧 질문 하나라 자연스러운 경계다."""
    chunks: list[FaqChunk] = []
    for path in sorted(directory.glob("*.md")):
        raw = path.read_text(encoding="utf-8")
        doc_title = raw.lstrip().splitlines()[0].removeprefix("#").strip()
        for section in re.split(r"\n(?=## )", raw):
            if not section.startswith("## "):
                continue
            heading = section.splitlines()[0].removeprefix("##").strip()
            chunks.append(FaqChunk(title=f"{doc_title} > {heading}", text=section.strip()))
    return chunks


async def _embed(client: Any, texts: list[str], task_type: str) -> list[list[float]]:
    response = await client.aio.models.embed_content(
        model=EMBED_MODEL,
        contents=texts,
        config=types.EmbedContentConfig(
            task_type=task_type, output_dimensionality=EMBED_DIM
        ),
    )
    # 차원을 줄인 임베딩은 정규화돼서 오지 않는다. 직접 정규화해야 내적=코사인 유사도가 된다.
    return [_normalize(e.values) for e in response.embeddings]


def _normalize(vector: list[float]) -> list[float]:
    norm = math.sqrt(sum(v * v for v in vector))
    return [v / norm for v in vector] if norm else vector


async def embed_queries(client: Any, texts: list[str]) -> list[list[float]]:
    """질문 쪽 임베딩. 라우터와 FAQ 검색이 같은 표현 공간을 쓰도록 한곳에 둔다."""
    return await _embed(client, texts, "RETRIEVAL_QUERY")


def cosine(a: list[float], b: list[float]) -> float:
    """둘 다 정규화된 벡터라 내적이 곧 코사인 유사도다."""
    return sum(x * y for x, y in zip(a, b, strict=True))


class FaqIndex:
    """FAQ 벡터 인덱스. 문서가 수십 개 수준이라 메모리에 두고 전수 비교한다.

    이 규모에서 벡터DB를 붙이면 운영 요소만 늘고 얻는 것이 없다. 문서가 수천 개로
    늘어나면 그때 교체한다.
    """

    def __init__(self, client: Any, chunks: list[FaqChunk], vectors: list[list[float]]) -> None:
        self._client = client
        self._chunks = chunks
        self._vectors = vectors

    @classmethod
    async def build(cls, client: Any, directory: Path = FAQ_DIR) -> "FaqIndex":
        chunks = load_chunks(directory)
        # 질문용/문서용 임베딩을 다르게 뽑아야 검색 정확도가 올라간다.
        vectors = await _embed(client, [c.text for c in chunks], "RETRIEVAL_DOCUMENT")
        logger.info("FAQ 색인 구축 완료: %d개 항목", len(chunks))
        return cls(client, chunks, vectors)

    def __len__(self) -> int:
        return len(self._chunks)

    async def search(self, query: str, top_k: int = 3) -> list[dict[str, Any]]:
        [query_vector] = await embed_queries(self._client, [query])
        return self.search_with_vector(query_vector, top_k)

    def search_with_vector(self, query_vector: list[float], top_k: int = 3) -> list[dict[str, Any]]:
        """이미 뽑아둔 질문 벡터로 검색한다.

        라우터가 분류하려고 같은 문장을 이미 임베딩했으므로, 그 벡터를 넘겨받아
        임베딩 호출을 한 번 아낀다. 둘 다 RETRIEVAL_QUERY라 같은 표현 공간이다.
        """
        scored = sorted(
            (
                (cosine(query_vector, vector), chunk)
                for vector, chunk in zip(self._vectors, self._chunks, strict=True)
            ),
            key=lambda pair: pair[0],
            reverse=True,
        )
        return [
            {"title": chunk.title, "content": chunk.text, "relevance": round(score, 3)}
            for score, chunk in scored[:top_k]
        ]

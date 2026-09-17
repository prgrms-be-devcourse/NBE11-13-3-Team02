from types import SimpleNamespace

from google.genai import types

from app.router import Route, RouteDecision


def text_part(text: str) -> types.Part:
    return types.Part.from_text(text=text)


def call_part(name: str, args: dict) -> types.Part:
    return types.Part(function_call=types.FunctionCall(name=name, args=args))


def chunk(parts: list[types.Part]) -> SimpleNamespace:
    return SimpleNamespace(
        candidates=[SimpleNamespace(content=SimpleNamespace(parts=parts))],
        usage_metadata=SimpleNamespace(prompt_token_count=100, candidates_token_count=20),
    )


def turn(*parts_per_chunk: list[types.Part]) -> list[SimpleNamespace]:
    """한 번의 API 호출이 흘려보낼 청크 목록."""
    return [chunk(parts) for parts in parts_per_chunk]


def text_turn(*fragments: str) -> list[SimpleNamespace]:
    return turn(*([text_part(f)] for f in fragments))


def call_turn(name: str, args: dict | None = None) -> list[SimpleNamespace]:
    return turn([call_part(name, args or {})])


class _AsyncChunks:
    def __init__(self, chunks: list) -> None:
        self._chunks = chunks

    def __aiter__(self):
        async def generate():
            for item in self._chunks:
                yield item

        return generate()


class FakeGenai:
    """client.aio.models.generate_content_stream만 흉내 내는 스텁."""

    def __init__(self, turns: list[list]) -> None:
        self._turns = list(turns)
        self.calls: list[dict] = []
        self.aio = SimpleNamespace(
            models=SimpleNamespace(generate_content_stream=self._stream)
        )

    async def _stream(self, *, model, contents, config) -> _AsyncChunks:
        # run_agent는 턴마다 같은 contents 리스트에 append 한다. 참조를 그대로 들고 있으면
        # 나중에 검사할 때 모든 호출이 최종 상태로 보이므로 호출 시점 스냅샷을 남긴다.
        self.calls.append({"model": model, "contents": list(contents), "config": config})
        if not self._turns:
            raise AssertionError("예상보다 많은 API 호출이 발생했습니다.")
        return _AsyncChunks(self._turns.pop(0))


class FakeFaq:
    """FAQ 인덱스 스텁. 테스트가 임베딩 API를 타지 않게 한다."""

    def __init__(self, results: list[dict] | None = None) -> None:
        self.queries: list[str] = []
        self._results = results if results is not None else [
            {
                "title": "배송 안내 > 배송지는 언제 등록하나요",
                "content": "배송지는 공동구매 모집 중 또는 상품 준비 중일 때만 등록할 수 있습니다.",
                "relevance": 0.81,
            }
        ]

    async def search(self, query: str, top_k: int = 3) -> list[dict]:
        self.queries.append(query)
        return self._results[:top_k]

    def search_with_vector(self, query_vector: list[float], top_k: int = 3) -> list[dict]:
        self.queries.append(f"<vector:{len(query_vector)}>")
        return self._results[:top_k]


class FakeEmbedClient:
    """client.aio.models.embed_content만 흉내 내는 스텁.

    텍스트별로 돌려줄 벡터를 미리 정해 둔다. 정규화는 embed_queries가 하므로
    여기서는 방향만 맞춰 주면 된다.
    """

    def __init__(self, vectors: dict[str, list[float]], default: list[float] | None = None) -> None:
        self._vectors = vectors
        self._default = default or [0.0, 0.0, 0.0, 1.0]
        self.embedded: list[str] = []
        self.aio = SimpleNamespace(models=SimpleNamespace(embed_content=self._embed))
        self.fail = False

    async def _embed(self, *, model, contents, config):
        if self.fail:
            raise RuntimeError("임베딩 실패")
        self.embedded.extend(contents)
        return SimpleNamespace(
            embeddings=[
                SimpleNamespace(values=self._vectors.get(text, self._default))
                for text in contents
            ]
        )


class FakeRouter:
    """언제나 정해진 경로를 돌려주는 라우터 스텁. 테스트가 임베딩 API를 타지 않게 한다."""

    def __init__(self, decision: RouteDecision | None = None) -> None:
        self._decision = decision if decision is not None else RouteDecision(Route.GENERAL, "stub")
        self.seen: list[tuple[str, bool]] = []

    async def classify(self, message: str, *, has_image: bool = False) -> RouteDecision:
        self.seen.append((message, has_image))
        return self._decision

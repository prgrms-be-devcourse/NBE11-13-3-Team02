from types import SimpleNamespace

from google.genai import types


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

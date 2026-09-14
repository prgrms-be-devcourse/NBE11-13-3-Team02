from types import SimpleNamespace


def text_delta(text: str) -> SimpleNamespace:
    return SimpleNamespace(
        type="content_block_delta",
        delta=SimpleNamespace(type="text_delta", text=text),
    )


def text_block(text: str) -> SimpleNamespace:
    return SimpleNamespace(type="text", text=text)


def tool_use_block(tool_id: str, name: str, args: dict) -> SimpleNamespace:
    return SimpleNamespace(type="tool_use", id=tool_id, name=name, input=args)


def final_message(content: list, stop_reason: str) -> SimpleNamespace:
    return SimpleNamespace(content=content, stop_reason=stop_reason, stop_details=None)


class FakeStream:
    def __init__(self, events: list, final: SimpleNamespace) -> None:
        self._events = events
        self._final = final

    async def __aenter__(self) -> "FakeStream":
        return self

    async def __aexit__(self, *exc_info: object) -> bool:
        return False

    def __aiter__(self):
        async def generate():
            for event in self._events:
                yield event

        return generate()

    async def get_final_message(self) -> SimpleNamespace:
        return self._final


class FakeAnthropic:
    """client.beta.messages.stream(...)만 흉내 내는 스텁. 턴마다 미리 준비한 응답을 돌려준다."""

    def __init__(self, turns: list[FakeStream]) -> None:
        self._turns = list(turns)
        self.calls: list[dict] = []
        self.beta = SimpleNamespace(messages=SimpleNamespace(stream=self._stream))

    def _stream(self, **kwargs) -> FakeStream:
        # run_agent는 턴마다 같은 messages 리스트에 append 한다. 참조를 그대로 들고 있으면
        # 나중에 검사할 때 모든 호출이 최종 상태로 보이므로 호출 시점 스냅샷을 남긴다.
        self.calls.append({**kwargs, "messages": list(kwargs["messages"])})
        if not self._turns:
            raise AssertionError("예상보다 많은 API 호출이 발생했습니다.")
        return self._turns.pop(0)

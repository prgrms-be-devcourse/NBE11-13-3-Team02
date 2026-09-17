"""프롬프트를 파일로 두고 버전을 붙인다.

프롬프트가 코드 안 문자열 상수로 있으면 두 가지가 곤란하다.

1. **어떤 답이 어떤 프롬프트에서 나왔는지 되짚을 수 없다.** 운영 중 이상한 답을
   발견해도, 그사이 프롬프트가 바뀌었다면 재현할 수가 없다.
2. **평가 숫자가 어느 프롬프트의 것인지 남지 않는다.** "정확도를 올렸다"는 주장의
   근거가 흐려진다.

그래서 프롬프트를 `app/prompts/*.md` 로 빼고 두 가지 식별자를 붙인다.

    version   사람이 붙이는 의미 있는 이름. 바꾼 이유를 CHANGELOG 에 적는다.
    digest    파일 내용의 sha256 앞 12자. 자동이라 손댈 수 없고, 고치면 반드시 바뀐다.

version 만 두면 고치고 올리는 걸 잊는다. digest 만 두면 무엇이 왜 바뀐지 모른다.
둘 다 둔다. 응답 메타데이터와 평가 결과에 digest 를 실어 보내면, 나중에 어떤
프롬프트가 그 답을 만들었는지 정확히 짚을 수 있다.
"""

import hashlib
from dataclasses import dataclass
from functools import lru_cache
from pathlib import Path

PROMPT_DIR = Path(__file__).parent / "prompts"

# 프롬프트를 의미 있게 바꿀 때마다 version 을 올리고 CHANGELOG 에 한 줄 적는다.
# 오타 수정처럼 동작이 달라지지 않는 변경은 digest 만 바뀌게 두어도 된다.
VERSIONS: dict[str, str] = {
    "agent_system": "v1",
    "faq_system": "v1",
}

CHANGELOG: dict[tuple[str, str], str] = {
    ("agent_system", "v1"): "부정 지시를 긍정 지시로 바꾼 초기 버전",
    ("faq_system", "v1"): "질문 라우팅의 FAQ 경로용. 발췌만 근거로 답하도록 제한",
}


@dataclass(frozen=True)
class Prompt:
    name: str
    version: str
    digest: str
    text: str

    @property
    def label(self) -> str:
        """로그와 이벤트에 싣는 짧은 식별자. 예: agent_system@v1+3f2a1b9c4d5e"""
        return f"{self.name}@{self.version}+{self.digest}"

    def format(self, **kwargs: str) -> str:
        return self.text.format(**kwargs)


@lru_cache
def load(name: str) -> Prompt:
    """프롬프트 하나를 읽는다. 기동 후에는 캐시되므로 파일을 고쳐도 재시작해야 반영된다.

    일부러 이렇게 둔다. 돌아가는 중에 프롬프트가 바뀌면 같은 대화 안에서도 답의
    근거가 달라져 디버깅이 불가능해진다.
    """
    path = PROMPT_DIR / f"{name}.md"
    text = path.read_text(encoding="utf-8")
    digest = hashlib.sha256(text.encode("utf-8")).hexdigest()[:12]
    return Prompt(name=name, version=VERSIONS.get(name, "unversioned"), digest=digest, text=text)


def all_prompts() -> list[Prompt]:
    return [load(name) for name in sorted(VERSIONS)]

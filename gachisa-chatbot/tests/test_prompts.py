"""프롬프트 버전 관리 테스트.

digest 는 "프롬프트를 고치면 반드시 티가 난다"를 보장하는 장치다. 그 성질이
깨지면 버전 관리 전체가 의미를 잃으므로 여기서 지킨다.
"""

import hashlib

import pytest

from app import prompts


def test_프롬프트마다_버전과_digest가_붙는다():
    for prompt in prompts.all_prompts():
        assert prompt.version, f"{prompt.name} 에 버전이 없습니다"
        assert len(prompt.digest) == 12
        assert prompt.text.strip()


def test_digest는_파일_내용의_해시다():
    prompt = prompts.load("faq_system")
    expected = hashlib.sha256(prompt.text.encode("utf-8")).hexdigest()[:12]
    assert prompt.digest == expected


def test_내용이_한_글자만_달라도_digest가_바뀐다(tmp_path):
    (tmp_path / "a.md").write_text("안내문입니다.", encoding="utf-8")
    (tmp_path / "b.md").write_text("안내문입니다!", encoding="utf-8")

    original = prompts.PROMPT_DIR
    try:
        prompts.PROMPT_DIR = tmp_path
        prompts.load.cache_clear()
        a = prompts.load("a")
        b = prompts.load("b")
    finally:
        prompts.PROMPT_DIR = original
        prompts.load.cache_clear()

    assert a.digest != b.digest


def test_label은_이름_버전_digest를_모두_담는다():
    prompt = prompts.load("agent_system")
    assert prompt.label == f"agent_system@{prompt.version}+{prompt.digest}"


def test_모든_버전에_changelog가_있다():
    """버전을 올리면서 이유를 안 적는 것을 막는다."""
    for name, version in prompts.VERSIONS.items():
        assert (name, version) in prompts.CHANGELOG, f"{name}@{version} 의 변경 이유가 없습니다"


def test_FAQ_프롬프트는_context_자리를_갖는다():
    """FAQ 경로는 검색 결과를 끼워 넣는다. 자리가 없으면 근거 없이 답하게 된다."""
    assert "{context}" in prompts.load("faq_system").text


@pytest.mark.parametrize("name", ["agent_system", "faq_system"])
def test_같은_이름은_같은_객체를_돌려준다(name):
    """요청마다 파일을 다시 읽으면 대화 중간에 프롬프트가 바뀔 수 있다."""
    assert prompts.load(name) is prompts.load(name)

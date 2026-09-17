# /// script
# requires-python = ">=3.11"
# dependencies = ["google-genai>=1.0", "python-dotenv>=1.0"]
# ///
"""회의록을 읽어 요약과 담당자별 할 일을 뽑는다.

회의에서 정한 것이 각자의 머릿속에만 남으면, 다음 회의에서 "그거 누가 하기로
했었죠?"부터 다시 시작한다. 회의록을 붙여넣으면 결정사항·보류·담당자별 할 일로
갈라 주고, 그대로 GitHub 이슈로 올릴 수 있는 형태까지 만든다.

    uv run tools/meeting_notes.py docs/meetings/2026-09-17.md
    uv run tools/meeting_notes.py docs/meetings/2026-09-17.md --issues   # 이슈 명령까지 출력
    uv run tools/meeting_notes.py docs/meetings/2026-09-17.md --create   # 실제로 이슈 생성

기본은 출력만 한다. --create 없이는 리포지토리에 아무것도 만들지 않는다.
사람이 눈으로 확인하지 않은 할 일이 팀 이슈 목록에 쌓이면 신뢰를 잃기 때문이다.

모델이 지어내는 것을 막으려고 두 가지를 건다.

    구조화 출력   response_schema 로 형태를 고정한다. 파싱이 결정적이 된다.
    담당자 화이트리스트
                 회의록에 없는 사람을 담당자로 적지 못하게 목록을 준다. 모르면
                 미정으로 둔다. 엉뚱한 사람에게 일이 배정되는 것보다 낫다.
"""

import argparse
import json
import os
import shlex
import subprocess
import sys
from pathlib import Path

from dotenv import load_dotenv
from google import genai
from google.genai import types

ROOT = Path(__file__).resolve().parents[1]

# 팀원 목록. 회의록에 이 이름들이 나오면 담당자로 잡는다.
# git shortlog 로 확인한 실제 기여자다.
MEMBERS = ["안서호", "김주형", "이석우"]

# 회의록의 한글 이름 → GitHub 계정. 이슈를 만들 때 담당자로 건다.
# 여기 없는 이름이나 "미정"은 담당자 없이 만든다.
GITHUB_HANDLES = {
    "안서호": "ahnseho02",
    "이석우": "lucku-1111",
    "김주형": "JuhyungKim-dev",
}

MODEL = os.environ.get("GEMINI_MODEL", "gemini-3.5-flash")

SCHEMA = {
    "type": "object",
    "properties": {
        "summary": {
            "type": "string",
            "description": "회의 전체를 3문장 이내로. 무엇을 다뤘고 무엇이 정해졌는지.",
        },
        "decisions": {
            "type": "array",
            "description": "회의에서 확정된 것만. 논의만 하고 안 정해진 것은 여기 넣지 않는다.",
            "items": {"type": "string"},
        },
        "pending": {
            "type": "array",
            "description": "논의했으나 결론이 나지 않아 다음으로 넘긴 것.",
            "items": {"type": "string"},
        },
        "todos": {
            "type": "array",
            "items": {
                "type": "object",
                "properties": {
                    "title": {"type": "string", "description": "할 일. 동사로 끝나는 한 줄."},
                    "owner": {
                        "type": "string",
                        "description": f"담당자. {', '.join(MEMBERS)} 중 하나이거나 '미정'.",
                    },
                    "detail": {"type": "string", "description": "왜 하는지와 완료 조건. 없으면 빈 문자열."},
                    "due": {"type": "string", "description": "기한. 회의록에 없으면 빈 문자열."},
                },
                "required": ["title", "owner", "detail", "due"],
            },
        },
    },
    "required": ["summary", "decisions", "pending", "todos"],
}

PROMPT = f"""당신은 개발팀의 회의록을 정리합니다.

팀원: {", ".join(MEMBERS)}

원칙:
- 회의록에 적힌 내용만 씁니다. 회의록에 없는 결정이나 할 일을 만들어내지 않습니다.
- 담당자는 위 팀원 중에서만 고릅니다. 회의록에서 누가 맡을지 분명하지 않으면 "미정"으로 둡니다.
- 확정된 것은 decisions, 결론이 안 난 것은 pending 으로 나눕니다. 둘을 섞지 않습니다.
- 할 일은 "무엇을 한다"가 드러나게 동사로 끝맺습니다.
- 기한은 회의록에 적힌 경우에만 씁니다.

회의록:
---
{{note}}
---
"""


def analyze(client: genai.Client, note: str) -> dict:
    response = client.models.generate_content(
        model=MODEL,
        contents=PROMPT.format(note=note),
        config=types.GenerateContentConfig(
            response_mime_type="application/json",
            response_json_schema=SCHEMA,
            # 도구를 쓰지 않는 호출이다. 꺼두지 않으면 SDK 가 매번 경고를 찍는다.
            automatic_function_calling=types.AutomaticFunctionCallingConfig(disable=True),
        ),
    )
    return json.loads(response.text)


def render(result: dict) -> None:
    print("=" * 66)
    print("요약")
    print("=" * 66)
    print(result["summary"])

    for key, label in [("decisions", "결정사항"), ("pending", "보류 · 다음 회의로")]:
        items = result.get(key) or []
        print(f"\n{label} ({len(items)})")
        print("-" * 66)
        for item in items:
            print(f"  · {item}")
        if not items:
            print("  (없음)")

    todos = result.get("todos") or []
    print(f"\n담당자별 할 일 ({len(todos)})")
    print("-" * 66)
    by_owner: dict[str, list[dict]] = {}
    for todo in todos:
        by_owner.setdefault(todo["owner"], []).append(todo)
    for owner in sorted(by_owner, key=lambda o: (o == "미정", o)):
        print(f"\n  [{owner}]")
        for todo in by_owner[owner]:
            due = f"  (기한 {todo['due']})" if todo["due"] else ""
            print(f"    · {todo['title']}{due}")
            if todo["detail"]:
                print(f"      {todo['detail']}")


def issue_body(todo: dict, source: Path) -> str:
    lines = []
    if todo["detail"]:
        lines += [todo["detail"], ""]
    if todo["due"]:
        lines += [f"**기한** {todo['due']}", ""]
    lines += [f"회의록: `{source.relative_to(ROOT) if source.is_relative_to(ROOT) else source}`"]
    lines += ["", "_회의록에서 자동 추출됐습니다. 내용이 다르면 고쳐 주세요._"]
    return "\n".join(lines)


def create_issues(todos: list[dict], source: Path, dry_run: bool) -> None:
    print("\n" + "=" * 66)
    print("GitHub 이슈" + (" (미리보기 — 실제로 만들지 않음)" if dry_run else ""))
    print("=" * 66)
    for todo in todos:
        title = f"[{todo['owner']}] {todo['title']}" if todo["owner"] != "미정" else todo["title"]
        handle = GITHUB_HANDLES.get(todo["owner"])
        command = ["gh", "issue", "create", "--title", title, "--body", issue_body(todo, source)]
        if handle:
            command += ["--assignee", handle]

        if dry_run:
            # repr 로 감싸면 줄바꿈이 \n 문자 그대로 들어가 본문이 깨진다.
            # shlex.quote 는 셸이 그대로 되읽을 수 있게 인용한다.
            print("\ngh issue create \\")
            print(f"  --title {shlex.quote(title)} \\")
            if handle:
                print(f"  --assignee {shlex.quote(handle)} \\")
            print(f"  --body {shlex.quote(issue_body(todo, source))}")
            continue

        result = subprocess.run(command, capture_output=True, text=True)

        # 담당자 지정만 실패하는 경우가 있다(리포지토리 협업자가 아니거나 계정명이
        # 바뀐 경우). 그때 이슈까지 못 만들면 손해가 크므로 담당자를 빼고 한 번 더 한다.
        if result.returncode != 0 and handle:
            print(f"  ! {handle} 을(를) 담당자로 걸지 못했습니다. 담당자 없이 만듭니다.")
            result = subprocess.run(command[:-2], capture_output=True, text=True)
            handle = None

        if result.returncode == 0:
            assigned = f"  (담당 {handle})" if handle else ""
            print(f"  ✅ {title}{assigned}\n     {result.stdout.strip()}")
        else:
            print(f"  ❌ {title}\n     {result.stderr.strip()}")


def load_api_key() -> str:
    # 챗봇과 같은 키를 쓴다. .env.local 이 팀의 단일 출처다.
    load_dotenv(ROOT / ".env.local")
    key = os.environ.get("GEMINI_API_KEY", "")
    if not key:
        sys.exit("GEMINI_API_KEY 를 찾지 못했습니다. .env.local 을 확인하세요(./setup.sh).")
    return key


def main() -> int:
    parser = argparse.ArgumentParser(description="회의록 → 요약 + 담당자별 할 일")
    parser.add_argument("note", type=Path, help="회의록 마크다운 파일")
    parser.add_argument("--issues", action="store_true", help="GitHub 이슈 명령을 미리보기로 출력")
    parser.add_argument("--create", action="store_true", help="실제로 GitHub 이슈를 생성")
    parser.add_argument("--json", action="store_true", help="가공 없이 JSON 으로 출력")
    args = parser.parse_args()

    if not args.note.exists():
        sys.exit(f"파일이 없습니다: {args.note}")
    note = args.note.read_text(encoding="utf-8").strip()
    if not note:
        sys.exit("회의록이 비어 있습니다.")

    result = analyze(genai.Client(api_key=load_api_key()), note)

    if args.json:
        print(json.dumps(result, ensure_ascii=False, indent=2))
        return 0

    render(result)

    if args.create:
        # 되돌리기 번거로운 작업이다. 몇 건인지 보여주고 한 번 묻는다.
        todos = result.get("todos") or []
        answer = input(f"\n이슈 {len(todos)}건을 실제로 만듭니다. 진행할까요? [y/N] ")
        if answer.strip().lower() != "y":
            print("취소했습니다.")
            return 0
        create_issues(todos, args.note, dry_run=False)
    elif args.issues:
        create_issues(result.get("todos") or [], args.note, dry_run=True)

    return 0


if __name__ == "__main__":
    raise SystemExit(main())

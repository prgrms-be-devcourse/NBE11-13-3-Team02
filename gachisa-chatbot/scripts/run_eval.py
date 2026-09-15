"""도구 라우팅 평가셋 실행기.

질문마다 Gemini를 한 번 호출해 '첫 번째로 고른 도구'가 기대값에 들어가는지 본다.
도구를 실제로 실행하지는 않는다 — 라우팅 판단은 첫 호출에서 끝나고, 전체 루프를 돌리면
질문 하나에 API를 2~3회 써서 무료 한도를 금방 소진하기 때문이다.

프로덕션과 같은 시스템 프롬프트·도구 선언을 그대로 쓴다. 다르면 측정 의미가 없다.

사용:
    uv run python scripts/run_eval.py                    # 전체 실행
    uv run python scripts/run_eval.py --limit 10         # 앞 10개만
    uv run python scripts/run_eval.py --resume 결과.json  # 중단된 지점부터
"""

import argparse
import asyncio
import json
import re
import sys
from collections import defaultdict
from datetime import datetime
from pathlib import Path

import yaml
from google import genai
from google.genai import errors, types

from app.agent import GEMINI_TOOLS, SYSTEM_PROMPT
from app.config import get_settings

EVAL_FILE = Path(__file__).parent.parent / "evals" / "routing.yaml"
RESULTS_DIR = Path(__file__).parent.parent / "evals" / "results"

# 무료 티어는 모델당 분당 5회다. 12초 간격이면 정확히 한도라 여유를 둔다.
SECONDS_BETWEEN_CALLS = 15.0
MAX_RETRIES = 5


def retry_delay(error: errors.ClientError) -> float:
    """429 응답이 알려주는 대기 시간을 그대로 쓴다. 추측보다 정확하다."""
    match = re.search(r"'retryDelay': '(\d+)s'", str(error))
    return float(match.group(1)) + 3 if match else SECONDS_BETWEEN_CALLS * 2


async def route_once(client, model: str, question: str) -> tuple[str | None, str]:
    """질문 하나에 대해 (첫 도구 이름 또는 None, 답변 텍스트 앞부분)을 돌려준다."""
    response = await client.aio.models.generate_content(
        model=model,
        contents=[types.Content(role="user", parts=[types.Part.from_text(text=question)])],
        config=types.GenerateContentConfig(
            system_instruction=SYSTEM_PROMPT,
            tools=GEMINI_TOOLS,
            automatic_function_calling=types.AutomaticFunctionCallingConfig(disable=True),
        ),
    )
    if not response.candidates:
        return None, ""

    text = ""
    for part in response.candidates[0].content.parts or []:
        if part.function_call is not None:
            return part.function_call.name, ""
        if part.text:
            text += part.text
    return None, text.strip()[:120]


async def run_case(client, model: str, case: dict) -> dict:
    for attempt in range(MAX_RETRIES):
        try:
            tool, text = await route_once(client, model, case["question"])
        except errors.ClientError as e:
            if e.code != 429 or attempt == MAX_RETRIES - 1:
                # 호출 자체가 실패한 것은 라우팅 오답이 아니다. passed를 매기지 않는다.
                return {**case, "actual": None, "text": "", "passed": None, "error": str(e.code)}
            delay = retry_delay(e)
            print(f"    429 — {delay:.0f}초 대기 후 재시도", flush=True)
            await asyncio.sleep(delay)
            continue

        expected = case["expected"]
        passed = (tool in expected) if expected else (tool is None)
        return {**case, "actual": tool, "text": text, "passed": passed, "error": None}


def summarize(all_results: list[dict]) -> None:
    errored = [r for r in all_results if r["passed"] is None]
    results = [r for r in all_results if r["passed"] is not None]
    if not results:
        print("\n채점 가능한 결과가 없습니다 (전부 호출 실패).")
        return

    by_category: dict[str, list[dict]] = defaultdict(list)
    for r in results:
        by_category[r["category"]].append(r)

    print("\n" + "=" * 62)
    print(f"{'카테고리':<14}{'통과':>8}{'전체':>8}{'정확도':>10}")
    print("-" * 62)
    for category, items in sorted(by_category.items()):
        passed = sum(1 for i in items if i["passed"])
        print(f"{category:<14}{passed:>8}{len(items):>8}{passed / len(items):>9.0%}")
    total_passed = sum(1 for r in results if r["passed"])
    print("-" * 62)
    print(f"{'합계':<14}{total_passed:>8}{len(results):>8}{total_passed / len(results):>9.0%}")

    if errored:
        print(f"\n호출 실패 {len(errored)}건은 채점에서 제외했습니다 (한도 초과 등).")
        print("  " + ", ".join(r["id"] for r in errored))

    failures = [r for r in results if not r["passed"]]
    if failures:
        print(f"\n실패 {len(failures)}건")
        for r in failures:
            detail = r["error"] or f"{r['actual']} (기대: {r['expected'] or '도구 없음'})"
            print(f"  [{r['id']}] {r['question']}")
            print(f"      → {detail}")


async def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--limit", type=int)
    parser.add_argument("--resume", type=Path)
    args = parser.parse_args()

    settings = get_settings()
    cases = yaml.safe_load(EVAL_FILE.read_text(encoding="utf-8"))

    done: list[dict] = []
    if args.resume and args.resume.exists():
        done = json.loads(args.resume.read_text(encoding="utf-8"))["results"]
        finished = {r["id"] for r in done}
        cases = [c for c in cases if c["id"] not in finished]
        print(f"{len(done)}건은 이미 끝나 건너뜁니다.")

    if args.limit:
        cases = cases[: args.limit]

    minutes = len(cases) * SECONDS_BETWEEN_CALLS / 60
    print(f"{len(cases)}문항 실행 (모델 {settings.gemini_model}, 예상 {minutes:.0f}분)\n")

    client = genai.Client(api_key=settings.gemini_api_key)
    results = list(done)
    for index, case in enumerate(cases, start=1):
        result = await run_case(client, settings.gemini_model, case)
        results.append(result)
        mark = {True: "O", False: "X", None: "-"}[result["passed"]]
        print(f"  {index:>2}/{len(cases)} {mark} [{case['id']}] {result['actual'] or '(도구 없음)'}", flush=True)
        if index < len(cases):
            await asyncio.sleep(SECONDS_BETWEEN_CALLS)

    RESULTS_DIR.mkdir(parents=True, exist_ok=True)
    out = RESULTS_DIR / f"routing-{datetime.now():%Y%m%d-%H%M}.json"
    out.write_text(
        json.dumps(
            {"model": settings.gemini_model, "results": results}, ensure_ascii=False, indent=2
        ),
        encoding="utf-8",
    )

    summarize(results)
    print(f"\n결과: {out.relative_to(Path.cwd())}")
    return 0


if __name__ == "__main__":
    sys.exit(asyncio.run(main()))

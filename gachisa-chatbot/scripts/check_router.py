"""질문 라우터를 `evals/routing.yaml` 50문항으로 평가한다.

`scripts/run_eval.py` 와 같은 평가셋을 쓰지만 측정 대상이 다르다.

    run_eval.py      생성 모델이 "첫 도구를 맞게 고르는가"  → 문항당 생성 호출 1회
    check_router.py  라우터가 "경로를 맞게 고르는가"        → 생성 호출 0회

라우터는 임베딩 모델만 쓰므로 무료 티어에서도 50문항을 끝까지 돌릴 수 있다.
run_eval.py 는 하루 20회 한도에 걸려 완주하지 못한다.

채점은 세 갈래다. 라우팅은 틀리는 방향에 따라 대가가 다르기 때문이다.

    맞음    기대한 경로로 갔다.
    안전    general 로 떨어졌다. 기존 동작 그대로라 답은 맞고, 아끼려던 호출만 못 아꼈다.
    틀림    엉뚱한 좁은 경로로 갔다. 필요한 도구가 없어 답이 나빠질 수 있다. 이것만 실패다.

질문은 한 번만 임베딩하고 임계값 판정은 메모리에서 한다. 덕분에 --sweep 으로
임계값을 훑어도 API 호출이 늘지 않는다.

    uv run python scripts/check_router.py
    uv run python scripts/check_router.py --verbose
    uv run python scripts/check_router.py --sweep
"""

import argparse
import asyncio
import json
import sys
from collections import Counter
from pathlib import Path

import yaml

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))

from google import genai  # noqa: E402

from app.config import get_settings  # noqa: E402
from app.rag import embed_queries  # noqa: E402
from app import prompts  # noqa: E402
from app.router import MIN_MARGIN, MIN_SCORE, QuestionRouter, Route  # noqa: E402

EVAL_PATH = Path(__file__).resolve().parents[1] / "evals" / "routing.yaml"

# 평가셋의 category 를 라우터 경로에 대응시킨다.
# none(도구 없이 답할 질문)과 adversarial(지시문 주입 시도)은 어느 좁은 경로에도
# 속하지 않는다. 판단을 미루고 전체 도구를 여는 general 이 정답이다.
CATEGORY_TO_ROUTE = {
    "faq": Route.FAQ,
    "search": Route.CATALOG,
    "orders": Route.ORDER,
    "none": Route.GENERAL,
    "adversarial": Route.GENERAL,
}

# 임베딩은 한 요청에 여러 문장을 담을 수 있다. 50문항을 한 번에 보내면 응답이
# 커지므로 적당히 끊는다.
BATCH = 16


def verdict_of(expected: Route, actual: Route) -> str:
    if actual is expected:
        return "맞음"
    return "안전" if actual is Route.GENERAL else "틀림"


async def embed_all(client, questions: list[str]) -> list[list[float]]:
    vectors: list[list[float]] = []
    for start in range(0, len(questions), BATCH):
        vectors.extend(await embed_queries(client, questions[start : start + BATCH]))
    return vectors


def evaluate(router: QuestionRouter, cases: list[dict], vectors: list[list[float] | None]):
    """(문항, 판정, 결정) 목록. vector 가 None 이면 규칙으로 끝난 문항이다."""
    rows = []
    for case, vector in zip(cases, vectors, strict=True):
        decision = router.rule_route(case["question"]) or router.decide(vector)
        expected = CATEGORY_TO_ROUTE[case["category"]]
        rows.append((case, verdict_of(expected, decision.route), decision))
    return rows


def report(rows) -> int:
    verdicts: Counter[str] = Counter()
    per_category: dict[str, Counter[str]] = {}
    misses = []

    for case, verdict, decision in rows:
        verdicts[verdict] += 1
        per_category.setdefault(case["category"], Counter())[verdict] += 1
        if verdict == "틀림":
            misses.append(
                f"  {case['id']:<16} {case['question']}\n"
                f"  {'':<16} 기대 {CATEGORY_TO_ROUTE[case['category']]}"
                f" / 실제 {decision.route} ({decision.reason}, {decision.score})"
            )

    total = sum(verdicts.values())
    print(f"{'분류':<14} {'맞음':>5} {'안전':>5} {'틀림':>5}   문항")
    print("-" * 46)
    for category in sorted(per_category):
        counts = per_category[category]
        print(
            f"{category:<14} {counts['맞음']:>5} {counts['안전']:>5}"
            f" {counts['틀림']:>5}   {sum(counts.values())}"
        )
    print("-" * 46)
    print(
        f"{'전체':<14} {verdicts['맞음']:>5} {verdicts['안전']:>5}"
        f" {verdicts['틀림']:>5}   {total}"
    )
    print()
    print(f"경로 적중률   {verdicts['맞음'] / total:.1%}")
    print(f"안전 실패     {verdicts['안전'] / total:.1%}  (기존 동작으로 처리, 답은 맞음)")
    print(f"잘못된 경로   {verdicts['틀림'] / total:.1%}")

    print()
    report_calls(rows)

    if misses:
        print("\n잘못된 경로로 간 문항:")
        print("\n".join(misses))
    return 1 if verdicts["틀림"] else 0


def report_calls(rows) -> None:
    """생성 모델 호출이 몇 번에서 몇 번으로 줄었는지 센다.

    기존 경로의 호출 수는 평가셋의 expected 로 알 수 있다. 도구를 써야 하는
    질문은 "도구 고르기 + 답변 만들기"로 2회, 도구 없이 답할 질문(expected: [])은
    1회다. 라우팅 후에는 faq 경로만 1회로 줄고 나머지는 그대로다.
    """
    before = after = 0
    for case, _, decision in rows:
        baseline = 1 if not case["expected"] else 2
        before += baseline
        after += 1 if decision.route is Route.FAQ else baseline

    saved = before - after
    print(f"생성 모델 호출  {before}회 → {after}회   ({saved}회 절약, {saved / before:.1%})")
    print(f"질문당 평균     {before / len(rows):.2f}회 → {after / len(rows):.2f}회")


def sweep(exemplars, client, cases, vectors) -> None:
    """임계값을 훑어 맞음/안전/틀림이 어떻게 맞바뀌는지 본다.

    임계값을 내리면 적중이 오르지만 잘못된 경로도 함께 오른다. 둘을 같은 표에
    놓고 봐야 어디서 멈출지 정할 수 있다.
    """
    print(f"{'min_score':>10} {'margin':>7} {'맞음':>6} {'안전':>6} {'틀림':>6}")
    print("-" * 40)
    for min_score in [0.55, 0.60, 0.65, 0.70, 0.75, 0.80, 0.85]:
        for min_margin in [0.02, 0.05]:
            router = QuestionRouter(
                exemplars, client, min_score=min_score, min_margin=min_margin
            )
            counts = Counter(v for _, v, _ in evaluate(router, cases, vectors))
            mark = "  ←현재" if (min_score, min_margin) == (MIN_SCORE, MIN_MARGIN) else ""
            print(
                f"{min_score:>10} {min_margin:>7} {counts['맞음']:>6}"
                f" {counts['안전']:>6} {counts['틀림']:>6}{mark}"
            )


BASELINE_PATH = Path(__file__).resolve().parents[1] / "evals" / "router_baseline.json"

# 측정값이 기준선보다 이만큼까지 나빠지는 것은 통과시킨다. 임베딩 모델이
# 업데이트되면 같은 코드로도 소수점이 흔들리므로, 진짜 회귀만 잡으려면 여유가 필요하다.
TOLERANCE = 2


def check_baseline(rows) -> int:
    """기준선과 비교해 회귀면 1을 돌려준다. CI 가 이 값으로 PR 을 막는다.

    맞음이 줄거나 틀림이 느는 것만 본다. '안전'은 둘 사이에서 움직이는 값이라
    따로 보면 같은 변화를 두 번 세게 된다.
    """
    if not BASELINE_PATH.exists():
        print(f"기준선이 없습니다: {BASELINE_PATH}")
        print("--save-baseline 으로 지금 수치를 기준선으로 저장하세요.")
        return 1

    baseline = json.loads(BASELINE_PATH.read_text(encoding="utf-8"))
    counts = Counter(v for _, v, _ in rows)
    problems = []

    if counts["맞음"] < baseline["맞음"] - TOLERANCE:
        problems.append(f"맞음 {baseline['맞음']} → {counts['맞음']} (기준선보다 낮음)")
    if counts["틀림"] > baseline["틀림"] + TOLERANCE:
        problems.append(f"틀림 {baseline['틀림']} → {counts['틀림']} (기준선보다 높음)")

    print()
    print(f"기준선  맞음 {baseline['맞음']}  틀림 {baseline['틀림']}  (허용 오차 ±{TOLERANCE})")
    print(f"현재    맞음 {counts['맞음']}  틀림 {counts['틀림']}")
    if problems:
        print("\n❌ 라우팅 품질이 기준선보다 나빠졌습니다:")
        for p in problems:
            print(f"   {p}")
        print("\n의도한 변경이면 --save-baseline 으로 기준선을 갱신하고 그 이유를 PR 에 적으세요.")
        return 1
    print("✅ 기준선 통과")
    return 0


def save_baseline(rows) -> None:
    counts = Counter(v for _, v, _ in rows)
    payload = {
        "맞음": counts["맞음"],
        "안전": counts["안전"],
        "틀림": counts["틀림"],
        "문항수": len(rows),
        "min_score": MIN_SCORE,
        "min_margin": MIN_MARGIN,
        "프롬프트": [p.label for p in prompts.all_prompts()],
    }
    BASELINE_PATH.write_text(
        json.dumps(payload, ensure_ascii=False, indent=2) + "\n", encoding="utf-8"
    )
    print(f"\n기준선을 저장했습니다: {BASELINE_PATH.name}")
    print(json.dumps(payload, ensure_ascii=False, indent=2))


async def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--verbose", action="store_true", help="문항별 결과를 모두 출력")
    parser.add_argument("--sweep", action="store_true", help="임계값을 훑어 비교")
    parser.add_argument("--limit", type=int, default=None)
    parser.add_argument(
        "--check-baseline", action="store_true", help="기준선보다 나빠졌으면 실패(CI용)"
    )
    parser.add_argument(
        "--save-baseline", action="store_true", help="현재 수치를 기준선으로 저장"
    )
    args = parser.parse_args()

    cases = yaml.safe_load(EVAL_PATH.read_text(encoding="utf-8"))
    if args.limit:
        cases = cases[: args.limit]

    client = genai.Client(api_key=get_settings().gemini_api_key)
    router = await QuestionRouter.build(client)

    # 규칙으로 끝나는 문항은 임베딩하지 않는다. 운영 경로와 같은 조건으로 잰다.
    needs_embedding = [c for c in cases if router.rule_route(c["question"]) is None]
    embedded = await embed_all(client, [c["question"] for c in needs_embedding])
    by_id = dict(zip((c["id"] for c in needs_embedding), embedded, strict=True))
    vectors = [by_id.get(c["id"]) for c in cases]

    rows = evaluate(router, cases, vectors)

    if args.verbose:
        for case, verdict, decision in rows:
            print(
                f"{case['id']:<16} {case['category']:<12} {str(decision.route):<9}"
                f" {decision.reason:<16} {decision.score:<6} {verdict}"
            )
        print()

    if args.sweep:
        sweep(router.exemplars, client, cases, vectors)
        print()

    exit_code = report(rows)

    if args.save_baseline:
        save_baseline(rows)
        return 0
    if args.check_baseline:
        return check_baseline(rows)
    return exit_code


if __name__ == "__main__":
    raise SystemExit(asyncio.run(main()))

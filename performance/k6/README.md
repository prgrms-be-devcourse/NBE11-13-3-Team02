# k6 performance tests

Run the first local smoke test while the backend is running on port 8080:

```powershell
k6 run .\performance\k6\category-smoke.js
```

The script ramps from 0 to 2 virtual users, then 5 virtual users, and back to 0. It uses only the public category API and does not call Toss Payments.

For a different backend address:

```powershell
k6 run -e BASE_URL=http://127.0.0.1:8080 .\performance\k6\category-smoke.js
```

## 500-user payment entry queue test

This test validates the Redis payment-entry queue, not Toss approval. It creates local buyer accounts and a group buy with matching capacity, then sends one queue-token request for every buyer at the same time.

1. Start the backend. The local profile recreates its schema at startup, so create this data only after the backend has started.
2. Prepare local-only accounts, tokens, and the group buy:

```powershell
.\performance\k6\prepare-queue-load-test.ps1
```

3. Run the 500-user one-shot burst:

```powershell
k6 run .\performance\k6\queue-500-users.js
```

Expected result: 500 successful queue-token responses, 10 `ADMITTED`, and 490 `WAITING`. Generated access tokens are ignored by Git.

For the faster queue-drain demonstration, use 100 users. The expected values automatically become 10 `ADMITTED` and 90 `WAITING`.

```powershell
.\performance\k6\prepare-queue-load-test.ps1 -UserCount 100
```

## 10-user batch queue drain demo

After the 500-user test, complete payment for the ten currently admitted users through the local PG demo key. The normal payment confirmation API, payment state transition, and queue-slot release run as they do after a real approval. The scheduler then admits the next ten users, so Grafana changes from `waiting 490` to `waiting 480` while `admitted` stays at 10.

```powershell
.\performance\k6\advance-queue-demo.ps1
```

Use `-BatchCount 3` to repeat the sequence three times and observe `490 → 480 → 470 → 460`. This uses `local-demo-*` payment keys in the local profile, so it does not call Toss Payments. A real Toss widget can still be demonstrated manually for one admitted user.

## Queue API stress test

This is a technical-limit test, not a unique-user queue test. It reuses the prepared local users and ramps concurrent queue-token requests through 10, 50, 100, 200, 500, 700, 1000, 1500, and 2000 virtual users as far as `MAX_VUS` allows. Watch Grafana for the first stage where API P95, 5xx, or DB connection pending degrades.

```powershell
& "C:\Program Files\k6\k6.exe" run .\performance\k6\queue-stress.js
```

To explore beyond the 500-user goal, add a higher final stage. For a faster local run, lower each stage duration.

```powershell
& "C:\Program Files\k6\k6.exe" run -e MAX_VUS=1000 -e RAMP_DURATION=5s -e HOLD_DURATION=15s .\performance\k6\queue-stress.js

## 대기열 검증 분리 실행

```powershell
# 0. 스모크: 인증·Redis·대기열 API 기본 연결을 1명으로 확인합니다.
.\performance\k6\run-queue-smoke-test.ps1

# 1. 대기열 정합성: 연결 거부만 같은 사용자로 짧게 재시도하고,
#    10명 ADMITTED / 나머지 WAITING 규칙을 검증합니다.
.\performance\k6\run-queue-integrity-test.ps1 -UserCount 500

# 2. 시스템 동시 수용량: 재시도 없이 동시 진입시켜 연결 거부가 시작되는 단계를 찾습니다.
.\performance\k6\run-system-capacity-test.ps1
```
```

An `http_req_duration` threshold failure is the result that identifies the capacity boundary; it does not mean the test crashed.

## Unique-user queue stress test

Use this test for the payment-queue capacity claim. Unlike the API stress test above, every stage creates the same number of **distinct users** as the target load and each user enters the queue exactly once. The server stays running between stages; only the Redis payment-queue keys are removed before the next stage, so the queue state is unambiguous: 100 users produce `waiting 90`, 500 users produce `waiting 490`, and 1000 users produce `waiting 990`.

```powershell
.\performance\k6\queue-unique-stress.ps1
```

The default stages are `100, 500, 1000, 2000`. To run a custom capacity range:

```powershell
.\performance\k6\queue-unique-stress.ps1 -Stages 1500,3000
```

The first stage where k6 reports a failed threshold is the capacity-boundary candidate. Per-stage k6 summaries are saved under `performance/k6/results/` and are ignored by Git.

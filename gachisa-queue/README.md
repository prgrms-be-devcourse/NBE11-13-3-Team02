# 가치사 대기열 서비스

core(gachisa-backend)에서 분리한 결제 대기열 서비스. Kotlin + 코루틴 + WebFlux.

## 왜 대기열을 분리했나

모듈 의존과 트랜잭션 경계를 조사한 결과, 대기열이 **분산 트랜잭션을 만들지 않고 뗄 수 있는
유일한 경계**였다.

- `payment`, `participation`, `order`는 한 트랜잭션에서 함께 쓰인다. 가로지르면 분산 트랜잭션이 된다.
- 대기열은 Redis만 쓴다. `@Transactional` 안에서 호출되더라도 롤백 대상이 아니었다.
  **이미 결과적 일관성 경계였고**, 분리해도 일관성 모델이 달라지지 않는다.
- 부하 특성이 다르다. 참여자가 몰리는 핫패스라 core와 따로 늘릴 이유가 실제로 있다.

## 왜 코루틴인가

코루틴은 논블로킹 I/O 위에서만 값을 낸다. JPA/JDBC 위에 얹으면 드라이버가 스레드를
붙잡으므로 처리량이 그대로다. core의 다른 모듈이 정확히 그 경우다.

이 서비스는 다르다.

- 저장소가 Redis뿐이고 **Lettuce는 논블로킹 드라이버**다
- core 호출은 WebClient로 나가고, 이것도 논블로킹이다
- 즉 요청 처리 전 구간에 스레드를 붙잡는 곳이 없다

그래서 만료 통지 같은 독립 작업을 `async`로 동시에 보낸다
([QueueService.processExpired](src/main/kotlin/com/gachisa/queue/core/QueueService.kt)).
순차로 보내면 지연이 합산돼 뒤에 있는 사용자의 입장이 그만큼 밀린다.

## 서비스 계약

**브라우저 → queue** (사용자 JWT. core와 같은 시크릿으로 검증만 하고 발급은 하지 않는다)

| 엔드포인트 | 설명 |
| --- | --- |
| `POST /api/group-buys/{id}/queue-token` | 대기열 등록 및 토큰 발급 |
| `GET /api/group-buys/{id}/queue-token/{token}/status` | 순번/입장 여부 조회 |

**core → queue** (`X-Internal-Token` 공유 시크릿)

| 엔드포인트 | 호출 시점 |
| --- | --- |
| `POST /internal/queues/{gid}/users/{uid}/require-admission?queueToken=` | 결제 생성 전 |
| `POST /internal/queues/{gid}/users/{uid}/payment-attempt` | 결제 시도 생성 후 |
| `POST /internal/queues/{gid}/users/{uid}/start-confirmation` | 결제 확정 시작 |
| `POST /internal/queues/{gid}/users/{uid}/confirmation-failed` | PG 거절 시 |
| `POST /internal/queues/{gid}/users/{uid}/complete` | 결제 완료/취소 시 |

**queue → core** (같은 공유 시크릿)

| 엔드포인트 | 용도 |
| --- | --- |
| `GET /internal/group-buys/{id}/queue-info` | 목표/현재 인원, 마감, 상태 |
| `POST /internal/payment-attempts/{id}/expire` | 입장 만료 시 결제 시도 되돌리기 |

`/internal` 은 브라우저에 열리면 남의 대기열을 조작할 수 있다. 네트워크 격리가 1차 방어이고
`InternalTokenFilter`가 2차 방어다.

## 공개 API 레이트 리밋

공개 API(`/api/**`)는 토큰 버킷으로 보호한다(`RateLimitFilter`). `/internal/**`은
이미 공유 시크릿으로 잠겨 있고 core만 호출하므로 대상이 아니다.

**왜 고정 윈도가 아니라 토큰 버킷인가.** "1분에 N회"식 고정 윈도는 창 경계에서
순간적으로 2N회까지 새어 나갈 수 있고(창이 바뀌는 순간 양쪽에서 최대치를 쓰면),
정상적인 폴링 패턴을 인위적으로 끊기도 한다. 토큰 버킷은 평균 속도만 억제하면서
순간적인 burst는 허용한다.

**용량과 보충 속도는 실제 트래픽에서 골랐다.** 프론트엔드가 결제 대기 중 1초
간격으로 상태를 폴링한다(`GroupBuyCheckoutPage.waitForAdmission`). 기본값
용량 5 / 초당 1.2 보충은 그 폴링을 계속 허용하면서, 짧은 시간에 수십 번씩
두드리는 남용은 막는다.

로그인한 사용자는 사용자 단위로, 토큰이 없거나 잘못됐으면 IP 단위로 제한한다.
초과하면 `429 Too Many Requests` + `Retry-After` 헤더를 돌려준다.

버킷은 프로세스 메모리에 사용자/IP별로 하나씩 쌓인다. 인스턴스 하나로 운영하는
지금 구성에서는 문제없지만, 여러 인스턴스로 늘리면 어느 인스턴스로 가느냐에 따라
한도가 갈라진다 — 그때는 Redis 같은 공유 저장소로 옮겨야 한다.

## core 쪽에서 선행된 변경

`PaymentConfirmationStateService.prepare()`가 `PESSIMISTIC_WRITE` 락을 쥔 채
`startConfirmation()`을 부르고 있었다. 대기열이 네트워크 너머로 가면 락 점유 시간이 왕복
시간만큼 늘어나 결제 폭주 때 정확히 문제가 된다. 그래서 트랜잭션을 둘로 나누고
대기열 호출을 그 사이로 옮겼다(core 커밋 `82eef6e`).

## 실행

`JWT_SECRET`은 core의 `jwt.secret`과 완전히 동일해야 한다.

```bash
JWT_SECRET=<core와 동일> QUEUE_INTERNAL_TOKEN=<공유 시크릿> ./gradlew bootRun
```

기본 포트 8081. Redis는 `REDIS_HOST`/`REDIS_PORT`.

## 테스트

```bash
./gradlew test
```

## 현재 상태

- [x] Redis 계층(Lua 스크립트 6개) 코루틴 포팅
- [x] 대기열 도메인 로직, 공개/내부 API, JWT·내부 토큰 인증
- [x] core 쪽 락 분리 선행 리팩터링
- [x] core의 인프로세스 QueueService를 HTTP 클라이언트로 교체
- [x] core에 internal API 2개 추가, queue 패키지 제거
- [x] 프론트엔드 프록시 분기
- [x] 공개 API 토큰 버킷 레이트 리밋

실제 core(MySQL+Redis)와 이 서비스를 함께 띄워 확인한 것:

| 경로 | 결과 |
| --- | --- |
| 브라우저 → queue 토큰 발급 | 정원만큼 ADMITTED, 나머지 WAITING |
| core → queue `requireAdmission` | 결제 시도 생성 201 |
| 위조한 대기열 토큰 | core가 `QUEUE_TOKEN_INVALID`로 복원 (400) |
| queue 서비스 정지 | core가 `QUEUE_UNAVAILABLE` (503), 결제만 막히고 나머지는 동작 |
| 사용자 JWT로 internal 호출 | 403 |

## 에러 코드가 프로세스를 건너 전달되는 방식

`ResponseStatusException`의 reason은 Spring 기본 에러 본문에 실리지 않는다. 그래서 어떤
실패인지 core가 구분할 수 없었다. 서비스 간 계약이므로 `QueueExceptionHandler`에서
`{"error": "QUEUE_TOKEN_INVALID", ...}`로 직접 만들어 내려주고, core의 `QueueClient`가
그 이름으로 자기 `ErrorCode`를 복원한다. 프론트엔드가 보는 응답은 분리 전과 같다.

## 남은 과제

core → queue 호출은 여전히 Tomcat 스레드를 붙잡는다(core는 Spring MVC). 분리로 얻은 것은
**DB 락 밖으로 뺀 것**이지 core가 논블로킹이 된 것은 아니다. core까지 논블로킹으로 가려면
Java 21 가상 스레드나 WebFlux 전환이 필요하다.

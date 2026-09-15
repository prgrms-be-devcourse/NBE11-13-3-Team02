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
- [ ] core에서 인프로세스 `QueueService`를 HTTP 클라이언트로 교체
- [ ] core에 `/internal/group-buys/{id}/queue-info`, `/internal/payment-attempts/{id}/expire` 추가
- [ ] core의 queue 패키지 제거

**아직 core와 연결되지 않았다.** 스텁 core로 전체 생명주기(발급 → 입장 → 확정 시작 →
완료 → 다음 사람 입장)를 확인했다.

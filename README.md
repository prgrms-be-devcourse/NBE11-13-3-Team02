# 같이사 (gachisa)

**공동구매(Group-buy) 커머스 플랫폼** — 여러 구매자가 모여 목표 인원을 채우면 할인가로 상품을 구매하는 서비스입니다.
`gachisa-backend`(Spring Boot) + `gachisa-frontend`(React/Vite) + `gachisa-chatbot`(FastAPI) 로 구성된 모노레포입니다.

## 목차

- [주요 기능](#주요-기능)
- [시스템 구성도](#시스템-구성도)
- [기술 스택](#기술-스택)
- [프로젝트 구조](#프로젝트-구조)
- [실행 방법](#실행-방법)
- [참고 사항](#참고-사항)

> **팀 작업 흐름은 [WORKFLOW.md](./WORKFLOW.md) 를 보세요.** 회의 → 이슈 → 브랜치 →
> PR → CI → 머지까지의 단계와, 그중 자동화된 부분(회의록 정리, 라우팅 회귀 검사)을
> 정리했습니다. 회의록 정리 도구 사용법은
> [docs/meetings/README.md](./docs/meetings/README.md) 에 따로 있습니다.

## 주요 기능

**구매자**
- 로그인 없이 공동구매/상품 목록 둘러보기, 참여 시점에만 로그인 요구
- 카테고리(트리 구조)·키워드·가격 조건으로 공동구매 검색, 마감임박순/가격순 정렬
- 공동구매 참여 → Toss Payments 결제 → 목표 인원 미달 시 자동 환불
- 결제 전 대기열 진입으로 순간 트래픽 제어
- 내 참여내역/주문내역/배송조회, 카카오·네이버 소셜 로그인
- AI 챗봇 상담 — 공동구매 검색, 내 주문/배송 조회, FAQ 답변, 상품 사진 업로드로 비슷한 공동구매 찾기

**판매자**
- 상품 등록/수정/판매중지·재개, 내 상품 검색·관리 페이지
- 공동구매 등록(목표 인원/할인율/모집 기간 설정), 마감 전 취소

**관리자**
- 카테고리 트리 관리(추가/수정/삭제), 배송 상태 관리
- 진행중인 공동구매 강제 취소
- 회원 계정 정지/정지 해제/탈퇴 처리
- 챗봇 사용자별 토큰 사용량 조회
- (로컬 전용) 공동구매 정원 동시성 제어 방식 비교 데모 (`/dev/concurrency`)

## 시스템 구성도

![같이사 시스템 구성도](./system-architecture.png)

- **사용자 → 프론트엔드**: HTTPS로 SPA 접속
- **프론트엔드 → 백엔드**: `/api/**` REST 호출, `Authorization: Bearer {JWT}` + httpOnly 리프레시 쿠키
- **프론트엔드 → 챗봇**: `/chat` SSE 스트림을 백엔드를 거치지 않고 직접 수신 (챗봇이 필요할 때만 백엔드를 REST로 호출)
- **백엔드 → MySQL**: 회원/상품/공동구매/주문/결제 등 영속 데이터
- **백엔드 → Redis**: 공동구매 재고 원자 예약(Lua Script), 결제 대기열, 리프레시 토큰 캐시
- **백엔드 → 외부 서비스**: Toss Payments(결제 승인/취소 + Webhook 수신), 카카오/네이버 OAuth2 로그인
- **백엔드 → Prometheus/Grafana**: 결제·대기열 신뢰성 지표 노출 및 대시보드 시각화 (선택적 관측 스택)

## 기술 스택

| 영역 | 스택 |
|---|---|
| Frontend | React 18, Vite 5, React Router v6, MUI v5, axios, Toss Payments SDK |
| Backend | Spring Boot 4.1 (Java 17 + Kotlin 2.3 혼용), Spring Security(JWT), Spring Data JPA + QueryDSL, Spring Data Redis |
| 챗봇 서비스 | Python 3.12, FastAPI, asyncio, Google Gemini (Tool Calling + RAG) |
| Database | MySQL 8 |
| Cache / 실시간 처리 | Redis (재고 원자 예약 Lua Script, 결제 대기열, 리프레시 토큰 캐시) |
| 관측(Observability) | Prometheus, Grafana, Micrometer |
| 부하 테스트 | k6 (결제/대기열 신뢰성·동시성·용량 테스트, `performance/k6/`) |
| 외부 연동 | Toss Payments(결제), Kakao/Naver OAuth2(소셜 로그인), Google Gemini(챗봇) |
| 인증 | JWT accessToken(메모리) + httpOnly refreshToken 쿠키, 자체 로그인 + 소셜 로그인 |
| 시크릿 관리 | Infisical (backend/frontend), 챗봇은 개인 API 키 특성상 로컬 `.env` |

## 프로젝트 구조

```
NBE11-13-3-Team02/
├── gachisa-backend/    # Spring Boot API 서버 (Java + Kotlin 혼용, 점진적 Kotlin 전환 중)
│   ├── .infisical.json     # Infisical 프로젝트 연결
│   ├── Dockerfile
│   └── src/main/{java,kotlin}/com/gachisa/
│       ├── user/           # 회원, 관리자용 계정 정지/탈퇴
│       ├── auth/           # 인증(JWT), 소셜 로그인(Kakao/Naver)
│       ├── product/        # 상품
│       ├── category/       # 카테고리(트리)
│       ├── groupbuy/       # 공동구매, 참여 동시성 제어, 정산 배치
│       ├── participation/  # 참여
│       ├── queue/          # Redis 기반 결제 입장 대기열 (구 대기열 서비스, 현재는 backend 인프로세스)
│       ├── payment/        # 결제/환불(Toss), Webhook
│       ├── order/          # 주문/배송
│       ├── concurrency/    # (local 전용) 동시성 검증 데모
│       ├── dev/            # k6 부하 테스트용 로컬 전용 데이터 생성기
│       └── global/         # 공통 설정(Security, 예외 처리)
├── gachisa-chatbot/    # RAG + Tool Calling 챗봇 서버 (FastAPI + Gemini)
│   └── app/
│       ├── agent.py         # 도구 호출 에이전트 루프
│       ├── router.py        # 질문 성격에 따른 사전 라우팅(불필요한 모델 호출 축소)
│       ├── tools.py         # 공동구매 검색 / 주문 / 배송 조회 도구
│       ├── rag.py           # FAQ 벡터 검색
│       ├── quality.py       # 프롬프트 품질/회귀 관련 유틸
│       └── faq/             # FAQ 원문
├── gachisa-frontend/   # React SPA
│   ├── Dockerfile
│   └── src/
│       ├── api/             # axios 기반 API 클라이언트
│       ├── pages/           # 라우트별 페이지 (구매자/판매자/관리자)
│       ├── components/      # 공통 컴포넌트, 챗봇 위젯
│       ├── context/         # 인증 컨텍스트
│       └── routes/          # 라우팅 + 역할별 접근 제어
├── observability/      # 로컬 개발용 Redis+Prometheus+Grafana 구성 (프론트/백엔드는 로컬에서 직접 실행)
├── performance/k6/     # 결제·대기열 신뢰성/동시성/용량 부하 테스트 스크립트
├── docs/meetings/      # 회의록 및 정리 자동화 도구
├── setup.sh            # 로컬 개발 환경 초기 설정 스크립트
├── run.sh              # 서비스를 로컬에서 직접 실행 (core/chatbot/frontend)
├── run-docker-full.sh  # 전체 스택을 Docker Compose로 한 번에 실행 (온보딩·발표 재현용)
├── docker-compose.full.yml
├── full-start.ps1 / full-stop.ps1   # 위 두 스크립트의 Windows(PowerShell) 버전
├── WORKFLOW.md
└── system-architecture.png
```

### 서비스 구성

| 서비스 | 포트 | 역할 |
|---|---|---|
| `gachisa-backend` | 8080 | 회원·상품·공동구매·참여·결제·대기열·주문 |
| `gachisa-chatbot` | 8000 | 사이트 안내 챗봇 |
| `gachisa-frontend` | 5173 | React SPA |
| Prometheus (선택) | 9090 | 지표 수집 |
| Grafana (선택) | 3000 | 대시보드 |

프론트엔드는 Vite 프록시로 `/api` 요청을 백엔드(8080), `/chat` 요청을 챗봇(8000)으로 보냅니다.

## 실행 방법

두 가지 방법이 있습니다. 평소 개발/디버깅은 **로컬 직접 실행**을, 팀원 환경 재현이나 발표 시연은 **Docker 전체 실행**을 씁니다.

### 방법 A. 로컬 직접 실행 (평소 개발용)

#### 0. 사전 준비

로컬에 MySQL 8 / Redis를 설치하고 아래 계정/DB를 만들어주세요.

- MySQL: `localhost:3306`, DB `gachisa`, 계정 `gachisa` / `gachisa1234`
- Redis: `localhost:6379`

시크릿(JWT_SECRET, Toss/카카오/네이버 키 등)은 Infisical이 원본입니다. 처음이라면 `infisical login`으로 로그인해두세요.

#### 1. 설정 파일 생성 + 의존성 설치

```bash
./setup.sh
```

다음을 자동으로 처리합니다.

- `application-local.yml`(백엔드)을 예제에서 복사 — 시크릿은 여기 없고 Infisical에 있습니다
- **`.env.local` 생성** — Infisical의 `JWT_SECRET`을 받아와 챗봇과 공유
- 프론트 `npm install`, 챗봇 `uv sync`

챗봇에 필요한 `GEMINI_API_KEY`는 [무료로 발급](https://aistudio.google.com/apikey)받아 `.env.local`에 채워주세요. 이 값은 팀 공유 시크릿이 아니라 개인 키라 Infisical 대상이 아닙니다.

값을 바꾸려면 Infisical에서 원본을 고치고 `./setup.sh`를 다시 실행하세요.

#### 2. 전체 실행

```bash
./run.sh
```

core(8080) · chatbot(8000) · frontend(5173)를 함께 띄웁니다. `Ctrl+C`로 전부 종료됩니다. 일부만 띄우려면 이름을 넘기세요 (`./run.sh core frontend`).

#### IntelliJ에서 실행 (팀 공용 실행 구성)

`.run/`에 실행 구성을 커밋해 두어 팀원 모두가 같은 실행 버튼을 씁니다. 클론 후 `./setup.sh` 실행 → Gradle 툴 윈도우에서 `gachisa-backend/build.gradle` 링크하면, 이후로는 상단 실행 구성에서 core를 버튼으로 실행할 수 있습니다. 챗봇/프론트는 터미널로 띄웁니다 (`./run.sh chatbot frontend`).

> **주의:** 같은 서비스를 IntelliJ와 터미널에서 동시에 띄우지 마세요. core는 `ddl-auto: create-drop`이라, 포트 충돌로 실패한 쪽이 종료되면서 먼저 떠 있던 인스턴스의 테이블까지 지웁니다. 실행 전 `lsof -ti tcp:8080`으로 확인하세요.

### 방법 B. Docker로 전체 스택 실행 (온보딩·발표 재현용)

```bash
./run-docker-full.sh up --build      # 전체 실행 (mysql/redis/backend/frontend/chatbot/prometheus/grafana)
./run-docker-full.sh down            # 종료
```

backend/frontend는 서로 다른 Infisical 프로젝트를 쓰기 때문에, 이 스크립트가 두 프로젝트의 시크릿을 셸에 합쳐 넣은 뒤 `docker-compose.full.yml`을 실행합니다. 챗봇의 `GEMINI_API_KEY`는 Infisical이 아니라 `gachisa-chatbot/.env`(위 `setup.sh`가 생성) 파일을 그대로 읽으므로, 먼저 로컬 실행용 설정을 한 번 해뒀어야 합니다.

Windows에서는 `full-start.ps1` / `full-stop.ps1`을 대신 씁니다.

> 로컬 개발용 관측 스택(`observability/docker-compose.yml`, Redis+Prometheus+Grafana만 Docker로 띄우고 프론트/백엔드는 로컬에서 실행하는 구성)과는 **동시에 띄우지 마세요.** 컨테이너 이름/포트가 겹칩니다.

## 참고 사항

- 백엔드 로컬 프로필은 `ddl-auto: create-drop`이라 앱을 켤 때마다 스키마가 새로 생성되고 `data.sql`로 시드 데이터가 들어갑니다. **앱을 끄면 데이터가 사라지는 게 정상입니다.**
- `/dev/concurrency`(공동구매 정원 동시성 검증 데모)는 `local`/`docker` 프로필에서만 노출됩니다.
- 카카오/네이버 로그인은 각 콘솔에 리다이렉트 URI(`{origin}/oauth/kakao`, `{origin}/oauth/naver`)를 등록해야 정상 동작합니다.
- 결제·대기열 신뢰성 부하 테스트는 `performance/k6/`에 있습니다. 실행하려면 `docker-compose.full.yml`의 `PAYMENT_TOSS_LOCAL_DEMO_ENABLED` 로컬 결제 데모 모드를 참고하세요 (실제 Toss 호출 없이 결제 성공/실패를 재현합니다).

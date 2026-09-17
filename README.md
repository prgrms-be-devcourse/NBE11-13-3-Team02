# 같이사 (gachisa)

**공동구매(Group-buy) 커머스 플랫폼** — 여러 구매자가 모여 목표 인원을 채우면 할인가로 상품을 구매하는 서비스입니다.
`gachisa-backend`(Spring Boot) + `gachisa-frontend`(React/Vite) 로 구성된 모노레포입니다.

## 목차

- [주요 기능](#주요-기능)
- [시스템 구성도](#시스템-구성도)
- [기술 스택](#기술-스택)
- [프로젝트 구조](#프로젝트-구조)
- [실행 방법](#실행-방법)
- [참고 사항](#참고-사항)

## 주요 기능

**구매자**
- 로그인 없이 공동구매/상품 목록 둘러보기, 참여 시점에만 로그인 요구
- 카테고리(트리 구조)·키워드·가격 조건으로 공동구매 검색, 마감임박순/가격순 정렬
- 공동구매 참여 → Toss Payments 결제 → 목표 인원 미달 시 자동 환불
- 결제 전 대기열(Queue) 진입으로 순간 트래픽 제어
- 내 참여내역/주문내역/배송조회, 카카오·네이버 소셜 로그인

**판매자**
- 상품 등록/수정/판매중지·재개, 내 상품 검색·관리 페이지
- 공동구매 등록(목표 인원/할인율/모집 기간 설정), 마감 전 취소

**관리자**
- 카테고리 트리 관리(추가/수정/삭제), 배송 상태 관리
- 진행중인 공동구매 강제 취소
- (로컬 전용) 공동구매 정원 동시성 제어 방식 비교 데모 (`/dev/concurrency`)

## 시스템 구성도

![같이사 시스템 구성도](./system-architecture.png)

- **사용자 → 프론트엔드**: HTTPS로 SPA 접속
- **프론트엔드 → 백엔드**: `/api/**` REST 호출, `Authorization: Bearer {JWT}` + httpOnly 리프레시 쿠키
- **백엔드 → MySQL**: 회원/상품/공동구매/주문/결제 등 영속 데이터
- **백엔드 → Redis**: 공동구매 재고 원자 예약(Lua Script), 결제 대기열, 리프레시 토큰 캐시
- **백엔드 → 외부 서비스**: Toss Payments(결제 승인/취소 + Webhook 수신), 카카오/네이버 OAuth2 로그인

## 기술 스택

| 영역 | 스택 |
|---|---|
| Frontend | React 18, Vite, React Router v6, MUI v5, axios, Toss Payments SDK |
| Backend (core) | Spring Boot 4 (Java 17), Spring Security(JWT), Spring Data JPA + QueryDSL, Spring Data Redis |
| 대기열 서비스 | Kotlin, Spring Boot 4 WebFlux, 코루틴, Reactive Redis |
| 챗봇 서비스 | Python 3.12, FastAPI, asyncio, Google Gemini |
| Database | MySQL 8 |
| Cache / 실시간 처리 | Redis (재고 원자 예약 Lua Script, 결제 대기열, 리프레시 토큰 캐시) |
| 외부 연동 | Toss Payments(결제), Kakao/Naver OAuth2(소셜 로그인), Google Gemini(챗봇) |
| 인증 | JWT accessToken(메모리) + httpOnly refreshToken 쿠키, 자체 로그인 + 소셜 로그인 |

## 프로젝트 구조

```
NBE11-13-2-Team02/
├── gachisa-backend/    # Spring Boot API 서버
│   └── src/main/java/com/gachisa/
│       ├── user/           # 회원
│       ├── auth/           # 인증(JWT), 소셜 로그인(Kakao/Naver)
│       ├── product/        # 상품
│       ├── category/       # 카테고리(트리)
│       ├── groupbuy/       # 공동구매, 참여 동시성 제어
│       ├── participation/  # 참여
│       ├── queue/          # Redis 기반 결제 입장 대기열
│       ├── payment/        # 결제/환불(Toss), Webhook
│       ├── order/          # 주문/배송
│       ├── concurrency/    # (local 전용) 동시성 검증 데모
│       └── global/         # 공통 설정(Security, 예외 처리)
├── gachisa-chatbot/    # RAG 챗봇 서버 (FastAPI + Gemini)
│   └── app/
│       ├── agent.py         # 도구 호출 에이전트 루프
│       ├── tools.py         # 공동구매 검색 / 주문 / 배송 / FAQ
│       ├── rag.py           # FAQ 벡터 검색
│       └── faq/             # FAQ 원문 (팀 검토 필요)
├── gachisa-frontend/   # React SPA
│   └── src/
│       ├── api/             # axios 기반 API 클라이언트
│       ├── pages/           # 라우트별 페이지
│       ├── components/      # 공통 컴포넌트
│       ├── context/         # 인증 컨텍스트
│       └── routes/          # 라우팅 + 역할별 접근 제어
├── setup.sh            # 로컬 개발 환경 초기 설정 스크립트
├── run.sh              # 전체 서비스 실행
└── system-architecture.png
```

### 서비스 구성

| 서비스 | 포트 | 역할 |
|---|---|---|
| `gachisa-backend` (core) | 8080 | 회원·상품·공동구매·참여·결제·대기열·주문 |
| `gachisa-chatbot` | 8000 | 사이트 안내 챗봇 |
| `gachisa-frontend` | 5173 | React SPA |

프론트엔드는 Vite 프록시로 `/api` 요청을 백엔드(8080), `/chat` 요청을 챗봇(8000)으로 보냅니다.

## 실행 방법

### 0. 사전 준비 (MySQL, Redis)

로컬에 MySQL 8 / Redis를 설치하고 아래 계정/DB를 만들어주세요.

- MySQL: `localhost:3306`, DB `gachisa`, 계정 `gachisa` / `gachisa1234`
- Redis: `localhost:6379`

### 1. 설정 파일 생성 + 프론트 의존성 설치

```bash
./setup.sh
```

다음을 자동으로 처리합니다.

- `application-local.yml`(백엔드) 를 예제에서 복사
- **`.env.local` 생성** — 챗봇과 공유할 `JWT_SECRET`
- 프론트 `npm install`, 챗봇 `uv sync`

`JWT_SECRET`은 Infisical이 원본이고, `setup.sh`가 이를
`.env.local`로 복사해 챗봇 서비스에 전달합니다. **값을 바꾸려면 원본을 고치고
`setup.sh`를 다시 실행**하세요. 설정 파일은 모두 `.gitignore` 대상이라 각자 로컬에만 있습니다.

채워야 하는 값:

| 값 | 위치 | 필요한 경우 |
|---|---|---|
| `GEMINI_API_KEY` | `.env.local` | 챗봇. [발급](https://aistudio.google.com/apikey) (무료) |
| `VITE_TOSS_CLIENT_KEY` | Infisical (frontend, `dev`) | 결제 테스트 |
| `VITE_KAKAO_CLIENT_ID` / `VITE_NAVER_CLIENT_ID` | Infisical (frontend, `dev`) | 소셜 로그인 |

### 2. 전체 실행

```bash
./run.sh
```

core(8080) · chatbot(8000) · frontend(5173)를 함께 띄웁니다. `Ctrl+C`로 전부 종료됩니다.

일부만 띄우려면 이름을 넘기세요.

```bash
./run.sh core frontend
```

### IntelliJ에서 실행 (팀 공용 실행 구성)

`.run/`에 실행 구성을 커밋해 두어 팀원 모두가 같은 실행 버튼을 씁니다. 클론 후
**한 번만** 아래를 해두면 이후로는 버튼만 누르면 됩니다.

1. `./setup.sh` 실행
2. Gradle 툴 윈도우(코끼리 아이콘) → `+` → `gachisa-backend/build.gradle` 링크

동기화가 끝나면 상단 실행 구성에서 core를 실행할 수 있습니다.

| 구성 | 실행 대상 |
| --- | --- |
| `core (8080)` | core 단독 |

챗봇(Python)과 프론트(Node)는 IDE 실행 구성 없이 터미널로 띄웁니다.

```bash
./run.sh chatbot frontend
```

> **주의:** 같은 서비스를 IntelliJ와 터미널에서 동시에 띄우지 마세요. core는
> `ddl-auto: create-drop`이라, 포트 충돌로 실패한 쪽이 종료되면서 **먼저 떠 있던
> 인스턴스의 테이블까지 지웁니다.** 실행 전 `lsof -ti tcp:8080`으로 확인하세요.

### 개별 실행

`./setup.sh`를 한 번 실행했다면 core를 환경변수 없이 실행할 수 있습니다.

```bash
# core
cd gachisa-backend && ./gradlew bootRun

# chatbot
cd gachisa-chatbot && uv run uvicorn app.main:app --port 8000

# frontend
cd gachisa-frontend && npm run dev
```

환경변수를 주면 그쪽이 우선합니다(`run.sh`가 쓰는 경로).

결제·소셜 로그인까지 테스트하려면 core 실행 전에 환경변수를 넘겨주세요:

```bash
export TOSS_SECRET_KEY=test_sk_...
export KAKAO_CLIENT_ID=...
export KAKAO_CLIENT_SECRET=...   # 카카오 콘솔에서 Client Secret을 켰다면 필수, 안 켰으면 생략 가능
export NAVER_CLIENT_ID=...
export NAVER_CLIENT_SECRET=...
```

## 참고 사항

- 백엔드 로컬 프로필은 `ddl-auto: create-drop` 이라 앱을 켤 때마다 스키마가 새로 생성되고 `data.sql`로 시드 데이터가 들어갑니다. **앱을 끄면 데이터가 사라지는 게 정상입니다.**
- `/dev/concurrency`(공동구매 정원 동시성 검증 데모)는 `@Profile("local")`로 막혀 있어 `local` 프로필이 꺼진 채로 실행하면 해당 API가 존재하지 않아 오류가 납니다. 위 실행 방법대로 띄우면 기본값이 `local`이라 문제없습니다.
- 카카오/네이버 로그인은 각 콘솔에 리다이렉트 URI(`{origin}/oauth/kakao`, `{origin}/oauth/naver`)를 등록해야 정상 동작합니다.

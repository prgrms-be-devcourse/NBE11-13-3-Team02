#!/usr/bin/env bash
# 개발용 전체 실행 스크립트. Ctrl+C 로 전부 함께 종료됩니다.
#
# 서비스가 여러 개로 나뉘면서 각각 다른 환경변수를 넘겨야 하는데, 특히
# JWT_SECRET 과 QUEUE_INTERNAL_TOKEN 은 서비스끼리 값이 같아야 동작합니다.
#
# core 는 DB 접속 정보까지 Infisical 에서 받으므로 `infisical run` 으로 감싸 실행합니다
# (application.yml 의 플레이스홀더에 기본값이 없어 환경변수 없이는 기동하지 않습니다).
# queue 와 챗봇은 아직 로컬 파일을 쓰므로 .env.local 에서 읽어 넘깁니다.
# 두 경로 모두 setup.sh 가 Infisical 에서 받아온 같은 JWT_SECRET 을 씁니다.
#
# 사용: ./run.sh            전체 실행
#       ./run.sh core queue  일부만 실행
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
SHARED_ENV="$ROOT_DIR/.env.local"
INFISICAL_ENV="${INFISICAL_ENV:-dev}"

if [ ! -f "$SHARED_ENV" ]; then
  echo "'.env.local' 이 없습니다. 먼저 ./setup.sh 를 실행하세요."
  exit 1
fi

set -a
# shellcheck disable=SC1090
source "$SHARED_ENV"
set +a

if [ -z "${JWT_SECRET:-}" ] || [ -z "${QUEUE_INTERNAL_TOKEN:-}" ]; then
  echo ".env.local 에 JWT_SECRET 또는 QUEUE_INTERNAL_TOKEN 이 비어 있습니다. ./setup.sh 를 다시 실행하세요."
  exit 1
fi

if ! command -v infisical >/dev/null 2>&1; then
  echo "infisical CLI 가 없습니다. core 는 이걸 통해 DB/시크릿을 받습니다."
  echo "  설치: brew install infisical/get-cli/infisical"
  exit 1
fi

TARGETS=("$@")
[ ${#TARGETS[@]} -eq 0 ] && TARGETS=(core queue chatbot frontend)

wants() {
  local name="$1"
  for t in "${TARGETS[@]}"; do [ "$t" = "$name" ] && return 0; done
  return 1
}

PIDS=()
cleanup() {
  echo ""
  echo "종료 중..."
  for pid in "${PIDS[@]:-}"; do
    kill "$pid" 2>/dev/null || true
  done
  wait 2>/dev/null || true
}
trap cleanup EXIT INT TERM

start() {
  local name="$1" dir="$2"
  shift 2
  echo "[$name] 시작"
  ( cd "$dir" && exec "$@" ) &
  PIDS+=($!)
}

if wants core; then
  # core 를 두 번 띄우면 두 번째 인스턴스가 포트 바인딩에 실패하고도 종료 훅에서
  # create-drop 의 DROP 을 실행해, 살아 있는 인스턴스의 테이블까지 날려버린다.
  # Spring 이 뜨기 전에 막는다.
  if lsof -ti tcp:8080 >/dev/null 2>&1; then
    echo "이미 8080 에서 core 가 실행 중입니다. 중복 실행하면 DB 가 초기화됩니다."
    echo "실행 중인 core 를 먼저 멈춘 뒤 다시 실행하세요."
    exit 1
  fi
  start core "$ROOT_DIR/gachisa-backend" \
    infisical run --env="$INFISICAL_ENV" --silent -- ./gradlew bootRun --console=plain
fi

if wants queue; then
  start queue "$ROOT_DIR/gachisa-queue" ./gradlew bootRun --console=plain
fi

if wants chatbot; then
  if command -v uv >/dev/null 2>&1 && [ -n "${GEMINI_API_KEY:-}" ]; then
    start chatbot "$ROOT_DIR/gachisa-chatbot" uv run uvicorn app.main:app --port 8000
  else
    echo "[chatbot] 건너뜀 (uv 미설치이거나 .env.local 의 GEMINI_API_KEY 가 비어 있음)"
  fi
fi

if wants frontend; then
  start frontend "$ROOT_DIR/gachisa-frontend" infisical run --env="$INFISICAL_ENV" --silent -- npm run dev
fi

echo ""
echo "core     http://localhost:8080"
echo "queue    http://localhost:8081"
echo "chatbot  http://localhost:8000"
echo "frontend http://localhost:5173"
echo ""
echo "Ctrl+C 로 전부 종료합니다."
wait

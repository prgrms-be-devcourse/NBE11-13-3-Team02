#!/usr/bin/env bash
# 개발용 전체 실행 스크립트. Ctrl+C 로 전부 함께 종료됩니다.
#
# 서비스가 여러 개로 나뉘면서 각각 다른 환경변수를 넘겨야 하는데, 특히
# JWT_SECRET 과 QUEUE_INTERNAL_TOKEN 은 서비스끼리 값이 같아야 동작합니다.
# 그 값들을 .env.local 한 곳에서 읽어 넘깁니다.
#
# 사용: ./run.sh            전체 실행
#       ./run.sh core queue  일부만 실행
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
SHARED_ENV="$ROOT_DIR/.env.local"

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
  start core "$ROOT_DIR/gachisa-backend" ./gradlew bootRun --console=plain
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
  start frontend "$ROOT_DIR/gachisa-frontend" npm run dev
fi

echo ""
echo "core     http://localhost:8080"
echo "queue    http://localhost:8081"
echo "chatbot  http://localhost:8000"
echo "frontend http://localhost:5173"
echo ""
echo "Ctrl+C 로 전부 종료합니다."
wait

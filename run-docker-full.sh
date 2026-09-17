#!/usr/bin/env bash
# docker-compose.full.yml 실행용 래퍼입니다.
#
# backend/frontend는 Infisical 프로젝트가 서로 달라서(.infisical.json이 각
# 디렉터리에 따로 있음) `infisical run`을 저장소 루트에서 바로 쓸 수 없습니다.
# 그래서 두 프로젝트의 시크릿을 각각 export 받아 이 셸에 합쳐 넣은 다음
# docker compose를 실행합니다. (챗봇은 gachisa-chatbot/.env를 compose가
# 직접 읽으므로 여기서 다룰 필요 없습니다.)
#
# 사용법:
#   ./run-docker-full.sh up --build
#   ./run-docker-full.sh down
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
BACKEND_DIR="$ROOT_DIR/gachisa-backend"
FRONTEND_DIR="$ROOT_DIR/gachisa-frontend"
INFISICAL_ENV="${INFISICAL_ENV:-dev}"

command -v infisical >/dev/null 2>&1 || {
  echo "infisical CLI 가 없습니다. 설치: brew install infisical/get-cli/infisical"
  exit 1
}

echo "[infisical] backend/frontend(${INFISICAL_ENV}) 시크릿을 불러오는 중..."
set -a
eval "$(cd "$BACKEND_DIR" && infisical export --env="$INFISICAL_ENV" --format=dotenv-export --silent)"
eval "$(cd "$FRONTEND_DIR" && infisical export --env="$INFISICAL_ENV" --format=dotenv-export --silent)"
set +a

if [ -z "${JWT_SECRET:-}" ]; then
  echo "JWT_SECRET 을 못 받아왔습니다. 'infisical login' 이 돼 있는지 확인하세요."
  exit 1
fi

if [ ! -f "$ROOT_DIR/gachisa-chatbot/.env" ]; then
  echo "! gachisa-chatbot/.env 가 없습니다. 챗봇 컨테이너가 뜨려면 먼저 ./setup.sh 를 실행하세요."
fi

exec docker compose -f "$ROOT_DIR/docker-compose.full.yml" "$@"

#!/usr/bin/env bash
# clone 직후 한 번 실행하면 로컬 개발에 필요한 설정 파일과 의존성을 준비해줍니다.
# 실행: ./setup.sh  (여러 번 실행해도 안전합니다 - 이미 있는 파일은 건드리지 않습니다)
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
BACKEND_DIR="$ROOT_DIR/gachisa-backend"
FRONTEND_DIR="$ROOT_DIR/gachisa-frontend"

echo "== 같이사 로컬 개발 환경 설정 =="

# --- 사전 체크 ---
missing=()
command -v java >/dev/null 2>&1 || missing+=("java (JDK 17+)")
command -v node >/dev/null 2>&1 || missing+=("node")
command -v npm >/dev/null 2>&1 || missing+=("npm")
command -v mysql >/dev/null 2>&1 || echo "  ! mysql CLI를 못 찾았어요. MySQL 8이 로컬에 설치/실행 중인지 확인해주세요."
command -v redis-cli >/dev/null 2>&1 || echo "  ! redis-cli를 못 찾았어요. Redis가 로컬에 설치/실행 중인지 확인해주세요."

if [ ${#missing[@]} -gt 0 ]; then
  echo "다음이 설치되어 있지 않습니다: ${missing[*]}"
  echo "설치 후 다시 실행해주세요."
  exit 1
fi

# --- 백엔드 설정 파일 ---
BACKEND_LOCAL_YML="$BACKEND_DIR/src/main/resources/application-local.yml"
BACKEND_LOCAL_YML_EXAMPLE="$BACKEND_LOCAL_YML.example"
if [ -f "$BACKEND_LOCAL_YML" ]; then
  echo "[backend] application-local.yml 이미 있음 (건너뜀)"
else
  cp "$BACKEND_LOCAL_YML_EXAMPLE" "$BACKEND_LOCAL_YML"
  echo "[backend] application-local.yml 생성함 (필요하면 jwt.secret 값을 바꿔주세요)"
fi

# --- 프론트엔드 설정 파일 + 의존성 설치 ---
FRONTEND_ENV="$FRONTEND_DIR/.env"
FRONTEND_ENV_EXAMPLE="$FRONTEND_DIR/.env.example"
if [ -f "$FRONTEND_ENV" ]; then
  echo "[frontend] .env 이미 있음 (건너뜀)"
else
  cp "$FRONTEND_ENV_EXAMPLE" "$FRONTEND_ENV"
  echo "[frontend] .env 생성함 - VITE_TOSS_CLIENT_KEY 값을 채워넣어야 결제 테스트가 됩니다"
fi

echo "[frontend] npm install 실행 중..."
(cd "$FRONTEND_DIR" && npm install)

echo ""
echo "== 설정 완료 =="
echo "1) MySQL(localhost:3306, db=gachisa, gachisa/gachisa1234)과 Redis(localhost:6379)가 떠 있는지 확인"
echo "2) 백엔드 실행:  cd gachisa-backend && ./gradlew bootRun"
echo "3) 프론트 실행:  cd gachisa-frontend && npm run dev"
echo ""
echo "결제(Toss) 테스트를 하려면:"
echo "  - 백엔드: TOSS_SECRET_KEY 환경변수 설정"
echo "  - 프론트: gachisa-frontend/.env 의 VITE_TOSS_CLIENT_KEY 값 채우기"

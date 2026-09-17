#!/usr/bin/env bash
# clone 직후 한 번 실행하면 로컬 개발에 필요한 설정 파일과 의존성을 준비해줍니다.
# 실행: ./setup.sh  (여러 번 실행해도 안전합니다 - 이미 있는 파일은 건드리지 않습니다)
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
BACKEND_DIR="$ROOT_DIR/gachisa-backend"
FRONTEND_DIR="$ROOT_DIR/gachisa-frontend"
CHATBOT_DIR="$ROOT_DIR/gachisa-chatbot"
SHARED_ENV="$ROOT_DIR/.env.local"

echo "== 같이사 로컬 개발 환경 설정 =="

# --- 사전 체크 ---
missing=()
command -v java >/dev/null 2>&1 || missing+=("java (JDK 17+)")
command -v node >/dev/null 2>&1 || missing+=("node")
command -v npm >/dev/null 2>&1 || missing+=("npm")
command -v mysql >/dev/null 2>&1 || echo "  ! mysql CLI를 못 찾았어요. MySQL 8이 로컬에 설치/실행 중인지 확인해주세요."
command -v redis-cli >/dev/null 2>&1 || echo "  ! redis-cli를 못 찾았어요. Redis가 로컬에 설치/실행 중인지 확인해주세요."
command -v uv >/dev/null 2>&1 || echo "  ! uv를 못 찾았어요. 챗봇 서버에 필요합니다: brew install uv"
command -v openssl >/dev/null 2>&1 || missing+=("openssl")
command -v infisical >/dev/null 2>&1 || missing+=("infisical (brew install infisical/get-cli/infisical)")

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
  echo "[backend] application-local.yml 생성함 (시크릿은 여기가 아니라 Infisical에 있습니다)"
fi

# --- 프론트엔드 의존성 설치 ---
# 시크릿(VITE_TOSS_CLIENT_KEY 등)은 Infisical(gachisa-frontend/.infisical.json)이
# 단일 출처다. run.sh 가 infisical run 으로 주입하므로 .env 파일은 만들지 않는다.
echo "[frontend] npm install 실행 중..."
(cd "$FRONTEND_DIR" && npm install)

# --- 서비스 간 공유 시크릿 ---
# core 의 시크릿은 Infisical 이 단일 출처다(gachisa-backend/.infisical.json).
# 챗봇은 아직 로컬 파일에서 읽으므로 같은 JWT_SECRET 을 내려보낸다.
INFISICAL_ENV="${INFISICAL_ENV:-dev}"
JWT_SECRET="$(cd "$BACKEND_DIR" && infisical secrets get JWT_SECRET \
  --env="$INFISICAL_ENV" --plain --silent 2>/dev/null || true)"

if [ -z "$JWT_SECRET" ]; then
  echo "  ! Infisical 에서 JWT_SECRET 을 읽지 못했습니다."
  echo "    로그인:      cd $BACKEND_DIR && infisical login"
  echo "    환경 확인:   cd $BACKEND_DIR && infisical secrets --env=$INFISICAL_ENV"
  echo "    (환경 이름이 dev 가 아니면 INFISICAL_ENV=이름 ./setup.sh 로 실행하세요)"
  exit 1
fi
if [ ${#JWT_SECRET} -lt 64 ]; then
  echo "  ! Infisical 의 JWT_SECRET 이 ${#JWT_SECRET}자입니다. HS512는 64자 이상이어야 합니다."
  exit 1
fi

if [ -f "$SHARED_ENV" ]; then
  echo "[shared] .env.local 이미 있음 - JWT_SECRET 만 최신화"
  GEMINI_API_KEY="$(awk -F= '/^GEMINI_API_KEY=/{print substr($0, index($0,"=")+1); exit}' "$SHARED_ENV")"
else
  GEMINI_API_KEY=""
  echo "[shared] .env.local 생성함"
fi

cat > "$SHARED_ENV" <<SHARED
# 서비스 간 공유 설정. git에 올라가지 않습니다(.gitignore).
# JWT_SECRET 은 Infisical 이 원본이고 setup.sh 가 여기로 복사합니다.
# 직접 고치지 말고 Infisical 에서 바꾼 뒤 ./setup.sh 를 다시 실행하세요.
# (core 는 이 파일을 쓰지 않습니다 — infisical run 으로 직접 주입받습니다)
JWT_SECRET=$JWT_SECRET
# https://aistudio.google.com/apikey 에서 발급(무료). 챗봇에 필요합니다.
GEMINI_API_KEY=$GEMINI_API_KEY
SHARED

# --- 챗봇 서버 ---
# 챗봇은 자기 디렉터리의 .env 를 읽는다. 위 공유 설정에서 복사해 둔다.
if command -v uv >/dev/null 2>&1; then
  cp "$SHARED_ENV" "$CHATBOT_DIR/.env"
  {
    echo "SPRING_BASE_URL=http://localhost:8080"
    echo 'CORS_ORIGINS=["http://localhost:5173"]'
  } >> "$CHATBOT_DIR/.env"
  echo "[chatbot] .env 생성함, 의존성 설치 중..."
  (cd "$CHATBOT_DIR" && uv sync --quiet)
else
  echo "[chatbot] uv가 없어 건너뜀"
fi

echo ""
echo "== 설정 완료 =="
echo "MySQL(localhost:3306, db=gachisa)과 Redis(localhost:6379)가 떠 있는지 확인하세요."
echo ""
echo "전체 실행:  ./run.sh     (core + chatbot + frontend)"
echo "개별 실행은 README.md 참고"
echo ""
if [ -z "$GEMINI_API_KEY" ]; then
  echo "! 챗봇을 쓰려면 .env.local 의 GEMINI_API_KEY 를 채우세요."
  echo "  https://aistudio.google.com/apikey (무료)"
fi
echo "! 결제(Toss) 테스트: 백엔드 TOSS_SECRET_KEY 환경변수 + Infisical(frontend)의 VITE_TOSS_CLIENT_KEY"

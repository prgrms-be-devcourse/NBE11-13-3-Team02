#!/usr/bin/env bash
# clone 직후 한 번 실행하면 로컬 개발에 필요한 설정 파일과 의존성을 준비해줍니다.
# 실행: ./setup.sh  (여러 번 실행해도 안전합니다 - 이미 있는 파일은 건드리지 않습니다)
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
BACKEND_DIR="$ROOT_DIR/gachisa-backend"
FRONTEND_DIR="$ROOT_DIR/gachisa-frontend"
QUEUE_DIR="$ROOT_DIR/gachisa-queue"
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
# queue 와 챗봇은 아직 로컬 파일에서 읽으므로, 같은 JWT_SECRET 을 여기서 받아 내려보낸다.
# 값이 하나라도 어긋나면 core 가 발급한 토큰을 나머지가 거부한다.
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
  QUEUE_INTERNAL_TOKEN="$(awk -F= '/^QUEUE_INTERNAL_TOKEN=/{print substr($0, index($0,"=")+1); exit}' "$SHARED_ENV")"
  GEMINI_API_KEY="$(awk -F= '/^GEMINI_API_KEY=/{print substr($0, index($0,"=")+1); exit}' "$SHARED_ENV")"
else
  # core ↔ queue 서비스 간 호출용. 사용자 토큰과 용도가 다르며 팀원마다 달라도 된다.
  QUEUE_INTERNAL_TOKEN="$(openssl rand -hex 20)"
  GEMINI_API_KEY=""
  echo "[shared] .env.local 생성함"
fi

cat > "$SHARED_ENV" <<SHARED
# 서비스 간 공유 설정. git에 올라가지 않습니다(.gitignore).
# JWT_SECRET 은 Infisical 이 원본이고 setup.sh 가 여기로 복사합니다.
# 직접 고치지 말고 Infisical 에서 바꾼 뒤 ./setup.sh 를 다시 실행하세요.
# (core 는 이 파일을 쓰지 않습니다 — infisical run 으로 직접 주입받습니다)
JWT_SECRET=$JWT_SECRET
QUEUE_INTERNAL_TOKEN=$QUEUE_INTERNAL_TOKEN

# https://aistudio.google.com/apikey 에서 발급(무료). 챗봇에 필요합니다.
GEMINI_API_KEY=$GEMINI_API_KEY
SHARED

# --- core 의 내부 토큰 ---
# QUEUE_INTERNAL_TOKEN 은 core 와 queue 가 서로를 호출할 때 쓰는 공유 토큰이라
# 양쪽 값이 같아야 한다. core 는 Infisical 로 뜨는데 이 값은 거기 없으므로
# (Infisical 은 core 전용 시크릿만 관리) 여기서 application-local.yml 에 넣어 둔다.
# 환경변수가 있으면 그쪽이 우선한다(run.sh 경로).
python3 - "$BACKEND_LOCAL_YML" "$QUEUE_INTERNAL_TOKEN" <<'PYEOF'
import re, sys
path, token = sys.argv[1], sys.argv[2]
body = open(path, encoding='utf-8').read()
block = (
    "\n# core \u2194 queue \uc11c\ube44\uc2a4 \uac04 \ud638\ucd9c\uc6a9 \uacf5\uc720 \ud1a0\ud070. setup.sh \uac00 \uc0dd\uc131\ud569\ub2c8\ub2e4.\n"
    "queue:\n  internal-token: " + token + "\n"
)
if re.search(r'^queue:', body, flags=re.M):
    body = re.sub(r'(^queue:\n(?:.*\n)*?\s*internal-token: ).*$', r'\g<1>' + token, body, flags=re.M)
else:
    body = body.rstrip("\n") + "\n" + block
open(path, 'w', encoding='utf-8').write(body)
PYEOF
echo "[backend] application-local.yml 에 queue.internal-token 반영함"

# --- 대기열 서버 ---
# queue 는 셸 환경변수(run.sh)로도 뜨지만, IDE 실행 버튼처럼 환경변수가 없는 경로에서도
# 떠야 한다. core 와 같은 규칙으로 application-local.yml 에 공유 시크릿을 넣어 둔다.
# 환경변수가 있으면 그쪽이 우선하도록 ${VAR:기본값} 형태로 쓴다.
QUEUE_LOCAL_YML="$QUEUE_DIR/src/main/resources/application-local.yml"
cat > "$QUEUE_LOCAL_YML" <<QUEUE_YML
# setup.sh 가 생성합니다. git에 올라가지 않습니다(.gitignore).
# jwt-secret 은 gachisa-backend/src/main/resources/application-local.yml 이 원본입니다.
# 직접 고치지 말고 원본을 고친 뒤 setup.sh 를 다시 실행하세요.

queue:
  jwt-secret: \${JWT_SECRET:$JWT_SECRET}
  internal-token: \${QUEUE_INTERNAL_TOKEN:$QUEUE_INTERNAL_TOKEN}
QUEUE_YML
echo "[queue] application-local.yml 생성함"

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
echo "전체 실행:  ./run.sh     (core + queue + chatbot + frontend)"
echo "개별 실행은 README.md 참고"
echo ""
if [ -z "$GEMINI_API_KEY" ]; then
  echo "! 챗봇을 쓰려면 .env.local 의 GEMINI_API_KEY 를 채우세요."
  echo "  https://aistudio.google.com/apikey (무료)"
fi
echo "! 결제(Toss) 테스트: 백엔드 TOSS_SECRET_KEY 환경변수 + Infisical(frontend)의 VITE_TOSS_CLIENT_KEY"

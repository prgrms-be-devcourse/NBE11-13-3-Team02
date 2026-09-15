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

# --- 서비스 간 공유 시크릿 ---
# core / queue / chatbot 이 같은 값을 써야 한다. application-local.yml 의 jwt.secret 을
# 단일 출처로 삼아 나머지에 전파한다. 이 파일은 .gitignore 대상이다.
# head 로 파이프를 끊으면 앞 단계가 SIGPIPE 로 죽어 set -e 에 걸린다. awk 한 번으로 끝낸다.
JWT_SECRET="$(awk '/^jwt:/{inblock=1; next} /^[^[:space:]]/{inblock=0} inblock && /^[[:space:]]*secret:/{sub(/^[[:space:]]*secret:[[:space:]]*/, ""); gsub(/["\x27]/, ""); print; exit}' "$BACKEND_LOCAL_YML")"

if [ -z "$JWT_SECRET" ]; then
  echo "  ! application-local.yml 에서 jwt.secret 을 찾지 못했습니다. 직접 확인해주세요."
  exit 1
fi
if [ ${#JWT_SECRET} -lt 64 ]; then
  echo "  ! jwt.secret 이 ${#JWT_SECRET}자입니다. HS512는 64자 이상이어야 합니다."
  exit 1
fi

# 예제 파일의 값을 그대로 쓰면 리포지토리를 본 사람은 누구나 토큰을 위조할 수 있다.
EXAMPLE_SECRET="$(awk '/^jwt:/{inblock=1; next} /^[^[:space:]]/{inblock=0} inblock && /^[[:space:]]*secret:/{sub(/^[[:space:]]*secret:[[:space:]]*/, ""); gsub(/["\x27]/, ""); print; exit}' "$BACKEND_LOCAL_YML_EXAMPLE")"
if [ "$JWT_SECRET" = "$EXAMPLE_SECRET" ]; then
  echo ""
  echo "  ! jwt.secret 이 예제 파일의 값 그대로입니다."
  echo "    로컬 개발만 하면 괜찮지만, 이대로 배포하면 누구나 액세스 토큰을 위조할 수 있습니다."
  echo "    새 값 생성:  openssl rand -base64 64 | tr -d '\\n'"
  echo "    적용:        $BACKEND_LOCAL_YML 의 jwt.secret 을 바꾸고 ./setup.sh 재실행"
  echo ""
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
# jwt.secret 은 gachisa-backend/src/main/resources/application-local.yml 이 원본이고
# 이 파일은 setup.sh 가 거기서 복사합니다. 직접 고치지 말고 원본을 고친 뒤 setup.sh 를 다시 실행하세요.
JWT_SECRET=$JWT_SECRET
QUEUE_INTERNAL_TOKEN=$QUEUE_INTERNAL_TOKEN

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
echo "전체 실행:  ./run.sh     (core + queue + chatbot + frontend)"
echo "개별 실행은 README.md 참고"
echo ""
if [ -z "$GEMINI_API_KEY" ]; then
  echo "! 챗봇을 쓰려면 .env.local 의 GEMINI_API_KEY 를 채우세요."
  echo "  https://aistudio.google.com/apikey (무료)"
fi
echo "! 결제(Toss) 테스트: 백엔드 TOSS_SECRET_KEY 환경변수 + gachisa-frontend/.env 의 VITE_TOSS_CLIENT_KEY"

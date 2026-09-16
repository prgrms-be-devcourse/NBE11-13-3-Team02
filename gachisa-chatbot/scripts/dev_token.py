"""로컬 테스트용 액세스 토큰 발급. Spring JwtTokenProvider와 동일한 클레임 구조를 만든다.

사용: uv run python scripts/dev_token.py [USER_ID] [NAME] [ROLE]
"""

import sys
import time

import jwt

from app.config import get_settings


def main() -> None:
    user_id = sys.argv[1] if len(sys.argv) > 1 else "1"
    name = sys.argv[2] if len(sys.argv) > 2 else "테스터"
    role = sys.argv[3] if len(sys.argv) > 3 else "USER"

    settings = get_settings()
    now = int(time.time())
    token = jwt.encode(
        {
            "sub": str(user_id),
            "name": name,
            "role": role,
            "iat": now,
            "exp": now + 1800,
        },
        settings.jwt_secret,
        algorithm=settings.jwt_algorithm,
    )
    print(token)


if __name__ == "__main__":
    main()

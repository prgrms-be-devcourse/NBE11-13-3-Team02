from dataclasses import dataclass
from typing import Annotated

import jwt
from fastapi import Depends, HTTPException, status
from fastapi.security import HTTPAuthorizationCredentials, HTTPBearer

from app.config import Settings, get_settings

_bearer = HTTPBearer(auto_error=False)


@dataclass(frozen=True)
class CurrentUser:
    user_id: int
    name: str
    role: str
    # 챗봇 서버는 자체 권한을 갖지 않는다. Spring을 호출할 때 이 토큰을 그대로 전달해
    # 인가 판단은 언제나 Spring이 하게 만든다. 서비스 계정을 두면 프롬프트 인젝션으로
    # 남의 주문을 읽어낼 수 있게 된다.
    access_token: str


def get_current_user(
    credentials: Annotated[HTTPAuthorizationCredentials | None, Depends(_bearer)],
    settings: Annotated[Settings, Depends(get_settings)],
) -> CurrentUser:
    if credentials is None:
        raise HTTPException(status.HTTP_401_UNAUTHORIZED, "인증 토큰이 없습니다.")

    token = credentials.credentials
    try:
        claims = jwt.decode(
            token,
            settings.jwt_secret,
            algorithms=[settings.jwt_algorithm],
            options={"require": ["exp", "sub"]},
        )
    except jwt.ExpiredSignatureError:
        raise HTTPException(status.HTTP_401_UNAUTHORIZED, "만료된 토큰입니다.")
    except jwt.InvalidTokenError:
        raise HTTPException(status.HTTP_401_UNAUTHORIZED, "유효하지 않은 토큰입니다.")

    return CurrentUser(
        user_id=int(claims["sub"]),
        name=claims.get("name", ""),
        role=claims.get("role", "USER"),
        access_token=token,
    )


CurrentUserDep = Annotated[CurrentUser, Depends(get_current_user)]

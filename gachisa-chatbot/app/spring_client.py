from typing import Annotated, Any

import httpx
from fastapi import Depends, Request


class SpringClient:
    """Spring 백엔드 호출용 얇은 래퍼. 항상 사용자 토큰을 실어 보낸다."""

    def __init__(
        self,
        base_url: str,
        timeout: float,
        transport: httpx.AsyncBaseTransport | None = None,
    ) -> None:
        self._client = httpx.AsyncClient(
            base_url=base_url, timeout=timeout, transport=transport
        )

    async def aclose(self) -> None:
        await self._client.aclose()

    async def get(
        self,
        path: str,
        access_token: str,
        params: dict[str, Any] | None = None,
    ) -> httpx.Response:
        return await self._client.get(
            path,
            params=params,
            headers={"Authorization": f"Bearer {access_token}"},
        )


def get_spring_client(request: Request) -> SpringClient:
    return request.app.state.spring_client


SpringClientDep = Annotated[SpringClient, Depends(get_spring_client)]

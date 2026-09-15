import time

import jwt
import pytest

from app.config import get_settings

TEST_SECRET = "test-secret-that-is-at-least-64-bytes-long-for-hs512-algorithm-ok"


@pytest.fixture(autouse=True)
def _test_settings(monkeypatch):
    monkeypatch.setenv("JWT_SECRET", TEST_SECRET)
    monkeypatch.setenv("SPRING_BASE_URL", "http://spring.test")
    monkeypatch.setenv("GEMINI_API_KEY", "test-key")
    get_settings.cache_clear()
    yield
    get_settings.cache_clear()


@pytest.fixture
def make_token():
    def _make(user_id: int = 7, name: str = "테스터", role: str = "USER", ttl: int = 1800) -> str:
        now = int(time.time())
        return jwt.encode(
            {"sub": str(user_id), "name": name, "role": role, "iat": now, "exp": now + ttl},
            TEST_SECRET,
            algorithm="HS512",
        )

    return _make

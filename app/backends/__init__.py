"""Pluggable authentication backends.

``SupabaseAuthBackend`` is the production path.  ``DemoAuthBackend`` keeps the
login page fully usable (and testable) when no Supabase project is configured.
Both expose exactly the same tiny interface so the routes never care which one
is active.
"""

from __future__ import annotations

from dataclasses import dataclass
from typing import Optional


class AuthFailure(Exception):
    """A safe, user-presentable authentication failure."""

    def __init__(
        self,
        message: str,
        *,
        code: str = "auth_failed",
        field: Optional[str] = None,
        status: int = 401,
    ) -> None:
        super().__init__(message)
        self.message = message
        self.code = code
        self.field = field
        self.status = status


@dataclass
class AuthSession:
    access_token: str
    refresh_token: str
    expires_at: int
    user: dict

    def as_dict(self) -> dict:
        return {
            "access_token": self.access_token,
            "refresh_token": self.refresh_token,
            "expires_at": self.expires_at,
            "user": self.user,
        }


@dataclass
class AuthOutcome:
    session: Optional[AuthSession] = None
    needs_confirmation: bool = False
    message: str = ""
    user: Optional[dict] = None


class BaseAuthBackend:
    """Interface implemented by every backend."""

    name = "base"
    supports_signup = True
    supports_password_reset = False

    def sign_in(self, email: str, password: str) -> AuthOutcome:  # pragma: no cover
        raise NotImplementedError

    def sign_up(self, email: str, password: str, name: str = "") -> AuthOutcome:
        raise NotImplementedError

    def refresh(self, refresh_token: str) -> Optional[AuthSession]:
        return None

    def sign_out(self, session: Optional[dict] = None) -> None:
        return None

    def request_password_reset(self, email: str) -> None:
        raise AuthFailure(
            "Password reset is not available in this environment.",
            code="not_supported",
            status=501,
        )


from .demo_backend import DemoAuthBackend  # noqa: E402  (circular-safe import)
from .supabase_backend import SupabaseAuthBackend  # noqa: E402


def build_backend(config) -> BaseAuthBackend:
    """Return the Supabase backend when configured, otherwise demo mode.

    ``config`` may be a Flask ``Config`` mapping or a plain config class, so
    every value is read defensively.
    """
    def get(key, default=None):
        try:
            value = config[key]
        except (TypeError, KeyError, IndexError):
            value = getattr(config, key, default)
        return default if value is None else value

    if get("FORCE_DEMO", False):
        return DemoAuthBackend(get("DEMO_STORE"))
    url = str(get("SUPABASE_URL", "") or "").strip()
    anon_key = str(get("SUPABASE_ANON_KEY", "") or "").strip()
    if url and anon_key:
        return SupabaseAuthBackend(
            url,
            anon_key,
            reset_redirect_url=str(get("SUPABASE_RESET_REDIRECT_URL", "") or ""),
        )
    return DemoAuthBackend(get("DEMO_STORE"))


__all__ = [
    "AuthFailure",
    "AuthOutcome",
    "AuthSession",
    "BaseAuthBackend",
    "DemoAuthBackend",
    "SupabaseAuthBackend",
    "build_backend",
]

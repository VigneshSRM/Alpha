"""Authentication backends.

``SupabaseAuthBackend`` is the only backend: every credential check, policy
decision and session is made by the backend, never by the browser.
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
    """Interface implemented by the backend."""

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


from .supabase_backend import SupabaseAuthBackend  # noqa: E402


def build_backend(config) -> BaseAuthBackend:
    """Build the Supabase backend from configuration.

    There is deliberately no offline/fallback mode: if the project is not
    configured the application refuses to start instead of quietly validating
    credentials anywhere other than Supabase.
    """
    def get(key, default=None):
        try:
            value = config[key]
        except (TypeError, KeyError, IndexError):
            value = getattr(config, key, default)
        return default if value is None else value

    url = str(get("SUPABASE_URL", "") or "").strip()
    anon_key = str(get("SUPABASE_ANON_KEY", "") or "").strip()
    if not url or not anon_key:
        raise RuntimeError(
            "Supabase is not configured: set SUPABASE_URL and SUPABASE_ANON_KEY "
            "(see .env.example). Refusing to start without a real auth backend."
        )
    return SupabaseAuthBackend(
        url,
        anon_key,
        reset_redirect_url=str(get("SUPABASE_RESET_REDIRECT_URL", "") or ""),
    )


__all__ = [
    "AuthFailure",
    "AuthOutcome",
    "AuthSession",
    "BaseAuthBackend",
    "SupabaseAuthBackend",
    "build_backend",
]

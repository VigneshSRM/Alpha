"""Supabase (Supabase Auth / GoTrue) implementation of the auth backend."""

from __future__ import annotations

import logging
import time
from typing import Optional

from . import AuthFailure, AuthOutcome, AuthSession, BaseAuthBackend

log = logging.getLogger(__name__)

GENERIC_LOGIN_ERROR = "Invalid e-mail or password. Please try again."
GENERIC_SIGNUP_ERROR = "We could not create your account. Please try again."


def _as_int(value, fallback: int) -> int:
    try:
        return int(value)
    except (TypeError, ValueError):
        return fallback


class SupabaseAuthBackend(BaseAuthBackend):
    name = "supabase"
    supports_password_reset = True

    def __init__(
        self,
        url: str,
        anon_key: str,
        *,
        reset_redirect_url: str = "",
    ) -> None:
        self.url = url
        self.anon_key = anon_key
        self.reset_redirect_url = reset_redirect_url
        # Imported lazily so the app still boots (in demo mode) if the SDK is
        # missing from a bare environment.
        from supabase import create_client

        self._create_client = create_client

    # -- helpers ----------------------------------------------------------
    def _client(self):
        """A fresh client per request keeps sessions from leaking between users."""
        return self._create_client(self.url, self.anon_key)

    @staticmethod
    def _session_from_auth_response(response) -> AuthSession | None:
        session = getattr(response, "session", None)
        if not session:
            return None
        user = getattr(response, "user", None) or getattr(session, "user", None)
        user_payload = {}
        if user is not None:
            user_payload = {
                "id": str(getattr(user, "id", "") or ""),
                "email": getattr(user, "email", "") or "",
                "created_at": str(getattr(user, "created_at", "") or ""),
                "user_metadata": getattr(user, "user_metadata", {}) or {},
            }
        return AuthSession(
            access_token=session.access_token,
            refresh_token=session.refresh_token,
            expires_at=_as_int(
                getattr(session, "expires_at", None), int(time.time()) + 3600
            ),
            user=user_payload,
        )

    @staticmethod
    def _map_error(error: Exception, *, action: str) -> AuthFailure:
        message = (getattr(error, "message", "") or str(error) or "").strip()
        code = (getattr(error, "code", "") or "").strip().lower()
        status = _as_int(getattr(error, "status", 0), 0)
        lowered = message.lower()

        log.warning("Supabase %s failed: status=%s code=%s msg=%s", action, status, code, message)

        # Transport-level failures (DNS, timeout, TLS) never reached Supabase.
        if status == 0 and not code:
            if any(
                marker in lowered
                for marker in (
                    "name or service not known",
                    "connection",
                    "timed out",
                    "timeout",
                    "temporary failure",
                    "ssl",
                    "network",
                )
            ):
                return AuthFailure(
                    "We cannot reach the sign-in service right now. "
                    "Please try again in a moment.",
                    code="service_unavailable",
                    status=502,
                )
        if status == 429 or "rate limit" in lowered or "too many" in lowered:
            return AuthFailure(
                "Too many attempts. Please wait a minute and try again.",
                code="rate_limited",
                status=429,
            )
        if action == "sign_in":
            if "email not confirmed" in lowered or code == "email_not_confirmed":
                return AuthFailure(
                    "Please confirm your e-mail address before signing in.",
                    code="email_unconfirmed",
                    field="email",
                    status=403,
                )
            return AuthFailure(
                GENERIC_LOGIN_ERROR, code="invalid_credentials", status=401
            )
        if action == "sign_up":
            if "already registered" in lowered or "already exists" in lowered:
                return AuthFailure(
                    "An account with this e-mail already exists. Try signing in.",
                    code="user_exists",
                    field="email",
                    status=409,
                )
            if "password" in lowered and (
                "weak" in lowered or "length" in lowered or "characters" in lowered
            ):
                return AuthFailure(
                    "Supabase rejected this password. Use at least 12 characters "
                    "with mixed case, numbers and symbols.",
                    code="weak_password",
                    field="password",
                    status=422,
                )
            if "invalid" in lowered and "email" in lowered:
                return AuthFailure(
                    "That e-mail address was rejected. Please check it.",
                    code="invalid_email",
                    field="email",
                    status=422,
                )
            return AuthFailure(GENERIC_SIGNUP_ERROR, code="signup_failed", status=400)
        return AuthFailure(
            "Something went wrong. Please try again in a moment.",
            code="server_error",
            status=502,
        )

    # -- interface ---------------------------------------------------------
    def sign_in(self, email: str, password: str) -> AuthOutcome:
        client = self._client()
        try:
            response = client.auth.sign_in_with_password(
                {"email": email, "password": password}
            )
        except Exception as error:  # supabase_auth.errors.AuthError
            raise self._map_error(error, action="sign_in") from error

        session = self._session_from_auth_response(response)
        if session is None:
            raise AuthFailure(GENERIC_LOGIN_ERROR, code="invalid_credentials")
        return AuthOutcome(session=session)

    def sign_up(self, email: str, password: str, name: str = "") -> AuthOutcome:
        client = self._client()
        payload = {
            "email": email,
            "password": password,
            "options": {"data": {"full_name": name}} if name else {},
        }
        try:
            response = client.auth.sign_up(payload)
        except Exception as error:
            raise self._map_error(error, action="sign_up") from error

        session = self._session_from_auth_response(response)
        if session is None:
            # E-mail confirmation is enabled on the project.
            return AuthOutcome(
                needs_confirmation=True,
                message=(
                    "Account created. Check your inbox to confirm your e-mail, "
                    "then sign in."
                ),
            )
        return AuthOutcome(session=session, message="Welcome to Alpha Group of India.")

    def refresh(self, refresh_token: str) -> Optional[AuthSession]:
        if not refresh_token:
            return None
        client = self._client()
        try:
            response = client.auth.refresh_session(refresh_token)
        except Exception as error:  # expired / revoked token
            log.info("Supabase refresh failed: %s", error)
            return None
        return self._session_from_auth_response(response)

    def sign_out(self, session: Optional[dict] = None) -> None:
        if not session:
            return
        client = self._client()
        try:
            client.auth.set_session(
                session.get("access_token", ""), session.get("refresh_token", "")
            )
            client.auth.sign_out()
        except Exception as error:  # never block logout on a network error
            log.info("Supabase sign_out failed: %s", error)

    def request_password_reset(self, email: str) -> None:
        client = self._client()
        options = (
            {"redirect_to": self.reset_redirect_url}
            if self.reset_redirect_url
            else {}
        )
        try:
            client.auth.reset_password_for_email(email, options)
        except Exception as error:
            failure = self._map_error(error, action="reset")
            if failure.code == "rate_limited" or failure.status == 429:
                raise failure
            # Never reveal whether an address is registered.
            log.info("Password reset request failed (suppressed): %s", error)

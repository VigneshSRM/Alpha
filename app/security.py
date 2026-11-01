"""Session handling and brute-force protection (no extra dependencies)."""

from __future__ import annotations

import threading
import time
from collections import defaultdict, deque
from typing import Optional

from flask import current_app, request, session


# ---------------------------------------------------------------------------
# Session helpers
# ---------------------------------------------------------------------------
SESSION_KEY = "auth"


def current_user() -> Optional[dict]:
    auth = session.get(SESSION_KEY)
    if not auth:
        return None
    user = auth.get("user") or {}
    if not user.get("email"):
        return None
    return user


def current_auth() -> Optional[dict]:
    return session.get(SESSION_KEY) or None


def login_session(auth_payload: dict, *, remember: bool) -> None:
    """Persist the backend session plus a small display-only user snapshot."""
    session.clear()
    session.permanent = bool(remember)
    session[SESSION_KEY] = {
        "access_token": auth_payload.get("access_token", ""),
        "refresh_token": auth_payload.get("refresh_token", ""),
        "expires_at": int(auth_payload.get("expires_at") or 0),
        "user": auth_payload.get("user") or {},
        "logged_in_at": int(time.time()),
        "backend": current_app.extensions["auth_backend"].name,
    }


def logout_session() -> None:
    session.pop(SESSION_KEY, None)
    session.clear()


def session_expired() -> bool:
    auth = current_auth()
    if not auth:
        return True
    expires_at = int(auth.get("expires_at") or 0)
    return bool(expires_at) and expires_at <= time.time()


def refresh_session_if_needed(skew: int) -> bool:
    """Silently refresh the Supabase access token when it is about to expire."""
    auth = current_auth()
    if not auth:
        return False
    expires_at = int(auth.get("expires_at") or 0)
    refresh_token = auth.get("refresh_token") or ""
    if not expires_at or not refresh_token:
        return False
    if expires_at - skew > time.time():
        return True

    backend = current_app.extensions["auth_backend"]
    refreshed = backend.refresh(refresh_token)
    if not refreshed:
        logout_session()
        return False

    auth.update(
        {
            "access_token": refreshed.access_token,
            "refresh_token": refreshed.refresh_token,
            "expires_at": refreshed.expires_at,
            "user": refreshed.user or auth.get("user") or {},
        }
    )
    session[SESSION_KEY] = auth
    return True


# ---------------------------------------------------------------------------
# Brute-force throttle (in-memory, per process)
# ---------------------------------------------------------------------------
class AttemptThrottle:
    """Sliding-window limiter keyed by e-mail *and* client IP."""

    def __init__(self, max_attempts: int, window_seconds: int) -> None:
        self.max_attempts = max(1, max_attempts)
        self.window = max(1, window_seconds)
        self._hits: dict[str, deque[float]] = defaultdict(deque)
        self._lock = threading.Lock()

    def _prune(self, key: str, now: float) -> deque[float]:
        bucket = self._hits[key]
        while bucket and now - bucket[0] > self.window:
            bucket.popleft()
        return bucket

    def retry_after(self, keys: list[str]) -> int:
        """Seconds to wait, or 0 when the caller may proceed."""
        now = time.time()
        with self._lock:
            for key in keys:
                bucket = self._prune(key, now)
                if len(bucket) >= self.max_attempts:
                    return max(1, int(bucket[0] + self.window - now) + 1)
        return 0

    def register_failure(self, keys: list[str]) -> int:
        """Record a failure and return the remaining attempts for the key."""
        now = time.time()
        with self._lock:
            for key in keys:
                self._prune(key, now).append(now)
            bucket = self._prune(keys[0], now)
            return max(0, self.max_attempts - len(bucket))

    def reset(self, keys: list[str]) -> None:
        with self._lock:
            for key in keys:
                self._hits.pop(key, None)


def attempt_keys(email: str) -> list[str]:
    ip = (
        request.headers.get("CF-Connecting-IP")
        or (request.headers.get("X-Forwarded-For") or "").split(",")[0].strip()
        or request.remote_addr
        or "unknown"
    )
    return [f"email:{email.strip().lower()}", f"ip:{ip}"]


def throttle() -> AttemptThrottle:
    return current_app.extensions.setdefault(
        "login_throttle",
        AttemptThrottle(
            current_app.config["LOGIN_MAX_ATTEMPTS"],
            current_app.config["LOGIN_WINDOW_SECONDS"],
        ),
    )

"""Local demo authentication backend.

Used automatically when ``SUPABASE_URL`` / ``SUPABASE_ANON_KEY`` are absent so
the login page is fully explorable before a Supabase project exists.  Passwords
are still hashed with Werkzeug's PBKDF2 implementation and the same policy is
enforced — only storage is local (``.data/demo_users.json``).
"""

from __future__ import annotations

import json
import secrets
import threading
import time
import uuid
from pathlib import Path
from typing import Optional

from werkzeug.security import check_password_hash, generate_password_hash

from . import AuthFailure, AuthOutcome, AuthSession, BaseAuthBackend

DEMO_EMAIL = "demo@alphagroup.in"
DEMO_PASSWORD = "Alpha@Wolf#2026!"
DEMO_NAME = "Demo User"

_LOCK = threading.RLock()
_SESSIONS: dict[str, dict] = {}


class DemoAuthBackend(BaseAuthBackend):
    name = "demo"
    supports_signup = True
    supports_password_reset = False

    def __init__(self, store_path: Path) -> None:
        self.store_path = Path(store_path)
        self._ensure_store()

    # -- storage ----------------------------------------------------------
    def _ensure_store(self) -> None:
        with _LOCK:
            if self.store_path.exists():
                return
            self.store_path.parent.mkdir(parents=True, exist_ok=True)
            seed = {
                "users": {
                    DEMO_EMAIL: {
                        "id": str(uuid.uuid4()),
                        "email": DEMO_EMAIL,
                        "name": DEMO_NAME,
                        "password_hash": generate_password_hash(DEMO_PASSWORD),
                        "created_at": "2026-01-01T00:00:00Z",
                    }
                }
            }
            self.store_path.write_text(json.dumps(seed, indent=2), encoding="utf-8")

    def _read(self) -> dict:
        with _LOCK:
            try:
                return json.loads(self.store_path.read_text(encoding="utf-8"))
            except (OSError, json.JSONDecodeError):
                return {"users": {}}

    def _write(self, data: dict) -> None:
        with _LOCK:
            self.store_path.parent.mkdir(parents=True, exist_ok=True)
            self.store_path.write_text(json.dumps(data, indent=2), encoding="utf-8")

    @staticmethod
    def _user_payload(record: dict) -> dict:
        return {
            "id": record["id"],
            "email": record["email"],
            "created_at": record.get("created_at", ""),
            "user_metadata": {"full_name": record.get("name", "")},
        }

    def _issue(self, record: dict, ttl: int = 3600) -> AuthSession:
        access_token = secrets.token_urlsafe(32)
        refresh_token = secrets.token_urlsafe(32)
        expires_at = int(time.time()) + ttl
        with _LOCK:
            _SESSIONS[refresh_token] = {
                "user_id": record["id"],
                "expires_at": expires_at + 60 * 60 * 24 * 7,
            }
        return AuthSession(
            access_token=access_token,
            refresh_token=refresh_token,
            expires_at=expires_at,
            user=self._user_payload(record),
        )

    # -- interface ---------------------------------------------------------
    def sign_in(self, email: str, password: str) -> AuthOutcome:
        email = email.strip().lower()
        record = self._read().get("users", {}).get(email)
        # Constant-ish work whether or not the user exists.
        stored_hash = (
            record["password_hash"]
            if record
            else generate_password_hash("placeholder-value-not-a-real-password")
        )
        valid = check_password_hash(stored_hash, password)
        if not record or not valid:
            raise AuthFailure(
                "Invalid e-mail or password. Please try again.",
                code="invalid_credentials",
            )
        return AuthOutcome(session=self._issue(record))

    def sign_up(self, email: str, password: str, name: str = "") -> AuthOutcome:
        email = email.strip().lower()
        data = self._read()
        users = data.setdefault("users", {})
        if email in users:
            raise AuthFailure(
                "An account with this e-mail already exists. Try signing in.",
                code="user_exists",
                field="email",
                status=409,
            )
        record = {
            "id": str(uuid.uuid4()),
            "email": email,
            "name": name.strip(),
            "password_hash": generate_password_hash(password),
            "created_at": time.strftime("%Y-%m-%dT%H:%M:%SZ", time.gmtime()),
        }
        users[email] = record
        self._write(data)
        return AuthOutcome(
            session=self._issue(record),
            message="Account created. Welcome to Alpha Group of India.",
        )

    def refresh(self, refresh_token: str) -> Optional[AuthSession]:
        with _LOCK:
            entry = _SESSIONS.get(refresh_token)
            if not entry or entry["expires_at"] < time.time():
                return None
            _SESSIONS.pop(refresh_token, None)
        users = self._read().get("users", {})
        match = next(
            (user for user in users.values() if user.get("id") == entry["user_id"]), None
        )
        if not match:
            return None
        return self._issue(match)

    def sign_out(self, session: Optional[dict] = None) -> None:
        if not session:
            return
        with _LOCK:
            _SESSIONS.pop(session.get("refresh_token", ""), None)

    def request_password_reset(self, email: str) -> None:
        # Nothing to send in demo mode; the route shows the generic message.
        return None

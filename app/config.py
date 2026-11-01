"""Application configuration.

Every secret is read from the environment so that nothing sensitive is ever
committed to the repository.  ``.env.example`` documents the expected keys.
"""

from __future__ import annotations

import os
from datetime import timedelta
from pathlib import Path

BASE_DIR = Path(__file__).resolve().parent.parent


def _load_dotenv() -> None:
    """Load a local ``.env`` file if python-dotenv is available."""
    try:
        from dotenv import load_dotenv
    except ImportError:  # pragma: no cover - optional dependency
        return
    load_dotenv(BASE_DIR / ".env")


def _env_bool(name: str, default: bool = False) -> bool:
    raw = os.getenv(name)
    if raw is None:
        return default
    return raw.strip().lower() in {"1", "true", "yes", "on"}


_load_dotenv()


class Config:
    # --- Flask -----------------------------------------------------------
    # A random ephemeral key keeps local previews working; production MUST set
    # SECRET_KEY so that sessions survive restarts / multiple workers.
    SECRET_KEY = os.getenv("SECRET_KEY") or os.urandom(32).hex()
    SESSION_COOKIE_NAME = "alpha_session"
    SESSION_COOKIE_HTTPONLY = True
    SESSION_COOKIE_SAMESITE = "Lax"
    SESSION_COOKIE_SECURE = _env_bool("SESSION_COOKIE_SECURE", False)
    PERMANENT_SESSION_LIFETIME = timedelta(hours=8)
    MAX_CONTENT_LENGTH = 64 * 1024  # tiny forms only

    # --- Supabase --------------------------------------------------------
    SUPABASE_URL = (os.getenv("SUPABASE_URL") or "").strip().rstrip("/")
    SUPABASE_ANON_KEY = (os.getenv("SUPABASE_ANON_KEY") or "").strip()
    # Redirect target used by the "forgot password" email flow.
    SUPABASE_RESET_REDIRECT_URL = (
        os.getenv("SUPABASE_RESET_REDIRECT_URL") or "http://localhost:5000/login"
    )

    # --- Auth behaviour ---------------------------------------------------
    # Set FORCE_DEMO=1 to test the UI without any Supabase credentials.
    FORCE_DEMO = _env_bool("FORCE_DEMO", False)
    # Failed sign-in attempts allowed per window, per IP + per email.
    LOGIN_MAX_ATTEMPTS = int(os.getenv("LOGIN_MAX_ATTEMPTS", "5"))
    LOGIN_WINDOW_SECONDS = int(os.getenv("LOGIN_WINDOW_SECONDS", "900"))
    # Refresh the Supabase access token this many seconds before it expires.
    TOKEN_REFRESH_SKEW = int(os.getenv("TOKEN_REFRESH_SKEW", "120"))
    REMEMBER_DAYS = int(os.getenv("REMEMBER_DAYS", "7"))

    # --- Paths ------------------------------------------------------------
    DEMO_STORE = Path(
        os.getenv("DEMO_STORE", str(BASE_DIR / ".data" / "demo_users.json"))
    )


class DevelopmentConfig(Config):
    DEBUG = True


class ProductionConfig(Config):
    DEBUG = False
    PREFERRED_URL_SCHEME = "https"


def get_config() -> type[Config]:
    env = (os.getenv("FLASK_ENV") or os.getenv("APP_ENV") or "development").lower()
    if env.startswith("prod"):
        return ProductionConfig
    return DevelopmentConfig

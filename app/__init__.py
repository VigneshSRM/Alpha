"""Alpha Group of India — application factory."""

from __future__ import annotations

import logging
import secrets
from typing import Any

import hmac

from flask import Flask, jsonify, render_template, request, session, url_for

from .backends import build_backend
from .config import BASE_DIR, get_config
from .password_policy import POLICY, RULES
from .security import current_user

CSRF_SESSION_KEY = "csrf_token"


def create_app(config_object: Any | None = None) -> Flask:
    app = Flask(__name__, static_folder="static", template_folder="templates")
    app.config.from_object(config_object or get_config())

    logging.basicConfig(
        level=logging.DEBUG if app.debug else logging.INFO,
        format="%(asctime)s %(levelname)s %(name)s: %(message)s",
    )

    app.extensions["auth_backend"] = build_backend(app.config)
    app.extensions["asset_version"] = _asset_version(app)

    from .routes import bp as auth_bp

    app.register_blueprint(auth_bp)

    # ------------------------------------------------------------------
    # CSRF protection (double-check on every non-GET request)
    # ------------------------------------------------------------------
    @app.before_request
    def _csrf_guard():
        if request.method in {"GET", "HEAD", "OPTIONS"}:
            return None
        if request.endpoint in {"static"}:
            return None
        expected = session.get(CSRF_SESSION_KEY)
        supplied = (
            request.form.get("csrf_token")
            or request.headers.get("X-CSRF-Token")
            or ""
        )
        if not expected or not hmac.compare_digest(str(expected), str(supplied)):
            fresh = secrets.token_urlsafe(32)
            session[CSRF_SESSION_KEY] = fresh
            if _wants_json():
                return (
                    jsonify(
                        {
                            "ok": False,
                            "message": "Your session expired. Please reload the page.",
                            "code": "csrf",
                            "csrfToken": fresh,
                        }
                    ),
                    400,
                )
            return render_template("error.html", code=400,
                                   message="Your session expired. Please reload the page."), 400
        return None

    @app.context_processor
    def _inject_globals():
        if CSRF_SESSION_KEY not in session:
            session[CSRF_SESSION_KEY] = secrets.token_urlsafe(32)
        return {
            "csrf_token": session[CSRF_SESSION_KEY],
            "policy": POLICY,
            "rules": RULES,
            "user": current_user(),
            "app_name": "Alpha Group of India",
            "tagline": "The Way to Lead",
            "backend_name": app.extensions["auth_backend"].name,
            "year": 2026,
            # Defaults so every template can be rendered from any route.
            "errors": {},
            "error": "",
            "notice": "",
            "form": {},
            "next_url": "/dashboard",
            "page_mode": "",
            "asset_v": app.extensions["asset_version"],
            "supports_signup": app.extensions["auth_backend"].supports_signup,
            "supports_reset": app.extensions["auth_backend"].supports_password_reset,
            "provider_name": "Supabase",
        }

    @app.after_request
    def _security_headers(response):
        response.headers.setdefault("X-Content-Type-Options", "nosniff")
        response.headers.setdefault("Referrer-Policy", "strict-origin-when-cross-origin")
        response.headers.setdefault("X-Permitted-Cross-Domain-Policies", "none")
        # NOTE: frame-ancestors intentionally allows the Arena preview host so
        # the page renders inside the live-preview iframe.
        response.headers.setdefault(
            "Content-Security-Policy",
            "default-src 'self'; "
            "img-src 'self' data:; "
            "style-src 'self'; "
            "script-src 'self'; "
            "font-src 'self'; "
            "connect-src 'self'; "
            "form-action 'self'; "
            "base-uri 'self'; "
            "frame-ancestors 'self' https://*.e2b.app https://*.arena.ai",
        )
        if app.config.get("SESSION_COOKIE_SECURE"):
            response.headers.setdefault(
                "Strict-Transport-Security", "max-age=31536000; includeSubDomains"
            )
        return response

    @app.errorhandler(404)
    def _not_found(_error):
        return render_template("error.html", code=404,
                              message="We could not find that page."), 404

    @app.errorhandler(413)
    def _too_large(_error):
        return render_template("error.html", code=413,
                              message="That request was too large."), 413

    @app.errorhandler(500)
    def _server_error(error):  # pragma: no cover
        app.logger.exception("Unhandled error: %s", error)
        return render_template("error.html", code=500,
                              message="Something went wrong on our side."), 500

    @app.get("/healthz")
    def _health():
        return jsonify(
            {"status": "ok", "backend": app.extensions["auth_backend"].name}
        )

    app.logger.info(
        "Alpha auth backend: %s (base dir: %s)",
        app.extensions["auth_backend"].name,
        BASE_DIR,
    )
    return app


def _asset_version(app: Flask) -> str:
    """Fingerprint of the static assets, used to bust browser caches.

    Every ``?v=...`` query string changes when any static file changes, so a
    redesign can never be masked by a stale CSS/JS copy in the browser.
    """
    latest = 0.0
    for path in (BASE_DIR / "app" / "static").rglob("*"):
        if path.is_file():
            latest = max(latest, path.stat().st_mtime)
    return str(int(latest))


def _wants_json() -> bool:
    if request.headers.get("X-Requested-With") == "fetch":
        return True
    accept = request.headers.get("Accept", "")
    return "application/json" in accept and "text/html" not in accept

"""Authentication routes.

The browser never decides whether credentials or passwords are acceptable:
every check below happens on the server, and every outcome (success, field
error, policy violation, throttle, provider failure) is returned from here.
"""

from __future__ import annotations

import re
import time

from flask import (
    Blueprint,
    current_app,
    jsonify,
    redirect,
    render_template,
    request,
    url_for,
)

from .backends import AuthFailure
from .password_policy import POLICY, RULES, evaluate
from .security import (
    attempt_keys,
    current_auth,
    current_user,
    login_session,
    logout_session,
    refresh_session_if_needed,
    throttle,
)

bp = Blueprint("auth", __name__)

EMAIL_RE = re.compile(r"^[A-Za-z0-9._%+\-]+@[A-Za-z0-9.\-]+\.[A-Za-z]{2,}$")
MAX_EMAIL_LENGTH = 254
MAX_NAME_LENGTH = 80


# ---------------------------------------------------------------------------
# Request / response helpers
# ---------------------------------------------------------------------------
def _is_async() -> bool:
    """True when the page submits the form with fetch()."""
    if request.headers.get("X-Requested-With") == "fetch":
        return True
    accept = request.headers.get("Accept", "")
    return "application/json" in accept and "text/html" not in accept


def _payload() -> dict:
    if request.is_json:
        data = request.get_json(silent=True) or {}
        return {
            key: ("" if value is None else str(value)) for key, value in data.items()
        }
    return {key: value for key, value in request.form.items()}


def _safe_next(default_endpoint: str = "auth.dashboard") -> str:
    """Only allow same-site relative redirects (no open redirect).

    Rejects absolute URLs, protocol-relative URLs (``//evil``), backslash
    tricks (``/\\evil``) and anything carrying a scheme.
    """
    candidate = (request.values.get("next") or "").strip()
    if (
        candidate.startswith("/")
        and not candidate.startswith("//")
        and "\\" not in candidate
        and ":" not in candidate.split("?")[0]
    ):
        return candidate
    return url_for(default_endpoint)


def _error_payload(
    message: str,
    *,
    code: str = "validation",
    errors: dict[str, str] | None = None,
    status: int = 422,
    extra: dict | None = None,
):
    errors = errors or {}
    body = {
        "ok": False,
        "code": code,
        "message": message,
        "errors": errors,
        "field": next(iter(errors)) if errors else None,
    }
    if extra:
        body.update(extra)
    return jsonify(body), status


def _render_error(
    template: str,
    message: str,
    *,
    errors: dict[str, str] | None = None,
    form: dict | None = None,
    status: int = 422,
):
    """Non-JavaScript fallback: re-render the page with server-side errors."""
    return (
        render_template(
            template,
            error=message,
            errors=errors or {},
            form=form or {},
            next_url=_safe_next(),
        ),
        status,
    )


def _form_snapshot(data: dict) -> dict:
    return {
        "email": (data.get("email") or "").strip(),
        "name": (data.get("name") or "").strip(),
        "remember": str(data.get("remember", "")).lower() in {"1", "true", "on", "yes"},
    }


def _backend():
    return current_app.extensions["auth_backend"]


def _view_context() -> dict:
    backend = _backend()
    return {
        "supports_signup": backend.supports_signup,
        "supports_reset": backend.supports_password_reset,
        "provider_name": "Supabase",
    }


# ---------------------------------------------------------------------------
# Pages
# ---------------------------------------------------------------------------
@bp.get("/")
def index():
    return redirect(url_for("auth.login"))


@bp.route("/login", methods=["GET", "POST"])
def login():
    if request.method == "GET":
        if current_user():
            return redirect(url_for("auth.dashboard"))
        return render_template(
            "login.html",
            form={"email": request.args.get("email", ""), "remember": False},
            next_url=_safe_next(),
            notice=request.args.get("notice", ""),
            page_mode="login",
            **_view_context(),
        )

    return _handle_login()


@bp.route("/signup", methods=["GET", "POST"])
def signup():
    if not _backend().supports_signup:
        return redirect(url_for("auth.login"))

    if request.method == "GET":
        if current_user():
            return redirect(url_for("auth.dashboard"))
        return render_template(
            "signup.html",
            form={"name": "", "email": "", "remember": True},
            next_url=url_for("auth.dashboard"),
            page_mode="signup",
            **_view_context(),
        )

    return _handle_signup()


# ---------------------------------------------------------------------------
# Sign in
# ---------------------------------------------------------------------------
def _handle_login():
    data = _payload()
    email = (data.get("email") or "").strip()
    password = data.get("password") or ""
    remember = str(data.get("remember", "")).lower() in {"1", "true", "on", "yes"}
    next_url = _safe_next()

    # --- 1. Shape of the submission (presence + e-mail format) -------------
    errors: dict[str, str] = {}
    if not email:
        errors["email"] = "Enter your e-mail address."
    elif len(email) > MAX_EMAIL_LENGTH or not EMAIL_RE.match(email):
        errors["email"] = "Enter a valid e-mail address, e.g. name@company.com."
    if not password:
        errors["password"] = "Enter your password."

    if errors:
        message = next(iter(errors.values()))
        if _is_async():
            return _error_payload(message, errors=errors)
        return _render_error(
            "login.html", message, errors=errors, form=_form_snapshot(data)
        )

    # --- 2. Brute-force throttle ------------------------------------------
    keys = attempt_keys(email)
    wait = throttle().retry_after(keys)
    if wait:
        message = f"Too many failed attempts. Please try again in {wait} seconds."
        if _is_async():
            return _error_payload(
                message, code="rate_limited", status=429, extra={"retryAfter": wait}
            )
        return _render_error(
            "login.html",
            message,
            errors={"form": message},
            form=_form_snapshot(data),
            status=429,
        )

    # --- 3. Credentials are checked by the backend, nowhere else ----------
    try:
        outcome = _backend().sign_in(email, password)
    except AuthFailure as failure:
        remaining = throttle().register_failure(keys)
        message = failure.message
        if failure.code == "invalid_credentials" and remaining <= 2:
            message = (
                f"{message} {remaining} attempt(s) left before this account is "
                "temporarily locked."
            )
        if _is_async():
            return _error_payload(
                message,
                code=failure.code,
                errors={failure.field: message} if failure.field else {"form": message},
                status=failure.status,
                extra={"attemptsRemaining": remaining},
            )
        return _render_error(
            "login.html",
            message,
            errors={failure.field: message} if failure.field else {"form": message},
            form=_form_snapshot(data),
            status=failure.status,
        )

    if outcome.needs_confirmation:
        if _is_async():
            return jsonify(
                {
                    "ok": True,
                    "code": "confirm_email",
                    "message": outcome.message,
                    "redirect": url_for("auth.login", notice=outcome.message),
                }
            )
        return redirect(url_for("auth.login", notice=outcome.message))

    # --- 4. Session -------------------------------------------------------
    throttle().reset(keys)
    assert outcome.session is not None
    login_session(outcome.session.as_dict(), remember=remember)

    if _is_async():
        return jsonify(
            {
                "ok": True,
                "message": "Signed in. Taking you to your dashboard…",
                "redirect": next_url,
            }
        )
    return redirect(next_url)


# ---------------------------------------------------------------------------
# Sign up
# ---------------------------------------------------------------------------
def _handle_signup():
    data = _payload()
    name = (data.get("name") or "").strip()
    email = (data.get("email") or "").strip()
    password = data.get("password") or ""
    confirm = data.get("confirm_password") or ""

    errors: dict[str, str] = {}
    policy_payload: dict = {}
    if len(name) < 2:
        errors["name"] = "Enter your full name (at least 2 characters)."
    elif len(name) > MAX_NAME_LENGTH:
        errors["name"] = f"That name is too long ({MAX_NAME_LENGTH} characters maximum)."

    if not email:
        errors["email"] = "Enter your e-mail address."
    elif len(email) > MAX_EMAIL_LENGTH or not EMAIL_RE.match(email):
        errors["email"] = "Enter a valid e-mail address, e.g. name@company.com."

    if not password:
        errors["password"] = "Enter a password."
    else:
        # The password policy is evaluated here — on the server — and the
        # browser receives the verdict, not the other way round.
        result = evaluate(password, {"email": email, "name": name})
        if not result.valid:
            errors["password"] = result.violations[0]
            policy_payload = {
                "checks": result.checks,
                "violations": result.violations,
                "score": result.score,
            }

    if not confirm:
        errors["confirm_password"] = "Re-enter your password."
    elif confirm != password:
        errors["confirm_password"] = "Passwords do not match."

    if errors:
        message = next(iter(errors.values()))
        if _is_async():
            return _error_payload(
                message,
                errors=errors,
                extra={"policy": policy_payload} if policy_payload else None,
            )
        return _render_error(
            "signup.html", message, errors=errors, form=_form_snapshot(data)
        )

    try:
        outcome = _backend().sign_up(email, password, name=name)
    except AuthFailure as failure:
        if _is_async():
            return _error_payload(
                failure.message,
                code=failure.code,
                errors={failure.field: failure.message}
                if failure.field
                else {"form": failure.message},
                status=failure.status,
            )
        return _render_error(
            "signup.html",
            failure.message,
            errors={failure.field: failure.message}
            if failure.field
            else {"form": failure.message},
            form=_form_snapshot(data),
            status=failure.status,
        )

    if outcome.session is not None:
        login_session(outcome.session.as_dict(), remember=True)
        target = url_for("auth.dashboard")
    else:
        target = url_for("auth.login", notice=outcome.message)

    message = outcome.message or "Welcome to Alpha Group of India."
    if _is_async():
        payload = {"ok": True, "message": message, "redirect": target}
        if outcome.session is None:
            payload["code"] = "confirm_email"
        return jsonify(payload)
    return redirect(target)


# ---------------------------------------------------------------------------
# Sign out / password reset
# ---------------------------------------------------------------------------
@bp.post("/logout")
def logout():
    auth = current_auth()
    try:
        _backend().sign_out(auth)
    finally:
        logout_session()
    if _is_async():
        return jsonify(
            {"ok": True, "message": "Signed out.", "redirect": url_for("auth.login")}
        )
    return redirect(url_for("auth.login", notice="You have been signed out."))


@bp.post("/forgot-password")
def forgot_password():
    data = _payload()
    email = (data.get("email") or "").strip()
    generic = (
        "If an account exists for that e-mail, a password reset link is on its way."
    )

    if not email or not EMAIL_RE.match(email):
        message = "Enter a valid e-mail address."
        if _is_async():
            return _error_payload(message, errors={"email": message})
        return redirect(url_for("auth.login", notice=message))

    try:
        _backend().request_password_reset(email)
    except AuthFailure as failure:
        if _is_async():
            return _error_payload(
                failure.message, code=failure.code, status=failure.status
            )
        return redirect(url_for("auth.login", notice=failure.message))

    if _is_async():
        return jsonify({"ok": True, "message": generic, "code": "reset_sent"})
    return redirect(url_for("auth.login", notice=generic))


# ---------------------------------------------------------------------------
# Protected area
# ---------------------------------------------------------------------------
@bp.get("/dashboard")
def dashboard():
    if not current_user():
        return redirect(url_for("auth.login", next=request.full_path.rstrip("?")))
    if not refresh_session_if_needed(current_app.config["TOKEN_REFRESH_SKEW"]):
        return redirect(
            url_for("auth.login", notice="Your session expired. Please sign in again.")
        )

    auth = current_auth() or {}
    user = auth.get("user") or {}
    metadata = user.get("user_metadata") or {}
    return render_template(
        "dashboard.html",
        page_title="Dashboard",
        account={
            "name": metadata.get("full_name")
            or metadata.get("name")
            or (user.get("email", "").split("@")[0] or "there"),
            "email": user.get("email", ""),
            "id": user.get("id", ""),
            "joined": str(user.get("created_at") or "")[:10],
        },
        session_info={
            "backend": auth.get("backend", _backend().name),
            "expires_at": auth.get("expires_at", 0),
            "logged_in_at": auth.get("logged_in_at", 0),
        },
        policy=POLICY,
        rules=RULES,
    )


@bp.get("/session")
def session_info():
    """JSON endpoint used by the dashboard countdown."""
    auth = current_auth()
    if not auth:
        return jsonify({"ok": False, "code": "unauthenticated"}), 401
    return jsonify(
        {
            "ok": True,
            "email": (auth.get("user") or {}).get("email"),
            "expires_at": auth.get("expires_at", 0),
            "now": time.time(),
            "backend": auth.get("backend"),
        }
    )

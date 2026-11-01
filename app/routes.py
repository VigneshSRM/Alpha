"""Authentication routes: login, signup, logout, password reset, dashboard."""

from __future__ import annotations

import re

from flask import (
    Blueprint,
    current_app,
    jsonify,
    redirect,
    render_template,
    request,
    session,
    url_for,
)

from .backends import AuthFailure
from .backends.demo_backend import DEMO_EMAIL, DEMO_PASSWORD, DEMO_NAME
from .password_policy import POLICY, evaluate
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


# ---------------------------------------------------------------------------
# Request helpers
# ---------------------------------------------------------------------------
def _wants_json() -> bool:
    if request.headers.get("X-Requested-With") == "fetch":
        return True
    accept = request.headers.get("Accept", "")
    return "application/json" in accept and "text/html" not in accept


def _payload() -> dict:
    if request.is_json:
        data = request.get_json(silent=True) or {}
        return {key: ("" if value is None else str(value)) for key, value in data.items()}
    return {key: value for key, value in request.form.items()}


def _error_response(
    message: str,
    *,
    code: str = "invalid",
    field: str | None = None,
    status: int = 400,
    errors: dict | None = None,
    policy_result: dict | None = None,
):
    if _wants_json():
        body = {"ok": False, "message": message, "code": code, "field": field}
        if errors:
            body["errors"] = errors
        if policy_result:
            body["policy"] = policy_result
        return jsonify(body), status

    return (
        render_template(
            "login.html",
            error=message,
            errors=errors or ({field: message} if field else {}),
            form=_form_snapshot(),
            next_url=_safe_next(),
            policy_result=policy_result,
        ),
        status,
    )


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


def _form_snapshot(**extra) -> dict:
    data = _payload() if request.method == "POST" else {}
    snapshot = {
        "email": (data.get("email") or "").strip(),
        "name": (data.get("name") or "").strip(),
        "remember": str(data.get("remember", "")).lower() in {"1", "true", "on", "yes"},
    }
    snapshot.update(extra)
    return snapshot


def _backend():
    return current_app.extensions["auth_backend"]


def _backend_context() -> dict:
    backend = _backend()
    return {
        "demo_mode": backend.name == "demo" and not (
            current_app.config["SUPABASE_URL"] and current_app.config["SUPABASE_ANON_KEY"]
        ),
        "demo_email": DEMO_EMAIL,
        "demo_password": DEMO_PASSWORD,
        "demo_name": DEMO_NAME,
        "supports_reset": backend.supports_password_reset,
    }


# ---------------------------------------------------------------------------
# Login
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
            **_backend_context(),
        )

    data = _payload()
    email = (data.get("email") or "").strip()
    password = data.get("password") or ""
    remember = str(data.get("remember", "")).lower() in {"1", "true", "on", "yes"}
    next_url = _safe_next()

    errors: dict[str, str] = {}
    if not email:
        errors["email"] = "Enter your e-mail address."
    elif len(email) > MAX_EMAIL_LENGTH or not EMAIL_RE.match(email):
        errors["email"] = "Enter a valid e-mail address, e.g. name@company.com."

    # The login form enforces the same complexity policy as signup.
    result = evaluate(password, {"email": email})
    if not password:
        errors["password"] = "Enter your password."
    elif not result.valid:
        errors["password"] = result.violations[0] if result.violations else "Password does not meet the policy."

    if errors:
        first = next(iter(errors.values()))
        return _error_response(
            first,
            code="validation",
            field=next(iter(errors)),
            status=422,
            errors=errors,
            policy_result=result.as_dict(),
        )

    keys = attempt_keys(email)
    wait = throttle().retry_after(keys)
    if wait:
        message = f"Too many failed attempts. Please try again in {wait} seconds."
        if _wants_json():
            return jsonify(
                {"ok": False, "message": message, "code": "rate_limited", "retryAfter": wait}
            ), 429
        return _error_response(message, code="rate_limited", status=429)

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
        if _wants_json():
            return jsonify(
                {
                    "ok": False,
                    "message": message,
                    "code": failure.code,
                    "field": failure.field,
                    "attemptsRemaining": remaining,
                }
            ), failure.status
        return _error_response(
            message, code=failure.code, field=failure.field, status=failure.status
        )

    if outcome.needs_confirmation:
        if _wants_json():
            return jsonify(
                {"ok": True, "code": "confirm_email", "message": outcome.message, "redirect": None}
            )
        return redirect(url_for("auth.login", notice=outcome.message))

    throttle().reset(keys)
    assert outcome.session is not None
    login_session(outcome.session.as_dict(), remember=remember)

    if _wants_json():
        return jsonify(
            {
                "ok": True,
                "message": "Signed in successfully. Redirecting…",
                "redirect": next_url,
            }
        )
    return redirect(next_url)


# ---------------------------------------------------------------------------
# Signup
# ---------------------------------------------------------------------------
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
            **_backend_context(),
        )

    data = _payload()
    name = (data.get("name") or "").strip()
    email = (data.get("email") or "").strip()
    password = data.get("password") or ""
    confirm = data.get("confirm_password") or ""
    remember = True

    errors: dict[str, str] = {}
    if len(name) < 2:
        errors["name"] = "Enter your full name (at least 2 characters)."
    elif len(name) > 80:
        errors["name"] = "That name is too long (80 characters maximum)."
    if not email:
        errors["email"] = "Enter your e-mail address."
    elif not EMAIL_RE.match(email) or len(email) > MAX_EMAIL_LENGTH:
        errors["email"] = "Enter a valid e-mail address, e.g. name@company.com."

    result = evaluate(password, {"email": email, "name": name})
    policy_payload = result.as_dict()
    if not result.valid:
        errors["password"] = result.violations[0] if result.violations else "Password does not meet the policy."
    if not confirm:
        errors["confirm_password"] = "Re-enter your password."
    elif confirm != password:
        errors["confirm_password"] = "Passwords do not match."
        policy_payload["valid"] = False

    if errors:
        if _wants_json():
            return jsonify(
                {
                    "ok": False,
                    "message": next(iter(errors.values())),
                    "code": "validation",
                    "field": next(iter(errors)),
                    "errors": errors,
                    "policy": policy_payload,
                }
            ), 422
        return (
            render_template(
                "signup.html",
                error=next(iter(errors.values())),
                errors=errors,
                form={"name": name, "email": email, "remember": True},
                policy_result=policy_payload,
                **_backend_context(),
            ),
            422,
        )

    try:
        outcome = _backend().sign_up(email, password, name=name)
    except AuthFailure as failure:
        if _wants_json():
            return jsonify(
                {
                    "ok": False,
                    "message": failure.message,
                    "code": failure.code,
                    "field": failure.field,
                }
            ), failure.status
        return (
            render_template(
                "signup.html",
                error=failure.message,
                errors={failure.field: failure.message} if failure.field else {},
                form={"name": name, "email": email, "remember": True},
                **_backend_context(),
            ),
            failure.status,
        )

    if outcome.session is not None:
        login_session(outcome.session.as_dict(), remember=remember)
        target = url_for("auth.dashboard")
        message = outcome.message or "Welcome to Alpha Group of India."
    else:
        target = url_for("auth.login", notice=outcome.message)
        message = outcome.message

    if _wants_json():
        return jsonify(
            {"ok": True, "message": message, "redirect": target if outcome.session else None}
        )
    return redirect(target)


# ---------------------------------------------------------------------------
# Logout / password reset
# ---------------------------------------------------------------------------
@bp.post("/logout")
def logout():
    auth = current_auth()
    try:
        _backend().sign_out(auth)
    finally:
        logout_session()
    if _wants_json():
        return jsonify({"ok": True, "message": "Signed out.", "redirect": url_for("auth.login")})
    return redirect(url_for("auth.login", notice="You have been signed out."))


@bp.post("/forgot-password")
def forgot_password():
    data = _payload()
    email = (data.get("email") or "").strip()
    generic = (
        "If an account exists for that e-mail, a password reset link is on its way."
    )
    if not EMAIL_RE.match(email):
        if _wants_json():
            return jsonify(
                {
                    "ok": False,
                    "message": "Enter a valid e-mail address.",
                    "code": "validation",
                    "field": "email",
                }
            ), 422
        return redirect(url_for("auth.login", notice="Enter a valid e-mail address."))

    try:
        _backend().request_password_reset(email)
    except AuthFailure as failure:
        if _wants_json():
            return jsonify(
                {"ok": False, "message": failure.message, "code": failure.code}
            ), failure.status
        return redirect(url_for("auth.login", notice=failure.message))

    if _wants_json():
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
        return redirect(url_for("auth.login", notice="Your session expired. Please sign in again."))
    auth = current_auth() or {}
    user = auth.get("user") or {}
    metadata = user.get("user_metadata") or {}
    return render_template(
        "dashboard.html",
        page_title="Dashboard",
        account={
            "name": metadata.get("full_name")
            or metadata.get("name")
            or user.get("email", "").split("@")[0],
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
        rules=_rules_for_dashboard(),
    )


def _rules_for_dashboard():
    from .password_policy import RULES

    return RULES


@bp.get("/session")
def session_info():
    """Small JSON endpoint the dashboard uses to keep the countdown honest."""
    auth = current_auth()
    if not auth:
        return jsonify({"ok": False, "code": "unauthenticated"}), 401
    return jsonify(
        {
            "ok": True,
            "email": (auth.get("user") or {}).get("email"),
            "expires_at": auth.get("expires_at", 0),
            "now": __import__("time").time(),
            "backend": auth.get("backend"),
        }
    )


# Keep the module import cheap for tests
POLICY_VERSION = f"{POLICY['minLength']}-{POLICY['minUppercase']}-{POLICY['minLowercase']}-{POLICY['minDigits']}-{POLICY['minSpecial']}"

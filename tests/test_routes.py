"""Route-level tests.

The Supabase client is replaced with an in-memory double, so the real routing,
validation, throttling and session code runs without touching the network.
"""

from __future__ import annotations

import re
import time
import unittest
from unittest import mock

from app import create_app
from app.backends import AuthFailure, AuthOutcome, AuthSession, BaseAuthBackend
from app.config import Config

CSRF_RE = re.compile(r'name="csrf_token" value="([^"]+)"')
VALID_PASSWORD = "Wolf@Runs#Dawn27!"


class FakeBackend(BaseAuthBackend):
    """Records calls and returns scripted outcomes."""

    name = "supabase"          # pretend to be the real provider
    supports_signup = True
    supports_password_reset = True

    def __init__(self, *args, **kwargs):
        self.calls: list[tuple[str, dict]] = []
        self.fail_sign_in = None       # AuthFailure instance or None
        self.fail_sign_up = None
        self.needs_confirmation = False
        self.sign_in_needs_confirmation = False

    # -- helpers ----------------------------------------------------------
    def _session(self, email: str) -> AuthSession:
        return AuthSession(
            access_token="access-token",
            refresh_token="refresh-token",
            expires_at=int(time.time()) + 3600,
            user={
                "id": "00000000-0000-4000-8000-000000000001",
                "email": email,
                "created_at": "2026-01-01T00:00:00Z",
                "user_metadata": {"full_name": "Test Person"},
            },
        )

    # -- interface --------------------------------------------------------
    def sign_in(self, email: str, password: str) -> AuthOutcome:
        self.calls.append(("sign_in", {"email": email, "password": password}))
        if self.fail_sign_in:
            raise self.fail_sign_in
        if self.sign_in_needs_confirmation:
            return AuthOutcome(
                needs_confirmation=True,
                message="Please confirm your e-mail address before signing in.",
            )
        return AuthOutcome(session=self._session(email))

    def sign_up(self, email: str, password: str, name: str = "") -> AuthOutcome:
        self.calls.append(("sign_up", {"email": email, "password": password, "name": name}))
        if self.fail_sign_up:
            raise self.fail_sign_up
        if self.needs_confirmation:
            return AuthOutcome(needs_confirmation=True, message="Confirm your e-mail.")
        return AuthOutcome(session=self._session(email), message="Welcome aboard.")

    def refresh(self, refresh_token: str):
        self.calls.append(("refresh", {"refresh_token": refresh_token}))
        return None

    def sign_out(self, session=None) -> None:
        self.calls.append(("sign_out", {}))

    def request_password_reset(self, email: str) -> None:
        self.calls.append(("request_password_reset", {"email": email}))


class TestConfig(Config):
    TESTING = True
    DEBUG = False
    SECRET_KEY = "test-secret-key"
    SUPABASE_URL = "https://example.supabase.co"
    SUPABASE_ANON_KEY = "test-anon-key"
    LOGIN_MAX_ATTEMPTS = 3
    LOGIN_WINDOW_SECONDS = 60


class AuthRouteTests(unittest.TestCase):
    def setUp(self):
        self.backend = FakeBackend()
        patcher = mock.patch("app.backends.SupabaseAuthBackend", return_value=self.backend)
        self.addCleanup(patcher.stop)
        patcher.start()
        self.app = create_app(TestConfig)
        self.client = self.app.test_client()

    # -- helpers ----------------------------------------------------------
    def csrf(self, path="/login"):
        response = self.client.get(path)
        match = CSRF_RE.search(response.get_data(as_text=True))
        self.assertIsNotNone(match, "CSRF token missing from page")
        return match.group(1)

    def post(self, path, data, token=None):
        data = dict(data)
        data.setdefault("csrf_token", token or self.csrf("/login"))
        return self.client.post(
            path,
            data=data,
            headers={"Accept": "application/json", "X-Requested-With": "fetch"},
        )

    def login(self, email="person@alphagroup.in", password=VALID_PASSWORD, token=None):
        return self.post(
            "/login", {"email": email, "password": password, "remember": "1"}, token=token
        )

    def signup(self, **overrides):
        data = {
            "name": "Test Person",
            "email": "person@alphagroup.in",
            "password": VALID_PASSWORD,
            "confirm_password": VALID_PASSWORD,
        }
        data.update(overrides)
        return self.post("/signup", data)

    # -- pages ------------------------------------------------------------
    def test_login_page_contains_policy_affordances(self):
        response = self.client.get("/login")
        html = response.get_data(as_text=True)
        self.assertEqual(response.status_code, 200)
        self.assertIn("alpha-logo-light.png", html)
        self.assertIn("hero-bg.png", html)
        self.assertIn('id="policyInfoBtn"', html)
        self.assertIn('id="togglePassword"', html)
        self.assertIn("At least 12 characters", html)
        self.assertIn("Sessions by Supabase Auth", html)

    def test_no_demo_shortcuts_are_exposed(self):
        html = self.client.get("/login").get_data(as_text=True)
        for marker in ("Demo mode", "demo-card", "fillDemo", "Use demo credentials", "demo@alphagroup.in"):
            self.assertNotIn(marker, html)

    def test_root_and_signup_render(self):
        self.assertEqual(self.client.get("/").status_code, 302)
        signup = self.client.get("/signup")
        self.assertEqual(signup.status_code, 200)
        self.assertIn('id="authForm"', signup.get_data(as_text=True))

    def test_health_reports_supabase(self):
        body = self.client.get("/healthz").json
        self.assertEqual(body["status"], "ok")
        self.assertEqual(body["backend"], "supabase")

    def test_security_headers_present(self):
        response = self.client.get("/login")
        self.assertEqual(response.headers["X-Content-Type-Options"], "nosniff")
        self.assertIn("frame-ancestors", response.headers["Content-Security-Policy"])

    # -- CSRF -------------------------------------------------------------
    def test_missing_csrf_is_rejected(self):
        response = self.client.post(
            "/login",
            data={"email": "person@alphagroup.in", "password": VALID_PASSWORD},
            headers={"Accept": "application/json"},
        )
        self.assertEqual(response.status_code, 400)
        self.assertEqual(response.json["code"], "csrf")
        self.assertTrue(response.json["csrfToken"])
        self.assertEqual(self.backend.calls, [])

    # -- login ------------------------------------------------------------
    def test_login_success_creates_session(self):
        response = self.login()
        self.assertEqual(response.status_code, 200)
        self.assertTrue(response.json["ok"])
        self.assertEqual(response.json["redirect"], "/dashboard")
        self.assertEqual(self.backend.calls[0][0], "sign_in")

        dashboard = self.client.get("/dashboard")
        self.assertEqual(dashboard.status_code, 200)
        self.assertIn("person@alphagroup.in", dashboard.get_data(as_text=True))

    def test_login_passes_credentials_untouched_to_backend(self):
        weak = "shortpass"
        self.login(password=weak)
        self.assertEqual(self.backend.calls[-1], ("sign_in", {"email": "person@alphagroup.in", "password": weak}))

    def test_login_reports_backend_credential_failure(self):
        self.backend.fail_sign_in = AuthFailure(
            "Invalid e-mail or password. Please try again.",
            code="invalid_credentials",
            status=401,
        )
        response = self.login()
        self.assertEqual(response.status_code, 401)
        self.assertFalse(response.json["ok"])
        self.assertEqual(response.json["code"], "invalid_credentials")
        self.assertIn("attemptsRemaining", response.json)

    def test_login_surfaces_backend_policy_error_on_password_field(self):
        self.backend.fail_sign_in = AuthFailure(
            "Password must contain at least 12 characters.",
            code="weak_password",
            field="password",
            status=422,
        )
        response = self.login(password="abc")
        self.assertEqual(response.status_code, 422)
        self.assertIn("password", response.json["errors"])
        self.assertEqual(response.json["field"], "password")

    def test_login_requires_both_fields(self):
        response = self.post("/login", {"email": "", "password": ""})
        self.assertEqual(response.status_code, 422)
        self.assertEqual(set(response.json["errors"]), {"email", "password"})
        self.assertEqual(self.backend.calls, [], "backend must not be called")

    def test_login_rejects_malformed_email_before_backend(self):
        response = self.login(email="not-an-email")
        self.assertEqual(response.status_code, 422)
        self.assertEqual(response.json["field"], "email")
        self.assertEqual(self.backend.calls, [])

    def test_login_rate_limit(self):
        self.backend.fail_sign_in = AuthFailure("Invalid e-mail or password.", status=401)
        token = self.csrf()
        statuses = [self.login(token=token).status_code for _ in range(TestConfig.LOGIN_MAX_ATTEMPTS + 1)]
        self.assertEqual(statuses[-1], 429)
        self.assertEqual(
            len([call for call in self.backend.calls if call[0] == "sign_in"]),
            TestConfig.LOGIN_MAX_ATTEMPTS,
        )

    def test_login_confirmation_flow(self):
        self.backend.sign_in_needs_confirmation = True
        response = self.login()
        self.assertTrue(response.json["ok"])
        self.assertEqual(response.json["code"], "confirm_email")

    def test_login_blocks_open_redirect(self):
        response = self.post(
            "/login",
            {
                "email": "person@alphagroup.in",
                "password": VALID_PASSWORD,
                "next": "https://evil.example.com",
            },
        )
        self.assertTrue(response.json["ok"])
        self.assertEqual(response.json["redirect"], "/dashboard")

    def test_logout_clears_session(self):
        self.login()
        self.assertEqual(self.client.get("/dashboard").status_code, 200)
        token = self.csrf("/dashboard")
        self.client.post("/logout", data={"csrf_token": token})
        self.assertEqual(self.client.get("/dashboard").status_code, 302)
        self.assertIn("sign_out", [call[0] for call in self.backend.calls])

    def test_signup_confirmation_redirects_to_login(self):
        self.backend.needs_confirmation = True
        response = self.signup(email="fresh@alphagroup.in")
        self.assertTrue(response.json["ok"])
        self.assertEqual(response.json["code"], "confirm_email")
        self.assertIn("/login", response.json["redirect"])
        self.assertEqual(self.client.get("/dashboard").status_code, 302)

    def test_dashboard_requires_login(self):
        response = self.client.get("/dashboard")
        self.assertEqual(response.status_code, 302)
        self.assertIn("/login", response.headers["Location"])

    # -- signup -----------------------------------------------------------
    def test_signup_policy_violation_is_decided_by_server(self):
        response = self.signup(password="weakpass", confirm_password="weakpass")
        self.assertEqual(response.status_code, 422)
        self.assertIn("password", response.json["errors"])
        self.assertIn("12 characters", response.json["errors"]["password"])
        self.assertIn("checks", response.json["policy"])
        self.assertEqual(self.backend.calls, [], "no account must be created")

    def test_signup_enforces_two_numbers_and_two_symbols(self):
        too_few = self.signup(password="WolfRunsDawn7!", confirm_password="WolfRunsDawn7!")
        self.assertIn("numbers", too_few.json["errors"]["password"])
        no_symbol = self.signup(password="WolfRunsDawn27", confirm_password="WolfRunsDawn27")
        self.assertIn("special characters", no_symbol.json["errors"]["password"])
        self.assertEqual(self.backend.calls, [])

    def test_signup_rejects_personal_data_in_password(self):
        response = self.signup(
            email="ravi.kumar@alphagroup.in",
            password="Ravi@Kumar#2027",
            confirm_password="Ravi@Kumar#2027",
        )
        self.assertEqual(response.status_code, 422)
        self.assertIn("name or e-mail", response.json["errors"]["password"])

    def test_signup_rejects_mismatched_confirmation(self):
        response = self.signup(confirm_password="Wolf@Runs#Dawn28!")
        self.assertEqual(response.status_code, 422)
        self.assertEqual(response.json["field"], "confirm_password")
        self.assertEqual(self.backend.calls, [])

    def test_signup_requires_name_and_email(self):
        response = self.signup(name="", email="bad")
        self.assertEqual(set(response.json["errors"]), {"name", "email"})
        self.assertEqual(self.backend.calls, [])

    def test_signup_success(self):
        response = self.signup()
        self.assertEqual(response.status_code, 200)
        self.assertTrue(response.json["ok"])
        self.assertEqual(response.json["redirect"], "/dashboard")
        self.assertEqual(self.backend.calls[0][0], "sign_up")
        self.assertEqual(self.backend.calls[0][1]["name"], "Test Person")

    def test_signup_duplicate_from_provider(self):
        self.backend.fail_sign_up = AuthFailure(
            "An account with this e-mail already exists. Try signing in.",
            code="user_exists",
            field="email",
            status=409,
        )
        response = self.signup()
        self.assertEqual(response.status_code, 409)
        self.assertEqual(response.json["code"], "user_exists")
        self.assertIn("email", response.json["errors"])

    # -- password reset ---------------------------------------------------
    def test_forgot_password_is_generic(self):
        response = self.post("/forgot-password", {"email": "someone@alphagroup.in"})
        self.assertEqual(response.status_code, 200)
        self.assertIn("If an account exists", response.json["message"])
        self.assertEqual(self.backend.calls[0][0], "request_password_reset")

    def test_forgot_password_rejects_bad_email(self):
        response = self.post("/forgot-password", {"email": "nope"})
        self.assertEqual(response.status_code, 422)
        self.assertEqual(response.json["field"], "email")
        self.assertEqual(self.backend.calls, [])

    # -- non-JavaScript fallback ------------------------------------------
    def test_server_renders_errors_without_javascript(self):
        token = self.csrf()
        response = self.client.post(
            "/login",
            data={"email": "person@alphagroup.in", "password": "", "csrf_token": token},
        )
        self.assertEqual(response.status_code, 422)
        html = response.get_data(as_text=True)
        self.assertIn("Enter your password.", html)


class ConfigurationTests(unittest.TestCase):
    def test_missing_supabase_keys_refuse_to_start(self):
        class NoKeys(TestConfig):
            SUPABASE_URL = ""
            SUPABASE_ANON_KEY = ""

        with self.assertRaises(RuntimeError):
            create_app(NoKeys)


if __name__ == "__main__":
    unittest.main()

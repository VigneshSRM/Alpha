"""Route-level tests running against the demo backend (no network needed)."""

from __future__ import annotations

import json
import re
import tempfile
import unittest
from pathlib import Path

from app import create_app
from app.backends.demo_backend import DEMO_EMAIL, DEMO_PASSWORD
from app.config import Config

CSRF_RE = re.compile(r'name="csrf_token" value="([^"]+)"')


class TestConfig(Config):
    TESTING = True
    DEBUG = False
    SECRET_KEY = "test-secret-key"
    FORCE_DEMO = True
    LOGIN_MAX_ATTEMPTS = 3
    LOGIN_WINDOW_SECONDS = 60


class AuthRouteTests(unittest.TestCase):
    def setUp(self):
        self.tmp = tempfile.TemporaryDirectory()
        TestConfig.DEMO_STORE = Path(self.tmp.name) / "demo_users.json"
        self.app = create_app(TestConfig)
        self.client = self.app.test_client()

    def tearDown(self):
        self.tmp.cleanup()

    # -- helpers ----------------------------------------------------------
    def csrf(self, path="/login"):
        response = self.client.get(path)
        match = CSRF_RE.search(response.get_data(as_text=True))
        self.assertIsNotNone(match, "CSRF token missing from page")
        return match.group(1)

    def login(self, email=DEMO_EMAIL, password=DEMO_PASSWORD, token=None):
        token = token or self.csrf()
        return self.client.post(
            "/login",
            data={"email": email, "password": password, "csrf_token": token},
            headers={"Accept": "application/json", "X-Requested-With": "fetch"},
        )

    # -- page rendering ---------------------------------------------------
    def test_login_page_renders_policy(self):
        response = self.client.get("/login")
        html = response.get_data(as_text=True)
        self.assertEqual(response.status_code, 200)
        self.assertIn("Password policy", html)
        self.assertIn("12 characters minimum", html)
        self.assertIn("alpha-logo.png", html)
        self.assertIn('id="policyInfoBtn"', html)
        self.assertIn('id="togglePassword"', html)

    def test_root_redirects_to_login(self):
        response = self.client.get("/")
        self.assertEqual(response.status_code, 302)
        self.assertIn("/login", response.headers["Location"])

    def test_health(self):
        response = self.client.get("/healthz")
        self.assertEqual(response.json["status"], "ok")
        self.assertEqual(response.json["backend"], "demo")

    def test_security_headers_present(self):
        response = self.client.get("/login")
        self.assertEqual(response.headers["X-Content-Type-Options"], "nosniff")
        self.assertIn("frame-ancestors", response.headers["Content-Security-Policy"])

    # -- CSRF -------------------------------------------------------------
    def test_missing_csrf_is_rejected(self):
        response = self.client.post(
            "/login",
            data={"email": DEMO_EMAIL, "password": DEMO_PASSWORD},
            headers={"Accept": "application/json"},
        )
        self.assertEqual(response.status_code, 400)
        self.assertEqual(response.json["code"], "csrf")

    # -- login ------------------------------------------------------------
    def test_login_success_creates_session_and_dashboard(self):
        response = self.login()
        self.assertEqual(response.status_code, 200)
        self.assertTrue(response.json["ok"])
        dashboard = self.client.get("/dashboard")
        self.assertEqual(dashboard.status_code, 200)
        self.assertIn(DEMO_EMAIL, dashboard.get_data(as_text=True))

    def test_login_wrong_password(self):
        response = self.login(password="Wrong@Pass12!")
        self.assertEqual(response.status_code, 401)
        self.assertFalse(response.json["ok"])
        self.assertEqual(response.json["code"], "invalid_credentials")
        self.assertIn("attemptsRemaining", response.json)

    def test_login_rejects_password_below_policy(self):
        response = self.login(password="short")
        self.assertEqual(response.status_code, 422)
        self.assertIn("12 characters", response.json["message"])

    def test_login_rejects_bad_email(self):
        response = self.login(email="not-an-email")
        self.assertEqual(response.status_code, 422)
        self.assertEqual(response.json["field"], "email")

    def test_rate_limit_after_repeated_failures(self):
        token = self.csrf()
        statuses = []
        for _ in range(TestConfig.LOGIN_MAX_ATTEMPTS + 1):
            statuses.append(self.login(password="Wrong@Pass12!", token=token).status_code)
        self.assertIn(429, statuses)
        self.assertEqual(statuses[-1], 429)

    def test_dashboard_requires_login(self):
        response = self.client.get("/dashboard")
        self.assertEqual(response.status_code, 302)
        self.assertIn("/login", response.headers["Location"])

    def test_dashboard_does_not_allow_open_redirect(self):
        token = self.csrf()
        response = self.client.post(
            "/login",
            data={
                "email": DEMO_EMAIL,
                "password": DEMO_PASSWORD,
                "csrf_token": token,
                "next": "https://evil.example.com",
            },
            headers={"Accept": "application/json", "X-Requested-With": "fetch"},
        )
        self.assertTrue(response.json["ok"])
        self.assertEqual(response.json["redirect"], "/dashboard")

    def test_logout_clears_session(self):
        self.login()
        self.assertEqual(self.client.get("/dashboard").status_code, 200)
        token = self.csrf("/dashboard")
        self.client.post("/logout", data={"csrf_token": token})
        self.assertEqual(self.client.get("/dashboard").status_code, 302)

    # -- signup -----------------------------------------------------------
    def test_signup_rejects_weak_password(self):
        token = self.csrf("/signup")
        response = self.client.post(
            "/signup",
            data={
                "name": "New Person",
                "email": "new@alphagroup.in",
                "password": "weakpass",
                "confirm_password": "weakpass",
                "csrf_token": token,
            },
            headers={"Accept": "application/json", "X-Requested-With": "fetch"},
        )
        self.assertEqual(response.status_code, 422)
        self.assertIn("password", response.json["errors"])

    def test_signup_rejects_mismatched_confirmation(self):
        token = self.csrf("/signup")
        response = self.client.post(
            "/signup",
            data={
                "name": "New Person",
                "email": "new@alphagroup.in",
                "password": "Wolf@Runs#Dawn27",
                "confirm_password": "Wolf@Runs#Dawn28",
                "csrf_token": token,
            },
            headers={"Accept": "application/json", "X-Requested-With": "fetch"},
        )
        self.assertEqual(response.status_code, 422)
        self.assertIn("confirm_password", response.json["errors"])

    def test_signup_success(self):
        token = self.csrf("/signup")
        response = self.client.post(
            "/signup",
            data={
                "name": "New Person",
                "email": "new@alphagroup.in",
                "password": "Wolf@Runs#Dawn27",
                "confirm_password": "Wolf@Runs#Dawn27",
                "csrf_token": token,
            },
            headers={"Accept": "application/json", "X-Requested-With": "fetch"},
        )
        self.assertEqual(response.status_code, 200)
        self.assertTrue(response.json["ok"])
        self.assertEqual(response.json["redirect"], "/dashboard")

    # -- password reset ---------------------------------------------------
    def test_forgot_password_is_generic(self):
        token = self.csrf()
        response = self.client.post(
            "/forgot-password",
            data={"email": "someone@alphagroup.in", "csrf_token": token},
            headers={"Accept": "application/json", "X-Requested-With": "fetch"},
        )
        self.assertEqual(response.status_code, 200)
        self.assertIn("If an account exists", response.json["message"])


if __name__ == "__main__":
    unittest.main()

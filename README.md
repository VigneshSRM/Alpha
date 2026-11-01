# Alpha Group of India — Secure Sign-in Portal

A full-bleed, photograph-led sign-in experience for **Alpha Group of India**, built with
**Python · Flask · Supabase**. One dark hero scene, one form column, no card-inside-a-card, no demo
account: every credential and every password decision is made by the backend.

> **The Way to Lead**

---

## 1. What is included

| Requirement | Where it lives |
| --- | --- |
| Minimum 12-character password with complexity checks | `app/password_policy.py` — evaluated **server side** on sign-up |
| Password **visibility** (eye) icon and **information** (policy) icon | `login.html` / `signup.html` → `#togglePassword`, `#policyInfoBtn` |
| Validation: ≥1 uppercase, ≥1 lowercase, ≥2 numbers, ≥2 special characters | `POLICY` constant in `app/password_policy.py`, enforced by Flask and again by Supabase Auth |
| Brand colours `#E6E6FA`, `#FF4500`, `#FFD700`, `#F4F4F4` | `app/static/css/app.css` design tokens |
| Backend-only validation, no demo/shortcut path | `app/routes.py` + `app/backends/supabase_backend.py` |
| Flask + Supabase stack | `app/` (Flask), Supabase Auth for credentials and sessions |

Screens: **sign in**, **create account**, password-reset dialog, password-policy dialog, error pages
and a protected **dashboard**. The layout is a single scene — the wolf photograph *is* the page
background — with the form floating directly on it. No nested boxes, no marketing cards.

---

## 2. What changed in this iteration

* **Full-bleed hero design.** `app/static/img/hero-bg.png` is a purpose-made 1997 px-wide scene: dark
  left two-thirds for typography, howling wolf on the right, ember/gold rim light. The form is a
  translucent panel placed on the image — there is no card wrapper, no split panel, no stacked boxes.
* **Dark-safe logo.** The supplied artwork is drawn for white paper; its `ALPHA GROUP OF` wordmark is
  deep purple. `scripts/make_logo_light.py` generates `alpha-logo-light.png` by recolouring only those
  purple pixels to brand lavender, leaving the gold crown, ember `INDIA` and the wolf untouched.
* **Demo mode removed.** There is no local credential vault, no seeded account and no browser-side
  password validator any more. If Supabase is not configured the app **refuses to start** rather than
  validating anything locally.
* **Validation is the backend's job.** `app/static/js/auth.js` only handles UX (visibility, caps-lock
  hint, dialogs, spinner) and renders the verdict it receives. The removed
  `password-policy.js` client validator is gone; the policy dialog is rendered from the server's
  `POLICY` constant.

---

## 3. Run it

```bash
python3 -m venv .venv
source .venv/bin/activate
pip install -r requirements.txt

cp .env.example .env      # then set SUPABASE_URL + SUPABASE_ANON_KEY + SECRET_KEY
python run.py             # → http://localhost:5000
```

`GET /healthz` returns the active backend, e.g. `{"status": "ok", "backend": "supabase"}`.
Without Supabase keys the process exits with a clear message:

```
RuntimeError: Supabase is not configured: set SUPABASE_URL and SUPABASE_ANON_KEY
```

---

## 4. The logic behind the login page

### 4.1 Request flow

```
Browser                                Flask                              Supabase
   │  GET /login ───────────────────────▶ │
   │ ◀── page + CSRF token + policy ────── │  (HttpOnly session cookie)
   │                                      │
   │  POST /login  (fetch, JSON) ───────▶ │
   │    email, password, csrf             │ 1. CSRF  (before_request, compare_digest)
   │                                      │ 2. required fields + e-mail shape
   │                                      │ 3. throttle: per e-mail AND per IP
   │                                      │ 4. backend.sign_in ─────────────▶ /auth/v1/token
   │                                      │ ◀──────────────────────── session ─┘
   │                                      │ 5. tokens → server-side session
   │ ◀── { ok, redirect } ──────────────── │ 6. clear failed-attempt counters
   │  navigate to /dashboard              │
```

The browser sends what the user typed and paints what comes back. It never decides whether a password
is acceptable, whether an account exists, or whether a session is valid.

### 4.2 Server-side checks, in order

1. **CSRF** — every mutating request must carry the per-session token (`X-CSRF-Token` or
   `csrf_token`). On failure a fresh token is returned so a retry just works → `400`.
2. **Presence + e-mail shape** — both fields required, e-mail ≤ 254 chars and pattern-checked → `422`
   with per-field errors. The backend is *not* called.
3. **Throttle** — `LOGIN_MAX_ATTEMPTS` (default 5) failures inside `LOGIN_WINDOW_SECONDS`
   (default 900) per e-mail **and** per IP → `429` with a retry hint.
4. **Credentials** — `SupabaseAuthBackend.sign_in()` → `supabase.auth.sign_in_with_password`. Provider
   errors are mapped to friendly messages; credential failures stay generic so the page never reveals
   which addresses are registered. Supabase-side password errors are surfaced on the `password` field.
5. **Session** — access token, refresh token, expiry and a user snapshot go into the Flask session
   (HttpOnly, SameSite=Lax). "Keep me signed in" makes it a 7-day permanent session.
6. **Redirect** — only same-site relative paths are honoured (`next=/dashboard`); absolute,
   protocol-relative and backslash tricks are rejected.

Sign-up repeats 1–3 with the same throttle keys, then:

* evaluates the **password policy on the server** (`evaluate(password, {email, name})`) and returns
  per-field verdicts plus the failed `checks` map;
* calls `SupabaseAuthBackend.sign_up()`, mapping `user_already_exists` / `weak_password` /
  `email_not_confirmed` to readable messages;
* creates the session immediately, or — when the project has e-mail confirmation enabled — redirects
  to `/login` with a notice.

### 4.3 Password policy

| Rule | Value |
| --- | --- |
| Minimum length | **12** characters |
| Maximum length | 128 characters |
| Uppercase | ≥ 1 (`A–Z`) |
| Lowercase | ≥ 1 (`a–z`) |
| Numbers | ≥ 2 (`0–9`) |
| Special characters | ≥ 2 from `! " # $ % & ' ( ) * + , - . / : ; < = > ? @ [ \ ] ^ _ ` { \| } ~` |
| Complexity gate | rejects dictionary words (`password`, `welcome`, `admin`, …), keyboard/alphabet sequences (`abcd`, `1234`, `qwerty`), 3+ repeated characters (`aaa`) and anything containing the user's own name, e-mail local-part or company name |

The same rules are applied when a password is **submitted on the sign-in form**, so a password that
cannot possibly be valid never reaches the provider. `supabase/schema.sql` mirrors the length and
character-class rules inside Supabase Auth as a third layer.

The **Password policy** dialog is generated from the server's `POLICY`/`RULES` constants, so what the
page explains can never drift from what the server enforces. Its "Suggest a strong password" button
generates a value with `crypto.getRandomValues` (`app/static/js/password-generator.js`) — generation
only; the server still validates it on submit.

### 4.4 Session lifecycle

* Tokens refresh silently via the refresh token when fewer than `TOKEN_REFRESH_SKEW` seconds remain
  (`/dashboard` calls `refresh_session_if_needed`).
* Revoked or expired refresh tokens destroy the session and bounce to `/login` with a notice.
* `POST /logout` calls Supabase `sign_out` and then clears the Flask session.

---

## 5. Supabase setup

1. Create the project at <https://supabase.com> and copy *Project Settings → API* values.
2. **Authentication → Providers → Email**: enable the provider; for local testing either disable
   *Confirm email* or leave it on (the app handles both paths).
3. Apply the server-side policy and `profiles` table:

   ```bash
   # Supabase dashboard → SQL → New query → paste the contents of supabase/schema.sql
   ```

   It sets `minimum_password_length = 12` with all character classes, creates a `profiles` table with
   row-level security, and adds a trigger that fills the profile after signup. If `auth.config` is not
   writable on your plan, set the same values under *Authentication → Providers → Email*.
4. Copy `.env.example` → `.env` and fill in `SECRET_KEY`, `SUPABASE_URL`, `SUPABASE_ANON_KEY`,
   `SUPABASE_RESET_REDIRECT_URL`.
5. Restart. Only the **anon** key is used; the service-role key never reaches the app.

---

## 6. Project structure

```
Alpha/
├── run.py                          # dev entry point (0.0.0.0:5000)
├── requirements.txt
├── .env.example
├── scripts/
│   └── make_logo_light.py          # generates the dark-background logo variant
├── app/
│   ├── __init__.py                 # app factory, CSRF, CSP, security headers, error pages
│   ├── config.py                   # env-driven configuration
│   ├── routes.py                   # /login /signup /logout /forgot-password /dashboard
│   ├── security.py                 # session helpers + brute-force throttle
│   ├── password_policy.py          # THE policy (server side)
│   ├── backends/
│   │   ├── __init__.py             # backend factory + shared types
│   │   └── supabase_backend.py     # Supabase Auth (only backend)
│   ├── templates/                  # base, login, signup, dashboard, error
│   └── static/
│       ├── css/app.css             # design tokens + full-bleed scene layout
│       ├── js/auth.js              # UX only: visibility, dialogs, async submit, error paint
│       ├── js/password-generator.js# "Suggest a strong password" (generation only)
│       ├── js/dashboard.js         # session countdown
│       └── img/                    # alpha-logo.png, alpha-logo-light.png, hero-bg.png
├── supabase/schema.sql
└── tests/                          # 43 unit + route tests (no network)
```

### Routes

| Method | Path | Purpose |
| --- | --- | --- |
| GET | `/` | redirect → `/login` |
| GET/POST | `/login` | sign-in page / sign-in endpoint (JSON when fetched, HTML otherwise) |
| GET/POST | `/signup` | account creation |
| POST | `/logout` | Supabase sign-out + session clear |
| POST | `/forgot-password` | sends a reset e-mail (always a generic response) |
| GET | `/dashboard` | protected landing page |
| GET | `/session` | JSON session info |
| GET | `/healthz` | backend/health probe |

---

## 7. Security notes

Implemented:

* CSRF token on every mutating request, verified with `hmac.compare_digest`.
* HttpOnly + SameSite=Lax session cookie (`SESSION_COOKIE_SECURE=1` behind HTTPS).
* Content-Security-Policy with no inline scripts/styles, plus `nosniff`, referrer policy,
  `X-Permitted-Cross-Domain-Policies`.
* Server-side validation of every field; nothing security-relevant is trusted from the browser.
* Uniform error messages for unknown e-mail vs. wrong password; generic reset responses.
* Sliding-window brute-force throttling per e-mail and per IP.
* `next` redirect whitelist (relative paths only).
* `noindex, nofollow` on the portal.
* No secrets in the repository — everything comes from the environment, and the app will not start
  without a real backend configured.

Before going live:

1. `SECRET_KEY` from a secret manager; `SESSION_COOKIE_SECURE=1`; TLS terminated at the proxy.
2. Run behind gunicorn: `gunicorn "app:create_app()" --bind 0.0.0.0:8000 --workers 3`.
3. Move the throttle to Redis so limits survive restarts and multiple workers.
4. Enable Supabase *leaked password protection* (HaveIBeenPwned) and MFA for admin accounts.
5. Add Flask-Limiter or a WAF in front of `/login` for IP-level protection.
6. Compress `hero-bg.png` / logo PNGs, or serve WebP, if bandwidth matters.

---

## 8. Tests

```bash
python -m unittest discover -s tests -v
```

* `tests/test_password_policy.py` — 14 cases covering each rule (length, upper, lower, digits,
  specials, dictionary words, sequences, repeats, personal data, max length, scoring).
* `tests/test_routes.py` — 29 cases. The Supabase client is replaced by an in-memory double, so the
  real routing, CSRF, throttle, policy, session and redirect code runs with no network. It asserts,
  among other things, that no demo affordances exist in the markup, that the backend is never called
  for malformed input, and that no account is created when the server rejects a password.

---

## 9. Troubleshooting

| Symptom | Fix |
| --- | --- |
| `RuntimeError: Supabase is not configured` | Set `SUPABASE_URL` + `SUPABASE_ANON_KEY` in `.env`. |
| `Invalid API key` in logs | `SUPABASE_ANON_KEY` is wrong or from another project. |
| Sign-up succeeds but no session | *Confirm email* is enabled — confirm the link, then sign in. |
| Login works locally but not behind a proxy | Set `SESSION_COOKIE_SECURE=1` and `SUPABASE_RESET_REDIRECT_URL` to the public URL. |
| Logo looks washed out | Regenerate with `python scripts/make_logo_light.py` after replacing `alpha-logo.png`. |

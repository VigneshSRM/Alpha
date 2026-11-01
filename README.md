# Alpha Group of India — Secure Sign-in Portal

A production-minded login experience for **Alpha Group of India** built with **Python · Flask · Supabase**,
styled with the group's brand palette and logo.

> **The Way to Lead**

![palette](https://img.shields.io/badge/palette-%23E6E6FA%20%C2%B7%20%23FF4500%20%C2%B7%20%23FFD700%20%C2%B7%20%23F4F4F4-2b1055)

---

## 1. What is included

| Requirement | Where it lives |
| --- | --- |
| Minimum 12-character password with complexity checks | `app/password_policy.py` (server) + `app/static/js/password-policy.js` (browser) |
| Password **visibility** (eye) icon and **information** (policy) icon | `login.html` / `signup.html` → `#togglePassword`, `#policyInfoBtn` |
| Validation: ≥1 uppercase, ≥1 lowercase, ≥2 numbers, ≥2 special characters | `POLICY` constant, enforced twice (client + server) |
| Brand colours `#E6E6FA`, `#FF4500`, `#FFD700`, `#F4F4F4` | `app/static/css/app.css` design tokens |
| Flask + Supabase stack | `app/` (Flask), `app/backends/supabase_backend.py` (Supabase Auth) |

Screens: **login**, **signup**, password-reset dialog, password-policy dialog, and a protected
**dashboard** to prove the session works. Fully responsive, keyboard accessible, and no external
fonts/scripts (so it works behind a strict CSP and offline networks).

---

## 2. Try it in 30 seconds

```bash
python3 -m venv .venv
source .venv/bin/activate
pip install -r requirements.txt
python run.py
# → http://localhost:5000
```

No environment variables are required to look around: if Supabase keys are missing the app starts in
**demo mode** and shows a dismissible "Demo mode" card on the login screen with ready-to-use credentials:

```
E-mail    demo@alphagroup.in
Password  Alpha@Wolf#2026!
```

You can also create new accounts in demo mode — they are stored as PBKDF2 hashes in
`.data/demo_users.json` (git-ignored). Demo sessions live in process memory.

---

## 3. The logic behind the login page

### 3.1 Request flow

```
Browser                          Flask                                  Supabase
   │  GET /login ─────────────────▶ │
   │ ◀── page + CSRF token + policy │  (session cookie, token in session)
   │                                │
   │  type password                 │
   │  ├─ JS policy engine runs locally: 6 rules, strength meter, eye/info icons
   │  └─ submit blocked until every rule passes
   │                                │
   │  POST /login (fetch, JSON) ───▶ │
   │      email, password, csrf      │  1. CSRF check          (before_request)
   │                                 │  2. field validation    (e-mail + policy)
   │                                 │  3. throttle check      (per e-mail + per IP)
   │                                 │  4. auth_backend.sign_in ─────────▶ auth/v1/token
   │                                 │ ◀────────────────────────────── session
   │                                 │  5. store tokens in signed HttpOnly session
   │ ◀── {ok, redirect} ───────────── │  6. clear failed-attempt counters
   │  navigate to /dashboard         │
```

Nothing about the password policy trusts the browser: the same six rules run again in
`app/password_policy.py` before any call to Supabase, and Supabase Auth applies its own
minimum-length/character-class policy as a third layer (`supabase/schema.sql`).

### 3.2 Validation order (server side, `POST /login`)

1. **CSRF** — every non-GET request must carry the per-session token (`X-CSRF-Token` header or
   `csrf_token` form field). Mismatch → `400`.
2. **E-mail** — length ≤ 254 and RFC-ish pattern; otherwise `422` with a field error.
3. **Password policy** — empty → "Enter your password"; otherwise the full policy is evaluated
   (length, uppercase, lowercase, digits, specials, complexity).
4. **Throttle** — `LOGIN_MAX_ATTEMPTS` (default 5) failures inside `LOGIN_WINDOW_SECONDS`
   (default 900) per e-mail **and** per IP → `429` with a retry hint.
5. **Supabase sign-in** — errors are mapped to friendly messages; credential errors stay generic
   ("Invalid e-mail or password") so the page never reveals which accounts exist.
6. **Session** — access token, refresh token, expiry and a small user snapshot are stored in the
   Flask session (HttpOnly, SameSite=Lax). "Keep me signed in" switches to a 7-day permanent session.
7. **Redirect** — only same-site relative paths are honoured (`next=/dashboard`), preventing open
   redirects.

### 3.3 Password policy details

| Rule | Value |
| --- | --- |
| Minimum length | **12** characters |
| Maximum length | 128 characters |
| Uppercase | ≥ 1 (`A–Z`) |
| Lowercase | ≥ 1 (`a–z`) |
| Numbers | ≥ 2 (`0–9`) |
| Special characters | ≥ 2 from `! " # $ % & ' ( ) * + , - . / : ; < = > ? @ [ \ ] ^ _ ` { \| } ~` |
| Complexity gate | rejects common dictionary words (`password`, `welcome`, `admin`, …), keyboard/alphabet sequences (`abcd`, `1234`, `qwerty`), 3+ repeated characters (`aaa`), and anything containing the user's name, e-mail local-part or company name |

The strength meter (0–100) is an *indicator* only; the six rules are the gate. The browser dialog
**Password policy** explains every rule, what is allowed/rejected, and can generate a compliant
password using `crypto.getRandomValues`.

Why validate on login too? The brief requires the policy on the password form; the same evaluator is
used for sign-in and sign-up so behaviour is identical. If you later need to let legacy accounts in
with shorter passwords, remove the policy check in the login handler only — signup stays strict.

### 3.4 Session lifecycle

* Tokens refresh silently via `refresh_token` when fewer than `TOKEN_REFRESH_SKEW` seconds remain
  (`/dashboard` calls `refresh_session_if_needed`).
* Revoked/expired refresh tokens destroy the session and bounce to `/login` with a notice.
* `POST /logout` calls Supabase `sign_out` then clears the Flask session.

---

## 4. Supabase setup

1. **Create the project** at <https://supabase.com> and copy *Project Settings → API* values.
2. **Configure Auth → Providers → Email**
   * Enable *Email* provider.
   * For local testing either disable *Confirm email* or leave it on and confirm via the e-mail link —
     the app handles both (`needs_confirmation` response).
3. **Apply the server-side policy + profiles table**

   ```bash
   # Supabase dashboard → SQL → New query → paste the contents of:
   cat supabase/schema.sql
   ```

   `schema.sql` sets `minimum_password_length = 12` with all character classes, creates a
   `profiles` table with row-level security, and adds a trigger that fills the profile after signup.
   If `auth.config` is not writable on your plan, set the same values in the dashboard
   (*Authentication → Providers → Email*).
4. **Environment**

   ```bash
   cp .env.example .env
   # then edit SECRET_KEY, SUPABASE_URL, SUPABASE_ANON_KEY
   ```

   Generate a strong secret: `python -c "import secrets; print(secrets.token_hex(32))"`.
5. **Restart** `python run.py`. The login screen no longer shows the demo card and the footer reads
   `Supabase auth`. `GET /healthz` reports the active backend:
   `{"status": "ok", "backend": "supabase"}`.

Only the **anon** key is used — the service-role key is never needed and must never be shipped to
Flask's client-facing code paths. Add your deployed URL to *Authentication → URL Configuration* so
reset links work (`SUPABASE_RESET_REDIRECT_URL`).

---

## 5. Project structure

```
Alpha/
├── run.py                          # dev entry point (0.0.0.0:5000)
├── requirements.txt
├── .env.example
├── app/
│   ├── __init__.py                 # application factory, CSRF, CSP, error pages
│   ├── config.py                   # env-driven configuration
│   ├── routes.py                   # /login /signup /logout /forgot-password /dashboard
│   ├── security.py                 # session helpers + brute-force throttle
│   ├── password_policy.py          # THE policy (server side)
│   ├── backends/
│   │   ├── __init__.py             # backend factory + shared types
│   │   ├── supabase_backend.py     # Supabase Auth (production)
│   │   └── demo_backend.py         # local PBKDF2 vault (no keys needed)
│   ├── templates/                  # base, login, signup, dashboard, error + partials
│   └── static/
│       ├── css/app.css             # design tokens + components
│       ├── js/password-policy.js   # browser mirror of the policy + generator
│       ├── js/auth.js              # form UX, dialogs, async submit
│       ├── js/dashboard.js         # session countdown
│       └── img/alpha-logo.png      # brand logo
├── supabase/schema.sql
└── tests/                          # 31 unit + route tests (no network)
```

### Routes

| Method | Path | Purpose |
| --- | --- | --- |
| GET | `/` | redirect → `/login` |
| GET/POST | `/login` | sign-in page / sign-in endpoint (JSON when fetched) |
| GET/POST | `/signup` | account creation |
| POST | `/logout` | Supabase sign-out + session clear |
| POST | `/forgot-password` | sends a reset e-mail (always generic response) |
| GET | `/dashboard` | protected landing page |
| GET | `/session` | JSON session info |
| GET | `/healthz` | backend/health probe |

---

## 6. Security notes

Implemented:

* CSRF token on every mutating request, verified with `hmac.compare_digest`.
* HttpOnly + SameSite=Lax session cookie (`SESSION_COOKIE_SECURE=1` behind HTTPS).
* Content-Security-Policy (no inline scripts/styles), `nosniff`, referrer policy.
* Parameter-free form handling, 64 KB request cap, server-side validation of every field.
* Uniform error messages for unknown e-mail vs. wrong password; generic reset responses.
* Sliding-window brute-force throttling per e-mail and per IP.
* `next` redirect whitelist (relative paths only).
* No secrets in the repository — everything comes from the environment.

Before going live:

1. `SECRET_KEY` from a secret manager; `SESSION_COOKIE_SECURE=1`; terminate TLS at the proxy.
2. Run behind gunicorn: `gunicorn "app:create_app()" --bind 0.0.0.0:8000 --workers 3`.
3. Move the throttle to Redis so limits survive restarts and multiple workers.
4. Enable Supabase *leaked password protection* (HaveIBeenPwned) and MFA for admin accounts.
5. Wire Flask-Limiter or your WAF in front of `/login` for IP-level protection.

---

## 7. Tests

```bash
python -m unittest discover -s tests -v
```

* `tests/test_password_policy.py` — 14 cases covering every rule (length, upper, lower, digits,
  specials, dictionary words, sequences, repeats, personal data, max length, scoring).
* `tests/test_routes.py` — 17 cases covering page rendering, CSRF rejection, successful/failed login,
  policy rejection, e-mail validation, rate limiting, redirect whitelist, logout, signup validation and
  the generic reset response. These run against the demo backend, so no network is required.

---

## 8. Troubleshooting

| Symptom | Fix |
| --- | --- |
| "Password reset is not configured" | Supabase keys missing (demo mode) — add `.env` values. |
| `Invalid API key` in logs | `SUPABASE_ANON_KEY` is wrong or from another project. |
| Sign-up succeeds but no session | *Confirm email* is enabled — confirm the link, then sign in. |
| Login works locally but not behind a proxy | Set `SESSION_COOKIE_SECURE=1` and `SUPABASE_RESET_REDIRECT_URL` to the public URL. |
| Seeded demo user rejected | Delete `.data/demo_users.json`; it is regenerated on next start. |

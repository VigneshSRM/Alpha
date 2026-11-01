# Alpha Group of India — Client Portal

Secure sign-in for the Alpha Group client portal. **Java 17 · Spring Boot 3.5 · MariaDB · Flyway.**

One full-bleed hero scene, one form column, no card-inside-a-card, no demo mode. Every
verdict a user sees — credentials, password policy, e-mail shape — is decided on the server.

> This repository previously contained a Flask + Supabase implementation. It has been
> replaced entirely: no Python, no Supabase, no external identity provider. Accounts,
> password hashes and sessions all live in MariaDB.

---

## 1. The password policy

| Rule | Value |
| --- | --- |
| Minimum length | **12** characters |
| Maximum length | 128 characters |
| Uppercase letters | at least **1** |
| Lowercase letters | at least **1** |
| Digits | at least **2** |
| Special characters | at least **2** (`! " # $ % & ' ( ) * + , - . / : ; < = > ? @ [ \ ] ^ _ \` { \| } ~`) |
| Complexity | no dictionary stems, no 3-in-a-row repeats, no 4-character keyboard/alphabet runs, nothing derived from the user's own name or e-mail |

`policy/PasswordPolicy.java` is the **only** implementation. Both the live check
(`POST /api/password-check`) and the form submit call it, so the green tick can never
disagree with the final decision. The rules are also serialised into the page as
`data-policy` for the client-side checklist rendering.

The "!" indicator next to the Password label:

* shows the requirement list on hover, tap or `Escape`/click to dismiss;
* turns **green with a tick** as soon as the typed password passes — live, on every keystroke;
* turns red with the first reason when it does not.

The eye icon toggles visibility on every password field.

## 2. Requirements

* **JDK 17** (`java -version` → 17.x; 21 and 25 also work, the build targets 17)
* **Maven 3.9+**
* **MariaDB 10.5+** (11.4 in the compose file) — or Docker

## 3. Run it

### With Docker (fastest)

```bash
docker compose up --build
# → http://localhost:8080/login
```

MariaDB starts, Flyway creates the schema, the app comes up behind it.

### Locally

```bash
# 1. a database
mysql -u root -p -e "CREATE DATABASE alpha_portal; \
  CREATE USER 'alpha'@'localhost' IDENTIFIED BY 'alpha'; \
  GRANT ALL ON alpha_portal.* TO 'alpha'@'localhost';"

# 2. the app
export DB_URL=jdbc:mariadb://localhost:3306/alpha_portal
export DB_USERNAME=alpha
export DB_PASSWORD=alpha
export SECRET_KEY="$(openssl rand -base64 48)"     # signs the remember-me cookie
mvn spring-boot:run -Dspring-boot.run.profiles=dev
```

Flyway applies `src/main/resources/db/migration` on startup — no manual SQL.

Create the first account through **/signup** (enabled by default; set
`SIGNUP_ENABLED=false` to close registration afterwards).

Registration takes a name, a work e-mail, a mobile number and a password. The
mobile number is picked from a searchable list of **236 dialling codes**
(`policy/Countries.java`); the picker collapses to a short `+91` button so the
number keeps almost all of the field. The same table drives
`policy/PhoneNumbers.java`, which validates the number server-side: the
punctuation people actually type is stripped, a national trunk `0` is dropped,
the digit count must match the country's numbering plan, and the result is
stored in E.164 (`+919876543210`). Because the picker and the validator read one
table, they cannot drift apart.

## 4. Configuration

| Variable | Default | Purpose |
| --- | --- | --- |
| `DB_URL` | `jdbc:mariadb://localhost:3306/alpha_portal` | JDBC URL |
| `DB_USERNAME` / `DB_PASSWORD` | `alpha` / `alpha` | database credentials |
| `SECRET_KEY` | dev placeholder | signs the remember-me cookie — **must be set in production** |
| `SESSION_COOKIE_SECURE` | `false` | set `true` behind HTTPS |
| `SIGNUP_ENABLED` | `true` | gate for self-service registration |
| `LOGIN_MAX_ATTEMPTS` / `LOGIN_WINDOW_SECONDS` | `5` / `900` | brute-force window |
| `BCRYPT_STRENGTH` | `12` | BCrypt work factor |
| `APP_NAME` | `Alpha Group of India` | title and footer |
| `PORT` | `8080` | HTTP port |

## 5. Database

Flyway owns the schema. `spring.jpa.hibernate.ddl-auto=none` — nothing is created implicitly.

| Migration | Contents |
| --- | --- |
| `V1__portal_user.sql` | `portal_user` (email, BCrypt hash, name, enabled, created_at, last_login_at) |
| `V2__spring_session.sql` | Spring Session JDBC tables — sessions survive a redeploy |
| `V3__password_reset_token.sql` | single-use reset tokens, stored as SHA-256 only |
| `V4__portal_user_phone.sql` | `phone` — E.164 mobile number captured at sign-up |
| `V5__business_profile.sql` | `business_profile` — one row per account: vertical, name, phone, type, GSTIN, address, and the accounting preferences |
| `V6__business_profile_fssai.sql` | `fssai`, `fssai_expiry` — the optional food licence a restaurant may keep |

To add a change, drop a new `V5__…sql` in `db/migration`; never edit an applied file.

The browser is told what happened, in words: a successful registration says so
and lands on `/onboarding` — the business profile; a duplicate e-mail, a closed sign-up, an
expired CSRF token, a 500 or a dead connection each get their own message, with
a link where one helps. `auth.js` only paints what the server decided — see
`failureMessage()` in `static/js/auth.js`.

## 6. Routes

| Method | Path | Access | Purpose |
| --- | --- | --- | --- |
| GET | `/` | public | redirect to `/login` |
| GET/POST | `/login` | public | sign in (HTML or JSON) |
| GET/POST | `/signup` | public | create an account, policy enforced server-side |
| POST | `/logout` | public (CSRF) | sign out, clear remember-me cookie |
| POST | `/forgot-password` | public | issue a reset token (always answers generically) |
| GET/POST | `/reset-password` | token | choose a new password |
| GET/POST | `/onboarding` | session | “Set up your Business profile”, validated by `OnboardingRules` |
| GET | `/dashboard` | session | protected area |
| GET | `/session` | session | JSON session status |
| POST | `/api/password-check` | public (CSRF) | live policy verdict |
| GET | `/healthz` | public | liveness probe |

## 6a. After registration — the business profile

`/onboarding` is one page in two halves: **Business** (vertical, name, phone,
type, country, state, GSTIN, address) and **Preferences** (language, time zone,
base currency, fiscal year, inventory start date). The lists live in
`onboarding/OnboardingRules.java` — 18 business types, 36 states and union
territories, 12 fiscal-year starts — and the same class validates the POST, so
the HTML and the rulebook cannot drift. GSTIN is optional and checked against
the 15-character format only when it is filled in.

Choosing **Restaurant** reveals a *Food licence* block — **FSSAI Number**
(optional, exactly 14 digits, spaces and dashes tolerated) and **FSSAI Expiry
Date** (optional). Choosing **Retail** hides it again, and the server drops both
values if a profile changes to retail, so a shop can never carry a food licence. Saving writes one
`business_profile` row and redirects to `/dashboard?welcome=1`, which is where
the welcome text now lives.

Every required field is marked with an orange asterisk and carries
`aria-required="true"`; the forms are `novalidate`, so the mark out front is the
only promise and the server is the only judge.

## 7. Security notes

* **Hashing** — BCrypt, work factor 12 (configurable). Plaintext is never stored, logged or sent anywhere.
* **No enumeration** — unknown address, wrong password and disabled account all produce one neutral message; `forgot-password` answers identically whether or not the address exists.
* **Brute force** — sliding window keyed by e-mail *and* client address (`CF-Connecting-IP`, first `X-Forwarded-For` hop, socket peer), answered with `429` and a retry-after.
* **Session fixation** — the session id is rotated on sign-in.
* **CSRF** — every state-changing request, including `fetch()` calls, carries a token (`csrf_token` field or `X-CSRF-Token` header). A stale token returns `{"code":"csrf"}` with a fresh one, and the page retries once silently.
* **CSP** — `default-src 'self'`, no `unsafe-inline`, `frame-ancestors 'none'`, plus `nosniff`, a strict referrer policy and `Permissions-Policy`.
* **Reset tokens** — 32 random bytes, stored as SHA-256, single-use, 60-minute expiry.
* **Open redirects** — `next` accepts same-site paths only.

## 8. Tests

```bash
mvn test
```

| Suite | Covers |
| --- | --- |
| `PasswordPolicyTest` | every rule and every violation message |
| `PortalWebTest` | page markup, CSRF, live check, BCrypt credential checks, sign-up rules, throttle → 429, reset flow, generic forgot-password |
| `LoginThrottleTest` | sliding window, reset on success, key independence |
| `SafeRedirectTest` | open-redirect rejection |

The web suite runs on **H2 in MySQL compatibility mode** with the same Flyway
migrations, so `mvn test` needs no database. Production runs on MariaDB.

## 9. Project structure

```
pom.xml
src/main/java/in/alphagroup/portal/
  PortalApplication.java
  config/      AlphaProperties, SecurityConfig, PasswordConfig,
               NamedCsrfTokenRepository, authentication entry point + denied handler
  policy/      PasswordPolicy (the single source of truth), Rule, PolicyResult
  security/    LoginThrottle, AttemptKeys
  user/        PortalUser (JPA + UserDetails), repository, service
  onboarding/  BusinessProfile, repository, OnboardingService,
               OnboardingRules (the lists and the rules)
  reset/       PasswordResetToken, repository, service
  util/        Emails, Requests, SafeRedirect
  web/         AuthController, ResetPasswordController, PasswordCheckController,
               DashboardController, SessionController, HealthController,
               ErrorPageController, GlobalModelAdvice, AssetVersion
src/main/resources/
  templates/   fragments, login, signup, reset-password, dashboard, error (Thymeleaf)
  static/      app.css, auth.js, dashboard.js, reset.js, brand images
  db/migration Flyway
```

## 10. Notes

* Mail delivery is not configured. With `alpha.log-reset-links=true` (the `dev` profile) the
  reset link is written to the application log; wire a real sender into
  `PasswordResetService.issue` before going live.
* `LoginThrottle` is per-JVM. Behind more than one instance, swap the bean for a Redis or
  Bucket4j implementation — the call sites do not change.
* `preview/*.png` are reference screenshots of the visual design.

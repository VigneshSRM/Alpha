# Alpha Group of India — Portal (Java 17 + Spring Boot)

A complete port of the Flask application in [`../app`](../app) to **Java 17 / Spring Boot 3.5**,
keeping Supabase Auth as the only credential store.

The pages, the stylesheet (`app.css`) and the JavaScript (`auth.js`, `dashboard.js`) are **shared**,
not copied — the Maven build pulls `../app/static` into the jar, so both stacks render a
byte-identical portal and the "!" password indicator behaves the same in each.

---

## 1. What is already familiar

| Concern | Flask | Here |
| --- | --- | --- |
| Templates | Jinja2 | Thymeleaf (`src/main/resources/templates`) |
| Password policy | `app/password_policy.py` | `policy/PasswordPolicy.java` (identical messages, identical rules) |
| Live check | `POST /api/password-check` | `web/PasswordCheckController` — same URL, same JSON |
| CSRF | Flask-WTF style token | Spring Security CSRF, renamed to `csrf_token` / `X-CSRF-Token` so `auth.js` is untouched |
| Brute force | `AttemptThrottle` | `security/LoginThrottle` |
| Sessions | Flask session | `HttpSession` + `SessionAuth` |
| Identity | `supabase-py` | `auth/SupabaseAuthClient` → GoTrue REST via `RestClient` |
| CSS / JS | `app/static` | served from the jar, sourced from `app/static` |

## 2. Requirements

* **JDK 17** (`java -version` → 17.x). Java 21/25 also run it; the build targets 17.
* **Maven 3.9+** (the wrapper is not committed, so use your local `mvn`).
* A Supabase project.

## 3. Configuration

Environment variables (or `src/main/resources/application.yml`):

| Variable | Required | Notes |
| --- | --- | --- |
| `SUPABASE_URL` | yes | `https://<project>.supabase.co` — **not** the `/auth/v1` path |
| `SUPABASE_ANON_KEY` | yes | the public *anon* key. Never the service-role key |
| `SECRET_KEY` | — | not used: sessions are server-side, the cookie only carries an id |
| `PORT` | no | defaults to `8080` |
| `APP_NAME` | no | defaults to `Alpha Group of India` |
| `LOGIN_MAX_ATTEMPTS` / `LOGIN_WINDOW_SECONDS` | no | defaults `5` / `900` |
| `SESSION_COOKIE_SECURE` | no | set `true` behind HTTPS |

Startup **fails fast** with an actionable message if the URL or key is missing — the same
guard the Flask app has.

### Supabase dashboard, one setting

Auth → **Password strength / minimum length** must be raised to **12**. GoTrue defaults to 6,
so a client calling the API directly could otherwise set a weaker password than this page allows.
This application always applies its own policy first, but the provider should agree.

## 4. Run

```bash
cd spring-boot

# development: serves CSS/JS/images straight from ../app/static
export SUPABASE_URL="https://your-project.supabase.co"
export SUPABASE_ANON_KEY="your-anon-key"
mvn spring-boot:run -Dspring-boot.run.profiles=dev

# or a packaged jar
mvn clean package
java -jar target/alpha-portal-1.0.0.jar
```

Open <http://localhost:8080/login>.

## 5. Test

```bash
mvn test
```

| Suite | Covers |
| --- | --- |
| `PasswordPolicyTest` | every rule: length, case, 2 digits, 2 symbols, sequences, repeats, dictionary stems, personal data, 128-character ceiling |
| `PortalWebTest` | page markup (logo in panel, `!` indicator, retired copy gone), CSRF, live check, sign-in/sign-up flows, open-redirect guard, throttle → 429 |
| `LoginThrottleTest` | sliding window, reset on success, key independence |
| `SafeRedirectTest` | open-redirect rejection |

## 6. Endpoints

| Method | Path | Auth | Purpose |
| --- | --- | --- | --- |
| GET | `/` | public | redirect to `/login` |
| GET/POST | `/login` | public | sign in (HTML or JSON) |
| GET/POST | `/signup` | public | create account, policy enforced server-side |
| POST | `/logout` | public (CSRF) | sign out |
| POST | `/forgot-password` | public | send reset e-mail |
| GET | `/dashboard` | session | protected area |
| GET | `/session` | session | JSON session status |
| POST | `/api/password-check` | public (CSRF) | live policy verdict |
| GET | `/healthz` | public | liveness probe |

## 7. Security notes

* The browser is never the validator: the tick, the error text and the accept/reject decision all
  come from `PasswordPolicy`, used by both `/api/password-check` and the form submit.
* Supabase wording is never echoed; provider errors map to neutral messages so accounts cannot be
  enumerated (`invalid_credentials`, `user_exists`, …).
* CSRF: session-backed tokens, exposed as `csrf_token` (form field) and `X-CSRF-Token` (header).
  A rotated token is returned with a `{"code":"csrf"}` 403 so `auth.js` can retry silently.
* CSP: `default-src 'self'` with no `unsafe-inline`; `frame-ancestors 'none'`.
* Session fixation: the session id is rotated on sign-in.
* Access tokens never reach the browser; only `JSESSIONID` does.

## 8. Known limitations

* `Keep me signed in` extends the server-side session timeout (30 min → 14 days). The `JSESSIONID`
  cookie itself remains a browser-session cookie; making it persist would need
  `spring-session` (or a refresh-token cookie). The Flask build uses a signed cookie, so it
  survives a browser restart — this port does not, deliberately, to avoid storing tokens client-side.
* `LoginThrottle` is per-JVM. Behind more than one instance swap the bean for a Redis/`Bucket4j`
  implementation; call sites do not change.
* Static assets are copied into the jar at build time from `../app/static`. If you extract this
  module into its own repository, point `shared.static.dir` at the assets instead.

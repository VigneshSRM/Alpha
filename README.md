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
* **MariaDB 10.5+**

## 3. Run it

No Docker, no exported variables. The portal reads its credentials from
**`config/hibernate.properties`** when it starts.

```bash
# 1. a database (once)
mysql -u root -p -e "CREATE DATABASE alpha_portal; \
  CREATE USER 'alpha'@'localhost' IDENTIFIED BY 'alpha'; \
  GRANT ALL ON alpha_portal.* TO 'alpha'@'localhost';"

# 2. the credentials file
cp config/hibernate.properties.example config/hibernate.properties
$EDITOR config/hibernate.properties      # url, user, password, remember-me key

# 3. run
mvn spring-boot:run
# → http://localhost:8080/login
```

Flyway applies `src/main/resources/db/migration` on startup — no manual SQL.
Nothing is read from the environment, so the only thing to change when the
database moves is that one file.

You can also build a jar and run it from the project root:

```bash
mvn clean package -DskipTests
java -jar target/alpha-portal-*.jar        # picks up ./config/hibernate.properties
```

Create the first account through **/signup** (enabled by default; set
`alpha.signup.enabled=false` in the properties file to close registration
afterwards).

Registration takes a name, a work e-mail, a mobile number and a password. The
mobile number is picked from a searchable list of **236 dialling codes**
(`policy/Countries.java`); the picker collapses to a short `+91` button so the
number keeps almost all of the field. The same table drives
`policy/PhoneNumbers.java`, which validates the number server-side: the
punctuation people actually type is stripped, a national trunk `0` is dropped,
the digit count must match the country's numbering plan, and the result is
stored in E.164 (`+919876543210`). Because the picker and the validator read one
table, they cannot drift apart.

## 4. Configuration — `config/hibernate.properties`

One file, copied from `config/hibernate.properties.example`. The real file is
git-ignored; the example is not. `application.yml` holds only structure and
imports this file with `spring.config.import`, so a key added here wins.

| Key | Example | Purpose |
| --- | --- | --- |
| `spring.datasource.url` | `jdbc:mariadb://localhost:3306/alpha_portal` | JDBC URL |
| `spring.datasource.username` / `password` | `alpha` / `alpha` | database credentials |
| `spring.datasource.hikari.maximum-pool-size` | `10` | connection pool |
| `spring.jpa.properties.hibernate.jdbc.time_zone` | `UTC` | Hibernate time zone |
| `alpha.security.remember-me-key` | `openssl rand -base64 48` | signs the remember-me cookie — **change it** |
| `alpha.security.login-max-attempts` / `login-window-seconds` | `5` / `900` | brute-force window |
| `alpha.security.bcrypt-strength` | `12` | BCrypt work factor |
| `server.port` | `8080` | listening port |
| `server.servlet.session.cookie.secure` | `false` | `true` once behind HTTPS |
| `spring.thymeleaf.cache` | `false` | template cache |
| `alpha.signup.enabled` | `true` | gate for self-service registration |
| `alpha.app-name` / `alpha.support-email` | `Alpha Group of India` | title and footer |

**API settings** live in the same file, under the `API` heading. The portal
validates every credential itself and calls no third-party service today, so
there is nothing to fill in yet — when one is added, its URL and key go there
(`alpha.api.base-url`, `alpha.api.key`), never into an environment variable.

Forget the file and the app stops before it opens a port, with Boot's own
`Failed to configure a DataSource: 'url' attribute is not specified` — which
points straight back here. Copy the example and it runs.

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
| `V7__business_module.sql` | `business_module` — which of the ten modules an account turned on, unique per (user, module) |

To add a change, drop a new `V8__…sql` in `db/migration`; never edit an applied file.

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
| GET/POST | `/modules` | session | the ten modules to start with, saved by `ModuleService` |
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
`business_profile` row and hands over to the module picker.

Every required field is marked with an orange asterisk and carries
`aria-required="true"`; the forms are `novalidate`, so the mark out front is the
only promise and the server is the only judge.

## 6b. The module picker

`/modules` is the last step before the dashboard. Ten cards — POS, Sales,
Inventory, Purchase, Alpha and Luna Customers (CRM), Documents, Templates,
Customization, Reports, Settings — fly out of the centre point of the page when
it loads, staggered by 45 ms and settling in 620 ms; `prefers-reduced-motion`
puts them there at once. The list is `modules/Modules.java` and nothing else, so
the cards, the POST and the `business_module` rows cannot disagree.

Choosing nothing is refused with `422` and one sentence — *“Choose at least one
module — you can switch the rest on later.”* The choice is written by
`ModuleService`, which replaces the account's rows in one transaction, and the
browser lands on `/dashboard?welcome=1&modules=<n>`, where the welcome text
lives.

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
migrations, so `mvn test` needs no database and never reads
`config/hibernate.properties` (the `test` profile skips the import). Production
runs on MariaDB.

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
  modules/     Modules (the ten cards), BusinessModule, repository, service
  reset/       PasswordResetToken, repository, service
  util/        Emails, Requests, SafeRedirect
  web/         AuthController, ResetPasswordController, PasswordCheckController,
               DashboardController, SessionController, HealthController,
               ErrorPageController, GlobalModelAdvice, AssetVersion
src/main/resources/
  application.yml   structure only — no credentials, no environment variables
  templates/   fragments, login, signup, reset-password, onboarding, modules,
               dashboard, error (Thymeleaf)
  static/      app.css, auth.js, onboarding.js, modules.js, dashboard.js,
               reset.js, brand images
  db/migration Flyway
config/
  hibernate.properties.example   ← copy to hibernate.properties (git-ignored)
```

## 10. Notes

* Mail delivery is not configured. With `alpha.log-reset-links=true` (the `dev` profile) the
  reset link is written to the application log; wire a real sender into
  `PasswordResetService.issue` before going live.
* `LoginThrottle` is per-JVM. Behind more than one instance, swap the bean for a Redis or
  Bucket4j implementation — the call sites do not change.
* `preview/*.png` are reference screenshots of the visual design.

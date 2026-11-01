# Java running in the sandbox

These are **live screenshots of a Java process executing this repo's password
policy**, not renders of static HTML. What ran:

- **JDK 17.0.8** (`javac 17.0.8`, Temurin), fetched from the npm registry as
  `javajre-linux-64` — Maven Central and Adoptium are both unreachable from this
  sandbox, and a JDK turned out to be published as an npm tarball.
- `javac -encoding UTF-8` compiled `policy/{Rule,PolicyResult,PasswordPolicy}`,
  `util/{Emails,SafeRedirect}` — **5 files, zero errors, zero warnings**.
- `PortalHarness.java` (a throwaway file in `/tmp`, not part of the repo) is a
  130-line `com.sun.net.httpserver` server. It serves the pages and answers
  `POST /api/password-check` with the exact envelope from
  `web/PasswordCheckController`, calling the **unmodified** `PasswordPolicy.evaluate`.
- The page was driven in headless Chromium: 29 requests were sent while typing,
  each answered by Java. `01-weak.png` is the verdict for `Summer2026`,
  `02-valid.png` is the verdict for `Alpha#2026#Group`.
- `03-signup.png` and `04-account.png` were captured by the **Playwright MCP
  server** driving the same Java process (see `scripts/mcp-smoke.mjs`).

## The MCP smoke test

`scripts/mcp-smoke.mjs` starts `@playwright/mcp` over stdio, initialises the
session and walks the app through its tools (`browser_navigate`,
`browser_snapshot`, `browser_click`, `browser_type`, `browser_evaluate`,
`browser_take_screenshot`). Assertions are made against **the accessibility
tree**, not against pixels — a control with a wrong accessible name fails the
test even if it looks right.

```bash
npm i -D @playwright/mcp
BASE_URL=http://127.0.0.1:8080 node scripts/mcp-smoke.mjs
```

Last run against this sandbox:

```
[PASS] sign-in page renders
[PASS] no stray error banner above the e-mail field
[PASS] password field has a clean accessible name — Password
[PASS] lands on the request-account page
[PASS] no stray error banner above the e-mail field (sign-up)
[PASS] weak password is rejected — unmet: length, special
[PASS] strong password is accepted — 6/6 rules met
[PASS] account page greets the user — Welcome back, Ava Menon
[PASS] account shows the e-mail — ava.menon@alphagroup.in
[PASS] account has name, e-mail, id and session rows — Name, E-mail, User ID, ...
[PASS] session countdown is live — 27m 14s
[PASS] policy chips rendered — 6 chips
[PASS] no console errors

all checks passed
```

It found one real defect on the first run: the `!` policy icon lived *inside*
the `<label>`, so the password box announced itself as `"Password Password
requirements — hover or tap for details"`. The icon is now a sibling of the
label (visually unchanged, `justify-content: flex-start` on `.field__top`), and
the field announces as `"Password"`.

## What this is not

**It is not Spring Boot.** `mvn spring-boot:run` cannot work here: 29 of the 34
source files import Spring/Jakarta, and compiling the whole tree yields 418
errors that are *all* missing packages —

| missing package | errors |
| --- | --- |
| `org.springframework.*` | 104 |
| `jakarta.servlet.*` | 15 |
| `jakarta.persistence.*` | 12 |
| `org.slf4j.*` | 8 |
| `org.hibernate.*`, `com.fasterxml.*` | 2 |

— because Maven Central (and every mirror of it) is blocked from this sandbox.
So the security config, controllers, JPA entities, Flyway migrations and
sessions were **not** exercised. Those still need `mvn test` on a machine with
network access.

Also note the harness re-implements the JSON envelope that
`PasswordCheckController` builds. The *policy decision* is the repo's code; the
15-line controller wrapper around it is a copy.

## To run the real thing

```bash
docker compose up --build          # MariaDB + Flyway + Spring Boot on :8080
# or
mvn test
mvn spring-boot:run -Dspring-boot.run.profiles=dev
```

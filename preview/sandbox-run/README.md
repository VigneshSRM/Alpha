# A real browser against the real policy code

These screenshots come from a headless Chromium session driven by Puppeteer
against a **running JVM** (OpenJDK 17.0.8). Every verdict in them was produced by
the repository's own classes:

| What the page did | What answered |
| --- | --- |
| typed a password, watched the `!` indicator | `policy/PasswordPolicy.evaluate(...)` over `POST /api/password-check` |
| submitted the request-account form with 5 digits | `policy/PhoneNumbers.validate("IN", "98765")` over `POST /signup` |
| corrected the number and submitted again | the same call, now accepting it and answering E.164 |

The pages themselves are the rendered Thymeleaf templates (see
`preview/spring-boot/README.md`), served by `scripts/sandbox/PortalHarness.java`
— a `com.sun.net.httpserver` program, **not Spring Boot**. Maven Central is
unreachable from this sandbox, so the Spring/Jakarta jars that 29 of the 34
sources import cannot be resolved and `mvn spring-boot:run` cannot run here. The
policy layer has no framework imports, so it compiles and runs as-is, and the
harness wires it to the browser the same way the controllers do.

## What the JVM printed while these screenshots were taken

```
harness listening on http://0.0.0.0:8080  (pages from /tmp/site)
policy: min 12 chars, 1 upper, 1 lower, 2 digits, 2 special
rules: [length, uppercase, lowercase, digits, special, complexity]
POST /api/password-check  len= 9  valid=false score= 52  strength=fair    failed=[Password must be at least 12 characters long., Add at least 2 special characters (! @ # $ % & *).]
POST /api/password-check  len=16  valid=false score= 85  strength=strong  failed=[Add at least 2 special characters (! @ # $ % & *).]
POST /api/password-check  len=25  valid=true  score= 97  strength=strong  failed=[]
POST /signup  country=IN digits= 5 -> (rejected: Enter a 10-digit mobile number for India.) errors=[phone]
POST /signup  country=IN digits=10 -> +919876543210                    errors=none
```

`len=25` is `Alpha#2026#Group` typed twice over — the field is `type="password"`,
and the harness never logs the password itself, only its length and the rules it
failed.

## What each screenshot shows

| File | State |
| --- | --- |
| `01-weak.png` | `Summer2026` in the password field, `!` panel open: unmet length and special characters, indicator red |
| `02-valid.png` | `Alpha#2026#Group`: 6/6 rules met, indicator green, score 97 “strong” |
| `03-signup.png` | the request-account form filled in by the smoke test — picker, hint and a valid number |
| `03-signup-rejected.png` | the server said **422**: “Enter a 10-digit mobile number for India.” in the banner and under the field |
| `04-account.png` | the corrected number was accepted (`200`, `+919876543210`) and the browser was redirected to the account page |
| `05-mobile-too-short.png` | seven digits typed: the hint gives way to “Keep going — India numbers have 10 digits.” |
| `06-phone-picker.png` | the dial-code button opened: all 236 countries, scrolled to the current one |
| `07-phone-search.png` | typing “united king” narrows the list to `+44 United Kingdom` |
| `08-phone-gb.png` | after choosing it: the button reads `+44` and the hint switches to “10 digits for United Kingdom” |
| `09-phone-mobile.png` | 390×844 with the list open — it stays inside the viewport, no sideways scroll |

## Reproduce it

```bash
javac -encoding UTF-8 -d out \
  $(find src/main/java/in/alphagroup/portal/policy \
         src/main/java/in/alphagroup/portal/util -name '*.java') \
  scripts/sandbox/PortalHarness.java

java -cp out PortalHarness 8080 /path/to/rendered/pages
```

## The other half of the proof

`scripts/mcp-smoke.mjs` drives the same pages through the Playwright **MCP**
server and asserts on what the accessibility tree exposes — accessible names,
the country picker's contents, and the absence of stray banners. Last run:

```
[PASS] country picker lists every country — 236 options
[PASS] India is preselected with its dial code — IN +91 India
[PASS] the closed picker shows only the dial code — +91
[PASS] the picker leaves most of the field to the number — 57px of 402px
[PASS] picker has an accessible name — Country dialling code
[PASS] hint states the digits India uses — 10 digits for India
[PASS] mobile field has a clean accessible name — Mobile number
[PASS] the dial-code button opens the full country list — 236 options
[PASS] opening the list puts the cursor in its search box — picker__search
[PASS] typing in the picker narrows the list — 1 match: +44 United Kingdom
[PASS] choosing a country updates the button and the hint — +44 / 10 digits for United Kingdom
[PASS] a country whose name starts with the query is ranked first — 2 matches, first: +91 India
[PASS] India can be picked again with the keyboard — IN
[PASS] a too-short number is flagged while typing — Keep going — India numbers have 10 digits.
...
30 checks, 0 failures
```

## To run the actual application

```bash
docker compose up --build                    # MariaDB + Flyway + Spring Boot on :8080
# or
mvn test && mvn spring-boot:run -Dspring-boot.run.profiles=dev
```

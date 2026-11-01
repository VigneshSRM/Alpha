# A real browser against the real policy code

These screenshots come from a headless Chromium session — the first eighteen
driven by Puppeteer, the module picker through the Playwright MCP server —
against a **running JVM** (OpenJDK 17.0.8). Every verdict in them was produced by
the repository's own classes:

| What the page did | What answered |
| --- | --- |
| typed a password, watched the `!` indicator | `policy/PasswordPolicy.evaluate(...)` over `POST /api/password-check` |
| submitted the request-account form with 5 digits | `policy/PhoneNumbers.validate("IN", "98765")` over `POST /signup` |
| submitted it twice with the same e-mail | the harness's duplicate check, standing in for the unique index (409) |
| corrected the number and submitted again | the same call, now accepting it and answering E.164 |
| chose three modules on the picker | `ModuleService.choose(...)`, standing in for the `business_module` writes (V7) |

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
| `10-registered.png` | the server answered `200 {"ok":true,…}` — “Account created. Next: your business profile.” |
| `11-account-welcome.png` | the business profile was saved, `/modules` opened, three modules were chosen, and `/dashboard?welcome=1&modules=3` confirms it |
| `12-signup-duplicate.png` | the same address again: `409 user_exists`, the reason in the banner, the field flagged, and a “Sign in instead” link |
| `13-signup-network.png` | `fetch` rejected — “Can't reach the server. Check your connection and try again.” |
| `14-signup-server-error.png` | a `500` — “Registration failed on our side…”, not a silent spinner |
| `15-onboarding.png` | “Set up your Business profile”: vertical cards, 236 dial codes, 18 business types, 36 states, GSTIN optional, and the footer in the right-hand corner |
| `16-onboarding-errors.png` | an empty submit: `422` from `OnboardingRules`, nine fields marked with their own message — and no banner repeating them |
| `17-onboarding-restaurant.png` | Retail chosen first, then Restaurant: the Food licence block appears with the optional 14-digit FSSAI number and an expiry date |
| `18-onboarding-fssai-error.png` | thirteen digits submitted: `422` — “An FSSAI licence number is exactly 14 digits.”, the number still in the field |
| `19-modules.png` | the module picker after the profile saves: the ten cards of the brief, settled after splitting out of the centre point, `0 of 10 chosen` |

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
      [PASS] sign-in page renders
  [PASS] no stray error banner above the e-mail field
  [PASS] password field has a clean accessible name — Password
  [PASS] lands on the request-account page
  [PASS] no stray error banner above the e-mail field (sign-up)
  [PASS] country picker lists every country — 236 options
  [PASS] India is preselected with its dial code — IN +91 India
  [PASS] the closed picker shows only the dial code — +91
  [PASS] the picker leaves most of the field to the number — 58px of 402px
  [PASS] picker has an accessible name — Country dialling code
  [PASS] hint states the digits India uses — 10 digits for India
  [PASS] mobile field has a clean accessible name — Mobile number
  [PASS] the dial-code button opens the full country list — 236 options
  [PASS] opening the list puts the cursor in its search box — picker__search
  [PASS] typing in the picker narrows the list — 1 match: +44 United Kingdom
  [PASS] choosing a country updates the button and the hint — +44 / 10 digits for United Kingdom
  [PASS] the list closes after choosing — true
  [PASS] a too-short number is flagged while typing — Keep going — United Kingdom numbers have 10 digits.
  [PASS] letters cannot be typed into the field — 987654321
  [PASS] a 10-digit Indian number clears the warning — clean
  [PASS] a country whose name starts with the query is ranked first — 2 matches, first: +91 India
  [PASS] India can be picked again with the keyboard — IN
  [PASS] weak password is rejected — unmet: length, special
  [PASS] strong password is accepted — 6/6 rules met
  [PASS] a successful registration says so — Account created. Next: your business profile.
  [PASS] sign-up hands over to the business profile — /onboarding — Set up your Business profile
  [PASS] business vertical offers Retail and Restaurant — Retail|Restaurant
  [PASS] business type list has the 18 types from the brief — 18 types
  [PASS] state list has the 36 states and union territories — 36 states
  [PASS] fiscal year offers twelve start months — 12 months
  [PASS] GSTIN is labelled optional — GSTIN Number (Optional)
  [PASS] required fields are marked before the first submit — 12 marks, 11 aria-required
  [PASS] the business-profile page has no wolf photograph — 0 scene images
  [PASS] the footer sits in the right-hand corner — 64px of breathing room at the right edge
  [PASS] the food licence stays out of the way for a shop — hidden
  [PASS] choosing Restaurant reveals the FSSAI number and expiry — shown
  [PASS] the FSSAI number takes exactly 14 digits — maxlength 14, hint “14 digits”
  [PASS] the expiry date is a date field — date
  [PASS] both licence fields are marked optional — true / true
  [PASS] choosing Retail hides the food licence again — hidden
  [PASS] an empty profile is answered field by field — 8 fields marked, 8 messages
  [PASS] no banner repeats the field messages on the profile page — (none)
  [PASS] a 13-digit FSSAI number is refused by the server — An FSSAI licence number is exactly 14 digits.
  [PASS] the licence block stays open with the number still in it — open — “1234567890123”
  [PASS] the profile saves and the module picker opens — /modules — 10 cards
  [PASS] the ten modules are the ones asked for — POS | Sales | Inventory | Purchase | Alpha and Luna Customers (CRM) | Documents | Templates | Customization | Reports | Settings
  [PASS] the cards split out of the centre point — data-split=1, first card opacity 1
  [PASS] nothing chosen yet is stated plainly — 0 of 10
  [PASS] choosing a card updates the count — 3
  [PASS] the chosen modules open the dashboard — /dashboard?welcome=1&modules=3
  [PASS] the dashboard has no wolf photograph — 0 scene images
  [PASS] a duplicate e-mail is explained, with a way out — That e-mail address is already registered. Sign in instead.
  [PASS] a dead connection is reported as such — Can't reach the server. Check your connection and try again.
  [PASS] a server failure says the failure is ours — Registration failed on our side — the server could not finish it. Please try again in a moment.
  [PASS] account page greets the user — Welcome back, Ava Menon
  [PASS] account shows the e-mail — ava.menon@alphagroup.in
  [PASS] account has name, e-mail, id and session rows — Name, E-mail, Mobile, User ID, Member since, Last sign-in, Provider, Session expires in, Signed in
  [PASS] session countdown is live — 29m 59s
  [PASS] policy chips rendered — 6 chips
  [PASS] no JavaScript errors in the console — clean
...
60 checks, 0 failures
```

## To run the actual application

```bash
mvn spring-boot:run                           # reads config/hibernate.properties
# or
mvn test && mvn spring-boot:run -Dspring-boot.run.profiles=dev
```

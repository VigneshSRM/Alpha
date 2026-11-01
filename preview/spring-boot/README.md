# What these screenshots are

These are **renders of the real Thymeleaf templates** in
`src/main/resources/templates/`, using the real `static/css/app.css`,
`static/js/auth.js` and the real images — **not** a screenshot of the Spring
Boot server running.

Provenance matters here, so plainly: the sandbox this was produced in has no
route to Maven Central, so the Spring and Jakarta jars cannot be resolved and
`mvn spring-boot:run` could not be executed there. What *was* executed is the
framework-free part — `policy/*`, `OnboardingRules`, `Modules` — compiled with
javac and served by `scripts/sandbox/PortalHarness.java`, which is how the
`preview/sandbox-run/` screenshots were taken. To still show what the pages look like, the templates were
expanded offline (fragments inlined, `th:if` branches resolved, `th:each`
checklist expanded, CSRF token and model values substituted) and the result was
loaded in headless Chromium with the real stylesheet and JavaScript. The render
step aborts if any Thymeleaf syntax survives, so nothing template-shaped is
presented as finished HTML.

Consequences for reading them:

* Everything visual — layout, colours, type, the wolf photograph, the `!`
  popover, the eye toggle, hover/focus behaviour — is the real CSS and JS.
* Anything that needs the server is inert: the live password check
  (`POST /api/password-check`) and the actual sign-in POST do not run, so the
  checkmarks stay in their empty state instead of turning green.
* The footer, flash messages and error states are shown only in the state that
  was rendered.

To see the application itself:

```bash
cp config/hibernate.properties.example config/hibernate.properties
mvn spring-boot:run            # MariaDB + Flyway + the app on :8080
```

| File | Page |
| --- | --- |
| `01-login.png` | Sign-in, 1440×900 |
| `02-login-requirements.png` | Sign-in with the password-requirements panel open (hover the `!`) |
| `03-login-mobile.png` | Sign-in, 390×844 |
| `04-reset-password.png` | Choose-a-new-password, valid token |
| `05-signup.png` | Request an account, 1440×900 — the dial-code button shows `+91` and leaves the field to the number |
| `06-signup-mobile-error.png` | Same page with a too-short number: the field says what the selected country expects |
|  | the button opens a searchable list of all 236 dialling codes (see `preview/sandbox-run/06-phone-picker.png`) |
| `07-signup-mobile.png` | Request an account, 390×844 |
| `08-onboarding.png` | “Set up your Business profile”, 1440×900 — no wolf photograph from here on, as asked |
| `09-modules.png` | The module picker: ten cards that split out of the centre point of the page |

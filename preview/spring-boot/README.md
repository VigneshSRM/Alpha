# What these screenshots are

These are **renders of the real Thymeleaf templates** in
`src/main/resources/templates/`, using the real `static/css/app.css`,
`static/js/auth.js` and the real images — **not** a screenshot of the Spring
Boot server running.

Provenance matters here, so plainly: the sandbox this was produced in has no
JDK and no route to Maven Central, so `mvn spring-boot:run` could not be
executed there. To still show what the pages look like, the templates were
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
docker compose up --build      # MariaDB + Flyway + the app on :8080
# or
mvn spring-boot:run -Dspring-boot.run.profiles=dev
```

| File | Page |
| --- | --- |
| `01-login.png` | Sign-in, 1440×900 |
| `02-login-requirements.png` | Sign-in with the password-requirements panel open (hover the `!`) |
| `03-login-mobile.png` | Sign-in, 390×844 |
| `04-reset-password.png` | Choose-a-new-password, valid token |

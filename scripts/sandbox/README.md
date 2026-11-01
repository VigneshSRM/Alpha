# Running the Java side without Spring

`PortalHarness.java` is **not** the application. It is a small
`com.sun.net.httpserver` program that lets the parts of the codebase with no
framework imports — `policy/` and `util/` — run and answer a real browser:

| The harness serves | From |
| --- | --- |
| the sign-in and reset pages, the CSS and the JS | a directory of rendered pages (see below) |
| `POST /api/password-check` | the repo's `PasswordPolicy.evaluate(...)`, same envelope as `PasswordCheckController` |
| `POST /signup` | the repo's `PhoneNumbers.validate`, `Emails.isValid` and `PasswordPolicy.evaluate`, in the same order as `AuthController.signup` |

What is missing is the Spring wiring around those calls — request binding, CSRF,
JPA, Spring Session — because Maven Central is unreachable from the sandbox
where this was written, so those jars cannot be resolved and the application
cannot be built there. On a machine with network access use `mvn test` /
`docker compose up --build` instead; the harness is only for demonstrating the
decision-making code.

## Compile and run

```bash
javac -encoding UTF-8 -d out \
  $(find src/main/java/in/alphagroup/portal/policy \
         src/main/java/in/alphagroup/portal/util -name '*.java') \
  scripts/sandbox/PortalHarness.java

java -cp out PortalHarness 8080 /path/to/rendered/pages
```

`/path/to/rendered/pages` holds `login.html`, `signup.html`,
`reset-password.html`, `dashboard.html` plus `css/`, `js/` and `img/`. The
screenshots in `preview/spring-boot/` were taken from exactly those pages; see
that folder's README for how they are produced from the templates.

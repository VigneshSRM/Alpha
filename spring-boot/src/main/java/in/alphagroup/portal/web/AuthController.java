package in.alphagroup.portal.web;

import in.alphagroup.portal.auth.AuthFailureException;
import in.alphagroup.portal.auth.AuthOutcome;
import in.alphagroup.portal.auth.SupabaseAuthClient;
import in.alphagroup.portal.policy.PasswordPolicy;
import in.alphagroup.portal.policy.PolicyResult;
import in.alphagroup.portal.security.AttemptKeys;
import in.alphagroup.portal.security.LoginThrottle;
import in.alphagroup.portal.security.SessionAuth;
import in.alphagroup.portal.util.Emails;
import in.alphagroup.portal.util.Requests;
import in.alphagroup.portal.util.SafeRedirect;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.ModelAndView;

/**
 * Sign in, sign up, sign out and password reset.
 *
 * <p>Nothing here trusts the browser: shapes are checked, the throttle is
 * consulted, and the password policy is applied — all before Supabase is called.
 * Each handler answers either HTML (a rendered page carrying server-side errors)
 * or JSON (when {@code auth.js} submitted with {@code fetch}), which is why the
 * return type is {@link Object}: Spring picks the view or message converter from
 * the value actually returned.
 */
@Controller
public class AuthController {

    private static final int MAX_NAME_LENGTH = 80;

    private final SupabaseAuthClient client;
    private final SessionAuth sessionAuth;
    private final LoginThrottle throttle;

    public AuthController(
            SupabaseAuthClient client, SessionAuth sessionAuth, LoginThrottle throttle) {
        this.client = client;
        this.sessionAuth = sessionAuth;
        this.throttle = throttle;
    }

    // ------------------------------------------------------------------
    // Pages
    // ------------------------------------------------------------------

    @GetMapping("/")
    public String index() {
        return "redirect:/login";
    }

    @GetMapping("/login")
    public Object loginPage(
            @RequestParam Map<String, String> params, HttpSession session, Model model) {

        if (sessionAuth.current(session).isPresent()) {
            return "redirect:/dashboard";
        }
        Map<String, Object> form = new LinkedHashMap<>();
        form.put("email", params.getOrDefault("email", ""));
        form.put("name", "");
        form.put("remember", Boolean.FALSE);

        model.addAttribute("form", form);
        model.addAttribute("errors", Map.of());
        model.addAttribute("error", null);
        model.addAttribute("notice", params.getOrDefault("notice", ""));
        model.addAttribute("nextUrl", SafeRedirect.sanitise(params.get("next"), "/dashboard"));
        model.addAttribute("pageMode", "login");
        return "login";
    }

    @GetMapping("/signup")
    public Object signupPage(HttpSession session, Model model) {
        if (!client.supportsSignup()) {
            return "redirect:/login";
        }
        if (sessionAuth.current(session).isPresent()) {
            return "redirect:/dashboard";
        }
        Map<String, Object> form = new LinkedHashMap<>();
        form.put("name", "");
        form.put("email", "");
        form.put("remember", Boolean.TRUE);

        model.addAttribute("form", form);
        model.addAttribute("errors", Map.of());
        model.addAttribute("error", null);
        model.addAttribute("notice", "");
        model.addAttribute("nextUrl", "/dashboard");
        model.addAttribute("pageMode", "signup");
        return "signup";
    }

    // ------------------------------------------------------------------
    // Sign in
    // ------------------------------------------------------------------

    @PostMapping("/login")
    public Object login(
            @RequestParam MultiValueMap<String, String> params,
            HttpServletRequest request,
            HttpSession session) {

        String email = value(params, "email").trim();
        String password = value(params, "password");
        boolean remember = truthy(value(params, "remember"));
        String nextUrl = SafeRedirect.sanitise(value(params, "next"), "/dashboard");
        boolean json = Requests.wantsJson(request);

        // 1. Shape of the submission.
        Map<String, String> errors = new LinkedHashMap<>();
        if (email.isEmpty()) {
            errors.put("email", "Enter your e-mail address.");
        } else if (!Emails.isValid(email)) {
            errors.put("email", "Enter a valid e-mail address, e.g. name@company.com.");
        }
        if (password.isEmpty()) {
            errors.put("password", "Enter your password.");
        }
        if (!errors.isEmpty()) {
            String message = errors.values().iterator().next();
            if (json) {
                return ResponseEntity.unprocessableEntity()
                        .body(errorBody(message, "validation", errors, null));
            }
            return renderForm("login", HttpStatus.UNPROCESSABLE_ENTITY,
                    message, errors, email, remember, nextUrl, "");
        }

        // 2. Brute-force throttle.
        List<String> keys = AttemptKeys.of(request, email);
        int wait = throttle.retryAfter(keys);
        if (wait > 0) {
            String message = "Too many failed attempts. Please try again in " + wait + " seconds.";
            Map<String, String> throttleErrors = Map.of("form", message);
            if (json) {
                Map<String, Object> extra = new LinkedHashMap<>();
                extra.put("retryAfter", wait);
                return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                        .body(errorBody(message, "rate_limited", throttleErrors, extra));
            }
            return renderForm("login", HttpStatus.TOO_MANY_REQUESTS,
                    message, throttleErrors, email, remember, nextUrl, "");
        }

        // 3. Credentials — Supabase decides, nothing local does.
        try {
            AuthOutcome outcome = client.signIn(email, password);

            if (outcome.needsConfirmation()) {
                String target = "/login?notice=" + encode(outcome.message());
                if (json) {
                    Map<String, Object> body = new LinkedHashMap<>();
                    body.put("ok", true);
                    body.put("code", "confirm_email");
                    body.put("message", outcome.message());
                    body.put("redirect", target);
                    return ResponseEntity.ok(body);
                }
                return "redirect:" + target;
            }

            // 4. Session — a new id, so a pre-login one cannot be replayed.
            throttle.reset(keys);
            request.changeSessionId();
            sessionAuth.login(session, outcome.session(), remember);

            if (json) {
                Map<String, Object> body = new LinkedHashMap<>();
                body.put("ok", true);
                body.put("message", "Signed in. Taking you to your dashboard…");
                body.put("redirect", nextUrl);
                return ResponseEntity.ok(body);
            }
            return "redirect:" + nextUrl;

        } catch (AuthFailureException failure) {
            int remaining = throttle.registerFailure(keys);
            String message = failure.getMessage();
            if ("invalid_credentials".equals(failure.code()) && remaining <= 2) {
                message = message + " " + remaining
                        + " attempt(s) left before this account is temporarily locked.";
            }
            Map<String, String> failureErrors = new LinkedHashMap<>();
            failureErrors.put(failure.field() == null ? "form" : failure.field(), message);

            if (json) {
                Map<String, Object> extra = new LinkedHashMap<>();
                extra.put("attemptsRemaining", remaining);
                return ResponseEntity.status(failure.status())
                        .body(errorBody(message, failure.code(), failureErrors, extra));
            }
            return renderForm("login", failure.status(),
                    message, failureErrors, email, remember, nextUrl, "");
        }
    }

    // ------------------------------------------------------------------
    // Sign up
    // ------------------------------------------------------------------

    @PostMapping("/signup")
    public Object signup(
            @RequestParam MultiValueMap<String, String> params,
            HttpServletRequest request,
            HttpSession session) {

        if (!client.supportsSignup()) {
            return "redirect:/login";
        }

        String name = value(params, "name").trim();
        String email = value(params, "email").trim();
        String password = value(params, "password");
        String confirm = value(params, "confirm_password");
        boolean remember = true;
        boolean json = Requests.wantsJson(request);

        Map<String, String> errors = new LinkedHashMap<>();
        Map<String, Object> policyPayload = new LinkedHashMap<>();

        if (name.length() < 2) {
            errors.put("name", "Enter your full name (at least 2 characters).");
        } else if (name.length() > MAX_NAME_LENGTH) {
            errors.put("name", "That name is too long (" + MAX_NAME_LENGTH + " characters maximum).");
        }

        if (email.isEmpty()) {
            errors.put("email", "Enter your e-mail address.");
        } else if (!Emails.isValid(email)) {
            errors.put("email", "Enter a valid e-mail address, e.g. name@company.com.");
        }

        if (password.isEmpty()) {
            errors.put("password", "Enter a password.");
        } else {
            PolicyResult result = PasswordPolicy.evaluate(password, email, name);
            if (!result.valid()) {
                errors.put("password", result.violations().get(0));
                policyPayload.put("checks", result.checks());
                policyPayload.put("violations", result.violations());
                policyPayload.put("score", result.score());
            }
        }

        if (confirm.isEmpty()) {
            errors.put("confirm_password", "Re-enter your password.");
        } else if (!confirm.equals(password)) {
            errors.put("confirm_password", "Passwords do not match.");
        }

        if (!errors.isEmpty()) {
            String message = errors.values().iterator().next();
            if (json) {
                Map<String, Object> extra = null;
                if (!policyPayload.isEmpty()) {
                    extra = new LinkedHashMap<>();
                    extra.put("policy", policyPayload);
                }
                return ResponseEntity.unprocessableEntity()
                        .body(errorBody(message, "validation", errors, extra));
            }
            return renderForm("signup", HttpStatus.UNPROCESSABLE_ENTITY,
                    message, errors, email, remember, "/dashboard", name);
        }

        try {
            AuthOutcome outcome = client.signUp(email, password, name);
            String target;
            if (outcome.session() != null) {
                request.changeSessionId();
                sessionAuth.login(session, outcome.session(), true);
                target = "/dashboard";
            } else {
                target = "/login?notice=" + encode(outcome.message());
            }

            String message = outcome.message() == null || outcome.message().isBlank()
                    ? "Welcome to Alpha Group of India."
                    : outcome.message();

            if (json) {
                Map<String, Object> body = new LinkedHashMap<>();
                body.put("ok", true);
                body.put("message", message);
                body.put("redirect", target);
                if (outcome.session() == null) {
                    body.put("code", "confirm_email");
                }
                return ResponseEntity.ok(body);
            }
            return "redirect:" + target;

        } catch (AuthFailureException failure) {
            Map<String, String> failureErrors = new LinkedHashMap<>();
            failureErrors.put(failure.field() == null ? "form" : failure.field(), failure.getMessage());
            if (json) {
                return ResponseEntity.status(failure.status()).body(
                        errorBody(failure.getMessage(), failure.code(), failureErrors, null));
            }
            return renderForm("signup", failure.status(),
                    failure.getMessage(), failureErrors, email, remember, "/dashboard", name);
        }
    }

    // ------------------------------------------------------------------
    // Sign out / password reset
    // ------------------------------------------------------------------

    @PostMapping("/logout")
    public Object logout(HttpServletRequest request, HttpSession session) {
        sessionAuth.logout(session);
        if (Requests.wantsJson(request)) {
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("ok", true);
            body.put("message", "Signed out.");
            body.put("redirect", "/login");
            return ResponseEntity.ok(body);
        }
        return "redirect:/login?notice=" + encode("You have been signed out.");
    }

    @PostMapping("/forgot-password")
    public Object forgotPassword(
            @RequestParam MultiValueMap<String, String> params, HttpServletRequest request) {

        String email = value(params, "email").trim();
        boolean json = Requests.wantsJson(request);
        String generic =
                "If an account exists for that e-mail, a password reset link is on its way.";

        if (!Emails.isValid(email)) {
            String message = "Enter a valid e-mail address.";
            if (json) {
                return ResponseEntity.unprocessableEntity()
                        .body(errorBody(message, "validation", Map.of("email", message), null));
            }
            return "redirect:/login?notice=" + encode(message);
        }

        try {
            client.requestPasswordReset(email);
        } catch (AuthFailureException failure) {
            if (json) {
                return ResponseEntity.status(failure.status())
                        .body(errorBody(failure.getMessage(), failure.code(), Map.of(), null));
            }
            return "redirect:/login?notice=" + encode(failure.getMessage());
        }

        if (json) {
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("ok", true);
            body.put("message", generic);
            body.put("code", "reset_sent");
            return ResponseEntity.ok(body);
        }
        return "redirect:/login?notice=" + encode(generic);
    }

    // ------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------

    /** JSON error envelope understood by {@code auth.js}. */
    private Map<String, Object> errorBody(
            String message, String code, Map<String, String> errors, Map<String, Object> extra) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("ok", false);
        body.put("code", code);
        body.put("message", message);
        body.put("errors", errors == null ? Map.of() : errors);
        body.put("field", errors == null || errors.isEmpty()
                ? null
                : errors.keySet().iterator().next());
        if (extra != null) {
            body.putAll(extra);
        }
        return body;
    }

    /** Non-JavaScript fallback: re-render the page with the server's verdict. */
    private ModelAndView renderForm(
            String view,
            HttpStatus status,
            String message,
            Map<String, String> errors,
            String email,
            boolean remember,
            String nextUrl,
            String name) {

        ModelAndView mav = new ModelAndView(view, status);
        Map<String, Object> form = new LinkedHashMap<>();
        form.put("email", email);
        form.put("name", name == null ? "" : name);
        form.put("remember", remember);
        mav.addObject("error", message);
        mav.addObject("errors", errors);
        mav.addObject("form", form);
        mav.addObject("notice", "");
        mav.addObject("nextUrl", nextUrl);
        mav.addObject("pageMode", view);
        return mav;
    }

    private static String value(MultiValueMap<String, String> params, String key) {
        String value = params.getFirst(key);
        return value == null ? "" : value;
    }

    private static boolean truthy(String value) {
        if (value == null) {
            return false;
        }
        String lowered = value.trim().toLowerCase(Locale.ROOT);
        return "1".equals(lowered) || "true".equals(lowered)
                || "on".equals(lowered) || "yes".equals(lowered);
    }

    private static String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }
}

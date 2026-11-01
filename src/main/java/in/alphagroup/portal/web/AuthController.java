package in.alphagroup.portal.web;

import in.alphagroup.portal.config.AlphaProperties;
import in.alphagroup.portal.policy.Countries;
import in.alphagroup.portal.policy.PasswordPolicy;
import in.alphagroup.portal.policy.PhoneNumbers;
import in.alphagroup.portal.policy.PolicyResult;
import in.alphagroup.portal.reset.PasswordResetService;
import in.alphagroup.portal.security.AttemptKeys;
import in.alphagroup.portal.security.LoginThrottle;
import in.alphagroup.portal.user.PortalUser;
import in.alphagroup.portal.user.PortalUserService;
import in.alphagroup.portal.util.Emails;
import in.alphagroup.portal.util.Requests;
import in.alphagroup.portal.util.SafeRedirect;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.authentication.LockedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.rememberme.RememberMeServices;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.ModelAndView;

/**
 * Sign in, sign up, sign out and "forgot password".
 *
 * <p>Credentials are verified by Spring Security against the BCrypt hash stored in
 * {@code portal_user}; there is no external identity provider. Nothing trusts the
 * browser: shapes are checked, the throttle is consulted and the password policy
 * is applied before any row is written.
 *
 * <p>Each handler answers either HTML (a rendered page carrying server-side
 * errors) or JSON (when {@code auth.js} submitted with {@code fetch}), which is
 * why the return type is {@link Object}: Spring picks the view or the message
 * converter from the value actually returned.
 */
@Controller
public class AuthController {

    private static final Logger log = LoggerFactory.getLogger(AuthController.class);
    private static final int MAX_NAME_LENGTH = 80;
    private static final String GENERIC_LOGIN_ERROR =
            "Invalid e-mail or password. Please try again.";

    private final AuthenticationManager authenticationManager;
    private final SecurityContextRepository securityContextRepository;
    private final RememberMeServices rememberMeServices;
    private final PortalUserService userService;
    private final PasswordResetService resetService;
    private final LoginThrottle throttle;
    private final AlphaProperties properties;

    public AuthController(
            AuthenticationManager authenticationManager,
            SecurityContextRepository securityContextRepository,
            RememberMeServices rememberMeServices,
            PortalUserService userService,
            PasswordResetService resetService,
            LoginThrottle throttle,
            AlphaProperties properties) {
        this.authenticationManager = authenticationManager;
        this.securityContextRepository = securityContextRepository;
        this.rememberMeServices = rememberMeServices;
        this.userService = userService;
        this.resetService = resetService;
        this.throttle = throttle;
        this.properties = properties;
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

        if (isSignedIn()) {
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
        if (!properties.getSignup().isEnabled()) {
            return "redirect:/login";
        }
        if (isSignedIn()) {
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
            HttpServletResponse response,
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
            return renderForm("login", HttpStatus.UNPROCESSABLE_ENTITY, message, errors,
                    Map.of("email", email, "remember", remember), nextUrl);
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
            return renderForm("login", HttpStatus.TOO_MANY_REQUESTS, message, throttleErrors,
                    Map.of("email", email, "remember", remember), nextUrl);
        }

        // 3. Credentials — Spring Security checks the BCrypt hash.
        try {
            Authentication authentication = authenticationManager.authenticate(
                    UsernamePasswordAuthenticationToken.unauthenticated(email, password));

            throttle.reset(keys);
            PortalUser user = (PortalUser) authentication.getPrincipal();
            userService.touchLastLogin(user);

            establishSession(request, response, session, authentication, remember, user);

            if (json) {
                Map<String, Object> body = new LinkedHashMap<>();
                body.put("ok", true);
                body.put("message", "Signed in. Taking you to your dashboard…");
                body.put("redirect", nextUrl);
                return ResponseEntity.ok(body);
            }
            return "redirect:" + nextUrl;

        } catch (LockedException | DisabledException locked) {
            String message = "This account is not available. Please contact the IT desk.";
            Map<String, String> failureErrors = Map.of("form", message);
            if (json) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN)
                        .body(errorBody(message, "account_disabled", failureErrors, null));
            }
            return renderForm("login", HttpStatus.FORBIDDEN, message, failureErrors,
                    Map.of("email", email, "remember", remember), nextUrl);

        } catch (AuthenticationException failure) {
            int remaining = throttle.registerFailure(keys);
            String message = GENERIC_LOGIN_ERROR;
            if (remaining <= 2) {
                message = message + " " + remaining
                        + " attempt(s) left before this account is temporarily locked.";
            }
            Map<String, String> failureErrors = new LinkedHashMap<>();
            failureErrors.put("form", message);

            log.info("Failed sign-in for {} from {} ({} left)",
                    email, AttemptKeys.clientAddress(request), remaining);

            if (json) {
                Map<String, Object> extra = new LinkedHashMap<>();
                extra.put("attemptsRemaining", remaining);
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                        .body(errorBody(message, "invalid_credentials", failureErrors, extra));
            }
            return renderForm("login", HttpStatus.UNAUTHORIZED, message, failureErrors,
                    Map.of("email", email, "remember", remember), nextUrl);
        }
    }

    // ------------------------------------------------------------------
    // Sign up
    // ------------------------------------------------------------------

    @PostMapping("/signup")
    public Object signup(
            @RequestParam MultiValueMap<String, String> params,
            HttpServletRequest request,
            HttpServletResponse response,
            HttpSession session) {

        if (!properties.getSignup().isEnabled()) {
            return "redirect:/login";
        }

        String name = value(params, "name").trim();
        String email = value(params, "email").trim();
        String password = value(params, "password");
        String confirm = value(params, "confirm_password");
        String countryCode = value(params, "country_code").trim();
        String phone = value(params, "phone").trim();
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

        // The picker offers every dialling code, but only this check decides.
        PhoneNumbers.Result phoneResult = PhoneNumbers.validate(countryCode, phone);
        if (!phoneResult.valid()) {
            errors.put("phone", phoneResult.error());
        }

        if (password.isEmpty()) {
            errors.put("password", "Enter a password.");
        } else {
            // The policy is applied here — on the server — before anything is stored.
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
            return renderForm("signup", HttpStatus.UNPROCESSABLE_ENTITY, message, errors,
                    signupForm(name, email, countryCode, phone), "/dashboard");
        }

        try {
            PortalUser user = userService.register(email, password, name, phoneResult.e164());
            Authentication authentication = UsernamePasswordAuthenticationToken.authenticated(
                    user, null, user.getAuthorities());
            establishSession(request, response, session, authentication, true, user);

            if (json) {
                Map<String, Object> body = new LinkedHashMap<>();
                body.put("ok", true);
                body.put("message", "Welcome to Alpha Group of India.");
                body.put("redirect", "/dashboard");
                return ResponseEntity.ok(body);
            }
            return "redirect:/dashboard";

        } catch (PortalUserService.EmailAlreadyUsedException taken) {
            String message = "An account with this e-mail already exists. Try signing in.";
            Map<String, String> failureErrors = Map.of("email", message);
            if (json) {
                return ResponseEntity.status(HttpStatus.CONFLICT)
                        .body(errorBody(message, "user_exists", failureErrors, null));
            }
            return renderForm("signup", HttpStatus.CONFLICT, message, failureErrors,
                    signupForm(name, email, countryCode, phone), "/dashboard");
        }
    }

    // ------------------------------------------------------------------
    // Sign out / password reset
    // ------------------------------------------------------------------

    @PostMapping("/logout")
    public Object logout(
            HttpServletRequest request, HttpServletResponse response, HttpSession session) {

        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        rememberMeServices.logout(request, response, authentication);
        SecurityContextHolder.clearContext();
        session.invalidate();

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

        // Always answered generically: an unknown address must look identical to a
        // known one, otherwise this endpoint becomes an account oracle.
        resetService.issue(email);

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
    // Session plumbing
    // ------------------------------------------------------------------

    /**
     * Rotates the session id, persists the authenticated context and, when the
     * box was ticked, writes the remember-me cookie.
     */
    private void establishSession(
            HttpServletRequest request,
            HttpServletResponse response,
            HttpSession session,
            Authentication authentication,
            boolean remember,
            PortalUser user) {

        // A fresh id, so a pre-login identifier cannot be replayed.
        request.changeSessionId();

        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(authentication);
        SecurityContextHolder.setContext(context);
        securityContextRepository.saveContext(context, request, response);

        session.setAttribute("auth.loggedInAt", Instant.now().getEpochSecond());
        session.setAttribute("auth.remember", remember);
        int days = Math.max(1, properties.getSecurity().getRememberMeDays());
        session.setMaxInactiveInterval(remember ? days * 86_400 : 1_800);

        if (remember) {
            rememberMeServices.loginSuccess(request, response, authentication);
        }
        log.info("Signed in: {} (remember={})", user.getEmail(), remember);
    }

    private boolean isSignedIn() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        return authentication != null
                && authentication.isAuthenticated()
                && authentication.getPrincipal() instanceof PortalUser;
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
    private static Map<String, Object> signupForm(
            String name, String email, String countryCode, String phone) {
        Map<String, Object> form = new LinkedHashMap<>();
        form.put("name", name);
        form.put("email", email);
        form.put("remember", Boolean.FALSE);
        form.put("country",
                Countries.byIso(countryCode) == null ? Countries.DEFAULT_ISO : countryCode);
        form.put("phone", phone);
        return form;
    }

    private ModelAndView renderForm(
            String view,
            HttpStatus status,
            String message,
            Map<String, String> errors,
            Map<String, Object> submitted,
            String nextUrl) {

        ModelAndView mav = new ModelAndView(view, status);
        Map<String, Object> form = new LinkedHashMap<>();
        form.put("email", submitted.getOrDefault("email", ""));
        form.put("name", submitted.getOrDefault("name", ""));
        form.put("remember", submitted.getOrDefault("remember", Boolean.FALSE));
        form.put("country", submitted.getOrDefault("country", Countries.DEFAULT_ISO));
        form.put("phone", submitted.getOrDefault("phone", ""));
        mav.addObject("error", message);
        mav.addObject("errors", errors);
        mav.addObject("form", form);
        mav.addObject("notice", "");
        mav.addObject("nextUrl", nextUrl);
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

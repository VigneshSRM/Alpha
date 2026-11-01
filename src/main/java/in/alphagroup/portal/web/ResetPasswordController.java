package in.alphagroup.portal.web;

import in.alphagroup.portal.policy.PasswordPolicy;
import in.alphagroup.portal.policy.PolicyResult;
import in.alphagroup.portal.reset.PasswordResetService;
import in.alphagroup.portal.user.PortalUser;
import in.alphagroup.portal.user.PortalUserService;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

/**
 * Completes a reset started on the sign-in page.
 *
 * <p>The token is single-use, expires after {@code alpha.security.reset-token-minutes}
 * and is stored only as a SHA-256 digest. The new password goes through exactly the
 * same {@link PasswordPolicy} as sign-up.
 */
@Controller
public class ResetPasswordController {

    private static final Logger log = LoggerFactory.getLogger(ResetPasswordController.class);

    private final PasswordResetService resetService;
    private final PortalUserService userService;

    public ResetPasswordController(
            PasswordResetService resetService, PortalUserService userService) {
        this.resetService = resetService;
        this.userService = userService;
    }

    @GetMapping("/reset-password")
    public String form(@RequestParam(required = false) String token, Model model) {
        Optional<PortalUser> user = resetService.resolve(token);
        if (user.isEmpty()) {
            model.addAttribute("invalid", true);
            model.addAttribute("token", "");
            model.addAttribute("email", "");
            model.addAttribute("errors", Map.of());
            model.addAttribute("pageMode", "reset");
            return "reset-password";
        }
        model.addAttribute("invalid", false);
        model.addAttribute("token", token);
        model.addAttribute("email", user.get().getEmail());
        model.addAttribute("errors", Map.of());
        model.addAttribute("pageMode", "reset");
        return "reset-password";
    }

    @PostMapping("/reset-password")
    public String submit(
            @RequestParam MultiValueMap<String, String> params, Model model) {

        String token = value(params, "token");
        String password = value(params, "password");
        String confirm = value(params, "confirm_password");

        Optional<PortalUser> maybeUser = resetService.resolve(token);
        if (maybeUser.isEmpty()) {
            model.addAttribute("invalid", true);
            model.addAttribute("token", "");
            model.addAttribute("email", "");
            model.addAttribute("errors", Map.of());
            model.addAttribute("pageMode", "reset");
            return "reset-password";
        }
        PortalUser user = maybeUser.get();

        Map<String, String> errors = new LinkedHashMap<>();
        if (password.isEmpty()) {
            errors.put("password", "Enter a password.");
        } else {
            PolicyResult result =
                    PasswordPolicy.evaluate(password, user.getEmail(), user.getFullName());
            if (!result.valid()) {
                errors.put("password", result.violations().get(0));
            }
        }
        if (confirm.isEmpty()) {
            errors.put("confirm_password", "Re-enter your password.");
        } else if (!confirm.equals(password)) {
            errors.put("confirm_password", "Passwords do not match.");
        }

        if (!errors.isEmpty()) {
            model.addAttribute("invalid", false);
            model.addAttribute("token", token);
            model.addAttribute("email", user.getEmail());
            model.addAttribute("errors", errors);
            model.addAttribute("error", errors.values().iterator().next());
            model.addAttribute("pageMode", "reset");
            return "reset-password";
        }

        userService.changePassword(user, password);
        resetService.consume(token);
        log.info("Password reset completed for {}", user.getEmail());
        return "redirect:/login?notice=Password%20updated.%20Please%20sign%20in.";
    }

    private static String value(MultiValueMap<String, String> params, String key) {
        String value = params.getFirst(key);
        return value == null ? "" : value;
    }
}

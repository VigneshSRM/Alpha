package in.alphagroup.portal.web;

import in.alphagroup.portal.policy.PasswordPolicy;
import in.alphagroup.portal.policy.PolicyResult;
import in.alphagroup.portal.policy.Rule;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Live password verdict for the "!" indicator.
 *
 * <p>Called by {@code auth.js} on every keystroke. The answer comes from the very
 * same {@link PasswordPolicy#evaluate} used when the form is submitted, so the
 * browser can never be told "green tick" for a password the server would then
 * reject. The password is never logged or persisted.
 */
@RestController
public class PasswordCheckController {

    @PostMapping("/api/password-check")
    public Map<String, Object> check(
            @RequestParam MultiValueMap<String, String> params, CsrfToken csrfToken) {

        String password = value(params, "password");
        String email = value(params, "email").trim();
        String name = value(params, "name").trim();

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("ok", true);

        if (password.length() > PasswordPolicy.MAX_LENGTH) {
            body.put("valid", false);
            body.put("empty", false);
            body.put("checks", allFalse());
            body.put("violations", List.of(
                    "Password must be at most " + PasswordPolicy.MAX_LENGTH + " characters."));
            body.put("csrfToken", csrfToken.getToken());
            return body;
        }

        if (password.isEmpty()) {
            body.put("valid", false);
            body.put("empty", true);
            body.put("checks", allFalse());
            body.put("violations", List.of());
            body.put("csrfToken", csrfToken.getToken());
            return body;
        }

        PolicyResult result = PasswordPolicy.evaluate(password, email, name);
        body.put("valid", result.valid());
        body.put("empty", false);
        body.put("checks", result.checks());
        body.put("violations", result.violations());
        body.put("score", result.score());
        body.put("strength", result.strength());
        // A rotated token is handed back so the caller can retry without a reload.
        body.put("csrfToken", csrfToken.getToken());
        return body;
    }

    private static Map<String, Boolean> allFalse() {
        Map<String, Boolean> checks = new LinkedHashMap<>();
        for (Rule rule : PasswordPolicy.RULES) {
            checks.put(rule.key(), Boolean.FALSE);
        }
        return checks;
    }

    private static String value(MultiValueMap<String, String> params, String key) {
        String value = params.getFirst(key);
        return value == null ? "" : value;
    }
}

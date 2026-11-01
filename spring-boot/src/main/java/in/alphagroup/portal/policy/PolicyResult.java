package in.alphagroup.portal.policy;

import java.util.List;
import java.util.Map;

/**
 * Outcome of evaluating one password.
 *
 * @param valid      true only when every check passed
 * @param checks     rule key to pass/fail, in display order
 * @param violations human-readable reasons, most important first
 * @param score      0-100 strength score
 * @param strength   weak | fair | good | strong
 */
public record PolicyResult(
        boolean valid,
        Map<String, Boolean> checks,
        List<String> violations,
        int score,
        String strength) {

    public boolean check(String key) {
        return Boolean.TRUE.equals(checks.get(key));
    }
}

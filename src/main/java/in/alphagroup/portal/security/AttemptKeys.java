package in.alphagroup.portal.security;

import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import java.util.Locale;

/** Builds the throttle keys: one per e-mail, one per client address. */
public final class AttemptKeys {

    private AttemptKeys() {
    }

    public static List<String> of(HttpServletRequest request, String email) {
        return List.of(
                "email:" + (email == null ? "" : email.trim().toLowerCase(Locale.ROOT)),
                "ip:" + clientAddress(request));
    }

    /** Cloudflare → first proxy hop → socket peer, in that order. */
    public static String clientAddress(HttpServletRequest request) {
        String cf = request.getHeader("CF-Connecting-IP");
        if (cf != null && !cf.isBlank()) {
            return cf.trim();
        }
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            int comma = forwarded.indexOf(',');
            String first = comma >= 0 ? forwarded.substring(0, comma) : forwarded;
            if (!first.isBlank()) {
                return first.trim();
            }
        }
        String remote = request.getRemoteAddr();
        return remote == null || remote.isBlank() ? "unknown" : remote;
    }
}

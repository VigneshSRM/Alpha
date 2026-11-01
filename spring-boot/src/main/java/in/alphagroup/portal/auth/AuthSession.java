package in.alphagroup.portal.auth;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * A Supabase (GoTrue) session, as held in the HTTP session.
 *
 * @param accessToken  short-lived JWT
 * @param refreshToken long-lived token used to silently renew
 * @param expiresAt    epoch seconds at which {@code accessToken} expires
 * @param user         raw GoTrue user object (id, email, user_metadata…)
 */
public record AuthSession(
        String accessToken,
        String refreshToken,
        long expiresAt,
        Map<String, Object> user) {

    public AuthSession {
        // Deliberately null-tolerant: GoTrue user payloads carry null fields.
        user = user == null
                ? Map.of()
                : Collections.unmodifiableMap(new LinkedHashMap<>(user));
    }

    public String email() {
        Object value = user.get("email");
        return value == null ? "" : value.toString();
    }

    public String id() {
        Object value = user.get("id");
        return value == null ? "" : value.toString();
    }

    /** {@code user_metadata.full_name || user_metadata.name || e-mail local part}. */
    @SuppressWarnings("unchecked")
    public String displayName() {
        Object metadata = user.get("user_metadata");
        if (metadata instanceof Map<?, ?> map) {
            for (String key : new String[] {"full_name", "name"}) {
                Object value = ((Map<String, Object>) map).get(key);
                if (value != null && !value.toString().isBlank()) {
                    return value.toString();
                }
            }
        }
        String email = email();
        int at = email.indexOf('@');
        return at > 0 ? email.substring(0, at) : "there";
    }

    public boolean isExpired() {
        return expiresAt > 0 && expiresAt <= System.currentTimeMillis() / 1000L;
    }
}

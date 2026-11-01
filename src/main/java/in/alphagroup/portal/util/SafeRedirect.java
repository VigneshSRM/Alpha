package in.alphagroup.portal.util;

/**
 * Guard for the {@code next} parameter — no open redirects.
 *
 * <p>Accepts only same-site relative paths. Rejects absolute URLs, protocol
 * relative URLs ({@code //evil.example}), backslash tricks ({@code /\evil}) and
 * anything carrying a scheme.
 */
public final class SafeRedirect {

    private SafeRedirect() {
    }

    public static String sanitise(String candidate, String fallback) {
        if (candidate == null) {
            return fallback;
        }
        String value = candidate.trim();
        if (value.isEmpty() || !value.startsWith("/") || value.startsWith("//")) {
            return fallback;
        }
        if (value.indexOf('\\') >= 0) {
            return fallback;
        }
        String path = value.indexOf('?') >= 0 ? value.substring(0, value.indexOf('?')) : value;
        if (path.indexOf(':') >= 0) {
            return fallback;
        }
        return value;
    }
}

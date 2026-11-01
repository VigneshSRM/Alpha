package in.alphagroup.portal.util;

import jakarta.servlet.http.HttpServletRequest;
import java.util.Locale;

/** Small request helpers shared by the controllers. */
public final class Requests {

    private Requests() {
    }

    /**
     * True when the browser submitted with {@code fetch()} rather than a normal
     * form post, in which case the controller answers with JSON.
     *
     * <p>Mirrors the Python {@code _is_async()} helper: the JavaScript always
     * sends {@code X-Requested-With: fetch}, and anything that clearly prefers
     * JSON over HTML is treated the same way.
     */
    public static boolean wantsJson(HttpServletRequest request) {
        if ("fetch".equalsIgnoreCase(request.getHeader("X-Requested-With"))) {
            return true;
        }
        String accept = request.getHeader("Accept");
        if (accept == null) {
            return false;
        }
        String lowered = accept.toLowerCase(Locale.ROOT);
        return lowered.contains("application/json") && !lowered.contains("text/html");
    }
}

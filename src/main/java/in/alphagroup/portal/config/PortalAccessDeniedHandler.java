package in.alphagroup.portal.config;

import in.alphagroup.portal.util.Requests;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.security.web.csrf.InvalidCsrfTokenException;
import org.springframework.security.web.csrf.MissingCsrfTokenException;

/**
 * 403 responses, including expired CSRF tokens.
 *
 * <p>A stale token is the one 403 a user actually hits (a tab left open over a
 * deploy). Instead of a dead end, the browser receives {@code {"code":"csrf"}}
 * with a freshly minted token — {@code auth.js} drops it into the hidden field
 * and retries once, silently.
 */
public class PortalAccessDeniedHandler implements AccessDeniedHandler {

    private final NamedCsrfTokenRepository tokens;

    public PortalAccessDeniedHandler(NamedCsrfTokenRepository tokens) {
        this.tokens = tokens;
    }

    @Override
    public void handle(
            HttpServletRequest request,
            HttpServletResponse response,
            AccessDeniedException accessDeniedException) throws IOException {

        boolean csrfProblem = accessDeniedException instanceof InvalidCsrfTokenException
                || accessDeniedException instanceof MissingCsrfTokenException;

        if (!Requests.wantsJson(request)) {
            response.setStatus(HttpServletResponse.SC_FORBIDDEN);
            response.setContentType(MediaType.TEXT_HTML_VALUE);
            response.getWriter().write(
                    "<!doctype html><meta charset=\"utf-8\">"
                            + "<title>Session expired</title>"
                            + "<p>Your session expired. <a href=\"/login\">Sign in again</a>.</p>");
            return;
        }

        response.setStatus(HttpServletResponse.SC_FORBIDDEN);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);

        String message = csrfProblem
                ? "Your session expired. Please try again."
                : "You do not have access to that resource.";

        StringBuilder json = new StringBuilder();
        json.append("{\"ok\":false,\"code\":\"")
                .append(csrfProblem ? "csrf" : "forbidden")
                .append("\",\"message\":\"").append(message).append('"');

        if (csrfProblem) {
            CsrfToken fresh = tokens.generateToken(request);
            tokens.saveToken(fresh, request, response);
            json.append(",\"csrfToken\":\"").append(fresh.getToken()).append('"');
        }
        json.append('}');
        response.getWriter().write(json.toString());
    }
}

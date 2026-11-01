package in.alphagroup.portal.config;

import in.alphagroup.portal.util.Requests;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import org.springframework.http.MediaType;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;

/**
 * What happens when an anonymous visitor asks for a protected page.
 *
 * <p>Browsers get a redirect to {@code /login?next=…} (the login page echoes the
 * value back into a hidden field so the user lands where they meant to go);
 * {@code fetch()} calls get a 401 with a JSON body they can display.
 */
public class PortalAuthenticationEntryPoint implements AuthenticationEntryPoint {

    @Override
    public void commence(
            HttpServletRequest request,
            HttpServletResponse response,
            AuthenticationException authException) throws IOException {

        if (Requests.wantsJson(request)) {
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            response.getWriter().write(
                    "{\"ok\":false,\"code\":\"unauthenticated\","
                            + "\"message\":\"Please sign in to continue.\"}");
            return;
        }

        String target = request.getRequestURI();
        String query = request.getQueryString();
        if (query != null && !query.isBlank()) {
            target = target + "?" + query;
        }
        String encoded = URLEncoder.encode(target, StandardCharsets.UTF_8);
        response.sendRedirect(request.getContextPath() + "/login?next=" + encoded);
    }
}

package in.alphagroup.portal.web;

import in.alphagroup.portal.config.AlphaProperties;
import in.alphagroup.portal.user.PortalUser;
import jakarta.servlet.http.HttpSession;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/** JSON session status, used by the dashboard clock. */
@RestController
public class SessionController {

    private final AlphaProperties properties;

    public SessionController(AlphaProperties properties) {
        this.properties = properties;
    }

    @GetMapping("/session")
    public ResponseEntity<Map<String, Object>> session(
            Authentication authentication, HttpSession session) {

        if (authentication == null || !(authentication.getPrincipal() instanceof PortalUser user)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("ok", false, "code", "unauthenticated"));
        }

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("ok", true);
        body.put("email", user.getEmail());
        body.put("expires_at",
                session.getLastAccessedTime() / 1000L + session.getMaxInactiveInterval());
        body.put("now", System.currentTimeMillis() / 1000L);
        body.put("backend", properties.getProviderName());
        return ResponseEntity.ok(body);
    }
}

package in.alphagroup.portal.web;

import in.alphagroup.portal.auth.AuthSession;
import in.alphagroup.portal.config.AlphaProperties;
import in.alphagroup.portal.security.SessionAuth;
import jakarta.servlet.http.HttpSession;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/** JSON session status, used by the dashboard clock. */
@RestController
public class SessionController {

    private final SessionAuth sessionAuth;
    private final AlphaProperties properties;

    public SessionController(SessionAuth sessionAuth, AlphaProperties properties) {
        this.sessionAuth = sessionAuth;
        this.properties = properties;
    }

    @GetMapping("/session")
    public ResponseEntity<Map<String, Object>> session(HttpSession session) {
        AuthSession auth = sessionAuth.current(session).orElse(null);
        if (auth == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("ok", false, "code", "unauthenticated"));
        }
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("ok", true);
        body.put("email", auth.email());
        body.put("expires_at", auth.expiresAt());
        body.put("now", System.currentTimeMillis() / 1000L);
        body.put("backend", properties.getProviderName().toLowerCase());
        return ResponseEntity.ok(body);
    }
}

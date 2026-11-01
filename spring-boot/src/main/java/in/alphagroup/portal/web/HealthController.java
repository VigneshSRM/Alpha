package in.alphagroup.portal.web;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/** Liveness probe for load balancers. Public, and says nothing sensitive. */
@RestController
public class HealthController {

    @GetMapping("/healthz")
    public Map<String, Object> health() {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("ok", true);
        body.put("status", "healthy");
        body.put("time", Instant.now().toString());
        return body;
    }
}

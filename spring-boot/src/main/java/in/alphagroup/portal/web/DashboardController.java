package in.alphagroup.portal.web;

import in.alphagroup.portal.auth.AuthSession;
import in.alphagroup.portal.config.AlphaProperties;
import in.alphagroup.portal.security.SessionAuth;
import jakarta.servlet.http.HttpSession;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

/** The protected area. Spring Security has already rejected anonymous callers. */
@Controller
public class DashboardController {

    private final SessionAuth sessionAuth;
    private final AlphaProperties properties;

    public DashboardController(SessionAuth sessionAuth, AlphaProperties properties) {
        this.sessionAuth = sessionAuth;
        this.properties = properties;
    }

    @GetMapping("/dashboard")
    public String dashboard(HttpSession session, Model model) {
        if (!sessionAuth.refreshIfNeeded(session)) {
            return "redirect:/login?notice=Your%20session%20expired.%20Please%20sign%20in%20again.";
        }
        AuthSession auth = sessionAuth.current(session).orElse(null);
        if (auth == null) {
            return "redirect:/login";
        }

        Map<String, Object> account = new LinkedHashMap<>();
        account.put("name", auth.displayName());
        account.put("email", auth.email());
        account.put("id", auth.id());
        account.put("joined", created(auth));

        Map<String, Object> sessionInfo = new LinkedHashMap<>();
        sessionInfo.put("backend", properties.getProviderName().toLowerCase());
        sessionInfo.put("expires_at", auth.expiresAt());
        sessionInfo.put("logged_in_at", sessionAuth.loggedInAt(session));

        model.addAttribute("pageTitle", "Dashboard");
        model.addAttribute("account", account);
        model.addAttribute("sessionInfo", sessionInfo);
        return "dashboard";
    }

    private String created(AuthSession auth) {
        Object value = auth.user().get("created_at");
        if (value == null) {
            return "";
        }
        String text = value.toString();
        return text.length() >= 10 ? text.substring(0, 10) : text;
    }
}

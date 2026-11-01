package in.alphagroup.portal.web;

import in.alphagroup.portal.config.AlphaProperties;
import in.alphagroup.portal.onboarding.OnboardingService;
import in.alphagroup.portal.policy.PhoneNumbers;
import in.alphagroup.portal.user.PortalUser;
import jakarta.servlet.http.HttpSession;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.GetMapping;

/** The protected area. Spring Security has already rejected anonymous callers. */
@Controller
public class DashboardController {

    private static final DateTimeFormatter DAY =
            DateTimeFormatter.ofPattern("d MMM yyyy", Locale.ENGLISH).withZone(ZoneOffset.UTC);

    private final AlphaProperties properties;
    private final OnboardingService onboarding;

    public DashboardController(AlphaProperties properties, OnboardingService onboarding) {
        this.properties = properties;
        this.onboarding = onboarding;
    }

    @GetMapping("/dashboard")
    public String dashboard(
            Authentication authentication,
            HttpSession session,
            @RequestParam(value = "welcome", required = false) String welcome,
            Model model) {
        PortalUser user = (PortalUser) authentication.getPrincipal();

        // Nobody reaches the dashboard without a business behind the account.
        if (onboarding.forUser(user.getId()).isEmpty()) {
            return "redirect:/onboarding";
        }

        Map<String, Object> account = new LinkedHashMap<>();
        account.put("name", user.getFullName());
        account.put("email", user.getEmail());
        account.put("phone", user.getPhone() == null ? "" : PhoneNumbers.display(user.getPhone()));
        account.put("id", user.getId());
        account.put("joined", user.getCreatedAt() == null ? "" : DAY.format(user.getCreatedAt()));
        account.put("lastLogin", user.getLastLoginAt() == null ? "" : DAY.format(user.getLastLoginAt()));

        long expiresAt = session.getLastAccessedTime() / 1000L + session.getMaxInactiveInterval();
        Object loggedInAt = session.getAttribute("auth.loggedInAt");

        Map<String, Object> sessionInfo = new LinkedHashMap<>();
        sessionInfo.put("backend", properties.getProviderName());
        sessionInfo.put("expires_at", expiresAt);
        sessionInfo.put("logged_in_at", loggedInAt instanceof Long epoch
                ? epoch
                : Instant.now().getEpochSecond());

        // A brand-new account lands here with ?welcome=1 and gets told so.
        model.addAttribute("notice", welcome == null
                ? ""
                : "Your account is ready. Welcome to Alpha Group of India.");

        model.addAttribute("pageTitle", "Dashboard");
        model.addAttribute("account", account);
        model.addAttribute("sessionInfo", sessionInfo);
        return "dashboard";
    }
}

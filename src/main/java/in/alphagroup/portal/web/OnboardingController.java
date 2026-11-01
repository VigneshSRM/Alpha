package in.alphagroup.portal.web;

import in.alphagroup.portal.onboarding.BusinessProfile;
import in.alphagroup.portal.onboarding.OnboardingRules;
import in.alphagroup.portal.onboarding.OnboardingService;
import in.alphagroup.portal.user.PortalUser;
import jakarta.servlet.http.HttpServletResponse;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

/**
 * The step after registration: the business profile.
 *
 * <p>Sign-up creates the account; this page captures the business behind it.
 * Reaching {@code /dashboard} without a profile shows the same page again, so
 * nobody slips past it.
 */
@Controller
public class OnboardingController {

    private final OnboardingService onboarding;

    public OnboardingController(OnboardingService onboarding) {
        this.onboarding = onboarding;
    }

    @GetMapping("/onboarding")
    public String form(Authentication authentication, Model model) {
        PortalUser user = (PortalUser) authentication.getPrincipal();
        if (onboarding.forUser(user.getId()).isPresent()) {
            return "redirect:/dashboard";
        }
        return render(model, OnboardingService.blankForm(), Map.of());
    }

    @PostMapping("/onboarding")
    public String submit(
            @RequestParam MultiValueMap<String, String> params,
            Authentication authentication,
            HttpServletResponse response,
            Model model) {

        PortalUser user = (PortalUser) authentication.getPrincipal();
        Map<String, String> form = flatten(params);
        Map<String, String> errors = OnboardingRules.validate(form);

        if (!errors.isEmpty()) {
            response.setStatus(HttpServletResponse.SC_UNPROCESSABLE_ENTITY);
            return render(model, form, errors);
        }

        BusinessProfile saved = onboarding.save(user.getId(), form);
        return "redirect:/dashboard?welcome=1&business="
                + java.net.URLEncoder.encode(saved.getBusinessName(),
                        java.nio.charset.StandardCharsets.UTF_8);
    }

    private String render(Model model, Map<String, String> form, Map<String, String> errors) {
        model.addAttribute("pageMode", "onboarding");
        model.addAttribute("pageTitle", "Set up your business profile");
        model.addAttribute("form", form);
        model.addAttribute("errors", errors);
        model.addAttribute("errorCount", errors.size());
        model.addAttribute("verticals", OnboardingRules.VERTICALS);
        model.addAttribute("businessTypes", OnboardingRules.BUSINESS_TYPES);
        model.addAttribute("states", OnboardingRules.INDIAN_STATES);
        model.addAttribute("fiscalYears", OnboardingRules.fiscalYears());
        model.addAttribute("monthLabels", OnboardingRules.MONTHS);
        model.addAttribute("language", OnboardingRules.LANGUAGE);
        model.addAttribute("timeZone", OnboardingRules.TIME_ZONE);
        model.addAttribute("timeZoneLabel", OnboardingRules.TIME_ZONE_LABEL);
        model.addAttribute("currency", OnboardingRules.CURRENCY);
        model.addAttribute("currencyLabel", OnboardingRules.CURRENCY_LABEL);
        return "onboarding";
    }

    private static Map<String, String> flatten(MultiValueMap<String, String> params) {
        Map<String, String> form = new LinkedHashMap<>();
        params.forEach((key, values) ->
                form.put(key, values.isEmpty() ? "" : values.get(0)));
        return form;
    }
}

package in.alphagroup.portal.web;

import in.alphagroup.portal.modules.ModuleService;
import in.alphagroup.portal.modules.Modules;
import in.alphagroup.portal.user.PortalUser;
import jakarta.servlet.http.HttpServletResponse;
import java.util.List;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

/**
 * The step after the business profile: pick the modules to switch on.
 *
 * <p>The cards fly out from the middle of the page — the animation lives in
 * {@code modules.js}; nothing here depends on it.
 */
@Controller
public class ModulesController {

    private final ModuleService moduleService;

    public ModulesController(ModuleService moduleService) {
        this.moduleService = moduleService;
    }

    @GetMapping("/modules")
    public String form(Authentication authentication, Model model) {
        PortalUser user = (PortalUser) authentication.getPrincipal();
        List<String> selected = moduleService.selected(user.getId());
        model.addAttribute("selected", selected);
        model.addAttribute("first", selected.isEmpty());
        return render(model, selected, null);
    }

    @PostMapping("/modules")
    public String submit(
            @RequestParam(value = "modules", required = false) List<String> modules,
            Authentication authentication,
            HttpServletResponse response,
            Model model) {

        PortalUser user = (PortalUser) authentication.getPrincipal();
        List<String> chosen = modules == null ? List.of() : modules;

        if (chosen.stream().noneMatch(Modules::isKnown)) {
            response.setStatus(HttpServletResponse.SC_UNPROCESSABLE_ENTITY);
            model.addAttribute("first", moduleService.selected(user.getId()).isEmpty());
            return render(model, chosen,
                    "Choose at least one module — you can switch the rest on later.");
        }

        List<String> saved = moduleService.save(user.getId(), chosen);
        return "redirect:/dashboard?welcome=1&modules=" + saved.size();
    }

    private String render(Model model, List<String> selected, String error) {
        model.addAttribute("pageMode", "modules");
        model.addAttribute("pageTitle", "Choose your modules");
        model.addAttribute("modules", Modules.ALL);
        model.addAttribute("selected", selected);
        model.addAttribute("error", error);
        model.addAttribute("selectedCount", selected.size());
        return "modules";
    }
}

package in.alphagroup.portal.web;

import jakarta.servlet.RequestDispatcher;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.boot.web.servlet.error.ErrorController;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.RequestMapping;

/** Renders {@code error.html} in the portal's own design, never a whitelabel page. */
@Controller
public class ErrorPageController implements ErrorController {

    @RequestMapping("/error")
    public String error(HttpServletRequest request, Model model) {
        Object statusAttribute = request.getAttribute(RequestDispatcher.ERROR_STATUS_CODE);
        int status = statusAttribute instanceof Integer code ? code : 500;
        HttpStatus resolved = HttpStatus.resolve(status);
        if (resolved == null) {
            resolved = HttpStatus.INTERNAL_SERVER_ERROR;
        }

        String message = switch (resolved) {
            case NOT_FOUND -> "We could not find that page.";
            case FORBIDDEN -> "You do not have access to that page.";
            case UNAUTHORIZED -> "Please sign in to continue.";
            case BAD_REQUEST -> "That request did not look right.";
            default -> "Something went wrong on our side.";
        };

        model.addAttribute("status", resolved.value());
        model.addAttribute("message", message);
        model.addAttribute("pageTitle", resolved.value() + " · " + resolved.getReasonPhrase());
        return "error";
    }
}

package in.alphagroup.portal.web;

import com.fasterxml.jackson.databind.ObjectMapper;
import in.alphagroup.portal.config.AlphaProperties;
import in.alphagroup.portal.policy.Countries;
import in.alphagroup.portal.policy.PasswordPolicy;
import java.time.Year;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;

/**
 * Values every page needs: brand name, footer year, the checklist rules and the
 * asset fingerprint. Keeps the templates free of controller noise.
 */
@ControllerAdvice
public class GlobalModelAdvice {

    private final AlphaProperties properties;
    private final AssetVersion assetVersion;
    private final String policyJson;

    public GlobalModelAdvice(
            AlphaProperties properties, AssetVersion assetVersion, ObjectMapper mapper) {
        this.properties = properties;
        this.assetVersion = assetVersion;
        this.policyJson = toJson(policyMap(), mapper);
    }

    @ModelAttribute
    public void populate(Model model) {
        model.addAttribute("appName", properties.getAppName());
        model.addAttribute("providerName", properties.getProviderName());
        model.addAttribute("supportEmail", properties.getSupportEmail());
        model.addAttribute("year", Year.now().getValue());
        model.addAttribute("assetV", assetVersion.getVersion());
        model.addAttribute("rules", PasswordPolicy.RULES);
        model.addAttribute("policy", policyMap());
        model.addAttribute("policyJson", policyJson);
        model.addAttribute("supportsSignup", properties.getSignup().isEnabled());
        model.addAttribute("countries", Countries.ALL);
        model.addAttribute("defaultCountry", Countries.DEFAULT_ISO);
        model.addAttribute("minLength", PasswordPolicy.MIN_LENGTH);
        model.addAttribute("maxLength", PasswordPolicy.MAX_LENGTH);
    }

    public static Map<String, Integer> policyMap() {
        Map<String, Integer> policy = new LinkedHashMap<>();
        policy.put("minLength", PasswordPolicy.MIN_LENGTH);
        policy.put("maxLength", PasswordPolicy.MAX_LENGTH);
        policy.put("minUppercase", PasswordPolicy.MIN_UPPERCASE);
        policy.put("minLowercase", PasswordPolicy.MIN_LOWERCASE);
        policy.put("minDigits", PasswordPolicy.MIN_DIGITS);
        policy.put("minSpecial", PasswordPolicy.MIN_SPECIAL);
        policy.put("maxRepeatedRun", PasswordPolicy.MAX_REPEATED_RUN);
        policy.put("sequenceLength", PasswordPolicy.SEQUENCE_LENGTH);
        return policy;
    }

    private static String toJson(Map<String, Integer> policy, ObjectMapper mapper) {
        try {
            return mapper.writeValueAsString(policy);
        } catch (Exception ex) {
            return "{}";
        }
    }
}

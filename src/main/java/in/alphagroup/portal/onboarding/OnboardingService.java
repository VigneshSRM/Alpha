package in.alphagroup.portal.onboarding;

import in.alphagroup.portal.policy.PhoneNumbers;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Reads and writes the one business profile each account owns. */
@Service
public class OnboardingService {

    private final BusinessProfileRepository profiles;

    public OnboardingService(BusinessProfileRepository profiles) {
        this.profiles = profiles;
    }

    @Transactional(readOnly = true)
    public Optional<BusinessProfile> forUser(Long userId) {
        return userId == null ? Optional.empty() : profiles.findByUserId(userId);
    }

    @Transactional
    public BusinessProfile save(Long userId, Map<String, String> form) {
        BusinessProfile profile = profiles.findByUserId(userId)
                .orElseGet(() -> new BusinessProfile(userId));

        profile.setVertical(OnboardingRules.value(form, "vertical"));
        profile.setBusinessName(OnboardingRules.value(form, "businessName"));
        profile.setPhone(PhoneNumbers.validate(
                OnboardingRules.value(form, "country_code"),
                OnboardingRules.value(form, "phone")).e164());
        profile.setBusinessType(OnboardingRules.value(form, "businessType"));
        profile.setCountry(OnboardingRules.value(form, "country").toUpperCase());
        profile.setState(OnboardingRules.value(form, "state"));
        profile.setGstin(emptyToNull(OnboardingRules.value(form, "gstin").toUpperCase()));
        profile.setAddressLine1(OnboardingRules.value(form, "addressLine1"));
        profile.setAddressLine2(emptyToNull(OnboardingRules.value(form, "addressLine2")));
        profile.setCity(OnboardingRules.value(form, "city"));
        profile.setPincode(OnboardingRules.value(form, "pincode"));
        profile.setLanguage(OnboardingRules.value(form, "language"));
        profile.setTimeZone(OnboardingRules.value(form, "timeZone"));
        profile.setCurrency(OnboardingRules.value(form, "currency"));
        profile.setFiscalYearStart(Integer.parseInt(
                OnboardingRules.value(form, "fiscalYearStart")));
        profile.setInventoryStartDate(LocalDate.parse(
                OnboardingRules.value(form, "inventoryStartDate")));

        return profiles.save(profile);
    }

    /** Blank form with the defaults the page shows. */
    public static Map<String, String> blankForm() {
        Map<String, String> form = new LinkedHashMap<>();
        form.put("vertical", "");
        form.put("businessName", "");
        form.put("country_code", "IN");
        form.put("phone", "");
        form.put("businessType", "");
        form.put("country", "IN");
        form.put("state", "");
        form.put("gstin", "");
        form.put("addressLine1", "");
        form.put("addressLine2", "");
        form.put("city", "");
        form.put("pincode", "");
        form.put("language", OnboardingRules.LANGUAGE);
        form.put("timeZone", OnboardingRules.TIME_ZONE);
        form.put("currency", OnboardingRules.CURRENCY);
        form.put("fiscalYearStart", "4");
        form.put("inventoryStartDate", "");
        return form;
    }

    /** Values to redisplay after a failed submit. */
    public static Map<String, String> formFrom(BusinessProfile profile) {
        Map<String, String> form = blankForm();
        if (profile == null) {
            return form;
        }
        form.put("vertical", profile.getVertical());
        form.put("businessName", profile.getBusinessName());
        form.put("phone", profile.getPhone() == null ? "" : profile.getPhone());
        form.put("businessType", profile.getBusinessType());
        form.put("country", profile.getCountry());
        form.put("state", profile.getState());
        form.put("gstin", profile.getGstin() == null ? "" : profile.getGstin());
        form.put("addressLine1", profile.getAddressLine1());
        form.put("addressLine2", profile.getAddressLine2() == null ? "" : profile.getAddressLine2());
        form.put("city", profile.getCity());
        form.put("pincode", profile.getPincode());
        form.put("language", profile.getLanguage());
        form.put("timeZone", profile.getTimeZone());
        form.put("currency", profile.getCurrency());
        form.put("fiscalYearStart", String.valueOf(profile.getFiscalYearStart()));
        form.put("inventoryStartDate", profile.getInventoryStartDate() == null
                ? "" : profile.getInventoryStartDate().toString());
        return form;
    }

    private static String emptyToNull(String value) {
        return value.isEmpty() ? null : value;
    }
}

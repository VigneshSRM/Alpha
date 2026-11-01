package in.alphagroup.portal.onboarding;

import in.alphagroup.portal.policy.Countries;
import in.alphagroup.portal.policy.PhoneNumbers;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * The business-profile form: the lists it offers and the rules it enforces.
 *
 * <p>Deliberately free of Spring so the same code validates the request in
 * {@code OnboardingController} and can be exercised on its own.
 */
public final class OnboardingRules {

    public static final List<String> VERTICALS = List.of("Retail", "Restaurant");

    public static final List<String> BUSINESS_TYPES = List.of(
            "Apparel, Footwear & Accessories",
            "Arts & Entertainment",
            "Baby & Toddler",
            "Beauty & Cosmetics",
            "Beauty Parlour, SPA & Salon",
            "Books, Toys & Other Hobbies",
            "Electronics & appliances",
            "Food & Beverage",
            "Hardware/DIY & Garden supplies",
            "Home decor & Furniture",
            "Hypermarket & Departmental stores",
            "Pet",
            "Pharma & Healthcare",
            "Services",
            "Sports",
            "Supermarket & Groceries",
            "Vehicle & Parts",
            "Others");

    public static final List<String> INDIAN_STATES = List.of(
            "Andaman and Nicobar Islands",
            "Andhra Pradesh",
            "Arunachal Pradesh",
            "Assam",
            "Bihar",
            "Chandigarh",
            "Chhattisgarh",
            "Dadra and Nagar Haveli and Daman and Diu",
            "Delhi",
            "Goa",
            "Gujarat",
            "Haryana",
            "Himachal Pradesh",
            "Jammu and Kashmir",
            "Jharkhand",
            "Karnataka",
            "Keralam",
            "Ladakh",
            "Lakshadweep",
            "Madhya Pradesh",
            "Maharashtra",
            "Manipur",
            "Meghalaya",
            "Mizoram",
            "Nagaland",
            "Odisha",
            "Puducherry",
            "Punjab",
            "Rajasthan",
            "Sikkim",
            "Tamil Nadu",
            "Telangana",
            "Tripura",
            "Uttar Pradesh",
            "Uttarakhand",
            "West Bengal");

    public static final List<String> MONTHS = List.of(
            "January", "February", "March", "April", "May", "June",
            "July", "August", "September", "October", "November", "December");

    public static final String LANGUAGE = "English";
    public static final String TIME_ZONE = "Asia/Kolkata";
    public static final String TIME_ZONE_LABEL = "( GMT +05:30 ) India Standard Time ( Asia/Kolkata )";
    public static final String CURRENCY = "INR";
    public static final String CURRENCY_LABEL = "INR - Indian Rupee";

    /** 22AAAAA0000A1Z5. */
    private static final Pattern GSTIN =
            Pattern.compile("^[0-9]{2}[A-Z]{5}[0-9]{4}[A-Z][1-9A-Z]Z[0-9A-Z]$");

    private static final Pattern PINCODE = Pattern.compile("^[1-9][0-9]{5}$");

    /** The FSSAI licence number is exactly fourteen digits. */
    private static final Pattern FSSAI = Pattern.compile("^[0-9]{14}$");

    /** Only a restaurant carries a food licence. */
    public static final String RESTAURANT = "Restaurant";

    /** Label for a fiscal year that starts in {@code month}. */
    public static String fiscalYearLabel(int month) {
        String start = MONTHS.get(month - 1);
        String end = MONTHS.get(month == 1 ? 11 : month - 2);
        return start + " - " + end;
    }

    public static Map<Integer, String> fiscalYears() {
        Map<Integer, String> years = new LinkedHashMap<>();
        for (int month = 1; month <= 12; month++) {
            years.put(month, fiscalYearLabel(month));
        }
        return years;
    }

    /** Validates the submitted form; returns field name -> message. */
    public static Map<String, String> validate(Map<String, String> form) {
        Map<String, String> errors = new LinkedHashMap<>();

        String vertical = value(form, "vertical");
        if (vertical.isEmpty()) {
            errors.put("vertical", "Choose whether this is a retail or restaurant business.");
        } else if (!VERTICALS.contains(vertical)) {
            errors.put("vertical", "Choose Retail or Restaurant.");
        }

        String businessName = value(form, "businessName");
        if (businessName.isEmpty()) {
            errors.put("businessName", "Enter your business name.");
        } else if (businessName.length() < 2 || businessName.length() > 120) {
            errors.put("businessName", "The business name must be 2 to 120 characters.");
        }

        PhoneNumbers.Result phone = PhoneNumbers.validate(
                value(form, "country_code"), value(form, "phone"));
        if (!phone.valid()) {
            errors.put("phone", phone.error());
        }

        String businessType = value(form, "businessType");
        if (businessType.isEmpty()) {
            errors.put("businessType", "Choose the business type.");
        } else if (!BUSINESS_TYPES.contains(businessType)) {
            errors.put("businessType", "Choose the business type from the list.");
        }

        String country = value(form, "country");
        if (country.isEmpty()) {
            errors.put("country", "Choose the country of business.");
        } else if (Countries.byIso(country) == null) {
            errors.put("country", "Choose a country from the list.");
        }

        String state = value(form, "state");
        if (state.isEmpty()) {
            errors.put("state", "Choose a state or union territory.");
        } else if ("IN".equalsIgnoreCase(country) && !INDIAN_STATES.contains(state)) {
            errors.put("state", "Choose a state or union territory from the list.");
        }

        String gstin = value(form, "gstin").toUpperCase(Locale.ROOT);
        if (!gstin.isEmpty() && !GSTIN.matcher(gstin).matches()) {
            errors.put("gstin", "A GSTIN is 15 characters, e.g. 22AAAAA0000A1Z5.");
        }

        // Optional, and only asked of a restaurant.
        if (RESTAURANT.equals(vertical)) {
            String fssai = value(form, "fssai").replaceAll("[\\s-]", "");
            if (!fssai.isEmpty() && !FSSAI.matcher(fssai).matches()) {
                errors.put("fssai", "An FSSAI licence number is exactly 14 digits.");
            }
            String expiry = value(form, "fssaiExpiry");
            if (!expiry.isEmpty()) {
                try {
                    LocalDate.parse(expiry);
                } catch (DateTimeParseException malformed) {
                    errors.put("fssaiExpiry", "That date could not be read.");
                }
            }
        }

        String line1 = value(form, "addressLine1");
        if (line1.isEmpty()) {
            errors.put("addressLine1", "Enter the street address.");
        } else if (line1.length() < 5) {
            errors.put("addressLine1", "That address looks too short.");
        }

        String city = value(form, "city");
        if (city.isEmpty()) {
            errors.put("city", "Enter the city or town.");
        }

        String pincode = value(form, "pincode");
        if (pincode.isEmpty()) {
            errors.put("pincode", "Enter the postal code.");
        } else if ("IN".equalsIgnoreCase(country) && !PINCODE.matcher(pincode).matches()) {
            errors.put("pincode", "An Indian PIN code is 6 digits.");
        }

        String fiscalYear = value(form, "fiscalYearStart");
        if (fiscalYear.isEmpty()) {
            errors.put("fiscalYearStart", "Choose when your fiscal year starts.");
        } else {
            try {
                int month = Integer.parseInt(fiscalYear);
                if (month < 1 || month > 12) {
                    errors.put("fiscalYearStart", "Choose a month from the list.");
                }
            } catch (NumberFormatException notANumber) {
                errors.put("fiscalYearStart", "Choose a month from the list.");
            }
        }

        String inventoryStart = value(form, "inventoryStartDate");
        if (inventoryStart.isEmpty()) {
            errors.put("inventoryStartDate", "Choose the date your inventory starts.");
        } else {
            try {
                LocalDate.parse(inventoryStart);
            } catch (DateTimeParseException malformed) {
                errors.put("inventoryStartDate", "That date could not be read.");
            }
        }

        return errors;
    }

    static String value(Map<String, String> form, String key) {
        String value = form.get(key);
        return value == null ? "" : value.trim();
    }

    private OnboardingRules() {
    }
}

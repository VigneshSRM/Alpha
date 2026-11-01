package in.alphagroup.portal.policy;

import java.util.Locale;

/**
 * Mobile-number validation for sign-up.
 *
 * <p>The browser sends the ISO country code and whatever the user typed; this
 * class decides. It accepts the punctuation people actually type (spaces,
 * dashes, dots, parentheses, a single leading {@code +}), strips a national
 * trunk {@code 0}, and then checks the length against the country's own plan in
 * {@link Countries}. The stored form is E.164 ({@code +919876543210}).
 *
 * <p>Keeping this server-side matters: a client could post any country code it
 * likes, so the picker is a convenience, not a source of truth.
 */
public final class PhoneNumbers {

    /** E.164 allows at most 15 digits including the country code. */
    public static final int MAX_E164_DIGITS = 15;

    /**
     * Outcome of one validation.
     *
     * @param valid    true when {@link #e164} may be stored
     * @param e164     normalised number, {@code +<dial><national>}, or empty
     * @param country  the resolved country, or null when the code was unknown
     * @param national national significant number as digits, or empty
     * @param error    user-facing message when {@code valid} is false, else empty
     */
    public record Result(boolean valid, String e164, Country country, String national, String error) {

        static Result rejected(Country country, String national, String error) {
            return new Result(false, "", country, national, error);
        }

        /** {@code +91 98765 43210}, for display. */
        public String formatted() {
            return valid ? country.format(national) : "";
        }
    }

    /**
     * @param isoCode       two-letter country code posted by the form
     * @param rawNumber     what the user typed, punctuation and all
     */
    public static Result validate(String isoCode, String rawNumber) {
        Country country = Countries.byIso(isoCode);
        if (country == null) {
            return Result.rejected(null, "", "Select your country.");
        }

        String raw = rawNumber == null ? "" : rawNumber.trim();
        if (raw.isEmpty()) {
            return Result.rejected(country, "", "Enter your mobile number.");
        }
        if (containsLetters(raw)) {
            return Result.rejected(country, "", "A mobile number can only contain digits.");
        }

        String digits = onlyDigits(raw);
        // People type the trunk prefix out of habit ("098765 43210").
        while (digits.length() > country.maxDigits() && digits.startsWith("0")) {
            digits = digits.substring(1);
        }
        if (digits.isEmpty()) {
            return Result.rejected(country, "", "Enter your mobile number.");
        }
        if (isPlaceholder(digits)) {
            return Result.rejected(country, digits,
                    "That number looks like a placeholder — enter your real mobile number.");
        }

        String e164 = country.dialCode() + digits;
        if (digits.length() < country.minDigits() || digits.length() > country.maxDigits()
                || e164.length() - 1 > MAX_E164_DIGITS) {
            return Result.rejected(country, digits, lengthMessage(country));
        }
        return new Result(true, e164, country, digits, "");
    }

    private static String lengthMessage(Country country) {
        int min = country.minDigits();
        int max = country.maxDigits();
        if (min == max) {
            return "Enter a " + min + "-digit mobile number for " + country.name() + ".";
        }
        return "Enter a mobile number with " + min + " to " + max + " digits for "
                + country.name() + ".";
    }

    private static boolean containsLetters(String raw) {
        for (int i = 0; i < raw.length(); i++) {
            char c = raw.charAt(i);
            if (Character.isLetter(c)) {
                return true;
            }
        }
        return false;
    }

    private static String onlyDigits(String raw) {
        StringBuilder sb = new StringBuilder(raw.length());
        for (int i = 0; i < raw.length(); i++) {
            char c = raw.charAt(i);
            if (c >= '0' && c <= '9') {
                sb.append(c);
            }
        }
        return sb.toString();
    }

    /** 0000000000, 1111111111 … are never real numbers. */
    private static boolean isPlaceholder(String digits) {
        char first = digits.charAt(0);
        for (int i = 1; i < digits.length(); i++) {
            if (digits.charAt(i) != first) {
                return false;
            }
        }
        return true;
    }

    /** Display helper: {@code +91 98765 43210} for a stored E.164 value. */
    public static String display(String e164) {
        if (e164 == null || e164.length() < 4) {
            return "";
        }
        String trimmed = e164.trim().toUpperCase(Locale.ROOT);
        for (Country country : Countries.ALL) {
            if (trimmed.startsWith(country.dialCode())) {
                String national = trimmed.substring(country.dialCode().length());
                if (national.length() >= country.minDigits()) {
                    return country.format(national);
                }
            }
        }
        return trimmed;
    }

    private PhoneNumbers() {
    }
}

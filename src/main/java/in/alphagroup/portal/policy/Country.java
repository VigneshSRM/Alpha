package in.alphagroup.portal.policy;

/**
 * One dialling code in the sign-up picker.
 *
 * @param iso       ISO 3166-1 alpha-2 code, the stable identifier posted by the form
 * @param dialCode  international prefix including the {@code +}, e.g. {@code +91}
 * @param name      display name
 * @param minDigits shortest valid national number
 * @param maxDigits longest valid national number
 */
public record Country(String iso, String dialCode, String name, int minDigits, int maxDigits) {

    /** {@code +91 98765 43210}. */
    public String format(String nationalNumber) {
        return dialCode + " " + nationalNumber;
    }
}

package in.alphagroup.portal.util;

import java.util.regex.Pattern;

/** E-mail shape checks. */
public final class Emails {

    public static final int MAX_LENGTH = 254;

    private static final Pattern EMAIL = Pattern.compile(
            "^[A-Za-z0-9._%+\\-]+@[A-Za-z0-9.\\-]+\\.[A-Za-z]{2,}$");

    private Emails() {
    }

    public static boolean isValid(String email) {
        if (email == null) {
            return false;
        }
        String trimmed = email.trim();
        return !trimmed.isEmpty()
                && trimmed.length() <= MAX_LENGTH
                && EMAIL.matcher(trimmed).matches();
    }
}

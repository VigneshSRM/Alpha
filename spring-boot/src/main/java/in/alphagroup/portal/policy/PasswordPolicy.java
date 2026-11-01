package in.alphagroup.portal.policy;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeSet;
import java.util.regex.Pattern;

/**
 * The single source of truth for password acceptability.
 *
 * <p>Policy (as requested by Alpha Group of India):
 *
 * <ul>
 *   <li>minimum 12 characters, maximum 128,</li>
 *   <li>at least 1 uppercase letter,</li>
 *   <li>at least 1 lowercase letter,</li>
 *   <li>at least 2 digits,</li>
 *   <li>at least 2 special characters,</li>
 *   <li>plus a complexity gate: no common/breached-style stems, no run of the
 *       same character more than twice, no keyboard/alphabet sequences, and
 *       nothing derived from the user's own name or e-mail address.</li>
 * </ul>
 *
 * <p>The browser never decides any of this — {@code /api/password-check} and the
 * form submit both call {@link #evaluate} here, so the "!" indicator can never
 * disagree with the server's final verdict.
 *
 * <p>This is a straight port of {@code app/password_policy.py}; the strings are
 * byte-for-byte identical so both apps say exactly the same thing to a user.
 */
public final class PasswordPolicy {

    public static final int MIN_LENGTH = 12;
    public static final int MAX_LENGTH = 128;
    public static final int MIN_UPPERCASE = 1;
    public static final int MIN_LOWERCASE = 1;
    public static final int MIN_DIGITS = 2;
    public static final int MIN_SPECIAL = 2;
    public static final int MAX_REPEATED_RUN = 2;
    public static final int SEQUENCE_LENGTH = 4;

    /** Printable ASCII punctuation that counts as a "special character". */
    public static final String SPECIAL_CHARS = "!\"#$%&'()*+,-./:;<=>?@[\\]^_`{|}~";

    /** Display order is significant: it is the order of the checklist. */
    public static final List<Rule> RULES = List.of(
            new Rule("length", "At least 12 characters", "a memorable passphrase"),
            new Rule("uppercase", "One uppercase letter", "A–Z"),
            new Rule("lowercase", "One lowercase letter", "a–z"),
            new Rule("digits", "Two numbers", "0–9"),
            new Rule("special", "Two special characters", "! @ # $ % & *"),
            new Rule("complexity", "No common patterns", "names, e-mails, sequences"));

    /** Very common password stems; anything containing one is rejected. */
    private static final List<String> COMMON_STEMS = List.of(
            "password", "passw0rd", "p@ssword", "qwerty", "letmein", "welcome",
            "admin", "iloveyou", "monkey", "dragon", "master", "sunshine",
            "princess", "football", "baseball", "superman", "trustno1", "abc123",
            "123456", "111111", "000000", "changeme", "secret");

    private static final List<String> SEQUENCES = List.of(
            "abcdefghijklmnopqrstuvwxyz",
            "0123456789",
            "qwertyuiop",
            "asdfghjkl",
            "zxcvbnm");

    private static final Pattern NON_ALNUM = Pattern.compile("[^a-z0-9]");
    private static final Pattern EMAIL_SPLIT = Pattern.compile("[._\\-+]");
    private static final Pattern NAME_SPLIT = Pattern.compile("[\\s._\\-]+");

    private PasswordPolicy() {
    }

    /** Convenience overload without personal-data context. */
    public static PolicyResult evaluate(String password) {
        return evaluate(password, Map.of());
    }

    public static PolicyResult evaluate(String password, String email, String name) {
        Map<String, String> context = new LinkedHashMap<>();
        context.put("email", email);
        context.put("name", name);
        return evaluate(password, context);
    }

    /**
     * Validates {@code rawPassword} against the policy.
     *
     * @param context may carry {@code email} and/or {@code name} so the password
     *                can be checked for personal-data reuse; may be null
     */
    public static PolicyResult evaluate(String rawPassword, Map<String, String> context) {
        String password = rawPassword == null ? "" : rawPassword;
        Map<String, String> ctx = context == null ? Map.of() : context;

        int upper = countUpper(password);
        int lower = countLower(password);
        int digits = countDigits(password);
        int special = countSpecial(password);

        boolean complexity = !password.isEmpty()
                && !hasCommonStem(password)
                && !hasSequence(password)
                && !hasRepeatedRun(password)
                && !leaksPersonalData(password, ctx);

        Map<String, Boolean> checks = new LinkedHashMap<>();
        checks.put("length", password.length() >= MIN_LENGTH);
        checks.put("uppercase", upper >= MIN_UPPERCASE);
        checks.put("lowercase", lower >= MIN_LOWERCASE);
        checks.put("digits", digits >= MIN_DIGITS);
        checks.put("special", special >= MIN_SPECIAL);
        checks.put("complexity", complexity);

        List<String> violations = new ArrayList<>();
        if (password.isEmpty()) {
            violations.add("Enter a password.");
        } else {
            if (password.length() > MAX_LENGTH) {
                violations.add("Password must be at most " + MAX_LENGTH + " characters.");
                checks.put("length", false);
            }
            if (!checks.get("length")) {
                violations.add("Password must be at least " + MIN_LENGTH + " characters long.");
            }
            if (!checks.get("uppercase")) {
                violations.add("Add at least " + MIN_UPPERCASE + " uppercase letter (A–Z).");
            }
            if (!checks.get("lowercase")) {
                violations.add("Add at least " + MIN_LOWERCASE + " lowercase letter (a–z).");
            }
            if (!checks.get("digits")) {
                violations.add("Add at least " + MIN_DIGITS + " numbers (0–9).");
            }
            if (!checks.get("special")) {
                violations.add("Add at least " + MIN_SPECIAL + " special characters (! @ # $ % & *).");
            }
            if (!complexity) {
                if (hasCommonStem(password)) {
                    violations.add("That password contains a well-known dictionary word. "
                            + "Choose something unique.");
                } else if (leaksPersonalData(password, ctx)) {
                    violations.add("Do not use your name or e-mail address in the password.");
                } else if (hasSequence(password)) {
                    violations.add("Avoid keyboard or alphabet sequences such as 'abcd' or '1234'.");
                } else if (hasRepeatedRun(password)) {
                    violations.add("Avoid repeating the same character more than twice in a row.");
                } else {
                    violations.add("Password is not complex enough.");
                }
            }
        }

        int score = score(password, checks, upper, lower, special);
        boolean valid = checks.values().stream().allMatch(Boolean::booleanValue);
        return new PolicyResult(valid, checks, List.copyOf(violations), score, strength(score));
    }

    public static boolean isValid(String password) {
        return evaluate(password).valid();
    }

    // ------------------------------------------------------------------
    // Character counting (ASCII letters, Unicode-aware digits — matching
    // Python's [A-Z], [a-z], \d and the explicit punctuation set)
    // ------------------------------------------------------------------
    static int countUpper(String value) {
        int total = 0;
        for (int i = 0; i < value.length(); i++) {
            char ch = value.charAt(i);
            if (ch >= 'A' && ch <= 'Z') {
                total++;
            }
        }
        return total;
    }

    static int countLower(String value) {
        int total = 0;
        for (int i = 0; i < value.length(); i++) {
            char ch = value.charAt(i);
            if (ch >= 'a' && ch <= 'z') {
                total++;
            }
        }
        return total;
    }

    static int countDigits(String value) {
        int total = 0;
        for (int i = 0; i < value.length(); i++) {
            if (Character.isDigit(value.charAt(i))) {
                total++;
            }
        }
        return total;
    }

    static int countSpecial(String value) {
        int total = 0;
        for (int i = 0; i < value.length(); i++) {
            if (SPECIAL_CHARS.indexOf(value.charAt(i)) >= 0) {
                total++;
            }
        }
        return total;
    }

    // ------------------------------------------------------------------
    // Complexity gates
    // ------------------------------------------------------------------

    /** Lowercase + strip accents so {@code Pàssw0rd} still trips the blocklist. */
    static String normalise(String value) {
        String decomposed = Normalizer.normalize(value, Normalizer.Form.NFKD);
        StringBuilder out = new StringBuilder(decomposed.length());
        for (int i = 0; i < decomposed.length(); i++) {
            char ch = decomposed.charAt(i);
            if (Character.getType(ch) != Character.NON_SPACING_MARK) {
                out.append(ch);
            }
        }
        return out.toString().toLowerCase(Locale.ROOT);
    }

    static boolean hasCommonStem(String password) {
        String normalised = normalise(password);
        String compact = NON_ALNUM.matcher(normalised).replaceAll("");
        for (String stem : COMMON_STEMS) {
            if (normalised.contains(stem) || compact.contains(stem)) {
                return true;
            }
        }
        return false;
    }

    static boolean hasSequence(String password) {
        String lowered = password.toLowerCase(Locale.ROOT);
        for (String seq : SEQUENCES) {
            String reversed = new StringBuilder(seq).reverse().toString();
            for (String source : List.of(seq, reversed)) {
                for (int start = 0; start + SEQUENCE_LENGTH <= source.length(); start++) {
                    if (lowered.contains(source.substring(start, start + SEQUENCE_LENGTH))) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    static boolean hasRepeatedRun(String password) {
        int run = 1;
        for (int i = 1; i < password.length(); i++) {
            if (password.charAt(i) == password.charAt(i - 1)) {
                run++;
                if (run > MAX_REPEATED_RUN) {
                    return true;
                }
            } else {
                run = 1;
            }
        }
        return false;
    }

    /** Name / e-mail fragments that must not appear inside the password. */
    static List<String> personalTokens(Map<String, String> context) {
        if (context == null || context.isEmpty()) {
            return List.of();
        }
        List<String> tokens = new ArrayList<>();
        String email = trimLower(context.get("email"));
        if (!email.isEmpty()) {
            String local = email.split("@", 2)[0];
            tokens.add(local);
            tokens.addAll(Arrays.asList(EMAIL_SPLIT.split(local)));
        }
        String name = trimLower(context.get("name"));
        if (!name.isEmpty()) {
            tokens.addAll(Arrays.asList(NAME_SPLIT.split(name.trim())));
        }
        // 2-letter fragments cause false positives; keep 3+ only.
        TreeSet<String> meaningful = new TreeSet<>();
        for (String token : tokens) {
            if (token != null && token.length() >= 3) {
                meaningful.add(token);
            }
        }
        return List.copyOf(meaningful);
    }

    static boolean leaksPersonalData(String password, Map<String, String> context) {
        String lowered = normalise(password);
        for (String token : personalTokens(context)) {
            if (lowered.contains(token)) {
                return true;
            }
        }
        return false;
    }

    // ------------------------------------------------------------------
    // Strength scoring (advisory only — never gates submission)
    // ------------------------------------------------------------------
    static int score(String password, Map<String, Boolean> checks, int upper, int lower, int special) {
        int points = 0;
        for (boolean ok : checks.values()) {
            if (ok) {
                points += 12; // 6 rules -> 72
            }
        }
        int length = password.length();
        if (length >= MIN_LENGTH) {
            points += 6;
        }
        if (length >= MIN_LENGTH + 4) {
            points += 8;
        }
        if (distinctChars(password) >= 10) {
            points += 7;
        }
        if (upper > 0 && lower > 0) {
            points += 4;
        }
        if (special >= 3) {
            points += 3;
        }
        return Math.max(0, Math.min(100, points));
    }

    private static int distinctChars(String value) {
        return (int) value.chars().distinct().count();
    }

    static String strength(int score) {
        if (score < 40) {
            return "weak";
        }
        if (score < 60) {
            return "fair";
        }
        if (score < 82) {
            return "good";
        }
        return "strong";
    }

    private static String trimLower(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }
}

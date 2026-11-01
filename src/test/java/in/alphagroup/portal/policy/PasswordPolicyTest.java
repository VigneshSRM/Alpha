package in.alphagroup.portal.policy;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * The policy is the security-critical part of this application, so it is tested
 * on its own with no Spring context and no database.
 */
class PasswordPolicyTest {

    private static final String GOOD = "Wolf@Runs#Dawn27!";

    @Test
    @DisplayName("a compliant password passes every rule")
    void acceptsCompliantPassword() {
        PolicyResult result = PasswordPolicy.evaluate(GOOD);

        assertThat(result.valid()).isTrue();
        assertThat(result.violations()).isEmpty();
        assertThat(result.checks()).hasSize(PasswordPolicy.RULES.size());
        assertThat(result.checks().values()).allMatch(Boolean::booleanValue);
        assertThat(result.strength()).isIn("fair", "good", "strong");
    }

    @Test
    @DisplayName("11 characters is one short of the minimum")
    void rejectsShortPassword() {
        PolicyResult result = PasswordPolicy.evaluate("Abcdef1234!");

        assertThat(result.valid()).isFalse();
        assertThat(result.check("length")).isFalse();
        assertThat(result.violations())
                .containsExactly("Password must be at least 12 characters long.");
    }

    @Test
    @DisplayName("one uppercase letter is required")
    void requiresUppercase() {
        PolicyResult result = PasswordPolicy.evaluate("wolf@runs#dawn27!");

        assertThat(result.check("uppercase")).isFalse();
        assertThat(result.violations()).contains("Add at least 1 uppercase letter (A–Z).");
    }

    @Test
    @DisplayName("one lowercase letter is required")
    void requiresLowercase() {
        PolicyResult result = PasswordPolicy.evaluate("WOLF@RUNS#DAWN27!");

        assertThat(result.check("lowercase")).isFalse();
        assertThat(result.violations()).contains("Add at least 1 lowercase letter (a–z).");
    }

    @Test
    @DisplayName("two numbers are required")
    void requiresTwoDigits() {
        PolicyResult result = PasswordPolicy.evaluate("Wolf@Runs#Dawn!x");

        assertThat(result.check("digits")).isFalse();
        assertThat(result.violations()).contains("Add at least 2 numbers (0–9).");
    }

    @Test
    @DisplayName("two special characters are required")
    void requiresTwoSpecials() {
        PolicyResult result = PasswordPolicy.evaluate("WolfRunsDawn27x");

        assertThat(result.check("special")).isFalse();
        assertThat(result.violations())
                .contains("Add at least 2 special characters (! @ # $ % & *).");
    }

    @ParameterizedTest
    @ValueSource(strings = {"Passw0rd!!x9", "Qwerty!!2024x", "Admin#2024!!z"})
    @DisplayName("dictionary stems are rejected even when the shape looks right")
    void rejectsCommonStems(String password) {
        PolicyResult result = PasswordPolicy.evaluate(password);

        assertThat(result.check("complexity")).isFalse();
        assertThat(result.violations())
                .contains("That password contains a well-known dictionary word. "
                        + "Choose something unique.");
    }

    @ParameterizedTest
    @ValueSource(strings = {"Wolfabcd12!!x", "Wolf12345!!x"})
    @DisplayName("keyboard and alphabet sequences are rejected")
    void rejectsSequences(String password) {
        PolicyResult result = PasswordPolicy.evaluate(password);

        assertThat(result.check("complexity")).isFalse();
        assertThat(result.violations())
                .contains("Avoid keyboard or alphabet sequences such as 'abcd' or '1234'.");
    }

    @Test
    @DisplayName("three identical characters in a row are rejected")
    void rejectsRepeatedRuns() {
        PolicyResult result = PasswordPolicy.evaluate("Wolffff@Runs27!");

        assertThat(result.check("complexity")).isFalse();
        assertThat(result.violations())
                .contains("Avoid repeating the same character more than twice in a row.");
    }

    @Test
    @DisplayName("the user's own name and e-mail must not appear")
    void rejectsPersonalData() {
        PolicyResult byName = PasswordPolicy.evaluate("Wolf@Runs#Dawn27!", "wolf@x.com", "Wolf Run");
        PolicyResult byEmail =
                PasswordPolicy.evaluate("Dawn27!@Runs#Alpha", "alphateam@x.com", "Someone");

        assertThat(byName.check("complexity")).isFalse();
        assertThat(byName.violations())
                .contains("Do not use your name or e-mail address in the password.");
        assertThat(byEmail.check("complexity")).isFalse();
    }

    @Test
    @DisplayName("anything over 128 characters is refused")
    void rejectsOverlongPassword() {
        PolicyResult result = PasswordPolicy.evaluate("Aa1!" + "x".repeat(130));

        assertThat(result.check("length")).isFalse();
        assertThat(result.violations()).contains("Password must be at most 128 characters.");
    }

    @Test
    @DisplayName("an empty password is reported, not scored")
    void reportsEmpty() {
        PolicyResult result = PasswordPolicy.evaluate("");

        assertThat(result.violations()).containsExactly("Enter a password.");
        assertThat(result.score()).isZero();
    }

    @Test
    @DisplayName("accented characters still trip the blocklist")
    void normalisesAccents() {
        assertThat(PasswordPolicy.hasCommonStem("Pàssw0rd")).isTrue();
    }

    @Test
    @DisplayName("checks are returned in checklist order")
    void checkOrderIsStable() {
        assertThat(PasswordPolicy.evaluate(GOOD).checks().keySet())
                .containsExactly("length", "uppercase", "lowercase",
                        "digits", "special", "complexity");
    }

    @Test
    @DisplayName("personal context is optional")
    void handlesNullContext() {
        assertThat(PasswordPolicy.evaluate(GOOD, (Map<String, String>) null).valid()).isTrue();
    }
}

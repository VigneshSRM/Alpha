package in.alphagroup.portal.policy;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class PhoneNumbersTest {

    @ParameterizedTest(name = "{0} + {1} -> {2}")
    @CsvSource({
            "IN, 9876543210,    +919876543210",
            "IN, 98765 43210,   +919876543210",
            "IN, 098765-43210,  +919876543210",
            "IN, (98765) 43210, +919876543210",
            "US, 4155550123,    +14155550123",
            "US, 415-555-0123,  +14155550123",
            "GB, 07911123456,   +447911123456",
            "AE, 501234567,     +971501234567",
            "SG, 81234567,      +6581234567",
            "AF, 700123456,     +93700123456",
    })
    @DisplayName("numbers are normalised to E.164")
    void acceptsAndNormalises(String iso, String typed, String expected) {
        PhoneNumbers.Result result = PhoneNumbers.validate(iso, typed);

        assertThat(result.valid()).isTrue();
        assertThat(result.e164()).isEqualTo(expected);
        assertThat(result.error()).isEmpty();
    }

    @ParameterizedTest(name = "{0} + {1}")
    @CsvSource({
            "IN, 98765",
            "IN, 98765432101",
            "US, 415555012",
            "GB, 12345",
    })
    @DisplayName("wrong length for the country is rejected")
    void rejectsWrongLength(String iso, String typed) {
        PhoneNumbers.Result result = PhoneNumbers.validate(iso, typed);

        assertThat(result.valid()).isFalse();
        assertThat(result.e164()).isEmpty();
        assertThat(result.error()).contains("mobile number");
    }

    @Test
    @DisplayName("the message names the country and its length")
    void lengthMessageNamesCountry() {
        PhoneNumbers.Result result = PhoneNumbers.validate("IN", "98765");

        assertThat(result.error()).isEqualTo("Enter a 10-digit mobile number for India.");
    }

    @Test
    @DisplayName("a range is spelled out when the country allows one")
    void rangeMessageForVariablePlans() {
        PhoneNumbers.Result result = PhoneNumbers.validate("ID", "8123");

        assertThat(result.valid()).isFalse();
        assertThat(result.error()).isEqualTo(
                "Enter a mobile number with 9 to 12 digits for Indonesia.");
    }

    @Test
    @DisplayName("unknown country code is rejected")
    void rejectsUnknownCountry() {
        PhoneNumbers.Result result = PhoneNumbers.validate("ZZ", "9876543210");

        assertThat(result.valid()).isFalse();
        assertThat(result.error()).isEqualTo("Select your country.");
        assertThat(result.country()).isNull();
    }

    @Test
    @DisplayName("a missing or blank number is reported as missing")
    void rejectsEmpty() {
        assertThat(PhoneNumbers.validate("IN", "").error())
                .isEqualTo("Enter your mobile number.");
        assertThat(PhoneNumbers.validate("IN", "   ").error())
                .isEqualTo("Enter your mobile number.");
        assertThat(PhoneNumbers.validate("IN", null).error())
                .isEqualTo("Enter your mobile number.");
    }

    @Test
    @DisplayName("letters are refused rather than silently dropped")
    void rejectsLetters() {
        PhoneNumbers.Result result = PhoneNumbers.validate("IN", "98765abcde");

        assertThat(result.valid()).isFalse();
        assertThat(result.error()).isEqualTo("A mobile number can only contain digits.");
    }

    @Test
    @DisplayName("repeated digits are treated as a placeholder")
    void rejectsPlaceholderNumbers() {
        PhoneNumbers.Result result = PhoneNumbers.validate("IN", "0000000000");

        assertThat(result.valid()).isFalse();
        assertThat(result.error()).startsWith("That number looks like a placeholder");
    }

    @Test
    @DisplayName("a stored number is formatted for display")
    void formatsForDisplay() {
        assertThat(PhoneNumbers.display("+919876543210")).isEqualTo("+91 9876543210");
        assertThat(PhoneNumbers.display("+14155550123")).isEqualTo("+1 4155550123");
        assertThat(PhoneNumbers.display(null)).isEmpty();
        assertThat(PhoneNumbers.display("")).isEmpty();
    }

    // ------------------------------------------------------------------
    // The table itself
    // ------------------------------------------------------------------

    @Test
    @DisplayName("the picker covers every country and its codes are well formed")
    void tableIsSane() {
        assertThat(Countries.ALL).hasSizeGreaterThan(200);

        assertThat(Countries.ALL).allSatisfy(country -> {
            assertThat(country.iso()).hasSize(2).isUpperCase();
            assertThat(country.dialCode()).startsWith("+");
            assertThat(country.dialCode().substring(1)).containsOnlyDigits();
            assertThat(country.name()).isNotBlank();
            assertThat(country.minDigits()).isPositive();
            assertThat(country.maxDigits()).isGreaterThanOrEqualTo(country.minDigits());
        });

        assertThat(Countries.ALL).extracting(Country::iso).doesNotHaveDuplicates();
    }

    @Test
    @DisplayName("India is the default and is present in the list")
    void indiaIsPresent() {
        Country india = Countries.byIso(Countries.DEFAULT_ISO);

        assertThat(india).isNotNull();
        assertThat(india.name()).isEqualTo("India");
        assertThat(india.dialCode()).isEqualTo("+91");
    }

    @Test
    @DisplayName("lookup is case-insensitive and null-safe")
    void lookupHandlesCase() {
        assertThat(Countries.byIso("in")).isSameAs(Countries.byIso("IN"));
        assertThat(Countries.byIso(null)).isNull();
        assertThat(Countries.byIso("ZZ")).isNull();
    }
}

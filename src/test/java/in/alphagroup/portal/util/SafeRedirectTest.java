package in.alphagroup.portal.util;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class SafeRedirectTest {

    private static final String FALLBACK = "/dashboard";

    @ParameterizedTest
    @ValueSource(strings = {
            "https://evil.example",
            "http://evil.example/login",
            "//evil.example",
            "/\\evil.example",
            "/redirect?url=https://evil.example:8443",
            "javascript:alert(1)",
            "",
            "   ",
            "dashboard"
    })
    @DisplayName("anything that is not a same-site path is replaced")
    void rejectsUnsafeTargets(String candidate) {
        assertThat(SafeRedirect.sanitise(candidate, FALLBACK)).isEqualTo(FALLBACK);
    }

    @ParameterizedTest
    @ValueSource(strings = {"/dashboard", "/dashboard?tab=security", "/settings/profile"})
    @DisplayName("same-site paths pass through")
    void keepsSafeTargets(String candidate) {
        assertThat(SafeRedirect.sanitise(candidate, FALLBACK)).isEqualTo(candidate);
    }

    @Test
    @DisplayName("a missing parameter falls back")
    void handlesNull() {
        assertThat(SafeRedirect.sanitise(null, FALLBACK)).isEqualTo(FALLBACK);
    }
}

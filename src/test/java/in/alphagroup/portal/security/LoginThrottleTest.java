package in.alphagroup.portal.security;

import static org.assertj.core.api.Assertions.assertThat;

import in.alphagroup.portal.config.AlphaProperties;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class LoginThrottleTest {

    private LoginThrottle throttle;
    private final List<String> keys = List.of("email:ada@alphagroup.in", "ip:10.0.0.1");

    @BeforeEach
    void setUp() {
        AlphaProperties properties = new AlphaProperties();
        properties.getSecurity().setLoginMaxAttempts(3);
        properties.getSecurity().setLoginWindowSeconds(60);
        throttle = new LoginThrottle(properties);
    }

    @Test
    @DisplayName("a clean key may proceed")
    void allowsFreshAttempts() {
        assertThat(throttle.retryAfter(keys)).isZero();
    }

    @Test
    @DisplayName("the limit is enforced after N failures")
    void blocksAfterLimit() {
        assertThat(throttle.registerFailure(keys)).isEqualTo(2);
        assertThat(throttle.registerFailure(keys)).isEqualTo(1);
        assertThat(throttle.registerFailure(keys)).isZero();
        assertThat(throttle.retryAfter(keys)).isPositive();
    }

    @Test
    @DisplayName("a successful sign-in clears the counters")
    void resetClearsWindow() {
        throttle.registerFailure(keys);
        throttle.registerFailure(keys);
        throttle.registerFailure(keys);
        throttle.reset(keys);

        assertThat(throttle.retryAfter(keys)).isZero();
    }

    @Test
    @DisplayName("one account does not lock out an unrelated one")
    void keysAreIndependent() {
        List<String> ada = List.of("email:ada@alphagroup.in", "ip:10.0.0.1");
        List<String> bob = List.of("email:bob@alphagroup.in", "ip:10.0.0.2");

        for (int i = 0; i < 3; i++) {
            throttle.registerFailure(ada);
        }

        assertThat(throttle.retryAfter(ada)).isPositive();
        assertThat(throttle.retryAfter(bob)).isZero();
    }
}

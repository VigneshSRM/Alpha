package in.alphagroup.portal.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

/**
 * Password hashing, kept in its own configuration class on purpose.
 *
 * <p>{@code SecurityConfig} needs {@code PortalUserService}, which needs the
 * encoder; declaring the encoder there as well would be a constructor cycle.
 * Keeping it separate also means one obvious place to change the algorithm.
 */
@Configuration
public class PasswordConfig {

    /**
     * BCrypt with a configurable work factor (12 by default ≈ 250 ms per hash on
     * modern hardware — slow for an attacker, invisible to a user).
     */
    @Bean
    public PasswordEncoder passwordEncoder(AlphaProperties properties) {
        int strength = Math.max(4, properties.getSecurity().getBcryptStrength());
        return new BCryptPasswordEncoder(strength);
    }
}

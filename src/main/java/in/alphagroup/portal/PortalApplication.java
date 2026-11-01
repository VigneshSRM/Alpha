package in.alphagroup.portal;

import in.alphagroup.portal.config.AlphaProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

/**
 * Alpha Group of India — client portal.
 *
 * <p>Java 17 / Spring Boot 3.5, MariaDB for accounts and sessions, Flyway for the
 * schema. There is no external identity provider: credentials are verified here
 * against a BCrypt hash and the password policy in {@code policy.PasswordPolicy}.
 */
@SpringBootApplication
@EnableConfigurationProperties(AlphaProperties.class)
public class PortalApplication {

    public static void main(String[] args) {
        SpringApplication.run(PortalApplication.class, args);
    }
}

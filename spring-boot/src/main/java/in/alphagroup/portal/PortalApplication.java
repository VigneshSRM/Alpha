package in.alphagroup.portal;

import in.alphagroup.portal.config.AlphaProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

/**
 * Alpha Group of India — client portal.
 *
 * <p>Java 17 / Spring Boot 3.5 port of the Flask application in {@code app/}.
 * The pages, stylesheet and JavaScript are shared, so both stacks render the
 * same portal; only the server changes.
 */
@SpringBootApplication
@EnableConfigurationProperties(AlphaProperties.class)
public class PortalApplication {

    public static void main(String[] args) {
        SpringApplication.run(PortalApplication.class, args);
    }
}

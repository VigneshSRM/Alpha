package in.alphagroup.portal.config;

import in.alphagroup.portal.security.SessionAuth;
import in.alphagroup.portal.security.SessionAuthenticationFilter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.csrf.CsrfTokenRequestAttributeHandler;
import org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter;

/**
 * Spring Security 6 wiring.
 *
 * <p>Two things are worth knowing:
 *
 * <ol>
 *   <li><b>No form login.</b> Supabase owns credentials, so Spring's login page,
 *       {@code UserDetailsService} and {@code PasswordEncoder} are all switched
 *       off. {@link SessionAuthenticationFilter} promotes the session instead.</li>
 *   <li><b>Raw CSRF tokens.</b> {@link CsrfTokenRequestAttributeHandler} is used
 *       instead of the default XOR handler so the value rendered into the hidden
 *       field is the same one {@code auth.js} can echo back in a header.</li>
 * </ol>
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    private static final String CONTENT_SECURITY_POLICY = String.join("; ",
            "default-src 'self'",
            "img-src 'self' data:",
            "style-src 'self'",
            "script-src 'self'",
            "font-src 'self'",
            "connect-src 'self'",
            "form-action 'self'",
            "base-uri 'none'",
            "frame-ancestors 'none'",
            "object-src 'none'");

    private final SessionAuth sessionAuth;

    public SecurityConfig(SessionAuth sessionAuth) {
        this.sessionAuth = sessionAuth;
    }

    @Bean
    public NamedCsrfTokenRepository csrfTokenRepository() {
        return new NamedCsrfTokenRepository();
    }

    @Bean
    public SecurityFilterChain securityFilterChain(
            HttpSecurity http, NamedCsrfTokenRepository csrfTokenRepository) throws Exception {

        http
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(HttpMethod.GET,
                                "/login", "/signup", "/forgot-password", "/healthz").permitAll()
                        .requestMatchers(HttpMethod.POST,
                                "/login", "/signup", "/forgot-password", "/logout").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/password-check").permitAll()
                        .requestMatchers("/css/**", "/js/**", "/img/**",
                                "/favicon.ico", "/error").permitAll()
                        .anyRequest().authenticated())
                .csrf(csrf -> csrf
                        .csrfTokenRepository(csrfTokenRepository)
                        .csrfTokenRequestHandler(new CsrfTokenRequestAttributeHandler()))
                .formLogin(AbstractHttpConfigurer::disable)
                .httpBasic(AbstractHttpConfigurer::disable)
                .logout(AbstractHttpConfigurer::disable)
                .requestCache(AbstractHttpConfigurer::disable)
                .exceptionHandling(exceptions -> exceptions
                        .authenticationEntryPoint(new PortalAuthenticationEntryPoint())
                        .accessDeniedHandler(new PortalAccessDeniedHandler(csrfTokenRepository)))
                .headers(headers -> {
                    headers.contentSecurityPolicy(csp -> csp.policyDirectives(CONTENT_SECURITY_POLICY));
                    headers.frameOptions(frame -> frame.deny());
                    headers.referrerPolicy(referrer -> referrer.policy(
                            ReferrerPolicyHeaderWriter.ReferrerPolicy.STRICT_ORIGIN_WHEN_CROSS_ORIGIN));
                    headers.permissionsPolicy(permissions -> permissions
                            .policy("geolocation=(), microphone=(), camera=()"));
                })
                .addFilterBefore(
                        new SessionAuthenticationFilter(sessionAuth),
                        UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }
}

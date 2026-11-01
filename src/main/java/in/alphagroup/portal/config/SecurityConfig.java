package in.alphagroup.portal.config;

import in.alphagroup.portal.user.PortalUserService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.ProviderManager;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.security.web.csrf.CsrfTokenRequestAttributeHandler;
import org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter;
import org.springframework.security.web.authentication.rememberme.RememberMeServices;
import org.springframework.security.web.authentication.rememberme.TokenBasedRememberMeServices;

/**
 * Spring Security wiring for a MariaDB-backed portal.
 *
 * <p>Worth knowing:
 *
 * <ol>
 *   <li><b>Credentials are checked by Spring.</b> A {@link DaoAuthenticationProvider}
 *       loads the {@code portal_user} row through {@link PortalUserService} and
 *       verifies the BCrypt hash stored in MariaDB. No external provider is involved.</li>
 *   <li><b>The login form is ours.</b> Spring's form-login is disabled — the
 *       controller drives {@link AuthenticationManager} so it can answer JSON to
 *       {@code auth.js} and HTML to a plain form post.</li>
 *   <li><b>Raw CSRF tokens.</b> {@link CsrfTokenRequestAttributeHandler} instead of
 *       the default XOR handler, so the value in the hidden field is the same one
 *       {@code auth.js} can echo back in an {@code X-CSRF-Token} header.</li>
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

    private final PortalUserService userService;
    private final AlphaProperties properties;

    public SecurityConfig(PortalUserService userService, AlphaProperties properties) {
        this.userService = userService;
        this.properties = properties;
    }

    @Bean
    public DaoAuthenticationProvider authenticationProvider(PasswordEncoder encoder) {
        DaoAuthenticationProvider provider = new DaoAuthenticationProvider();
        provider.setUserDetailsService(userService);
        provider.setPasswordEncoder(encoder);
        // Never reveal whether the address exists.
        provider.setHideUserNotFoundExceptions(true);
        return provider;
    }

    @Bean
    public AuthenticationManager authenticationManager(DaoAuthenticationProvider provider) {
        return new ProviderManager(provider);
    }

    /** Keeps the "keep me signed in" cookie valid across restarts. */
    @Bean
    public RememberMeServices rememberMeServices() {
        AlphaProperties.Security security = properties.getSecurity();
        TokenBasedRememberMeServices services =
                new TokenBasedRememberMeServices(security.getRememberMeKey(), userService);
        services.setTokenValiditySeconds(Math.max(1, security.getRememberMeDays()) * 86_400);
        services.setParameter("remember");
        services.setAlwaysRemember(false);
        return services;
    }

    @Bean
    public SecurityContextRepository securityContextRepository() {
        return new HttpSessionSecurityContextRepository();
    }

    @Bean
    public NamedCsrfTokenRepository csrfTokenRepository() {
        return new NamedCsrfTokenRepository();
    }

    @Bean
    public SecurityFilterChain securityFilterChain(
            HttpSecurity http,
            NamedCsrfTokenRepository csrfTokenRepository,
            RememberMeServices rememberMeServices) throws Exception {

        http
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(HttpMethod.GET,
                                "/", "/login", "/signup", "/forgot-password",
                                "/reset-password", "/healthz").permitAll()
                        .requestMatchers(HttpMethod.POST,
                                "/login", "/signup", "/forgot-password",
                                "/reset-password", "/logout").permitAll()
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
                .rememberMe(remember -> remember
                        .rememberMeServices(rememberMeServices)
                        .key(properties.getSecurity().getRememberMeKey()))
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
                });

        return http.build();
    }
}

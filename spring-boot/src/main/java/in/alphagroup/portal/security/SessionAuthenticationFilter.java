package in.alphagroup.portal.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import java.io.IOException;
import java.util.List;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Bridges our Supabase session into Spring Security's context.
 *
 * <p>Credentials are verified by Supabase, not by Spring, so nothing would ever
 * populate the {@code SecurityContext} on its own. This filter promotes the
 * {@code auth} session attribute to a {@code ROLE_USER} authentication for the
 * duration of the request, which lets the filter chain use ordinary
 * {@code authenticated()} rules instead of hand-rolled checks.
 */
public class SessionAuthenticationFilter extends OncePerRequestFilter {

    private final SessionAuth sessionAuth;

    public SessionAuthenticationFilter(SessionAuth sessionAuth) {
        this.sessionAuth = sessionAuth;
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {

        if (SecurityContextHolder.getContext().getAuthentication() == null) {
            HttpSession session = request.getSession(false);
            sessionAuth.current(session).ifPresent(auth -> {
                UsernamePasswordAuthenticationToken authentication =
                        UsernamePasswordAuthenticationToken.authenticated(
                                auth, null, List.of(new SimpleGrantedAuthority("ROLE_USER")));
                SecurityContextHolder.getContext().setAuthentication(authentication);
            });
        }
        chain.doFilter(request, response);
    }
}

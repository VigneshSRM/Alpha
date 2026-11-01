package in.alphagroup.portal.security;

import in.alphagroup.portal.auth.AuthSession;
import in.alphagroup.portal.auth.SupabaseAuthClient;
import in.alphagroup.portal.config.AlphaProperties;
import jakarta.servlet.http.HttpSession;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Owns the {@code auth} attribute in the HTTP session: sign-in, silent token
 * renewal and sign-out.
 *
 * <p>The Supabase access token never reaches the browser — the page only sees a
 * {@code JSESSIONID}. Tokens are renewed server-side a minute before they lapse,
 * exactly like the Flask implementation.
 */
@Component
public class SessionAuth {

    public static final String SESSION_KEY = "auth";
    public static final String REMEMBER_KEY = "auth.remember";
    public static final String LOGGED_IN_AT_KEY = "auth.loggedInAt";

    private static final Logger log = LoggerFactory.getLogger(SessionAuth.class);

    private final SupabaseAuthClient client;
    private final AlphaProperties properties;

    public SessionAuth(SupabaseAuthClient client, AlphaProperties properties) {
        this.client = client;
        this.properties = properties;
    }

    public Optional<AuthSession> current(HttpSession session) {
        if (session == null) {
            return Optional.empty();
        }
        Object value = session.getAttribute(SESSION_KEY);
        if (value instanceof AuthSession auth && !auth.email().isBlank()) {
            return Optional.of(auth);
        }
        return Optional.empty();
    }

    /**
     * Stores the provider session and applies the "keep me signed in" idle
     * timeout. Call after {@code request.changeSessionId()} so a pre-login
     * session identifier cannot be reused.
     */
    public void login(HttpSession session, AuthSession auth, boolean remember) {
        session.setAttribute(SESSION_KEY, auth);
        session.setAttribute(REMEMBER_KEY, remember);
        session.setAttribute(LOGGED_IN_AT_KEY, System.currentTimeMillis() / 1000L);
        int days = Math.max(1, properties.getSecurity().getRememberMeDays());
        long seconds = remember ? days * 86_400L : 1_800L;
        session.setMaxInactiveInterval((int) Math.min(Integer.MAX_VALUE, seconds));
    }

    /** Epoch seconds of the original sign-in, or 0 when unknown. */
    public long loggedInAt(HttpSession session) {
        if (session == null) {
            return 0L;
        }
        Object value = session.getAttribute(LOGGED_IN_AT_KEY);
        return value instanceof Long epoch ? epoch : 0L;
    }

    public void logout(HttpSession session) {
        if (session == null) {
            return;
        }
        Object value = session.getAttribute(SESSION_KEY);
        if (value instanceof AuthSession auth) {
            client.signOut(auth.accessToken());
        }
        session.invalidate();
    }

    /**
     * Renews the access token when it is within the refresh skew of expiry.
     *
     * @return true when a usable session exists afterwards
     */
    public boolean refreshIfNeeded(HttpSession session) {
        Optional<AuthSession> existing = current(session);
        if (existing.isEmpty()) {
            return false;
        }
        AuthSession auth = existing.get();
        long now = System.currentTimeMillis() / 1000L;
        int skew = Math.max(0, properties.getSecurity().getTokenRefreshSkewSeconds());
        if (auth.expiresAt() <= 0 || auth.refreshToken().isBlank()) {
            return true;
        }
        if (auth.expiresAt() - skew > now) {
            return true;
        }

        Optional<AuthSession> refreshed = client.refresh(auth.refreshToken());
        if (refreshed.isEmpty()) {
            log.info("Refresh token rejected; signing out {}", auth.email());
            session.invalidate();
            return false;
        }
        AuthSession next = refreshed.get();
        session.setAttribute(SESSION_KEY, next);
        return true;
    }
}

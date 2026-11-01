package in.alphagroup.portal.config;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.security.web.csrf.CsrfTokenRepository;
import org.springframework.security.web.csrf.DefaultCsrfToken;
import org.springframework.security.web.csrf.HttpSessionCsrfTokenRepository;

/**
 * Session-backed CSRF tokens, renamed to the pair the front-end uses.
 *
 * <p>{@code auth.js} reads the hidden field {@code csrf_token} and replays it in
 * an {@code X-CSRF-Token} header, so the repository simply relabels Spring's
 * default names and the JavaScript needs no changes.
 */
public class NamedCsrfTokenRepository implements CsrfTokenRepository {

    public static final String HEADER_NAME = "X-CSRF-Token";
    public static final String PARAMETER_NAME = "csrf_token";

    private final CsrfTokenRepository delegate = new HttpSessionCsrfTokenRepository();

    @Override
    public CsrfToken generateToken(HttpServletRequest request) {
        return rename(delegate.generateToken(request));
    }

    @Override
    public void saveToken(CsrfToken token, HttpServletRequest request, HttpServletResponse response) {
        delegate.saveToken(token == null ? null : rename(token), request, response);
    }

    @Override
    public CsrfToken loadToken(HttpServletRequest request) {
        return rename(delegate.loadToken(request));
    }

    private CsrfToken rename(CsrfToken token) {
        if (token == null) {
            return null;
        }
        return new DefaultCsrfToken(HEADER_NAME, PARAMETER_NAME, token.getToken());
    }
}

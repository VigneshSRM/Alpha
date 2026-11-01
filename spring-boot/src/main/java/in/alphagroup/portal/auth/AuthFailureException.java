package in.alphagroup.portal.auth;

import org.springframework.http.HttpStatus;

/**
 * A user-presentable authentication failure.
 *
 * <p>Carries the HTTP status the browser should receive, a stable machine code
 * the JavaScript reacts to ({@code invalid_credentials}, {@code rate_limited}…)
 * and optionally the form field to blame.
 */
public class AuthFailureException extends RuntimeException {

    private final String code;
    private final HttpStatus status;
    private final String field;

    public AuthFailureException(String message, String code, HttpStatus status, String field) {
        super(message);
        this.code = code;
        this.status = status;
        this.field = field;
    }

    public String code() {
        return code;
    }

    public HttpStatus status() {
        return status;
    }

    /** Form field the message belongs to, or null for a form-level error. */
    public String field() {
        return field;
    }
}

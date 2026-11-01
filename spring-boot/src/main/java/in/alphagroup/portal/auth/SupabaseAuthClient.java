package in.alphagroup.portal.auth;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import in.alphagroup.portal.config.AlphaProperties;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

/**
 * Supabase Auth (GoTrue) over plain REST.
 *
 * <p>Supabase has no official Java SDK, and the community ones are thin, so this
 * talks to the documented GoTrue endpoints directly with Spring's
 * {@link RestClient}. Only the public {@code anon} key is used — this app never
 * holds the service-role key, so row-level security always applies.
 *
 * <p>Every provider error is translated into an {@link AuthFailureException}
 * carrying a message that is safe to show a user. Provider wording never leaks
 * to the browser, which is what stops account enumeration.
 */
@Component
public class SupabaseAuthClient {

    private static final Logger log = LoggerFactory.getLogger(SupabaseAuthClient.class);

    private static final String GENERIC_LOGIN_ERROR =
            "Invalid e-mail or password. Please try again.";
    private static final String GENERIC_SIGNUP_ERROR =
            "We could not create your account. Please try again.";

    private final RestClient rest;
    private final ObjectMapper mapper;
    private final String anonKey;

    public SupabaseAuthClient(AlphaProperties properties, ObjectMapper mapper) {
        AlphaProperties.Supabase supabase = properties.getSupabase();
        String url = supabase.getUrl() == null ? "" : supabase.getUrl().trim();
        this.anonKey = supabase.getAnonKey() == null ? "" : supabase.getAnonKey().trim();
        this.mapper = mapper;

        if (url.isEmpty() || anonKey.isEmpty()) {
            // Fail at startup with an actionable message rather than at first login.
            throw new IllegalStateException(
                    "SUPABASE_URL and SUPABASE_ANON_KEY must both be set. "
                            + "Copy the project URL and the anon (public) key from "
                            + "Supabase → Project Settings → API.");
        }
        if (url.endsWith("/")) {
            url = url.substring(0, url.length() - 1);
        }

        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        Duration timeout = Duration.ofSeconds(Math.max(1, supabase.getTimeoutSeconds()));
        factory.setConnectTimeout(timeout);
        factory.setReadTimeout(timeout);

        this.rest = RestClient.builder().baseUrl(url).requestFactory(factory).build();
    }

    /** True when the provider supports self-service registration. */
    public boolean supportsSignup() {
        return true;
    }

    /** True when the provider can send a reset e-mail. */
    public boolean supportsPasswordReset() {
        return true;
    }

    // ------------------------------------------------------------------
    // Operations
    // ------------------------------------------------------------------

    public AuthOutcome signIn(String email, String password) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("email", email);
        body.put("password", password);

        JsonNode node = post("/auth/v1/token?grant_type=password", body, null, "sign_in");
        AuthSession session = toSession(node);
        if (session == null) {
            throw new AuthFailureException(
                    GENERIC_LOGIN_ERROR, "invalid_credentials", HttpStatus.UNAUTHORIZED, null);
        }
        return AuthOutcome.signedIn(session);
    }

    public AuthOutcome signUp(String email, String password, String name) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("email", email);
        body.put("password", password);
        if (name != null && !name.isBlank()) {
            body.put("data", Map.of("full_name", name));
        }

        JsonNode node = post("/auth/v1/signup", body, null, "sign_up");
        AuthSession session = toSession(node);
        if (session != null) {
            return new AuthOutcome(session, "Welcome to Alpha Group of India.", false);
        }
        return AuthOutcome.awaitingConfirmation(
                "Check your inbox to confirm your e-mail address, then sign in.");
    }

    /** Silent renewal. Returns empty when the refresh token is no longer valid. */
    public Optional<AuthSession> refresh(String refreshToken) {
        if (refreshToken == null || refreshToken.isBlank()) {
            return Optional.empty();
        }
        Map<String, Object> body = Map.of("refresh_token", refreshToken);
        try {
            JsonNode node = post("/auth/v1/token?grant_type=refresh_token", body, null, "refresh");
            return Optional.ofNullable(toSession(node));
        } catch (AuthFailureException failure) {
            log.debug("Refresh rejected by Supabase: {}", failure.code());
            return Optional.empty();
        }
    }

    /** Best effort — the local session is cleared regardless of the outcome. */
    public void signOut(String accessToken) {
        try {
            post("/auth/v1/logout", Map.of(), accessToken, "sign_out");
        } catch (AuthFailureException failure) {
            log.debug("Sign-out call failed ({}), clearing locally anyway.", failure.code());
        }
    }

    public void requestPasswordReset(String email) {
        post("/auth/v1/recover", Map.of("email", email), null, "reset");
    }

    // ------------------------------------------------------------------
    // Plumbing
    // ------------------------------------------------------------------

    private JsonNode post(String path, Map<String, Object> body, String bearer, String action) {
        try {
            String raw = rest.post()
                    .uri(path)
                    .header("apikey", anonKey)
                    .headers(headers -> {
                        headers.setContentType(MediaType.APPLICATION_JSON);
                        headers.setAccept(java.util.List.of(MediaType.APPLICATION_JSON));
                        if (bearer != null && !bearer.isBlank()) {
                            headers.setBearerAuth(bearer);
                        }
                    })
                    .body(body == null ? Map.of() : body)
                    .retrieve()
                    .body(String.class);

            if (raw == null || raw.isBlank()) {
                return mapper.createObjectNode();
            }
            return mapper.readTree(raw);
        } catch (RestClientResponseException ex) {
            throw mapError(action, ex.getStatusCode().value(), ex.getResponseBodyAsString());
        } catch (ResourceAccessException ex) {
            log.warn("Supabase unreachable during {}: {}", action, ex.getMessage());
            throw new AuthFailureException(
                    "We cannot reach the sign-in service right now. "
                            + "Please try again in a moment.",
                    "service_unavailable",
                    HttpStatus.BAD_GATEWAY,
                    null);
        } catch (AuthFailureException ex) {
            throw ex;
        } catch (Exception ex) {
            log.warn("Unexpected Supabase failure during {}: {}", action, ex.toString());
            throw new AuthFailureException(
                    "Something went wrong. Please try again in a moment.",
                    "server_error",
                    HttpStatus.BAD_GATEWAY,
                    null);
        }
    }

    private AuthSession toSession(JsonNode node) {
        if (node == null) {
            return null;
        }
        String accessToken = node.path("access_token").asText("");
        JsonNode userNode = node.path("user");
        if (accessToken.isBlank() || !userNode.isObject()) {
            return null;
        }
        String refreshToken = node.path("refresh_token").asText("");
        long expiresAt = node.path("expires_at").asLong(0);
        if (expiresAt <= 0) {
            long expiresIn = node.path("expires_in").asLong(3600);
            expiresAt = System.currentTimeMillis() / 1000L + expiresIn;
        }
        Map<String, Object> user = mapper.convertValue(userNode, new TypeReference<>() {
        });
        return new AuthSession(accessToken, refreshToken, expiresAt, user);
    }

    private AuthFailureException mapError(String action, int status, String responseBody) {
        String body = responseBody == null ? "" : responseBody;
        String lowered = body.toLowerCase(Locale.ROOT);
        String code = errorCode(body);

        log.warn("Supabase {} failed: status={} code={}", action, status, code);

        if (status == 429 || lowered.contains("rate limit") || lowered.contains("too many")) {
            return new AuthFailureException(
                    "Too many attempts. Please wait a minute and try again.",
                    "rate_limited",
                    HttpStatus.TOO_MANY_REQUESTS,
                    null);
        }

        if ("sign_in".equals(action)) {
            if (lowered.contains("email not confirmed") || "email_not_confirmed".equals(code)) {
                return new AuthFailureException(
                        "Please confirm your e-mail address before signing in.",
                        "email_unconfirmed",
                        HttpStatus.FORBIDDEN,
                        "email");
            }
            return new AuthFailureException(
                    GENERIC_LOGIN_ERROR, "invalid_credentials", HttpStatus.UNAUTHORIZED, null);
        }

        if ("sign_up".equals(action)) {
            if (lowered.contains("already registered") || lowered.contains("already exists")) {
                return new AuthFailureException(
                        "An account with this e-mail already exists. Try signing in.",
                        "user_exists",
                        HttpStatus.CONFLICT,
                        "email");
            }
            if (lowered.contains("password")
                    && (lowered.contains("weak")
                            || lowered.contains("length")
                            || lowered.contains("characters"))) {
                return new AuthFailureException(
                        "Supabase rejected this password. Use at least 12 characters "
                                + "with mixed case, numbers and symbols.",
                        "weak_password",
                        HttpStatus.UNPROCESSABLE_ENTITY,
                        "password");
            }
            if (lowered.contains("invalid") && lowered.contains("email")) {
                return new AuthFailureException(
                        "That e-mail address was rejected. Please check it.",
                        "invalid_email",
                        HttpStatus.UNPROCESSABLE_ENTITY,
                        "email");
            }
            return new AuthFailureException(
                    GENERIC_SIGNUP_ERROR, "signup_failed", HttpStatus.BAD_REQUEST, null);
        }

        if ("reset".equals(action) && status == 429) {
            return new AuthFailureException(
                    "Too many reset requests. Please wait a minute and try again.",
                    "rate_limited",
                    HttpStatus.TOO_MANY_REQUESTS,
                    null);
        }

        return new AuthFailureException(
                "Something went wrong. Please try again in a moment.",
                "server_error",
                HttpStatus.BAD_GATEWAY,
                null);
    }

    private String errorCode(String body) {
        if (body == null || body.isBlank() || !body.trim().startsWith("{")) {
            return "";
        }
        try {
            JsonNode node = mapper.readTree(body);
            for (String key : new String[] {"error_code", "code", "error"}) {
                String value = node.path(key).asText("");
                if (!value.isBlank()) {
                    return value.toLowerCase(Locale.ROOT);
                }
            }
        } catch (Exception ignored) {
            // Body was not JSON; the lowered text match is enough.
        }
        return "";
    }

    /** Exposed for diagnostics only — never rendered to a browser. */
    String maskedKey() {
        if (anonKey.length() <= 8) {
            return "***";
        }
        return anonKey.substring(0, 4) + "…" + anonKey.substring(anonKey.length() - 4);
    }

    static HttpHeaders jsonHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        return headers;
    }
}

package in.alphagroup.portal.reset;

import in.alphagroup.portal.config.AlphaProperties;
import in.alphagroup.portal.user.PortalUser;
import in.alphagroup.portal.user.PortalUserRepository;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Locale;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Issues and consumes password reset tokens.
 *
 * <p>There is no mail server wired up, so the link is written to the log when
 * {@code alpha.log-reset-links} is on (the {@code dev} profile). Swapping in a
 * real sender means calling it from {@link #issue(String)} — nothing else
 * changes.
 */
@Service
public class PasswordResetService {

    private static final Logger log = LoggerFactory.getLogger(PasswordResetService.class);
    private static final int TOKEN_BYTES = 32;

    private final PasswordResetTokenRepository tokens;
    private final PortalUserRepository users;
    private final AlphaProperties properties;
    private final SecureRandom random = new SecureRandom();

    public PasswordResetService(
            PasswordResetTokenRepository tokens,
            PortalUserRepository users,
            AlphaProperties properties) {
        this.tokens = tokens;
        this.users = users;
        this.properties = properties;
    }

    /**
     * Creates a token for the address, if it belongs to an account.
     *
     * @return the relative reset link, or empty when the address is unknown
     *         (the caller must still answer generically — no enumeration)
     */
    @Transactional
    public Optional<String> issue(String email) {
        Optional<PortalUser> user = users.findByEmailIgnoreCase(email.trim());
        if (user.isEmpty()) {
            return Optional.empty();
        }

        byte[] raw = new byte[TOKEN_BYTES];
        random.nextBytes(raw);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(raw);

        int minutes = Math.max(1, properties.getSecurity().getResetTokenMinutes());
        tokens.save(new PasswordResetToken(
                user.get().getId(),
                sha256(token),
                Instant.now().plus(Duration.ofMinutes(minutes))));

        String link = "/reset-password?token=" + token;
        if (properties.isLogResetLinks()) {
            log.info("Password reset link for {} (valid {} min): {}", user.get().getEmail(), minutes, link);
        } else {
            log.info("Password reset token issued for {} (delivery not configured)", user.get().getEmail());
        }
        return Optional.of(link);
    }

    /** Resolves a token to the account it belongs to, without consuming it. */
    @Transactional(readOnly = true)
    public Optional<PortalUser> resolve(String token) {
        if (token == null || token.isBlank()) {
            return Optional.empty();
        }
        return tokens.findByTokenHash(sha256(token))
                .filter(PasswordResetToken::isUsable)
                .flatMap(entry -> users.findById(entry.getUserId()));
    }

    /** Marks every live token for the account as spent (single-use guarantee). */
    @Transactional
    public void consume(String token) {
        tokens.findByTokenHash(sha256(token)).ifPresent(entry -> {
            entry.markUsed();
            tokens.save(entry);
        });
    }

    static String sha256(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(value.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash).toLowerCase(Locale.ROOT);
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 unavailable", impossible);
        }
    }
}

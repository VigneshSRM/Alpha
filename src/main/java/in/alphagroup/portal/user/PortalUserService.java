package in.alphagroup.portal.user;

import java.time.Instant;
import java.util.Locale;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Account lookups plus registration. */
@Service
public class PortalUserService implements UserDetailsService {

    private final PortalUserRepository repository;
    private final PasswordEncoder encoder;

    public PortalUserService(PortalUserRepository repository, PasswordEncoder encoder) {
        this.repository = repository;
        this.encoder = encoder;
    }

    @Override
    @Transactional(readOnly = true)
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        return find(username);
    }

    @Transactional(readOnly = true)
    public PortalUser find(String email) {
        if (email == null || email.isBlank()) {
            throw new UsernameNotFoundException("No such account");
        }
        return repository.findByEmailIgnoreCase(email.trim())
                .orElseThrow(() -> new UsernameNotFoundException("No such account"));
    }

    /**
     * Creates an account. The caller must already have applied the password
     * policy — this method only hashes and persists.
     *
     * @throws EmailAlreadyUsedException when the address is taken
     */
    @Transactional
    public PortalUser register(String email, String rawPassword, String fullName, String phone) {
        String normalised = normalise(email);
        if (repository.existsByEmailIgnoreCase(normalised)) {
            throw new EmailAlreadyUsedException(normalised);
        }
        try {
            return repository.save(
                    new PortalUser(
                            normalised, encoder.encode(rawPassword), fullName.trim(), phone));
        } catch (DataIntegrityViolationException race) {
            // Two sign-ups for the same address at the same moment.
            throw new EmailAlreadyUsedException(normalised);
        }
    }

    @Transactional
    public void touchLastLogin(PortalUser user) {
        user.setLastLoginAt(Instant.now());
        repository.save(user);
    }

    @Transactional
    public void changePassword(PortalUser user, String rawPassword) {
        user.setPasswordHash(encoder.encode(rawPassword));
        repository.save(user);
    }

    public static String normalise(String email) {
        return email == null ? "" : email.trim().toLowerCase(Locale.ROOT);
    }

    /** Raised when registration collides with an existing address. */
    public static class EmailAlreadyUsedException extends RuntimeException {
        public EmailAlreadyUsedException(String email) {
            super("An account with this e-mail already exists: " + email);
        }
    }
}

package in.alphagroup.portal.user;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PortalUserRepository extends JpaRepository<PortalUser, Long> {

    Optional<PortalUser> findByEmailIgnoreCase(String email);

    boolean existsByEmailIgnoreCase(String email);
}

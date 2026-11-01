package in.alphagroup.portal.modules;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BusinessModuleRepository extends JpaRepository<BusinessModule, Long> {

    List<BusinessModule> findByUserIdOrderByIdAsc(Long userId);

    void deleteByUserId(Long userId);
}

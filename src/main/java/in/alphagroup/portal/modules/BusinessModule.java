package in.alphagroup.portal.modules;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import org.hibernate.annotations.CreationTimestamp;

/** One switched-on module for one account. */
@Entity
@Table(name = "business_module")
public class BusinessModule {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "module_code", nullable = false, length = 20)
    private String moduleCode;

    @CreationTimestamp
    @Column(name = "selected_at", nullable = false, updatable = false)
    private Instant selectedAt;

    protected BusinessModule() {
        // for JPA
    }

    public BusinessModule(Long userId, String moduleCode) {
        this.userId = userId;
        this.moduleCode = moduleCode;
    }

    public Long getId() {
        return id;
    }

    public Long getUserId() {
        return userId;
    }

    public String getModuleCode() {
        return moduleCode;
    }

    public Instant getSelectedAt() {
        return selectedAt;
    }
}

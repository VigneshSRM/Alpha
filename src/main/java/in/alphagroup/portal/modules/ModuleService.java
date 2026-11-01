package in.alphagroup.portal.modules;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Which modules an account has switched on. */
@Service
public class ModuleService {

    private final BusinessModuleRepository repository;

    public ModuleService(BusinessModuleRepository repository) {
        this.repository = repository;
    }

    /** Codes in the order the picker lists them. */
    @Transactional(readOnly = true)
    public List<String> selected(Long userId) {
        Set<String> chosen = new LinkedHashSet<>();
        for (BusinessModule saved : repository.findByUserIdOrderByIdAsc(userId)) {
            chosen.add(saved.getModuleCode());
        }
        return Modules.ALL.stream()
                .map(Modules.Module::code)
                .filter(chosen::contains)
                .toList();
    }

    @Transactional
    public List<String> save(Long userId, List<String> codes) {
        Set<String> keep = new LinkedHashSet<>();
        for (String code : codes) {
            if (Modules.isKnown(code)) {
                keep.add(code);
            }
        }
        repository.deleteByUserId(userId);
        repository.flush();
        for (Modules.Module module : Modules.ALL) {
            if (keep.contains(module.code())) {
                repository.save(new BusinessModule(userId, module.code()));
            }
        }
        return selected(userId);
    }
}

package vn.racehorse.audit.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import vn.racehorse.audit.entity.AuditEntry;
import vn.racehorse.audit.repository.AuditEntryRepository;
import vn.racehorse.common.ClubClock;
import vn.racehorse.security.AccountPrincipal;

import java.util.Map;

/**
 * Records key actions (BR-092). Joins the caller's transaction, so an action and its audit entry
 * are committed or rolled back together (TECH-008).
 */
@Service
public class AuditService {

    private final AuditEntryRepository entries;
    private final ObjectMapper json;
    private final ClubClock clock;

    public AuditService(AuditEntryRepository entries, ObjectMapper json, ClubClock clock) {
        this.entries = entries;
        this.json = json;
        this.clock = clock;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void record(AccountPrincipal actor, String action, String targetType, Long targetId,
                       Map<String, ?> details) {
        entries.save(new AuditEntry(actor.id(), actor.displayName(), clock.now(), action, targetType,
                targetId, toJson(details)));
    }

    /** For actions taken by the system itself, such as creating the first Club Manager. */
    @Transactional(propagation = Propagation.MANDATORY)
    public void recordSystem(String action, String targetType, Long targetId, Map<String, ?> details) {
        entries.save(new AuditEntry(null, "Hệ thống", clock.now(), action, targetType, targetId,
                toJson(details)));
    }

    private String toJson(Map<String, ?> details) {
        try {
            return json.writeValueAsString(details == null ? Map.of() : details);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("Audit details are not serialisable", e);
        }
    }
}

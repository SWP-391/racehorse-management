package vn.racehorse.security;

import jakarta.persistence.criteria.Predicate;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import vn.racehorse.account.entity.Role;
import vn.racehorse.common.ClubClock;
import vn.racehorse.common.exception.ApiException;
import vn.racehorse.horse.entity.CareRole;
import vn.racehorse.horse.entity.Horse;
import vn.racehorse.horse.repository.HorseRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * THE rule of "which horses may this person see" (BR-261, BR-153, BR-185, TECH-F3). The Club Manager sees every horse;
 * a Head Trainer, a Veterinarian and a Groom see the taken-in horses they look after (main person, or substitute while
 * the return date has not come); a Horse Owner sees own horses in any intake state. The rule exists in three shapes
 * that must agree: {@link #specification()} for JPA, {@link #sql(String)} for native queries and
 * {@link #canSee(AccountPrincipal, Horse, LocalDate)} for one loaded horse. The taken-in part is
 * {@link HorseRepository#managed()}, the specification twin of {@code HorseRepository.MANAGED}.
 *
 * <p>A horse a person may not see answers 404 {@code HORSE_NOT_FOUND}, the same as a horse that does not exist,
 * never 403. With no signed-in person (a job or an event listener) nothing is hidden.
 */
@Component
public class HorseVisibility {

    private final HorseRepository horses;
    private final ClubClock clock;

    public HorseVisibility(HorseRepository horses, ClubClock clock) {
        this.horses = horses;
        this.clock = clock;
    }

    /** The signed-in person, or null when the code runs without one (a scheduled job, an event). */
    public AccountPrincipal actor() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return auth != null && auth.getPrincipal() instanceof AccountPrincipal p ? p : null;
    }

    private static boolean seesAll(AccountPrincipal actor) {
        return actor == null || actor.role() == Role.CLUB_MANAGER;
    }

    public static ApiException notFound() {
        return new ApiException(HttpStatus.NOT_FOUND, "HORSE_NOT_FOUND", "Không tìm thấy ngựa.");
    }

    // ---- JPA ----------------------------------------------------------------------------------------------

    public Specification<Horse> specification() {
        return specification(actor(), clock.today());
    }

    public Specification<Horse> specification(AccountPrincipal actor, LocalDate today) {
        if (seesAll(actor)) {
            return (root, query, cb) -> cb.conjunction();
        }
        if (actor.role() == Role.HORSE_OWNER) {
            return (root, query, cb) -> cb.equal(root.get("owner").get("id"), actor.id());
        }
        CareRole care = CareRole.of(actor.role());
        return HorseRepository.managed().and((root, query, cb) -> {
            Predicate main = cb.equal(root.get(care.mainProperty()).get("id"), actor.id());
            Predicate substitute = cb.and(cb.equal(root.get(care.tempProperty()).get("id"), actor.id()),
                    cb.greaterThan(root.<LocalDate>get(care.untilProperty()), today));
            return cb.or(main, substitute);
        });
    }

    // ---- SQL ----------------------------------------------------------------------------------------------

    /** A condition on the table {@code horse} under the given alias, for the current person. Ids and dates are literals. */
    public String sql(String alias) {
        return sql(actor(), clock.today(), alias);
    }

    public String sql(AccountPrincipal actor, LocalDate today, String alias) {
        if (seesAll(actor)) {
            return "true";
        }
        long id = actor.id();
        if (actor.role() == Role.HORSE_OWNER) {
            return alias + ".owner_id = " + id;
        }
        CareRole care = CareRole.of(actor.role());
        return alias + ".intake_state = 'ACCEPTED' and (" + alias + "." + care.mainColumn() + " = " + id
                + " or (" + alias + "." + care.tempColumn() + " = " + id + " and " + alias + "." + care.untilColumn()
                + " > DATE '" + today + "'))";
    }

    /** `column in (visible horses)`: the form for a native query over a table that has a horse id column. */
    public String sqlHorseIdIn(String column) {
        AccountPrincipal actor = actor();
        if (seesAll(actor)) {
            return "true";
        }
        return column + " in (select hv.id from horse hv where " + sql(actor, clock.today(), "hv") + ")";
    }

    // ---- one horse and lists --------------------------------------------------------------------------------

    public boolean canSee(AccountPrincipal actor, Horse horse, LocalDate today) {
        if (seesAll(actor)) {
            return true;
        }
        if (actor.role() == Role.HORSE_OWNER) {
            return horse.getOwner().getId().equals(actor.id());
        }
        if (!horse.isTakenIn()) {
            return false;
        }
        CareRole care = CareRole.of(actor.role());
        if (care.main(horse) != null && care.main(horse).getId().equals(actor.id())) {
            return true;
        }
        return care.temp(horse) != null && care.temp(horse).getId().equals(actor.id())
                && care.until(horse) != null && today.isBefore(care.until(horse));
    }

    public boolean canSee(Horse horse) {
        return canSee(actor(), horse, clock.today());
    }

    /** The horse when the current person may see it; 404 {@code HORSE_NOT_FOUND} when not, or when it does not exist. */
    public Horse require(Long horseId) {
        return find(horseId).orElseThrow(HorseVisibility::notFound);
    }

    public Optional<Horse> find(Long horseId) {
        if (horseId == null) {
            return Optional.empty();
        }
        return horses.findById(horseId).filter(this::canSee);
    }

    /** The loaded horse itself when the current person may see it, else 404 {@code HORSE_NOT_FOUND}. */
    public Horse check(Horse horse) {
        if (horse == null || !canSee(horse)) {
            throw notFound();
        }
        return horse;
    }

    /** As {@link #require} but takes the row lock of the horse (a write that must take turns). */
    public Horse requireForUpdate(Long horseId) {
        return horseId == null ? check(null) : check(horses.findByIdForUpdate(horseId).orElse(null));
    }

    public boolean isVisible(Long horseId) {
        return find(horseId).isPresent();
    }

    /** Taken-in horses the current person may see, by name (the "managed" view used by every module but the registry). */
    public List<Horse> managed() {
        return horses.findAll(HorseRepository.managed().and(specification()), Sort.by("name").ascending());
    }

    /** The registry view: horses in any intake state the current person may see, by name. */
    public List<Horse> registry() {
        return horses.findAll(specification(), Sort.by("name").ascending());
    }

    /** Ids of the visible horses (taken-in ones for staff, any state for the Club Manager and an owner). */
    public Set<Long> visibleIds() {
        return registry().stream().map(Horse::getId).collect(Collectors.toSet());
    }

    /** Keeps the horses the current person may see; the filter for lists of rows that carry a horse id. */
    public <T> List<T> filterByHorseId(List<T> rows, java.util.function.Function<T, Long> horseIdOf) {
        AccountPrincipal actor = actor();
        if (seesAll(actor)) {
            return rows;
        }
        Set<Long> visible = visibleIds();
        return rows.stream().filter(r -> horseIdOf.apply(r) != null && visible.contains(horseIdOf.apply(r))).toList();
    }
}

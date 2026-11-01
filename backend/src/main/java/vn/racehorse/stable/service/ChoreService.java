package vn.racehorse.stable.service;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.racehorse.account.entity.Role;
import vn.racehorse.account.entity.UserAccount;
import vn.racehorse.account.repository.UserAccountRepository;
import vn.racehorse.audit.service.AuditService;
import vn.racehorse.common.ClubClock;
import vn.racehorse.common.exception.ApiException;
import vn.racehorse.common.paging.PageParams;
import vn.racehorse.common.paging.PageResponse;
import vn.racehorse.common.paging.Paging;
import vn.racehorse.common.paging.Search;
import vn.racehorse.horse.entity.Horse;
import vn.racehorse.horse.repository.HorseRepository;
import vn.racehorse.ration.service.RationQuantities;
import vn.racehorse.schedule.service.GroomScheduleService;
import vn.racehorse.security.AccessGuard;
import vn.racehorse.security.AccountPrincipal;
import vn.racehorse.security.Permission;
import vn.racehorse.stable.dto.ChoreDay;
import vn.racehorse.stable.dto.ChoreHorseRange;
import vn.racehorse.stable.dto.ChoreView;
import vn.racehorse.stable.dto.DayChores;
import vn.racehorse.stable.dto.HorseChores;
import vn.racehorse.stable.entity.Chore;
import vn.racehorse.stable.entity.ChoreEntry;
import vn.racehorse.stable.repository.ChoreEntryRepository;
import vn.racehorse.stock.dto.StockLine;
import vn.racehorse.stock.exception.InsufficientStockException;
import vn.racehorse.stock.service.StockNotices;
import vn.racehorse.stock.service.StockService;
import vn.racehorse.stock.service.SupplyUsed;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Daily chores: list, mark, cancel and history (BR-124 to BR-127, BR-133). A chore not done has no row; the list is
 * derived from horses x chores x the day's active entries (TECH-091). Marking never touches health, lock or training.
 * Marking "Cho ăn" takes the horse's ration of that day out of stock, in the same transaction, and cancelling it puts
 * the stock back (BR-173); the Club Manager may fill a past day with a reason (BR-174).
 */
@Service
public class ChoreService {

    private final ChoreEntryRepository entries;
    private final HorseRepository horses;
    private final UserAccountRepository accounts;
    private final AccessGuard access;
    private final AuditService audit;
    private final ClubClock clock;
    private final GroomScheduleService schedules;
    private final RationQuantities rations;
    private final StockService stock;
    private final StockNotices stockNotices;

    public ChoreService(ChoreEntryRepository entries, HorseRepository horses, UserAccountRepository accounts,
                        AccessGuard access, AuditService audit, ClubClock clock, GroomScheduleService schedules,
                        RationQuantities rations, StockService stock, StockNotices stockNotices) {
        this.rations = rations;
        this.stock = stock;
        this.stockNotices = stockNotices;
        this.schedules = schedules;
        this.entries = entries;
        this.horses = horses;
        this.accounts = accounts;
        this.access = access;
        this.audit = audit;
        this.clock = clock;
    }

    /** All non-archived horses (Owner: own only) with the four chores of the date. */
    @Transactional(readOnly = true)
    public ChoreDay list(String date) {
        AccountPrincipal user = access.require(Permission.VIEW_CHORES);
        LocalDate day = date == null || date.isBlank() ? clock.today() : parseDate(date);
        boolean owner = user.role() == Role.HORSE_OWNER;
        if (owner && !day.equals(clock.today())) {
            // PERM-036: the Owner sees done or not done for today only.
            throw ApiException.forbidden();
        }
        Map<Long, Map<Chore, ChoreEntry>> done = doneByHorse(day);
        List<HorseChores> rows = horsesOn(user, day, done.keySet()).stream().map(h -> {
            Map<Chore, ChoreEntry> byChore = done.getOrDefault(h.getId(), Map.of());
            List<ChoreView> views = java.util.Arrays.stream(Chore.values()).map(c -> {
                ChoreEntry e = byChore.get(c);
                if (e == null) {
                    return new ChoreView(c, false, null, null, null, null);
                }
                return owner ? new ChoreView(c, true, null, null, null, null)
                        : new ChoreView(c, true, e.getId(), e.getGroom().getDisplayName(), e.getMarkedAt(), e.getGroom().getId(),
                        e.isOnBehalf(), e.isBackfilled());
            }).toList();
            return new HorseChores(h, views);
        }).toList();
        return new ChoreDay(day, clock.today(), rows);
    }

    static final int MAX_RANGE_DAYS = 31;

    /**
     * The chore history of a date range, paged by horse (BR-188, TECH-182): every date on which the horse is listed under
     * the rule of a past day, with its four chores. The Owner gets own horses and done or not done only (AC-376).
     */
    @Transactional(readOnly = true)
    public PageResponse<ChoreHorseRange> range(LocalDate from, LocalDate to, Long horseId, PageParams params) {
        AccountPrincipal user = access.require(Permission.VIEW_CHORES);
        LocalDate end = to == null ? clock.today() : to;
        LocalDate start = from == null ? end.minusDays(6) : from;
        if (end.isBefore(start)) {
            throw ApiException.invalid("INVALID_RANGE", "to", "Ngày kết thúc phải sau ngày bắt đầu.");
        }
        if (start.plusDays(MAX_RANGE_DAYS).isBefore(end.plusDays(1))) {
            throw ApiException.invalid("RANGE_TOO_LONG", "to", "Chỉ xem được tối đa " + MAX_RANGE_DAYS + " ngày một lần.");
        }
        boolean owner = user.role() == Role.HORSE_OWNER;
        Map<LocalDate, Map<Long, Map<Chore, ChoreEntry>>> byDay = new HashMap<>();
        for (ChoreEntry e : entries.findByChoreDateBetweenAndCancelledAtIsNull(start, end)) {
            byDay.computeIfAbsent(e.getChoreDate(), k -> new HashMap<>())
                    .computeIfAbsent(e.getHorseId(), k -> new EnumMap<>(Chore.class)).put(e.getChore(), e);
        }
        Map<Long, Horse> horsesById = new java.util.LinkedHashMap<>();
        Map<Long, List<LocalDate>> listedOn = new HashMap<>();
        for (LocalDate day = end; !day.isBefore(start); day = day.minusDays(1)) {
            Map<Long, Map<Chore, ChoreEntry>> done = byDay.getOrDefault(day, Map.of());
            for (Horse h : horsesOn(user, day, done.keySet())) {
                horsesById.putIfAbsent(h.getId(), h);
                listedOn.computeIfAbsent(h.getId(), k -> new java.util.ArrayList<>()).add(day);
            }
        }
        List<ChoreHorseRange> rows = horsesById.values().stream()
                .filter(h -> horseId == null || h.getId().equals(horseId))
                .filter(h -> Search.matches(params.q(), h.getName(), h.getRegistrationNumber()))
                .sorted(java.util.Comparator.comparing(Horse::getName, String.CASE_INSENSITIVE_ORDER)
                        .thenComparing(Horse::getId))
                .map(h -> new ChoreHorseRange(h, listedOn.get(h.getId()).stream().map(day -> {
                    Map<Chore, ChoreEntry> byChore = byDay.getOrDefault(day, Map.of()).getOrDefault(h.getId(), Map.of());
                    return new DayChores(day, java.util.Arrays.stream(Chore.values()).map(c -> view(c, byChore.get(c), owner)).toList());
                }).toList()))
                .toList();
        return Paging.page(rows, params);
    }

    private static ChoreView view(Chore chore, ChoreEntry e, boolean owner) {
        if (e == null) {
            return new ChoreView(chore, false, null, null, null, null);
        }
        return owner ? new ChoreView(chore, true, null, null, null, null)
                : new ChoreView(chore, true, e.getId(), e.getGroom().getDisplayName(), e.getMarkedAt(), e.getGroom().getId(),
                e.isOnBehalf(), e.isBackfilled());
    }

    @Transactional
    public ChoreView mark(Long horseId, String choreCode, String date) {
        AccountPrincipal actor = access.require(Permission.MARK_CHORES);
        Chore chore = Chore.parse(choreCode);
        if (chore == null) {
            throw ApiException.invalid("VALIDATION_FAILED", "chore", "Việc chăm sóc không hợp lệ.");
        }
        LocalDate day = date == null || date.isBlank() ? clock.today() : parseDate(date);
        Horse horse = horseId == null ? null : horses.findById(horseId).orElse(null);
        if (horse == null) {
            throw ApiException.notFound("ngựa");
        }
        if (horse.isArchived()) {
            throw new ApiException(HttpStatus.CONFLICT, "HORSE_ARCHIVED", "Ngựa đã ngừng quản lý: không thể đánh dấu việc.");
        }
        if (!day.equals(clock.today())) {
            throw notToday();
        }
        if (entries.findByHorseIdAndChoreDateAndChoreAndCancelledAtIsNull(horseId, day, chore).isPresent()) {
            throw alreadyDone();
        }
        UserAccount groom = accounts.findById(actor.id()).orElseThrow();
        ChoreEntry entry;
        try {
            // "Làm thay": the Groom is outside their barn hours or on leave at this moment (BR-169, TECH-147).
            boolean onBehalf = schedules.outsideHoursOrOnLeave(groom.getId(), clock.now());
            entry = entries.saveAndFlush(new ChoreEntry(horseId, day, chore, groom, clock.now(), onBehalf));
        } catch (DataIntegrityViolationException e) {
            // A concurrent mark won the partial unique index (BR-125).
            throw alreadyDone();
        }
        if (chore == Chore.FEEDING) {
            takeRation(horse, entry, day, actor.id(), SupplyUsed.Source.FEED);
        }
        Map<String, Object> details = new LinkedHashMap<>();
        details.put("horseId", horseId);
        details.put("chore", chore.name());
        details.put("date", day.toString());
        if (entry.isOnBehalf()) {
            details.put("onBehalf", true);
        }
        audit.record(actor, "CHORE_MARKED", "CHORE_ENTRY", entry.getId(), details);
        return new ChoreView(chore, true, entry.getId(), groom.getDisplayName(), entry.getMarkedAt(), groom.getId(),
                entry.isOnBehalf());
    }

    /**
     * Cancels a mistaken mark of today with a mandatory reason; the entry stays in the history. A Groom cancels only a
     * mark they made themselves; the Club Manager may cancel any mark (BR-126, BR-170, TECH-138).
     */
    @Transactional
    public ChoreView cancel(Long entryId, String reason) {
        AccountPrincipal actor = access.requireAny(Permission.MARK_CHORES, Permission.MANAGE_CHORE_MARKS);
        ChoreEntry entry = entries.findById(entryId).orElseThrow(() -> ApiException.notFound("việc chăm sóc"));
        boolean ownMark = Permission.MARK_CHORES.allows(actor.role()) && entry.getGroom().getId().equals(actor.id());
        if (!ownMark && !Permission.MANAGE_CHORE_MARKS.allows(actor.role())) {
            throw ApiException.forbidden();
        }
        if (reason == null || reason.isBlank()) {
            throw ApiException.invalid("VALIDATION_FAILED", "reason", "Bắt buộc nhập lý do hủy.");
        }
        if (!entry.getChoreDate().equals(clock.today())) {
            throw notToday();
        }
        if (horses.findById(entry.getHorseId()).map(Horse::isArchived).orElse(false)) {
            throw new ApiException(HttpStatus.CONFLICT, "HORSE_ARCHIVED", "Ngựa đã ngừng quản lý: không thể hủy đánh dấu.");
        }
        if (entry.isCancelled()) {
            throw new ApiException(HttpStatus.CONFLICT, "CHORE_ALREADY_CANCELLED", "Đánh dấu này đã được hủy.");
        }
        UserAccount canceller = accounts.findById(actor.id()).orElseThrow();
        entry.cancel(canceller, reason.trim(), clock.now());
        if (entry.getChore() == Chore.FEEDING) {
            stock.reverse("CHORE_ENTRY", entry.getId(), actor.id());
        }
        Map<String, Object> details = new LinkedHashMap<>();
        details.put("horseId", entry.getHorseId());
        details.put("chore", entry.getChore().name());
        details.put("date", entry.getChoreDate().toString());
        details.put("reason", entry.getCancelReason());
        audit.record(actor, "CHORE_CANCELLED", "CHORE_ENTRY", entry.getId(), details);
        return new ChoreView(entry.getChore(), false, null, null, null, null);
    }

    /**
     * The Club Manager marks a chore of a past day, with a mandatory reason (BR-174, TECH-158). The horse must have been
     * managed that day; "Cho ăn" takes the ration in force on that date out of stock with the same rules as today.
     */
    @Transactional
    public ChoreView backfill(Long horseId, String choreCode, String date, String reason) {
        AccountPrincipal actor = access.require(Permission.MANAGE_CHORE_MARKS);
        Chore chore = Chore.parse(choreCode);
        if (chore == null) {
            throw ApiException.invalid("VALIDATION_FAILED", "chore", "Việc chăm sóc không hợp lệ.");
        }
        if (date == null || date.isBlank()) {
            throw ApiException.invalid("VALIDATION_FAILED", "date", "Bắt buộc nhập ngày.");
        }
        LocalDate day = parseDate(date);
        if (reason == null || reason.isBlank()) {
            throw ApiException.invalid("VALIDATION_FAILED", "reason", "Bắt buộc nhập lý do bổ sung.");
        }
        if (!day.isBefore(clock.today())) {
            throw ApiException.invalid("DATE_NOT_PAST", "date", "Chỉ bổ sung được ngày đã qua.");
        }
        Horse horse = horseId == null ? null : horses.findById(horseId).orElse(null);
        if (horse == null) {
            throw ApiException.notFound("ngựa");
        }
        if (clock.toClubTime(horse.getCreatedAt()).toLocalDate().isAfter(day)) {
            throw ApiException.invalid("HORSE_NOT_YET_MANAGED", "date", "Ngựa chưa được quản lý vào ngày này.");
        }
        if (horse.getArchivedAt() != null && clock.toClubTime(horse.getArchivedAt()).toLocalDate().isBefore(day)) {
            throw new ApiException(HttpStatus.CONFLICT, "HORSE_ARCHIVED", "Ngựa đã ngừng quản lý vào ngày này.");
        }
        if (entries.findByHorseIdAndChoreDateAndChoreAndCancelledAtIsNull(horseId, day, chore).isPresent()) {
            throw alreadyDone();
        }
        UserAccount manager = accounts.findById(actor.id()).orElseThrow();
        ChoreEntry entry;
        try {
            entry = entries.saveAndFlush(ChoreEntry.backfill(horseId, day, chore, manager, clock.now(), reason.trim()));
        } catch (DataIntegrityViolationException e) {
            throw alreadyDone();
        }
        if (chore == Chore.FEEDING) {
            takeRation(horse, entry, day, actor.id(), SupplyUsed.Source.FEED_BACKFILL);
        }
        Map<String, Object> details = new LinkedHashMap<>();
        details.put("horseId", horseId);
        details.put("chore", chore.name());
        details.put("date", day.toString());
        details.put("reason", reason.trim());
        audit.record(actor, "CHORE_BACKFILLED", "CHORE_ENTRY", entry.getId(), details);
        return new ChoreView(chore, true, entry.getId(), manager.getDisplayName(), entry.getMarkedAt(), manager.getId(),
                false, true);
    }

    /**
     * Takes the ration of the day out of stock for a "Cho ăn" mark (BR-173, TECH-157). A horse without a ration takes
     * nothing. A short item refuses the whole mark: the Club Manager is told at once (in a separate transaction so the
     * notice survives) and nothing is written.
     */
    private void takeRation(Horse horse, ChoreEntry entry, LocalDate day, Long actorId, SupplyUsed.Source source) {
        Map<Long, Long> use = rations.dailyUse(horse.getId(), day);
        if (use.isEmpty()) {
            return;
        }
        List<StockLine> lines = use.entrySet().stream()
                .map(e -> new StockLine(e.getKey(), e.getValue())).toList();
        try {
            stock.use(horse.getId(), lines, source, "CHORE_ENTRY", entry.getId(), day, actorId);
        } catch (InsufficientStockException e) {
            e.getShortages().forEach(s -> stockNotices.shortage(s, horse.getName()));
            Map<String, String> fields = new LinkedHashMap<>();
            e.getShortages().forEach(s -> fields.put(s.name(), "Thiếu " + s.missing() + " " + s.unit()));
            String names = String.join(", ", e.getShortages().stream()
                    .map(s -> "\"" + s.name() + "\" (thiếu " + s.missing() + " " + s.unit() + ")").toList());
            throw new ApiException(HttpStatus.CONFLICT, "INSUFFICIENT_STOCK",
                    "Không đủ vật tư trong kho để cho ăn: " + names + ".", fields);
        }
    }

    /** Every entry of a horse including cancelled ones; not for the Horse Owner (TECH-094). */
    @Transactional(readOnly = true)
    public List<ChoreEntry> history(Long horseId) {
        AccountPrincipal user = access.require(Permission.VIEW_CHORES);
        if (user.role() == Role.HORSE_OWNER) {
            throw ApiException.forbidden();
        }
        horses.findById(horseId).orElseThrow(() -> ApiException.notFound("ngựa"));
        return entries.findByHorseIdOrderByChoreDateDescMarkedAtDescIdDesc(horseId);
    }

    // ---- shared with the barn page and the reminder -------------------------------------------------------

    /**
     * The horses of a date's list: today, the non-archived ones; a past day, the horses registered by then and not
     * archived before it, plus any horse with an entry that day, so a horse archived since keeps its history readable
     * (BR-127).
     */
    List<Horse> horsesOn(AccountPrincipal user, LocalDate day, java.util.Set<Long> withEntries) {
        if (!day.isBefore(clock.today())) {
            return visibleHorses(user);
        }
        List<Horse> all = user.role() == Role.HORSE_OWNER
                ? horses.findByOwnerIdOrderByNameAsc(user.id())
                : horses.findAllByOrderByNameAsc();
        return all.stream()
                .filter(h -> withEntries.contains(h.getId())
                        || (!clock.toClubTime(h.getCreatedAt()).toLocalDate().isAfter(day)
                        && (h.getArchivedAt() == null || !clock.toClubTime(h.getArchivedAt()).toLocalDate().isBefore(day))))
                .toList();
    }

    /** Non-archived horses the user may see: every one, or only own for a Horse Owner (BR-127, TECH-094). */
    List<Horse> visibleHorses(AccountPrincipal user) {
        List<Horse> all = user.role() == Role.HORSE_OWNER
                ? horses.findByOwnerIdOrderByNameAsc(user.id())
                : horses.findAllByOrderByNameAsc();
        return all.stream().filter(h -> !h.isArchived()).toList();
    }

    Map<Long, Map<Chore, ChoreEntry>> doneByHorse(LocalDate day) {
        Map<Long, Map<Chore, ChoreEntry>> done = new HashMap<>();
        for (ChoreEntry e : entries.findByChoreDateAndCancelledAtIsNull(day)) {
            done.computeIfAbsent(e.getHorseId(), k -> new EnumMap<>(Chore.class)).put(e.getChore(), e);
        }
        return done;
    }

    private static LocalDate parseDate(String date) {
        try {
            return LocalDate.parse(date.trim());
        } catch (DateTimeParseException e) {
            throw ApiException.invalid("VALIDATION_FAILED", "date", "Ngày không hợp lệ (định dạng yyyy-MM-dd).");
        }
    }

    private static ApiException notToday() {
        return new ApiException(HttpStatus.CONFLICT, "CHORE_NOT_TODAY",
                "Chỉ được đánh dấu hoặc hủy việc của ngày hôm nay.");
    }

    private static ApiException alreadyDone() {
        return new ApiException(HttpStatus.CONFLICT, "CHORE_ALREADY_DONE", "Việc này đã được đánh dấu hoàn thành.");
    }
}

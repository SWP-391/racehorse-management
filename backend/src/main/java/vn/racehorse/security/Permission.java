package vn.racehorse.security;

import vn.racehorse.account.entity.Role;

import java.util.EnumSet;
import java.util.Set;

/**
 * The approved permission matrix (ba/09-permission-matrix.md) in one place (TECH-007).
 * A role not listed for a permission is denied (default deny, BR-091). Data scope such as
 * "own horses" is enforced by the services, not here. Later slices add their PERM rows.
 */
public enum Permission {

    /** PERM-001 View horse profile and pedigree (Owner: own horses only). */
    VIEW_HORSES("PERM-001", Role.HEAD_TRAINER, Role.VETERINARIAN, Role.HORSE_OWNER, Role.CLUB_MANAGER),
    /** PERM-002 View the minimal horse list (name, registration number) to choose a horse for an incident. */
    VIEW_HORSE_OPTIONS("PERM-002", Role.HEAD_TRAINER, Role.VETERINARIAN, Role.GROOM, Role.CLUB_MANAGER),
    /** PERM-003 Create and edit horse profile and pedigree. */
    MANAGE_HORSES("PERM-003", Role.CLUB_MANAGER),
    /** PERM-004 Archive and restore a horse. */
    ARCHIVE_HORSES("PERM-004", Role.CLUB_MANAGER),
    /** PERM-005 Create, edit, activate, complete, cancel a plan; restore a plan version. */
    MANAGE_PLANS("PERM-005", Role.HEAD_TRAINER),
    /** PERM-006 Draft, schedule and cancel sessions; set responsible person. */
    SCHEDULE_SESSIONS("PERM-006", Role.HEAD_TRAINER),
    /** PERM-007 Start and end sessions; record indicators, form and comment. */
    RUN_SESSIONS("PERM-007", Role.HEAD_TRAINER),
    /** PERM-008 Set alert thresholds. */
    SET_THRESHOLDS("PERM-008", Role.HEAD_TRAINER),
    /** PERM-009 View live simulated data. */
    VIEW_LIVE("PERM-009", Role.HEAD_TRAINER),
    /** PERM-010 Receive threshold alerts. */
    RECEIVE_ALERTS("PERM-010", Role.HEAD_TRAINER, Role.VETERINARIAN),
    /** PERM-011 View progress board and fitness charts. */
    VIEW_PROGRESS_BOARD("PERM-011", Role.HEAD_TRAINER),
    /** PERM-012 View plans, schedules, sessions and comments (Owner: own horses, schedule and comments). */
    VIEW_PLANS("PERM-012", Role.HEAD_TRAINER, Role.VETERINARIAN, Role.HORSE_OWNER, Role.CLUB_MANAGER),
    /** PERM-013 Review blocked sessions after a lock (keep, shift, edit). */
    REVIEW_BLOCKED_SESSIONS("PERM-013", Role.HEAD_TRAINER),
    /** PERM-014 Add a note requesting lock review. */
    REQUEST_LOCK_REVIEW("PERM-014", Role.HEAD_TRAINER),
    /** PERM-015 Change health status (horse idle). */
    CHANGE_HEALTH_STATUS("PERM-015", Role.VETERINARIAN),
    /** PERM-016 Record examinations, diagnosis, weight, treatment, prescription. */
    RECORD_EXAMINATIONS("PERM-016", Role.VETERINARIAN),
    /** PERM-017 Read examination details (diagnosis, body area, treatment, prescription). */
    VIEW_EXAMINATIONS("PERM-017", Role.HEAD_TRAINER, Role.VETERINARIAN),
    /** PERM-018 Place and lift a lock (horse idle), choose return level. */
    MANAGE_LOCKS("PERM-018", Role.VETERINARIAN),
    /** PERM-019 Register and withdraw a pending lock request (horse training). */
    REQUEST_PENDING_LOCK("PERM-019", Role.VETERINARIAN),
    /** PERM-020 View health status, lock and activity status. */
    VIEW_HEALTH("PERM-020", Role.HEAD_TRAINER, Role.VETERINARIAN, Role.CLUB_MANAGER),
    /** PERM-021 View readiness, health indicators and weight (Owner: own horses only). */
    VIEW_READINESS("PERM-021", Role.HEAD_TRAINER, Role.VETERINARIAN, Role.HORSE_OWNER, Role.CLUB_MANAGER),
    /** PERM-022 Manage care items and record completion. */
    MANAGE_CARE("PERM-022", Role.VETERINARIAN),
    /** PERM-023 Send incident reports (with an optional body region) and view their status. */
    SEND_INCIDENTS("PERM-023", Role.GROOM),
    /** PERM-024 Receive, acknowledge and close incident reports. */
    HANDLE_INCIDENTS("PERM-024", Role.VETERINARIAN),
    /** PERM-025 Manage staff and Owner accounts. */
    MANAGE_ACCOUNTS("PERM-025", Role.CLUB_MANAGER),
    /** PERM-026 View audit log. */
    VIEW_AUDIT_LOG("PERM-026", Role.CLUB_MANAGER),
    /** PERM-027 Configure the auto-close grace period. */
    CONFIGURE_GRACE_PERIOD("PERM-027", Role.CLUB_MANAGER),
    /** PERM-028 Create injury marks, change their recovery status and cancel a mistaken mark (only while Mới). */
    MANAGE_INJURY_MARKS("PERM-028", Role.VETERINARIAN),
    /** PERM-029 View injury marks, the 3D view and recovery history (read-only for the Head Trainer). */
    VIEW_INJURY_MARKS("PERM-029", Role.HEAD_TRAINER, Role.VETERINARIAN),
    /** PERM-030 Upload and delete trial-run videos. */
    MANAGE_TRIAL_VIDEOS("PERM-030", Role.HEAD_TRAINER),
    /** PERM-031 Watch trial-run videos (Owner: own horses only). */
    WATCH_TRIAL_VIDEOS("PERM-031", Role.HEAD_TRAINER, Role.HORSE_OWNER),
    /** PERM-032 View training sessions assigned to oneself, read-only (Groom: only as responsible person). */
    VIEW_ASSIGNED_SESSIONS("PERM-032", Role.GROOM),
    /** PERM-033 Confirm "Đã nhận" for a session assigned to oneself. */
    ACKNOWLEDGE_ASSIGNMENT("PERM-033", Role.GROOM),
    /** PERM-034 Configure the late-change threshold for responsible persons (BR-123). */
    CONFIGURE_LATE_CHANGE_THRESHOLD("PERM-034", Role.CLUB_MANAGER),
    /** PERM-035 Mark a daily chore done and cancel a mark (same day, with reason). */
    MARK_CHORES("PERM-035", Role.GROOM),
    /** PERM-036 View daily chore lists and chore history (Owner: own horses, done or not only). */
    VIEW_CHORES("PERM-036", Role.HEAD_TRAINER, Role.VETERINARIAN, Role.GROOM, Role.HORSE_OWNER, Role.CLUB_MANAGER),
    /** PERM-037 Assign, move and clear a horse's stall. */
    MANAGE_STALLS("PERM-037", Role.CLUB_MANAGER),
    /** PERM-038 View the barn page (Groom: name, registration number, stall, chores; Owner: own horses). */
    VIEW_BARN("PERM-038", Role.HEAD_TRAINER, Role.VETERINARIAN, Role.GROOM, Role.HORSE_OWNER, Role.CLUB_MANAGER),
    /** PERM-039 Manage supply items and quantities on hand. */
    MANAGE_SUPPLIES("PERM-039", Role.CLUB_MANAGER),
    /** PERM-055 Manage the catalog of units of supplies. */
    MANAGE_SUPPLY_UNITS("PERM-055", Role.CLUB_MANAGER),
    /** PERM-056 View own timetable (read-only): barn hours, leave and sessions of a Groom, sessions of assigned horses of a Head Trainer. */
    VIEW_OWN_TIMETABLE("PERM-056", Role.GROOM, Role.HEAD_TRAINER),
    /** PERM-057 Export statements and invoices to a spreadsheet and print an invoice. */
    EXPORT_BILLING("PERM-057", Role.HORSE_OWNER, Role.CLUB_MANAGER),
    /** PERM-040 View supply items and restock proposals (Groom: item list and own proposals). */
    VIEW_SUPPLIES("PERM-040", Role.GROOM, Role.CLUB_MANAGER),
    /** PERM-041 Propose a restock. */
    PROPOSE_RESTOCK("PERM-041", Role.GROOM),
    /** PERM-042 Approve or reject a restock proposal. */
    DECIDE_RESTOCK("PERM-042", Role.CLUB_MANAGER),
    /** PERM-043 Compose and change a horse's ration. */
    COMPOSE_RATION("PERM-043", Role.VETERINARIAN),
    /** PERM-044 View a horse's ration (Owner: current version of own horses; history for staff). */
    VIEW_RATION("PERM-044", Role.HEAD_TRAINER, Role.VETERINARIAN, Role.GROOM, Role.HORSE_OWNER, Role.CLUB_MANAGER),
    /** PERM-045 Assign, change and temporarily assign a horse's Head Trainer. */
    ASSIGN_TRAINER("PERM-045", Role.CLUB_MANAGER),
    /** PERM-053 Cancel any chore mark of the current day and mark a chore of a past day, each with a reason (BR-170, BR-174). */
    MANAGE_CHORE_MARKS("PERM-053", Role.CLUB_MANAGER),
    /** PERM-046 Manage stable areas: create, rename, set the capacity, delete when empty (BR-163). */
    MANAGE_AREAS("PERM-046", Role.CLUB_MANAGER),
    /** PERM-047 Set a Groom's fixed weekly barn schedule and record leave (BR-166, BR-167). */
    MANAGE_SCHEDULES("PERM-047", Role.CLUB_MANAGER),
    /** PERM-048 View Groom barn schedules and leave (a Groom: own only). */
    VIEW_SCHEDULES("PERM-048", Role.HEAD_TRAINER, Role.GROOM, Role.CLUB_MANAGER),
    /** PERM-049 Set supply prices, record receipts, expected receipts and adjustments (BR-171, BR-177). */
    MANAGE_STOCK("PERM-049", Role.CLUB_MANAGER),
    /** PERM-050 View stock quantities and days of cover; the Veterinarian sees medicines only, never prices (BR-182). */
    VIEW_STOCK("PERM-050", Role.VETERINARIAN, Role.CLUB_MANAGER),
    /** PERM-054 Configure the club's leave notice and the stock warning threshold (BR-167, BR-175). */
    CONFIGURE_THRESHOLDS("PERM-054", Role.CLUB_MANAGER),
    /** PERM-051 Set service prices and close a billing month (BR-178, BR-180). */
    SET_PRICES_AND_CLOSE_MONTH("PERM-051", Role.CLUB_MANAGER),
    /** PERM-052 View cost statements and invoices (Owner: own horses only; BR-182). */
    VIEW_BILLING("PERM-052", Role.HORSE_OWNER, Role.CLUB_MANAGER);

    private final String baId;
    private final Set<Role> allowed;

    Permission(String baId, Role first, Role... rest) {
        this.baId = baId;
        this.allowed = EnumSet.of(first, rest);
    }

    public String baId() {
        return baId;
    }

    public Set<Role> allowedRoles() {
        return allowed;
    }

    public boolean allows(Role role) {
        return allowed.contains(role);
    }
}

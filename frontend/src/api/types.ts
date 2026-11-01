export type Role = 'HEAD_TRAINER' | 'VETERINARIAN' | 'GROOM' | 'HORSE_OWNER' | 'CLUB_MANAGER';
export const ROLES: Role[] = ['HEAD_TRAINER', 'VETERINARIAN', 'GROOM', 'HORSE_OWNER', 'CLUB_MANAGER'];

/** Mirrors the service's Permission enum (ba/09-permission-matrix.md). */
export type Permission =
  | 'VIEW_HORSES'
  | 'MANAGE_HORSES'
  | 'ARCHIVE_HORSES'
  | 'MANAGE_PLANS'
  | 'SCHEDULE_SESSIONS'
  | 'RUN_SESSIONS'
  | 'VIEW_PROGRESS_BOARD'
  | 'VIEW_PLANS'
  | 'REVIEW_BLOCKED_SESSIONS'
  | 'REQUEST_PENDING_LOCK'
  | 'CONFIGURE_GRACE_PERIOD'
  | 'VIEW_HORSE_OPTIONS'
  | 'RECORD_EXAMINATIONS'
  | 'VIEW_EXAMINATIONS'
  | 'MANAGE_CARE'
  | 'SEND_INCIDENTS'
  | 'HANDLE_INCIDENTS'
  | 'SET_THRESHOLDS'
  | 'VIEW_LIVE'
  | 'RECEIVE_ALERTS'
  | 'REQUEST_LOCK_REVIEW'
  | 'CHANGE_HEALTH_STATUS'
  | 'MANAGE_LOCKS'
  | 'VIEW_HEALTH'
  | 'VIEW_READINESS'
  | 'MANAGE_ACCOUNTS'
  | 'VIEW_AUDIT_LOG'
  | 'MANAGE_INJURY_MARKS'
  | 'VIEW_INJURY_MARKS'
  | 'MANAGE_TRIAL_VIDEOS'
  | 'WATCH_TRIAL_VIDEOS'
  | 'VIEW_ASSIGNED_SESSIONS'
  | 'VIEW_OWN_TIMETABLE'
  | 'EXPORT_BILLING'
  | 'ACKNOWLEDGE_ASSIGNMENT'
  | 'CONFIGURE_LATE_CHANGE_THRESHOLD'
  | 'CONFIGURE_THRESHOLDS'
  | 'MANAGE_AREAS'
  | 'MANAGE_SCHEDULES'
  | 'VIEW_SCHEDULES'
  | 'MARK_CHORES'
  | 'VIEW_CHORES'
  | 'MANAGE_STALLS'
  | 'VIEW_BARN'
  | 'MANAGE_SUPPLIES'
  | 'VIEW_SUPPLIES'
  | 'PROPOSE_RESTOCK'
  | 'DECIDE_RESTOCK'
  | 'COMPOSE_RATION'
  | 'VIEW_RATION'
  | 'ASSIGN_TRAINER'
  | 'MANAGE_CHORE_MARKS'
  | 'MANAGE_STOCK'
  | 'VIEW_STOCK'
  | 'SET_PRICES_AND_CLOSE_MONTH'
  | 'VIEW_BILLING';

export type RationItemType = 'GRAIN' | 'HAY' | 'VITAMIN';
export const RATION_ITEM_TYPES: RationItemType[] = ['GRAIN', 'HAY', 'VITAMIN'];

export interface RationItem {
  type: RationItemType;
  supplyItemId: number;
  supplyItemName: string;
  unit: string;
  active: boolean;
  quantity: number;
}

export interface RationMeal {
  name: string;
  time: string;
  items: RationItem[];
}

export interface RationVersion {
  version: number;
  effectiveDate: string;
  savedByName: string;
  savedAt: string;
  meals: RationMeal[];
}

export interface Ration {
  horseId: number;
  horseName: string;
  archived: boolean;
  current: RationVersion | null;
  /** Null for a Horse Owner (BR-142). */
  history: RationVersion[] | null;
}

export interface RationOption {
  id: number;
  name: string;
  unit: string;
}

export interface RationTodayEntry {
  horseId: number;
  meals: RationMeal[];
}

/** What the Veterinarian's editor sends (quantities are text until saved; the service parses them). */
export interface RationMealInput {
  name: string;
  time: string;
  items: { type: RationItemType | ''; supplyItemId: number | null; quantity: string }[];
}

export type HealthStatus = 'ELIGIBLE' | 'UNDER_OBSERVATION' | 'INJURED' | 'QUARANTINED';
export const HEALTH_STATUSES: HealthStatus[] = ['ELIGIBLE', 'UNDER_OBSERVATION', 'INJURED', 'QUARANTINED'];
export type Readiness = 'READY' | 'UNDER_OBSERVATION' | 'NOT_READY';
export const READINESS_VALUES: Readiness[] = ['READY', 'UNDER_OBSERVATION', 'NOT_READY'];
export type LockKind = 'MANUAL' | 'AUTOMATIC' | 'INTAKE';
export type ReturnLevel = 'RESUME_AS_IS' | 'RESUME_WITH_ADJUSTMENT';
export type Activity = 'IDLE' | 'TRAINING';

export interface TrainingLock {
  id: number;
  kind: LockKind;
  reason: string;
  placedBy: string;
  placedAt: string;
  reviewDate: string | null;
  reviewNotes: { author: string; note: string; createdAt: string }[];
}

export interface HorseHealth {
  horseId: number;
  horseName: string;
  registrationNumber: string;
  status: HealthStatus;
  lock: TrainingLock | null;
  pendingLock?: { reason: string; requestedBy: string; requestedAt: string; sessionId: number | null } | null;
  activity: Activity;
  readiness: Readiness;
  newUnexamined: boolean;
  history: { from: HealthStatus | null; to: HealthStatus; reason: string; changedBy: string; changedAt: string }[];
}

/** One page of a list plus the number of rows that match (BR-185). */
export interface Page<T> {
  items: T[];
  page: number;
  size: number;
  total: number;
}

/** Paging, search and filters of a list; unset values are not sent. */
export interface ListQuery {
  page?: number;
  size?: number;
  q?: string;
  [filter: string]: string | number | boolean | undefined;
}

/** A choice of a search-box select (TECH-178). */
export interface OptionItem {
  id: number;
  label: string;
  hint: string | null;
}

export interface Inbox extends Page<{ id: number; message: string; link: string | null; createdAt: string; read: boolean }> {
  unread: number;
}

export interface Me {
  id: number;
  username: string;
  displayName: string;
  role: Role;
  permissions: Permission[];
}

export interface Account {
  id: number;
  username: string;
  displayName: string;
  role: Role;
  active: boolean;
  createdAt: string;
  /** Only in the list and only for a Groom: false flags "Chưa có lịch làm" (BR-162). */
  hasSchedule?: boolean;
}

export interface AuditEntry {
  id: number;
  actorId: number | null;
  actorName: string;
  occurredAt: string;
  occurredAtClub: string;
  action: string;
  targetType: string;
  targetId: number | null;
  details: string;
}

export type Sex = 'MALE' | 'FEMALE';
export type ArchiveReason = 'SOLD' | 'RETIRED' | 'DECEASED' | 'OTHER';
export const ARCHIVE_REASONS: ArchiveReason[] = ['SOLD', 'RETIRED', 'DECEASED', 'OTHER'];

export interface Horse {
  id: number;
  name: string;
  registrationNumber: string;
  sex: Sex;
  birthDate: string;
  coatColour: string | null;
  breed: string | null;
  owner: { id: number; displayName: string };
  archived: boolean;
  archiveReason: ArchiveReason | null;
  archiveNote: string | null;
  archivedAt: string | null;
  /** Changes with every new photo and is part of the photo URL; null while the horse has none (BR-184). */
  photoVersion: number | null;
  /** The Head Trainer who writes now (the temporary one while a loan lasts), the main one and the loan (BR-152, BR-156). */
  assignedTrainer: TrainerRef | null;
  mainTrainer: TrainerRef | null;
  temporaryTrainer: { id: number; displayName: string; until: string } | null;
  /** True when the signed-in user is the assigned trainer: the client shows write controls from this (BR-153). */
  canWrite: boolean;
  /** Health status for a role that may read health (PERM-020), else null (BR-209). */
  healthStatus?: HealthStatus | null;
}

export interface TrainerRef {
  id: number;
  displayName: string;
  active: boolean;
}

export interface HorseInput {
  name: string;
  sex: Sex | '';
  birthDate: string;
  coatColour: string;
  breed: string;
  ownerId: number | '';
  trainerId: number | '';
}

export type PedigreePosition = 'SIRE' | 'DAM' | 'SIRE_SIRE' | 'SIRE_DAM' | 'DAM_SIRE' | 'DAM_DAM';
export const PEDIGREE_POSITIONS: PedigreePosition[] = ['SIRE', 'DAM', 'SIRE_SIRE', 'SIRE_DAM', 'DAM_SIRE', 'DAM_DAM'];

export interface Ancestor {
  position: PedigreePosition;
  horse: { id: number; name: string } | null;
  name: string | null;
}

export interface AncestorInput {
  position: PedigreePosition;
  horseId?: number;
  name?: string;
}

export type PlanState = 'DRAFT' | 'ACTIVE' | 'COMPLETED' | 'CANCELLED';
export type SessionState =
  | 'DRAFT'
  | 'SCHEDULED'
  | 'IN_PROGRESS'
  | 'COMPLETED'
  | 'AUTO_CLOSED'
  | 'MISSED'
  | 'BLOCKED_BY_LOCK'
  | 'AWAITING_REVIEW'
  | 'MISSED_DUE_TO_LOCK'
  | 'CANCELLED';
export type SessionType = 'TROT' | 'GALLOP' | 'TRIAL_RUN' | 'OTHER';
export const SESSION_TYPES: SessionType[] = ['TROT', 'GALLOP', 'TRIAL_RUN', 'OTHER'];
export type Intensity = 'LIGHT' | 'MEDIUM' | 'HEAVY';
export type DataSource = 'MANUAL' | 'SIMULATED' | 'DEVICE';

export interface Phase {
  id?: number;
  ordinal?: number;
  name: string;
  startDate: string;
  endDate: string;
  goal: string;
  distanceM: number | '';
  weeklyVolumeKm: number | '';
  surface: string;
}

/** Chưa xác nhận / Đã nhận; null when the responsible person is not a Groom (BR-118, Q-046). */
export type AssignmentAck = 'UNCONFIRMED' | 'ACKNOWLEDGED';

/** What a Groom sees of a session assigned to them (BR-114), plus what to confirm (BR-118): nothing more. */
export interface AssignedSession {
  id: number;
  horseId: number;
  horseName: string;
  date: string;
  /** "HH:mm:ss"; null for a session created before start times existed (BR-120). */
  startTime: string | null;
  type: SessionType;
  plannedDurationMin: number;
  state: SessionState;
  assignmentAck: AssignmentAck | null;
  /** Club-local "HH:mm"; null without a start time (BR-190). */
  plannedEnd: string | null;
  plannedEndNextDay: boolean;
  endedAt: string | null;
}

export interface Plan {
  id: number;
  horseId: number;
  title: string | null;
  state: PlanState;
  cancelReason: string | null;
  completionSuggested: boolean;
  phases: Phase[];
  assignedTrainer: { id: number; displayName: string } | null;
  canWrite: boolean;
}

export interface Session {
  id: number;
  planId: number;
  horseId: number;
  date: string;
  startTime: string | null;
  type: SessionType;
  plannedDurationMin: number;
  targetDistanceM: number | null;
  targetSurface: string | null;
  responsiblePerson: { id: number; displayName: string } | null;
  /** Upcoming, not a draft and nobody responsible: the flag "Cần người phụ trách" (BR-159). */
  needsResponsible: boolean;
  declinedBy: { id: number; displayName: string } | null;
  declineReason: string | null;
  declinedAt: string | null;
  assignedTrainer: { id: number; displayName: string } | null;
  canWrite: boolean;
  state: SessionState;
  startedAt: string | null;
  endedAt: string | null;
  plannedEnd: string | null;
  plannedEndNextDay: boolean;
  endTimeAutomatic: boolean;
  overtime: boolean;
  lockDuringSession: boolean;
  missingIndicators: boolean;
  earlyEndReason: string | null;
  cancelReason: string | null;
  distanceM: number | null;
  durationMin: number | null;
  surface: string | null;
  intensity: Intensity | null;
  form: number | null;
  avgHeartRate: number | null;
  avgHeartRateSource: DataSource | null;
  maxSpeed: number | null;
  maxSpeedSource: DataSource | null;
  weightKg: number | null;
  comment: string | null;
  assignmentAck: AssignmentAck | null;
}

export interface SessionInput {
  date: string;
  /** "HH:mm", mandatory for a new session (BR-120). */
  startTime: string;
  type: SessionType | '';
  plannedDurationMin: number | '';
  targetDistanceM: number | '';
  targetSurface: string;
  responsiblePersonId: number | '';
  draft: boolean;
}

export interface Indicators {
  distanceM: number | '';
  durationMin: number | '';
  surface: string;
  intensity: Intensity | '';
  form: number | '';
  avgHeartRate: number | '';
  maxSpeed: number | '';
  weightKg: number | '';
  comment: string;
  earlyEndReason?: string;
  actualEndTime?: string;
}

export interface ScheduleItem {
  id: number;
  date: string;
  startTime: string | null;
  type: SessionType;
  state: SessionState;
  form: number | null;
  comment: string | null;
  videoCount: number;
  plannedEnd: string | null;
  plannedEndNextDay: boolean;
  endedAt: string | null;
}

export interface BoardRow {
  horseId: number;
  horseName: string;
  status: HealthStatus;
  readiness: Readiness;
  locked: boolean;
  newUnexamined: boolean;
  currentPlan: Plan | null;
  recentSessions: Session[];
  trend: Session[];
  assignedTrainer: { id: number; displayName: string } | null;
  canWrite: boolean;
}

export interface ShiftPreview {
  sessionId: number;
  from: string;
  to: string;
}

export interface PlanVersion {
  versionNo: number;
  reason: string;
  createdBy: string;
  createdAt: string;
}

export interface Examination {
  id: number;
  horseId: number;
  examDate: string;
  diagnosis: string;
  /** Code from the one body-region catalog (BR-110). */
  bodyRegion: string | null;
  bodyDetail: string | null;
  /** Free text of an examination recorded before the catalog, shown unchanged (AC-171). */
  bodyAreaLegacy: string | null;
  weightKg: number | null;
  treatment: string | null;
  prescription: string | null;
  recordedBy: string;
}

export interface ExaminationInput {
  diagnosis: string;
  bodyRegion: string;
  bodyDetail: string;
  weightKg: number | '';
  treatment: string;
  prescription: string;
  /** The fee type chosen from the price list; the Veterinarian never sees an amount (BR-178, BR-182). */
  feeTypeId?: number | '';
}

export interface WeightPoint {
  date: string;
  weightKg: number;
}

export type CareType = 'VACCINATION' | 'DEWORMING' | 'HOOF_CHECK';
export const CARE_TYPES: CareType[] = ['VACCINATION', 'DEWORMING', 'HOOF_CHECK'];
export type DueState = 'OK' | 'DUE_SOON' | 'OVERDUE';

export interface CareItem {
  id: number;
  horseId: number;
  horseName: string;
  type: CareType;
  name: string;
  intervalDays: number;
  nextDue: string;
  state: DueState;
  /** The latest completion; notes and batch stay in the history (BR-187). */
  lastCompletion: { dateDone: string; product: string | null; performer: string | null } | null;
  hasDetails: boolean;
  /** The medicine and dose planned for the next completion; the stock forecast counts it (BR-175). */
  plannedSupplyItemId: number | null;
  plannedSupplyItemName: string | null;
  plannedQuantity: number | null;
}

export interface CareEvent {
  kind: 'COMPLETION' | 'OVERRIDE' | 'POSTPONEMENT';
  eventDate: string;
  performer: string | null;
  product: string | null;
  batch: string | null;
  note: string | null;
  previousDue: string;
  newDue: string;
  by: string;
  /** The medicine taken from stock and the dose; never a price (BR-182). */
  supplyItemId: number | null;
  quantity: number | null;
}

export type IncidentState = 'NEW' | 'ACKNOWLEDGED' | 'HANDLED';

export interface Incident {
  id: number;
  horseId: number;
  horseName: string;
  groom: string;
  description: string;
  bodyRegion: string | null;
  state: IncidentState;
  createdAt: string;
  closingNote: string | null;
  linkedExaminationId: number | null;
  photoIds: number[];
}

export interface HorseOption {
  id: number;
  name: string;
  registrationNumber: string;
}

export type Measure = 'HEART_RATE' | 'SPEED';

export interface Sample {
  offsetSecond: number;
  heartRate: number;
  speed: number;
}

export interface TelemetryAlert {
  sessionId: number;
  measure: Measure;
  limit: number;
  value: number;
  offsetSecond: number;
  raisedAt: string;
  dataSource: DataSource;
  label: string;
}

/** Counts over the whole herd, independent of the page of the health list. */
export interface HealthSummary {
  total: number;
  byStatus: Record<HealthStatus, number>;
  locked: number;
}

export type LiveWindow = 'ONE_MINUTE' | 'FIVE_MINUTES' | 'SESSION';

export interface LiveFigures {
  current: number;
  min: number;
  max: number;
  avg: number;
}

export interface LiveView {
  active: boolean;
  sessionId: number | null;
  dataSource: DataSource | null;
  label: string | null;
  elapsedSeconds: number | null;
  current: Sample | null;
  recent: Sample[];
  alerts: TelemetryAlert[];
  /** The alert limits, drawn as lines on the chart (BR-189). */
  thresholds: { measure: Measure; limit: number }[];
  summary: { heartRate: LiveFigures; speed: LiveFigures; windowSeconds: number } | null;
}

export interface Threshold {
  measure: Measure;
  limit: number;
  setAt: string;
}

export type MarkStatus = 'NEW' | 'RECOVERING' | 'RECOVERED' | 'CANCELLED';
export interface BodyRegion {
  code: string;
  vi: string;
  en: string;
}
export interface MarkEvent {
  from: MarkStatus | null;
  to: MarkStatus;
  date: string;
  note: string | null;
  by: string;
  at: string;
}
export interface InjuryMark {
  id: number;
  horseId: number;
  regionCode: string;
  status: MarkStatus;
  examinationId: number;
  examinationDate: string | null;
  examinationDiagnosis: string | null;
  createdBy: string;
  createdAt: string;
  history: MarkEvent[];
}

/** A trial-run video (REQ-027); the file is played from `contentUrl`. */
export interface TrialVideo {
  id: number;
  sessionId: number;
  horseId: number;
  fileName: string;
  sizeBytes: number;
  uploadedBy: string;
  uploadedAt: string;
  contentUrl: string;
}

/** Stable care (TECH-091 to TECH-095). */
export type ChoreCode = 'FEEDING' | 'STALL_CLEANING' | 'WASHING' | 'ICE_SOAK';
export const CHORES: ChoreCode[] = ['FEEDING', 'STALL_CLEANING', 'WASHING', 'ICE_SOAK'];

/** A stall: an area of the catalog and a slot from 1 to its capacity (BR-164). */
export interface Stall {
  areaId: number;
  area: string;
  slot: number;
}

export interface Area {
  id: number;
  name: string;
  capacity: number;
  occupied: number;
}

/** One chore of one horse on one day; the Owner's copy carries no entry id, Groom name or time. */
export interface ChoreState {
  chore: ChoreCode;
  done: boolean;
  entryId: number | null;
  groomName: string | null;
  markedAt: string | null;
  /** Who made the mark; null for the Horse Owner, who sees done or not done only (BR-124, BR-170). */
  groomId: number | null;
  /** Marked outside the Groom's barn hours or on leave: "làm thay"; absent for the Horse Owner (BR-169). */
  onBehalf?: boolean;
  /** Filled in by the Club Manager for a past day, with a reason (BR-174). */
  backfilled?: boolean;
}

export interface ChoreHistoryEntry {
  entryId: number;
  date: string;
  chore: ChoreCode;
  groomName: string;
  markedAt: string;
  cancelledAt: string | null;
  cancelReason: string | null;
  cancelledByName: string | null;
  groomId: number | null;
  onBehalf?: boolean;
  backfilled?: boolean;
  backfillReason?: string | null;
}

export interface ChoreHorse {
  horseId: number;
  horseName: string;
  registrationNumber: string;
  stall: Stall | null;
  chores: ChoreState[];
}

export interface ChoreDay {
  date: string;
  /** The club's current date; a day is editable only when `date === today`. */
  today: string;
  horses: ChoreHorse[];
  page: number;
  size: number;
  total: number;
}

/** One day of the chore history: horses listed, chores they had and how many were done (BR-188, BR-209). */
export interface ChoreDaySummary {
  date: string;
  horses: number;
  chores: number;
  done: number;
}

export interface ChoreRangeHorse {
  horseId: number;
  horseName: string;
  registrationNumber: string;
  days: { date: string; chores: ChoreState[] }[];
}

export interface BarnHorse {
  horseId: number;
  horseName: string;
  registrationNumber: string;
  choresDone: number;
  choresTotal: number;
  /** The old free-text stall that did not fit the catalog, until the Club Manager places the horse (BR-129). */
  legacyStall: string | null;
}

/** One stall of an area; `horse` is null for an empty placeholder (BR-165). */
export interface BarnSlot {
  slot: number;
  horse: BarnHorse | null;
}

export interface BarnArea {
  id: number;
  name: string;
  capacity: number;
  slots: BarnSlot[];
}

export interface Barn {
  areas: BarnArea[];
  unplaced: BarnHorse[];
  canPlace: boolean;
}

/** One range of a Groom's fixed weekly barn hours: ISO weekday (Monday = 1) and "HH:mm" (BR-166). */
export interface ScheduleRange {
  weekday: number;
  start: string;
  end: string;
}

export interface Leave {
  id: number;
  date: string;
  /** Recorded less than the leave notice ahead: "đột xuất", a reminder only (BR-167). */
  late: boolean;
  noticeDays: number;
  reason: string | null;
  recordedBy: string;
  recordedAt: string;
  cancelled: boolean;
}

export type SupplyCategory = 'FEED' | 'MEDICINE' | 'TOOLS';
export const SUPPLY_CATEGORIES: SupplyCategory[] = ['FEED', 'MEDICINE', 'TOOLS'];

/** Hết hàng and Cần nhập; the club has one threshold, so there is no other state (BR-175). */
export type StockState = 'OK' | 'RESTOCK' | 'OUT';

/**
 * What the viewer may see: the Club Manager has the stock, the price and the forecast, a Groom the catalog only
 * (AC-231), so the other fields are absent for a Groom.
 */
export interface SupplyItem {
  id: number;
  name: string;
  category: SupplyCategory;
  unit: string;
  unitId: number;
  active: boolean;
  quantity?: number;
  unitPrice?: number;
  /** Whole days the stock lasts at the current rations; absent for an item no ration uses. */
  daysOfCover?: number;
  state?: StockState;
  dailyUse?: number;
  expectedQuantity?: number;
}

/** A unit of the catalog supplies are counted in (BR-194); renamed or deactivated, never deleted. */
export interface SupplyUnit {
  id: number;
  name: string;
  active: boolean;
  itemCount: number;
}

export interface SupplyInput {
  name: string;
  category: SupplyCategory | '';
  unitId: number | '';
  /** Required when adding; the price is changed by its own action afterwards. */
  price?: number | '';
  /** Only when adding: the opening stock becomes the first ledger line. */
  openingQuantity?: number | '';
  active?: boolean;
}

export type MovementKind = 'RECEIPT' | 'USE_CARE' | 'USE_FEED' | 'REVERSAL' | 'ADJUSTMENT';

export interface StockMovement {
  id: number;
  kind: MovementKind;
  delta: number;
  balanceAfter: number;
  unitPrice: number;
  occurredOn: string;
  refType: string | null;
  refId: number | null;
  reason: string | null;
  actorName: string | null;
  createdAt: string;
}

export interface ExpectedReceipt {
  id: number;
  expectedDate: string;
  quantity: number;
  state: 'EXPECTED' | 'RECEIVED' | 'CANCELLED';
}

/** A medicine for the Veterinarian's picker: quantity and state, never a price (BR-182). */
export interface Medicine {
  id: number;
  name: string;
  unit: string;
  quantity: number;
  state: StockState;
}

export type ProposalState = 'PENDING' | 'APPROVED' | 'REJECTED';

export interface RestockProposal {
  id: number;
  itemId: number;
  itemName: string;
  unit: string;
  groomName: string;
  quantity: number;
  note: string | null;
  state: ProposalState;
  createdAt: string;
  decidedByName: string | null;
  decidedAt: string | null;
  decisionReason: string | null;
}

/** The Grooms the assigned trainer may name for a time range and why each other one cannot be (BR-162). */
export type UnavailableReason = 'BARN_HOURS' | 'LEAVE' | 'OVERLAP';

export interface AssignableGrooms {
  available: { id: number; displayName: string }[];
  unavailable: { id: number; displayName: string; reason: UnavailableReason; detail: string; sessionId: number | null }[];
  /** Earliest start (HH:mm, same day) at which someone is free, only when nobody is. */
  nextFree: string | null;
}

// ---- owner billing (BR-178 to BR-183)

export type ChargeCategory = 'MEDICINE' | 'FEED' | 'DAILY_FEE' | 'EXAM' | 'ADJUSTMENT';
export const CHARGE_CATEGORIES: ChargeCategory[] = ['MEDICINE', 'FEED', 'DAILY_FEE', 'EXAM', 'ADJUSTMENT'];

export interface FeeTypeOption {
  id: number;
  name: string;
}

export interface PriceRow {
  id: number;
  amount: number;
  effectiveFrom: string;
}

export interface FeeTypeView {
  id: number;
  name: string;
  active: boolean;
  currentPrice: number | null;
  prices: PriceRow[];
}

export interface PriceList {
  dailyFee: { currentPrice: number | null; prices: PriceRow[] };
  feeTypes: FeeTypeView[];
}

export interface StatementLine {
  date: string;
  description: string;
  quantity: number;
  unitPrice: number;
  amount: number;
  reason: string | null;
}

export interface StatementCategory {
  category: ChargeCategory;
  total: number;
  lines: StatementLine[];
}

export interface StatementHorse {
  horseId: number | null;
  horseName: string;
  total: number;
  categories: StatementCategory[];
}

export interface Statement {
  month: string;
  closed: boolean;
  ownerId: number | null;
  ownerName: string | null;
  total: number;
  horses: StatementHorse[];
  /** The total of each category across horses, computed by the service (BR-203). */
  categories: { category: string; total: number }[];
  invoiceId: number | null;
  invoiceNumber: string | null;
  /** When the month was closed; null while it is open. */
  closedAt: string | null;
  /** The daily fee is created after each day, so a running statement shows it up to yesterday. */
  dailyFeeThrough: string;
}

/** What the Club Manager's billing overview draws; every figure is summed by the service (BR-180, BR-209). */
export interface BillingOverview {
  scope: string;
  total: number;
  owners: number;
  months: { month: string; total: number }[];
  categories: { category: string; total: number }[];
  topOwners: { ownerId: number; ownerName: string; total: number }[];
}

export interface StatementOwnerRow {
  ownerId: number;
  ownerName: string;
  total: number;
  closed: boolean;
  invoiceId: number | null;
}

export interface InvoiceRow {
  id: number;
  ownerId: number;
  ownerName: string;
  month: string;
  number: string;
  total: number;
  closedAt: string;
}

export interface AdjustmentInput {
  ownerId: number | '';
  horseId: number | '';
  date: string;
  amount: number | '';
  reason: string;
}

export type ClosingState = 'READY' | 'CLOSED' | 'NOT_FINISHED' | 'FEES_PENDING' | 'PREVIOUS_MONTH_OPEN';

/** Whether a month can be closed now and the reason when it cannot (BR-205). */
export interface ClosingStatus {
  month: string;
  state: ClosingState;
  openMonth: string | null;
  message: string | null;
}

export interface TimetableItem {
  id: number;
  horseId: number;
  horseName: string;
  startTime: string | null;
  plannedEnd: string | null;
  endsNextDay: boolean;
  type: string;
  state: string;
  /** Only in a Head Trainer's timetable. */
  groomName: string | null;
}

export interface TimetableDay {
  date: string;
  barnHours: { weekday: number; start: string; end: string }[];
  leave: { late: boolean; reason: string | null } | null;
  items: TimetableItem[];
}

export interface Timetable {
  from: string;
  to: string;
  days: TimetableDay[];
}

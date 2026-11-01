import { download, request } from './client';
import type {
  Account,
  AssignableGrooms,
  AssignedSession,
  Ration,
  RationMealInput,
  RationOption,
  RationTodayEntry,
  AncestorInput,
  Ancestor,
  ArchiveReason,
  AuditEntry,
  HealthStatus,
  Horse,
  HorseHealth,
  ClosingStatus,
  HorseInput,
  Inbox,
  Indicators,
  Me,
  Phase,
  Plan,
  PlanVersion,
  Readiness,
  ReturnLevel,
  Role,
  ScheduleItem,
  Session,
  SessionInput,
  ShiftPreview,
  BoardRow,
  CareEvent,
  CareItem,
  Examination,
  ExaminationInput,
  HorseOption,
  Incident,
  BodyRegion,
  InjuryMark,
  MarkStatus,
  WeightPoint,
  LiveView,
  Measure,
  TelemetryAlert,
  Threshold,
  TrialVideo,
  Area,
  Barn,
  Leave,
  ScheduleRange,
  ChoreCode,
  ChoreDay,
  HealthSummary,
  ChoreDaySummary,
  ChoreRangeHorse,
  CareType,
  DueState,
  ListQuery,
  LiveWindow,
  OptionItem,
  Page,
  PlanState,
  SessionState,
  ChoreHistoryEntry,
  ChoreState,
  RestockProposal,
  Stall,
  SupplyInput,
  SupplyItem,
  SupplyUnit,
  Timetable,
  StockMovement,
  ExpectedReceipt,
  Medicine,
  AdjustmentInput,
  FeeTypeOption,
  InvoiceRow,
  PriceList,
  Statement,
  BillingOverview,
  StatementOwnerRow,
} from './types';

/** `?a=1&b=2` from the set values of a list query (empty strings and undefined are left out). */
export function listQs(query: ListQuery = {}): string {
  const params = new URLSearchParams();
  for (const [key, value] of Object.entries(query)) {
    if (value !== undefined && value !== '') params.set(key, String(value));
  }
  const text = params.toString();
  return text ? `?${text}` : '';
}

/** The URL of a horse's photo; `v` is the version of the photo, so a replaced photo is never served from cache (BR-184). */
export function horsePhotoUrl(horseId: number, version: number, variant: 'thumb' | 'full' = 'full'): string {
  return `/api/horses/${horseId}/photo?variant=${variant}&v=${version}`;
}

/** Kinds of choices the service offers to the search-box selects. */
export type OptionKind = 'horses' | 'owners' | 'trainers' | 'grooms' | 'staff' | 'accounts' | 'feed';

export const api = {
  me: () => request<Me>('GET', '/api/auth/me'),
  login: (username: string, password: string) => request<Me>('POST', '/api/auth/login', { username, password }),
  logout: () => request<void>('POST', '/api/auth/logout'),

  accounts: (query: ListQuery & { role?: Role; active?: boolean } = {}) =>
    request<Page<Account>>('GET', `/api/accounts${listQs(query)}`),
  createAccount: (input: { username: string; displayName: string; password: string; roles: Role[]; schedule?: ScheduleRange[] }) =>
    request<Account>('POST', '/api/accounts', input),
  changeRole: (id: number, roles: Role[]) => request<Account>('PUT', `/api/accounts/${id}/roles`, { roles }),
  deactivate: (id: number) => request<Account>('POST', `/api/accounts/${id}/deactivate`),

  audit: (query: ListQuery & { from?: string; to?: string; actorId?: number; action?: string } = {}) =>
    request<Page<AuditEntry>>('GET', `/api/audit${listQs(query)}`),

  /** Choices for a search-box select: horses, owners, grooms, staff, accounts or feed (TECH-178). */
  options: (kind: OptionKind, query: ListQuery & { role?: Role } = {}) =>
    request<Page<OptionItem>>('GET', `/api/options/${kind}${listQs({ size: 20, ...query })}`),

  horses: (query: ListQuery & { ownerId?: number; area?: string; archived?: boolean; trainerId?: number; noTrainer?: boolean; readiness?: Readiness } = {}) =>
    request<Page<Horse>>('GET', `/api/horses${listQs(query)}`),
  uploadHorsePhoto: (id: number, file: File) => {
    const form = new FormData();
    form.append('file', file);
    return request<Horse>('PUT', `/api/horses/${id}/photo`, form);
  },
  horse: (id: number) => request<Horse>('GET', `/api/horses/${id}`),
  registerHorse: (input: HorseInput) => request<Horse>('POST', '/api/horses', input),
  changeTrainer: (id: number, trainerId: number) => request<Horse>('PUT', `/api/horses/${id}/trainer`, { trainerId }),
  lendTrainer: (id: number, trainerId: number, until: string) =>
    request<Horse>('PUT', `/api/horses/${id}/trainer/temporary`, { trainerId, until }),
  endLoan: (id: number) => request<Horse>('DELETE', `/api/horses/${id}/trainer/temporary`),
  updateHorse: (id: number, input: HorseInput) => request<Horse>('PUT', `/api/horses/${id}`, input),
  pedigree: (id: number) => request<Ancestor[]>('GET', `/api/horses/${id}/pedigree`),
  updatePedigree: (id: number, ancestors: AncestorInput[]) =>
    request<Ancestor[]>('PUT', `/api/horses/${id}/pedigree`, { ancestors }),
  archiveHorse: (id: number, reason: ArchiveReason | null, note: string) =>
    request<Horse>('POST', `/api/horses/${id}/archive`, { reason, note }),
  restoreHorse: (id: number) => request<Horse>('POST', `/api/horses/${id}/restore`),

  healthSummary: () => request<HealthSummary>('GET', '/api/health/summary'),
  herdHealth: (query: ListQuery & { status?: HealthStatus; activity?: string } = {}) =>
    request<Page<HorseHealth>>('GET', `/api/health${listQs(query)}`),
  horseHealth: (id: number) => request<HorseHealth>('GET', `/api/horses/${id}/health`),
  changeStatus: (id: number, status: HealthStatus | null, reason: string) =>
    request<HorseHealth>('POST', `/api/horses/${id}/health/status`, { status, reason }),
  lock: (id: number, reason: string, reviewDate: string | null) =>
    request<HorseHealth>('POST', `/api/horses/${id}/lock`, { reason, reviewDate }),
  lift: (id: number, returnLevel: ReturnLevel | null) =>
    request<HorseHealth>('POST', `/api/horses/${id}/lock/lift`, { returnLevel }),
  requestReview: (id: number, note: string) =>
    request<HorseHealth>('POST', `/api/horses/${id}/lock/review-notes`, { note }),
  readinessList: () => request<{ horseId: number; readiness: Readiness }[]>('GET', '/api/readiness'),
  readiness: (id: number) => request<{ horseId: number; readiness: Readiness }>('GET', `/api/horses/${id}/readiness`),

  notifications: (query: ListQuery & { unread?: boolean } = {}) =>
    request<Inbox>('GET', `/api/notifications${listQs(query)}`),
  markRead: (id: number) => request<unknown>('POST', `/api/notifications/${id}/read`),

  requestPendingLock: (id: number, reason: string) =>
    request<HorseHealth>('POST', `/api/horses/${id}/pending-lock`, { reason }),
  withdrawPendingLock: (id: number) => request<HorseHealth>('POST', `/api/horses/${id}/pending-lock/withdraw`),

  plansOf: (horseId: number, query: ListQuery & { state?: PlanState } = {}) =>
    request<Page<Plan>>('GET', `/api/horses/${horseId}/plans${listQs(query)}`),
  createPlan: (horseId: number, title: string, phases: Phase[]) =>
    request<Plan>('POST', `/api/horses/${horseId}/plans`, { title, phases: phases.map(clean) }),
  plan: (id: number) => request<Plan>('GET', `/api/plans/${id}`),
  updatePlan: (id: number, title: string, phases: Phase[]) =>
    request<Plan>('PUT', `/api/plans/${id}`, { title, phases: phases.map(clean) }),
  activatePlan: (id: number) => request<Plan>('POST', `/api/plans/${id}/activate`),
  completePlan: (id: number) => request<Plan>('POST', `/api/plans/${id}/complete`),
  cancelPlan: (id: number, reason: string) => request<Plan>('POST', `/api/plans/${id}/cancel`, { reason }),
  keepAfterLock: (id: number) => request<Session[]>('POST', `/api/plans/${id}/review/keep`),
  shiftPlan: (id: number, days: number, dryRun: boolean) =>
    request<ShiftPreview[]>('POST', `/api/plans/${id}/shift`, { days, dryRun }),
  planVersions: (id: number, query: ListQuery = {}) => request<Page<PlanVersion>>('GET', `/api/plans/${id}/versions${listQs(query)}`),
  restoreVersion: (id: number, versionNo: number) =>
    request<PlanVersion>('POST', `/api/plans/${id}/versions/${versionNo}/restore`),

  sessionsOf: (planId: number, query: ListQuery & { state?: SessionState; from?: string; to?: string } = {}) =>
    request<Page<Session>>('GET', `/api/plans/${planId}/sessions${listQs(query)}`),
  createSession: (planId: number, input: SessionInput, acknowledgeWarning = false) =>
    request<Session>('POST', `/api/plans/${planId}/sessions`, { ...clean(input), acknowledgeWarning }),
  session: (id: number) => request<{ session: Session; lockPendingAtEnd: boolean }>('GET', `/api/sessions/${id}`),
  scheduleSession: (id: number) => request<Session>('POST', `/api/sessions/${id}/schedule`),
  cancelSession: (id: number, reason: string) => request<Session>('POST', `/api/sessions/${id}/cancel`, { reason }),
  startSession: (id: number, acknowledgeWarning: boolean) =>
    request<Session>('POST', `/api/sessions/${id}/start`, { acknowledgeWarning }),
  endSession: (id: number, indicators: Indicators) =>
    request<Session>('POST', `/api/sessions/${id}/end`, clean(indicators)),
  completeIndicators: (id: number, indicators: Indicators) =>
    request<Session>('PUT', `/api/sessions/${id}/indicators`, clean(indicators)),
  staff: () => request<{ id: number; displayName: string }[]>('GET', '/api/staff'),
  /** Grooms free for a time range, with the reason for each one who is not (BR-162, TECH-136). */
  assignableGrooms: (q: { horseId: number; date: string; startTime: string; durationMin: number; sessionId?: number }) =>
    request<AssignableGrooms>('GET', `/api/sessions/assignable-grooms${listQs({ ...q })}`),
  declineSession: (id: number, reason: string) => request<void>('POST', `/api/sessions/${id}/decline`, { reason }),
  assignSession: (id: number, responsiblePersonId: number, acknowledgeWarning = false) =>
    request<Session>('PUT', `/api/sessions/${id}/responsible`, { responsiblePersonId, acknowledgeWarning }),
  mySessions: (query: ListQuery & { state?: SessionState; from?: string; to?: string } = {}) =>
    request<Page<AssignedSession>>('GET', `/api/my-sessions${listQs(query)}`),
  acknowledgeSession: (id: number) => request<AssignedSession>('POST', `/api/sessions/${id}/acknowledge`),

  progress: (query: ListQuery & { status?: HealthStatus; trainerId?: number } = {}) =>
    request<Page<BoardRow>>('GET', `/api/progress${listQs(query)}`),
  schedule: (horseId: number, query: ListQuery & { state?: SessionState; from?: string; to?: string } = {}) =>
    request<Page<ScheduleItem>>('GET', `/api/horses/${horseId}/schedule${listQs(query)}`),
  gracePeriod: () => request<{ minutes: number }>('GET', '/api/settings/grace-period'),
  setGracePeriod: (minutes: number) => request<{ minutes: number }>('PUT', '/api/settings/grace-period', { minutes }),
  lateChangeThreshold: () => request<{ hours: number }>('GET', '/api/settings/late-change-threshold'),
  setLateChangeThreshold: (hours: number) =>
    request<{ hours: number }>('PUT', '/api/settings/late-change-threshold', { hours }),

  examinations: (horseId: number, page = 0, size = 20) =>
    request<Page<Examination>>('GET', `/api/horses/${horseId}/examinations?page=${page}&size=${size}`),
  recordExamination: (horseId: number, input: ExaminationInput) =>
    request<Examination>('POST', `/api/horses/${horseId}/examinations`, clean(input)),
  weights: (horseId: number) => request<WeightPoint[]>('GET', `/api/horses/${horseId}/weights`),

  bodyRegions: () => request<BodyRegion[]>('GET', '/api/body-regions'),
  injuryMarks: (horseId: number, query: ListQuery & { cancelled?: boolean } = {}) =>
    request<Page<InjuryMark>>('GET', `/api/horses/${horseId}/injury-marks${listQs(query)}`),
  createInjuryMark: (horseId: number, regionCode: string, examinationId: number | null) =>
    request<InjuryMark>('POST', `/api/horses/${horseId}/injury-marks`, { regionCode, examinationId }),
  changeMarkStatus: (markId: number, status: MarkStatus, note: string) =>
    request<InjuryMark>('POST', `/api/injury-marks/${markId}/status`, { status, note: note || null }),
  cancelMark: (markId: number, reason: string) =>
    request<InjuryMark>('POST', `/api/injury-marks/${markId}/cancel`, { reason }),

  careDue: (query: ListQuery & { type?: CareType; state?: DueState } = {}) =>
    request<Page<CareItem>>('GET', `/api/care/due${listQs(query)}`),
  careOf: (horseId: number, query: ListQuery & { type?: CareType; state?: DueState } = {}) =>
    request<Page<CareItem>>('GET', `/api/horses/${horseId}/care${listQs(query)}`),
  createCare: (
    horseId: number,
    input: {
      type: string;
      name: string;
      intervalDays: number | '';
      firstDue: string;
      plannedSupplyItemId?: number | '';
      plannedQuantity?: number | '';
    },
  ) => request<CareItem>('POST', `/api/horses/${horseId}/care`, clean(input)),
  /** A vaccination or deworming names the medicine taken from stock; a hoof check has none (BR-172). */
  completeCare: (
    id: number,
    input: {
      dateDone: string;
      performer: string;
      product?: string;
      batch: string;
      note: string;
      supplyItemId?: number | '';
      quantity?: number | '';
    },
  ) => request<CareItem>('POST', `/api/care/${id}/complete`, clean(input)),
  setCarePlan: (id: number, supplyItemId: number, quantity: number) =>
    request<CareItem>('PUT', `/api/care-items/${id}/plan`, { supplyItemId, quantity }),
  clearCarePlan: (id: number) => request<CareItem>('DELETE', `/api/care-items/${id}/plan`),
  medicines: (all = false) => request<Medicine[]>('GET', `/api/stock/medicines${all ? '?all=true' : ''}`),
  overrideCare: (id: number, newDue: string, reason: string) =>
    request<CareItem>('POST', `/api/care/${id}/override`, clean({ newDue, reason })),
  postponeCare: (id: number, newDue: string, reason: string) =>
    request<CareItem>('POST', `/api/care/${id}/postpone`, clean({ newDue, reason })),
  careHistory: (id: number, query: ListQuery = {}) => request<Page<CareEvent>>('GET', `/api/care/${id}/history${listQs(query)}`),

  horseOptions: () => request<HorseOption[]>('GET', '/api/horse-options'),
  incidents: (query: ListQuery & { state?: string; horseId?: number } = {}) =>
    request<Page<Incident>>('GET', `/api/incidents${listQs(query)}`),
  openIncident: (id: number) => request<Incident>('GET', `/api/incidents/${id}`),
  sendIncident: (horseId: number | '', description: string, photos: File[], bodyRegion = '') => {
    const form = new FormData();
    if (horseId !== '') form.append('horseId', String(horseId));
    form.append('description', description);
    if (bodyRegion !== '') form.append('bodyRegion', bodyRegion);
    photos.forEach((p) => form.append('photos', p));
    return request<Incident>('POST', '/api/incidents', form);
  },
  closeIncident: (id: number, note: string, examinationId: number | null) =>
    request<Incident>('POST', `/api/incidents/${id}/close`, { note, examinationId }),

  live: (horseId: number, window: LiveWindow = 'ONE_MINUTE') =>
    request<LiveView>('GET', `/api/horses/${horseId}/live?window=${window}`),
  thresholds: (horseId: number) => request<Threshold[]>('GET', `/api/horses/${horseId}/thresholds`),
  setThreshold: (horseId: number, measure: Measure, limit: number | null) =>
    request<Threshold[]>('PUT', `/api/horses/${horseId}/thresholds`, { measure, limit }),
  alerts: (horseId: number, query: ListQuery = {}) => request<Page<TelemetryAlert>>('GET', `/api/horses/${horseId}/alerts${listQs(query)}`),

  videos: (sessionId: number) => request<TrialVideo[]>('GET', `/api/sessions/${sessionId}/videos`),
  uploadVideo: (sessionId: number, file: File) => {
    const form = new FormData();
    form.append('file', file);
    return request<TrialVideo>('POST', `/api/sessions/${sessionId}/videos`, form);
  },
  deleteVideo: (id: number) => request<void>('DELETE', `/api/videos/${id}`),

  chores: (date?: string, query: ListQuery & { pending?: boolean } = {}) =>
    request<ChoreDay>('GET', `/api/chores${listQs({ ...query, date })}`),
  choreRange: (query: ListQuery & { from?: string; to?: string; horseId?: number } = {}) =>
    request<Page<ChoreRangeHorse>>('GET', `/api/chores/range${listQs(query)}`),
  choreDays: (query: ListQuery & { from?: string; to?: string; horseId?: number } = {}) =>
    request<Page<ChoreDaySummary>>('GET', `/api/chores/days${listQs(query)}`),
  backfillChore: (input: { horseId: number; chore: ChoreCode; date: string; reason: string }) =>
    request<ChoreState>('POST', '/api/chores/backfill', input),
  markChore: (horseId: number, chore: ChoreCode, date: string) =>
    request<ChoreState>('POST', `/api/horses/${horseId}/chores/${chore}/done`, { date }),
  cancelChore: (entryId: number, reason: string) =>
    request<ChoreState>('POST', `/api/chore-entries/${entryId}/cancel`, { reason }),
  choreHistory: (horseId: number, page = 0) =>
    request<Page<ChoreHistoryEntry>>('GET', `/api/horses/${horseId}/chores/history?page=${page}`),
  barn: () => request<Barn>('GET', '/api/barn'),
  setStall: (horseId: number, stall: { areaId: number; slot: number }) =>
    request<Stall>('PUT', `/api/horses/${horseId}/stall`, stall),
  clearStall: (horseId: number) => request<void>('DELETE', `/api/horses/${horseId}/stall`),

  areas: () => request<Area[]>('GET', '/api/areas'),
  createArea: (input: { name: string; capacity: number }) => request<Area>('POST', '/api/areas', input),
  updateArea: (id: number, input: { name: string; capacity: number }) => request<Area>('PUT', `/api/areas/${id}`, input),
  deleteArea: (id: number) => request<void>('DELETE', `/api/areas/${id}`),

  groomSchedule: (accountId: number) => request<{ ranges: ScheduleRange[] }>('GET', `/api/accounts/${accountId}/schedule`),
  setGroomSchedule: (accountId: number, ranges: ScheduleRange[]) =>
    request<{ ranges: ScheduleRange[] }>('PUT', `/api/accounts/${accountId}/schedule`, { ranges }),
  leave: (accountId: number, query: ListQuery = {}) => request<Page<Leave>>('GET', `/api/accounts/${accountId}/leave${listQs(query)}`),
  recordLeave: (accountId: number, input: { date: string; reason?: string }) =>
    request<Leave>('POST', `/api/accounts/${accountId}/leave`, input),
  cancelLeave: (leaveId: number) => request<Leave>('POST', `/api/leave/${leaveId}/cancel`),
  leaveNotice: () => request<{ days: number }>('GET', '/api/settings/leave-notice'),
  setLeaveNotice: (days: number) => request<{ days: number }>('PUT', '/api/settings/leave-notice', { days }),

  supplies: (query: ListQuery & { category?: string; active?: boolean } = {}) =>
    request<Page<SupplyItem>>('GET', `/api/supplies${listQs(query)}`),
  supplyUnits: () => request<SupplyUnit[]>('GET', '/api/supply-units'),
  addSupplyUnit: (name: string) => request<SupplyUnit>('POST', '/api/supply-units', { name }),
  renameSupplyUnit: (id: number, name: string) => request<SupplyUnit>('PUT', `/api/supply-units/${id}`, { name }),
  setSupplyUnitActive: (id: number, active: boolean) =>
    request<SupplyUnit>('POST', `/api/supply-units/${id}/${active ? 'activate' : 'deactivate'}`, {}),
  createSupply: (input: SupplyInput) => request<SupplyItem>('POST', '/api/supplies', clean(input)),
  updateSupply: (id: number, input: SupplyInput) => request<SupplyItem>('PUT', `/api/supplies/${id}`, clean(input)),
  setSupplyPrice: (id: number, price: number) =>
    request<{ id: number; unitPrice: number }>('PUT', `/api/supplies/${id}/price`, { price }),
  receiveStock: (id: number, quantity: number, note: string, expectedReceiptId?: number) =>
    request<StockMovement>('POST', `/api/supplies/${id}/receipts`, clean({ quantity, note, expectedReceiptId })),
  adjustStock: (id: number, newQuantity: number, reason: string) =>
    request<StockMovement>('POST', `/api/supplies/${id}/adjustments`, { newQuantity, reason }),
  stockMovements: (id: number, page = 0) => request<Page<StockMovement>>('GET', `/api/supplies/${id}/movements?page=${page}`),
  expectedReceipts: (id: number) => request<ExpectedReceipt[]>('GET', `/api/supplies/${id}/expected-receipts`),
  expectReceipt: (id: number, expectedDate: string, quantity: number) =>
    request<ExpectedReceipt>('POST', `/api/supplies/${id}/expected-receipts`, { expectedDate, quantity }),
  cancelExpectedReceipt: (id: number, expectedId: number) =>
    request<void>('DELETE', `/api/supplies/${id}/expected-receipts/${expectedId}`),
  stockWarning: () => request<{ days: number }>('GET', '/api/settings/stock-warning'),
  setStockWarning: (days: number) => request<{ days: number }>('PUT', '/api/settings/stock-warning', { days }),
  proposeRestock: (itemId: number, quantity: number | '', note: string) =>
    request<RestockProposal>('POST', `/api/supplies/${itemId}/proposals`, clean({ quantity, note })),
  ration: (horseId: number) => request<Ration>('GET', `/api/horses/${horseId}/ration`),
  saveRation: (horseId: number, meals: RationMealInput[]) =>
    request<Ration>('PUT', `/api/horses/${horseId}/ration`, { meals }),
  rationOptions: () => request<RationOption[]>('GET', '/api/rations/options'),
  rationsToday: () => request<RationTodayEntry[]>('GET', '/api/rations/today'),
  proposals: (query: ListQuery & { state?: string } = {}) =>
    request<Page<RestockProposal>>('GET', `/api/restock-proposals${listQs(query)}`),
  approveProposal: (id: number) => request<RestockProposal>('POST', `/api/restock-proposals/${id}/approve`),
  rejectProposal: (id: number, reason: string) =>
    request<RestockProposal>('POST', `/api/restock-proposals/${id}/reject`, clean({ reason })),

  // billing (BR-178 to BR-183)
  priceList: () => request<PriceList>('GET', '/api/billing/prices'),
  setPrice: (kind: 'DAILY_FEE' | 'EXAM_FEE', feeTypeId: number | null, amount: number, effectiveFrom: string) =>
    request<PriceList>('POST', '/api/billing/prices', { kind, feeTypeId, amount, effectiveFrom }),
  /** A fee type is created with its first price in one step (BR-207, AC-453). */
  createFeeType: (name: string, amount: number, effectiveFrom: string) =>
    request<{ id: number; name: string }>('POST', '/api/billing/fee-types', { name, amount, effectiveFrom }),
  billingOverview: (month: string) => request<BillingOverview>('GET', `/api/billing/overview?month=${month}`),
  deactivateFeeType: (id: number) => request<{ id: number }>('POST', `/api/billing/fee-types/${id}/deactivate`),
  examFeeTypes: () => request<FeeTypeOption[]>('GET', '/api/exam-fee-types'),
  myStatement: (month: string) => request<Statement>('GET', `/api/billing/my-statement?month=${month}`),
  statementOwners: (month: string) => request<StatementOwnerRow[]>('GET', `/api/billing/statements?month=${month}`),
  ownerStatement: (ownerId: number, month: string) =>
    request<Statement>('GET', `/api/billing/statements/${ownerId}?month=${month}`),
  horseCosts: (horseId: number, month: string) =>
    request<Statement>('GET', `/api/horses/${horseId}/costs?month=${month}`),
  invoices: (month: string, page = 0) => request<Page<InvoiceRow>>('GET', `/api/billing/invoices?month=${month}&page=${page}`),
  /** Photo version by horse id for the horses the viewer may see and that have a photo (avatars in lists, BR-184). */
  horsePhotoVersions: () => request<Record<string, number>>('GET', '/api/horses/photo-versions'),
  invoice: (id: number) => request<Statement>('GET', `/api/billing/invoices/${id}`),
  closingStatus: (month: string) => request<ClosingStatus>('GET', `/api/billing/months/${month}/closing`),
  myTimetable: (from: string, to: string) => request<Timetable>('GET', `/api/my-timetable?from=${from}&to=${to}`),
  /** Excel downloads (PERM-057): the file name comes from the service. */
  exportAllStatements: (month: string) => download(`/api/billing/statements/export?month=${month}`),
  exportOwnerStatement: (ownerId: number, month: string) => download(`/api/billing/statements/${ownerId}/export?month=${month}`),
  exportMyStatement: (month: string) => download(`/api/billing/my-statement/export?month=${month}`),
  exportInvoice: (id: number) => download(`/api/billing/invoices/${id}/export`),
  closeMonth: (month: string) =>
    request<{ month: string; invoices: number; total: number }>('POST', `/api/billing/months/${month}/close`),
  addAdjustment: (input: AdjustmentInput) => request<void>('POST', '/api/billing/adjustments', clean(input)),
};

/** Form values use '' for empty inputs; the service expects null. */
function clean<T extends object>(input: T): Record<string, unknown> {
  return Object.fromEntries(Object.entries(input).map(([k, v]) => [k, v === '' ? null : v]));
}

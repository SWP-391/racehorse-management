/**
 * Maximum text lengths, one per column of the database (backend/src/main/resources/db/migration). A text field carries
 * the length of the column it is saved in, so a longer entry is stopped on the field and never reaches a 500 (input-validation).
 */
export const LIMITS = {
  username: 100,
  displayName: 200,
  horseName: 200,
  coatColour: 100,
  breed: 100,
  ancestorName: 200,
  planTitle: 200,
  phaseName: 200,
  surface: 100,
  areaName: 100,
  supplyName: 200,
  supplyUnit: 50,
  mealName: 100,
  careName: 200,
  performer: 200,
  product: 200,
  batch: 100,
  bodyArea: 200,
  feeTypeName: 200,
  invoiceReason: 500,
  voidReason: 200,
  /** Columns of type TEXT: a reason, a note, a description. The service takes any length; the form stops a pasted essay. */
  longText: 2000,
} as const;


import type { MarkStatus } from '../api/types';

/** The three layers of the shared model; node names are `<layer>.<REGION_CODE>` (TECH-048). */
export type Layer = 'skin' | 'muscle' | 'skeleton';
export const LAYERS: Layer[] = ['skin', 'muscle', 'skeleton'];
export type LayerState = Record<Layer, boolean>;
export const ALL_LAYERS: LayerState = { skin: true, muscle: true, skeleton: true };

/** Reads a model node name such as `muscle.LF_FETLOCK`; anything else (anchors, groups) gives null. */
export function parseNodeName(name: string | undefined | null): { layer: Layer; code: string } | null {
  const m = /^(skin|muscle|skeleton)\.([A-Z][A-Z_]*)$/.exec(name ?? '');
  return m ? { layer: m[1] as Layer, code: m[2] } : null;
}

export const anchorName = (code: string) => `anchor.${code}`;

/** Marker colour per recovery status. */
export const STATUS_COLOURS: Record<MarkStatus, string> = {
  NEW: '#d7261e',
  RECOVERING: '#f0a400',
  RECOVERED: '#2e9d4a',
  // Never drawn: a cancelled mark is left off the model (BR-112).
  CANCELLED: '#8a8a8a',
};

/**
 * Status changes offered in the status form (BR-097, ST-060 to ST-063): nothing leaves Đã hồi phục or Đã hủy.
 * Cancelling (Mới to Đã hủy) has its own form because it needs a reason (BR-112).
 */
export function nextStatuses(from: MarkStatus): MarkStatus[] {
  switch (from) {
    case 'NEW':
      return ['RECOVERING', 'RECOVERED'];
    case 'RECOVERING':
      return ['RECOVERED'];
    default:
      return [];
  }
}

/** True where the browser can create a WebGL context (BR-102: otherwise the list alone is shown). */
export function supportsWebGl(): boolean {
  try {
    const canvas = document.createElement('canvas');
    return !!(canvas.getContext('webgl2') || canvas.getContext('webgl'));
  } catch {
    return false;
  }
}

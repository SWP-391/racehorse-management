import { useCallback } from 'react';
import { api } from '../api/api';
import { useAuth } from '../auth/AuthContext';
import { useLoad } from './useLoad';

/**
 * The photo version of each horse the viewer may see, for the avatars of list pages (BR-184). Asked only when the
 * role may view horses (the photo follows the horse profile's access), so Grooms never trigger a refused request,
 * and one call serves the whole page. A horse without a photo, or any failure, simply shows the initial.
 */
export function useHorsePhotos(): (horseId: number) => number | null {
  const { can } = useAuth();
  const versions = useLoad(() => api.horsePhotoVersions(), [], can('VIEW_HORSES'));
  const data = versions.state.data;
  return useCallback((horseId: number) => data?.[String(horseId)] ?? null, [data]);
}

import * as AvatarPrimitive from '@radix-ui/react-avatar';
import { horsePhotoUrl } from '../api/api';
import { cn } from '@/lib/utils';
import { Avatar, AvatarFallback } from './ui/display';

interface HorseAvatarProps {
  id: number;
  name: string;
  /** From `Horse.photoVersion`; null while the horse has no photo (BR-184). */
  photoVersion: number | null;
  /** The small rendition suits lists and cards, the full one the profile. */
  variant?: 'thumb' | 'full';
  className?: string;
}

/** The horse's photo in a fixed box (the layout never jumps), or a circle with its initial when there is none. */
export function HorseAvatar({ id, name, photoVersion, variant = 'thumb', className }: HorseAvatarProps) {
  return (
    <Avatar className={cn('size-10', className)}>
      {photoVersion !== null && (
        <AvatarPrimitive.Image
          src={horsePhotoUrl(id, photoVersion, variant)}
          alt={`Ảnh ${name}`}
          loading="lazy"
          className="size-full object-cover"
        />
      )}
      <AvatarFallback aria-hidden>{name.trim().charAt(0).toUpperCase()}</AvatarFallback>
    </Avatar>
  );
}

import { Children, useEffect, useRef, type ReactNode } from 'react';
import { motion, useReducedMotion } from 'motion/react';
import { cn } from '@/lib/utils';

const STEP_S = 0.03;
const MAX_STAGGERED = 8;

/**
 * Feed list whose items rise in once on first load (30 ms step, at most 8 items) and never on refetch
 * (motion.md § Rules). Stands in for React Bits AnimatedList, which pulls in an icon library the kit
 * does not use (lucide only); same behaviour on `motion`.
 */
export function StaggerList({ children, className, as = 'ul' }: { children: ReactNode; className?: string; as?: 'ul' | 'ol' }) {
  const reduced = useReducedMotion();
  const mounted = useRef(false);
  useEffect(() => {
    mounted.current = true;
  }, []);
  const animate = !mounted.current && !reduced;
  const List = as;
  return (
    <List className={cn('m-0 list-none p-0', className)}>
      {Children.map(children, (child, i) =>
        child == null ? null : (
          <motion.li
            initial={animate && i < MAX_STAGGERED ? { opacity: 0, y: 6 } : false}
            animate={{ opacity: 1, y: 0 }}
            transition={{ duration: 0.2, delay: i * STEP_S, ease: [0.2, 0.8, 0.2, 1] }}
          >
            {child}
          </motion.li>
        ),
      )}
    </List>
  );
}

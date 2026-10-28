import Threads from '../effects/Threads';

const css = (name: string) => getComputedStyle(document.documentElement).getPropertyValue(name).trim();

/** The Login page's one signature effect: slow ruled threads in the club's green and brass (motion.md § Effects layer). */
export default function LoginBackdrop() {
  return (
    <Threads
      aria-hidden
      color={css('--chart-1')}
      accentColor={css('--chart-2')}
      amplitude={1.2}
      distance={0.25}
      lineCount={48}
      thickness={0.5}
      speed={0.25}
      angle={-12}
      brightness={1.1}
      fade={0.35}
      opacity={0.55}
    />
  );
}

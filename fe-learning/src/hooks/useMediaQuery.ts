import { useEffect, useState } from 'react';

/**
 * Subscribes to a CSS media query and re-renders on change.
 * SSR-safe and cleans up its own listener.
 */
export function useMediaQuery(query: string): boolean {
  const [matches, setMatches] = useState<boolean>(() => {
    if (typeof window === 'undefined') {
      return false;
    }
    return window.matchMedia(query).matches;
  });

  useEffect(() => {
    const mediaQueryList: MediaQueryList = window.matchMedia(query);
    const handleChange = (event: MediaQueryListEvent): void => setMatches(event.matches);

    setMatches(mediaQueryList.matches);
    mediaQueryList.addEventListener('change', handleChange);

    return () => mediaQueryList.removeEventListener('change', handleChange);
  }, [query]);

  return matches;
}

/** Matches the Tailwind `lg` breakpoint (1024px). */
export const useIsDesktop = (): boolean => useMediaQuery('(min-width: 1024px)');
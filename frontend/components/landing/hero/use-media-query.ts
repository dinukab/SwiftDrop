"use client";

import { useSyncExternalStore } from "react";

/**
 * Reports whether a media query matches. During server rendering and hydration it reports
 * `false`, so the first client render matches the server HTML and the real value follows.
 */
export function useMediaQuery(query: string): boolean {
  return useSyncExternalStore(
    (onChange) => {
      const list = window.matchMedia(query);
      list.addEventListener("change", onChange);
      return () => list.removeEventListener("change", onChange);
    },
    () => window.matchMedia(query).matches,
    () => false
  );
}

/** Matches Tailwind's `lg` breakpoint, where the hero pins and animates. */
export const DESKTOP_QUERY = "(min-width: 64rem)";
export const REDUCED_MOTION_QUERY = "(prefers-reduced-motion: reduce)";

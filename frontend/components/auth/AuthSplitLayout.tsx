"use client";

import type { ReactNode } from "react";
import { REDUCED_MOTION_QUERY, useMediaQuery } from "@/components/landing/hero/use-media-query";
import { AuthVisual, type AuthVisualContent } from "./AuthVisual";

type AuthSplitLayoutProps = {
  visual: AuthVisualContent;
  children: ReactNode;
};

/**
 * Sign-in and sign-up layout: a photo column (45%) beside the form on large screens, and a
 * 160px photo banner above the form below 1024px. The photo column stays put while a long
 * form scrolls.
 */
export function AuthSplitLayout({ visual, children }: AuthSplitLayoutProps) {
  const reducedMotion = useMediaQuery(REDUCED_MOTION_QUERY);

  return (
    <div className="flex min-h-screen flex-1 flex-col font-body lg:grid lg:grid-cols-[45%_1fr]">
      <aside className="hidden lg:sticky lg:top-0 lg:block lg:h-screen">
        <AuthVisual content={visual} variant="panel" reducedMotion={reducedMotion} />
      </aside>

      <main className="flex flex-1 flex-col bg-white">
        <div className="lg:hidden">
          <AuthVisual content={visual} variant="banner" reducedMotion={reducedMotion} />
        </div>

        <div className="flex flex-1 items-center justify-center px-6 py-10 sm:px-10">
          <div className="w-full max-w-md">{children}</div>
        </div>
      </main>
    </div>
  );
}

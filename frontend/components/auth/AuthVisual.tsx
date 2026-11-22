"use client";

import Image from "next/image";
import Link from "next/link";
import { AnimatePresence, motion } from "framer-motion";
import { cn } from "cn";

export type AuthVisualContent = {
  image: string;
  tagline: string;
};

type AuthVisualProps = {
  content: AuthVisualContent;
  /** `panel` is the tall desktop column; `banner` is the 160px strip above the form on small screens. */
  variant: "panel" | "banner";
  reducedMotion: boolean;
};

export function AuthVisual({ content, variant, reducedMotion }: AuthVisualProps) {
  const panel = variant === "panel";

  return (
    <div className={cn("relative overflow-hidden bg-navy", panel ? "h-full" : "h-40")}>
      {/* Both layers stay mounted during the fade, so the photos crossfade instead of blinking. */}
      <AnimatePresence initial={false}>
        <motion.div
          key={content.image + content.tagline}
          className="absolute inset-0"
          initial={{ opacity: 0 }}
          animate={{ opacity: 1 }}
          exit={{ opacity: 0 }}
          transition={{ duration: reducedMotion ? 0 : 0.4, ease: "easeInOut" }}
        >
          <Image
            src={content.image}
            alt=""
            fill
            sizes={panel ? "45vw" : "100vw"}
            className={cn("object-cover", panel ? "object-center" : "object-[center_30%]")}
          />
          <div
            className={cn(
              "absolute inset-x-0 bottom-0 bg-linear-to-t from-navy/90 to-transparent",
              panel ? "h-2/3" : "h-full"
            )}
          />
          <p
            className={cn(
              "absolute font-display font-semibold tracking-tight text-white",
              panel
                ? "right-12 bottom-12 left-12 max-w-md text-4xl leading-tight"
                : "right-4 bottom-4 left-4 text-lg leading-snug"
            )}
          >
            {content.tagline}
          </p>
        </motion.div>
      </AnimatePresence>

      <Link
        href="/"
        aria-label="SwiftDrop home"
        className={cn(
          "absolute z-10 rounded-lg font-extrabold tracking-tight text-white outline-none focus-visible:ring-3 focus-visible:ring-brand",
          panel ? "top-10 left-12 text-2xl" : "top-4 left-4 text-lg"
        )}
      >
        Swift<span className="text-brand">Drop</span>
      </Link>
    </div>
  );
}

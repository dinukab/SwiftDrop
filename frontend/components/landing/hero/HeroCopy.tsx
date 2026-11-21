"use client";

import { useEffect, useState } from "react";
import { AnimatePresence, motion, useTransform, type MotionValue } from "framer-motion";
import { HERO_COPY, TIMELINE, TRUST_ROW, WORD_ROTATION_MS } from "./data";
import { TrackingInput } from "./TrackingInput";

function RotatingWord({ rotate }: { rotate: boolean }) {
  const [index, setIndex] = useState(0);
  const words = HERO_COPY.rotatingWords;

  useEffect(() => {
    if (!rotate) return;
    const timer = window.setInterval(
      () => setIndex((current) => (current + 1) % words.length),
      WORD_ROTATION_MS
    );
    return () => window.clearInterval(timer);
  }, [rotate, words.length]);

  const word = rotate ? words[index] : words[0];

  return (
    // Fixed one-line box: words slide inside it without moving the lines around it.
    <span className="relative block h-[1.12em] overflow-hidden text-brand">
      <AnimatePresence initial={false}>
        <motion.span
          key={word}
          className="absolute inset-x-0 top-0 whitespace-nowrap"
          initial={{ y: "100%", opacity: 0 }}
          animate={{ y: "0%", opacity: 1 }}
          exit={{ y: "-100%", opacity: 0 }}
          transition={{ duration: 0.55, ease: [0.22, 1, 0.36, 1] }}
        >
          {word}
        </motion.span>
      </AnimatePresence>
    </span>
  );
}

type HeroCopyProps = {
  progress: MotionValue<number>;
  /** Scroll-linked movement (desktop without reduced motion). */
  animate: boolean;
  /** Word rotation; off only for reduced motion. */
  rotateWords: boolean;
};

export function HeroCopy({ progress, animate, rotateWords }: HeroCopyProps) {
  const y = useTransform(progress, TIMELINE.copyExit, [0, -40]);
  const opacity = useTransform(progress, TIMELINE.copyExit, [1, 0.6]);

  return (
    <motion.div
      className="w-full max-w-xl will-change-transform lg:max-w-lg xl:max-w-xl"
      style={animate ? { y, opacity } : undefined}
    >
      <p className="inline-flex items-center gap-2.5 rounded-full border border-white/14 bg-white/7 px-3.5 py-1.5 text-sm font-medium text-white/90">
        <span className="relative flex size-2" aria-hidden="true">
          <span className="absolute inline-flex size-full animate-ping rounded-full bg-status-live opacity-60 motion-reduce:animate-none" />
          <span className="relative inline-flex size-2 rounded-full bg-status-live" />
        </span>
        {HERO_COPY.badge}
      </p>

      <h1
        id="hero-heading"
        className="mt-6 font-display text-5xl leading-[1.04] font-bold tracking-tight text-white lg:text-6xl xl:[@media(min-height:50rem)]:text-7xl"
      >
        <span className="sr-only">{HERO_COPY.headingAccessible} </span>
        <span aria-hidden="true">
          <span className="block">{HERO_COPY.headingLead}</span>
          <RotatingWord rotate={rotateWords} />
        </span>
        <span className="mt-3 block text-2xl font-semibold tracking-tight text-white/85 lg:text-3xl">
          {HERO_COPY.tagline}
        </span>
      </h1>

      <p className="mt-6 max-w-lg text-lg leading-relaxed text-ink-light">{HERO_COPY.subtitle}</p>

      <TrackingInput className="mt-8 max-w-lg" />

      <ul className="mt-6 flex flex-wrap gap-x-5 gap-y-2.5 text-sm font-medium text-white/85">
        {TRUST_ROW.map(({ icon: Icon, label }) => (
          <li key={label} className="inline-flex items-center gap-2">
            <Icon aria-hidden="true" className="size-4 text-status-live" />
            {label}
          </li>
        ))}
      </ul>
    </motion.div>
  );
}

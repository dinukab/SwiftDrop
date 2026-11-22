"use client";

import { useRef } from "react";
import { MotionConfig, useScroll, useTransform } from "framer-motion";
import { REDUCED_MOTION_QUERY, useMediaQuery } from "../hero/use-media-query";
import { CtaBlock } from "./CtaBlock";
import { AUDIENCE_CTA_LABEL, CTA_BLOCKS } from "./data";

export function AudienceCta() {
  const sectionRef = useRef<HTMLElement>(null);
  const reducedMotion = useMediaQuery(REDUCED_MOTION_QUERY);
  const { scrollYProgress } = useScroll({ target: sectionRef, offset: ["start end", "end start"] });
  const decorationY = useTransform(scrollYProgress, [0, 1], [20, -20]);

  return (
    // "user" drops transform animations (entrance slide, hover lift) for reduced motion.
    <MotionConfig reducedMotion="user">
      <section
        ref={sectionRef}
        id="business"
        aria-label={AUDIENCE_CTA_LABEL}
        // Clips the blocks' sideways entrance so it never adds horizontal scroll.
        className="overflow-x-clip bg-surface py-24 font-body md:py-32"
      >
        <div className="mx-auto grid max-w-[1240px] grid-cols-1 gap-6 px-4 sm:px-6 lg:grid-cols-2">
          {CTA_BLOCKS.map((content) => (
            <CtaBlock
              key={content.variant}
              content={content}
              decorationY={reducedMotion ? undefined : decorationY}
            />
          ))}
        </div>
      </section>
    </MotionConfig>
  );
}

"use client";

import { useRef } from "react";
import { motion, useScroll, useSpring, useTransform } from "framer-motion";
import { ArrowDown } from "lucide-react";
import { HERO_COPY, TIMELINE } from "./data";
import { HeroBackground } from "./HeroBackground";
import { HeroCopy } from "./HeroCopy";
import { JourneyCard } from "./JourneyCard";
import { TrustBar } from "./TrustBar";
import { DESKTOP_QUERY, REDUCED_MOTION_QUERY, useMediaQuery } from "./use-media-query";

/**
 * Pinned, scroll-driven hero. On `lg` screens without reduced motion the section is 250vh tall
 * and its content sticks to the viewport while scroll progress drives the scene. The pinning
 * is CSS (`lg:motion-safe:`), so it is right before hydration; JavaScript only adds motion.
 */
export function Hero() {
  const sectionRef = useRef<HTMLElement>(null);
  const isDesktop = useMediaQuery(DESKTOP_QUERY);
  const reducedMotion = useMediaQuery(REDUCED_MOTION_QUERY);
  const animate = isDesktop && !reducedMotion;

  const { scrollYProgress } = useScroll({
    target: sectionRef,
    offset: ["start start", "end end"],
  });
  const progress = useSpring(scrollYProgress, { stiffness: 50, damping: 20, mass: 0.8 });
  const hintOpacity = useTransform(progress, TIMELINE.hintExit, [1, 0]);

  return (
    <section
      ref={sectionRef}
      aria-labelledby="hero-heading"
      // Slides under the 4rem sticky navbar so the scene starts at the top of the page.
      className="relative -mt-16 font-body lg:motion-safe:h-[250vh]"
    >
      <div className="relative flex flex-col overflow-hidden bg-navy lg:min-h-screen lg:motion-safe:sticky lg:motion-safe:top-0 lg:motion-safe:h-screen">
        <HeroBackground progress={progress} animate={animate} />

        <div className="relative mx-auto flex w-full max-w-7xl flex-1 flex-col px-4 pt-28 pb-12 sm:px-6 lg:px-8 lg:pt-20 lg:pb-8">
          <div className="flex flex-1 items-center">
            <HeroCopy progress={progress} animate={animate} rotateWords={!reducedMotion} />
          </div>
          <TrustBar progress={progress} animate={animate} className="mt-12 lg:mt-6" />
          <JourneyCard progress={progress} animate={animate} />
        </div>

        <motion.p
          aria-hidden="true"
          className="pointer-events-none absolute inset-x-0 bottom-8 hidden items-center justify-center gap-2 text-sm font-medium text-white/70 lg:motion-safe:flex"
          style={animate ? { opacity: hintOpacity } : undefined}
        >
          {HERO_COPY.scrollHint}
          <ArrowDown className="size-4 animate-bounce" />
        </motion.p>
      </div>
    </section>
  );
}

"use client";

import Link from "next/link";
import { useCallback, useEffect, useRef, useState } from "react";
import {
  MotionConfig,
  motion,
  useMotionValueEvent,
  useScroll,
  useSpring,
  useTransform,
} from "framer-motion";
import { Package } from "lucide-react";
import { cn } from "cn";
import { SectionHeader } from "../SectionHeader";
import { DESKTOP_QUERY, REDUCED_MOTION_QUERY, useMediaQuery } from "../hero/use-media-query";
import { HOW_IT_WORKS_COPY, STEPS } from "./data";
import { StepCard, type StepState } from "./StepCard";

const LAST = STEPS.length - 1;

/** Where the route runs, measured from the number circles inside the steps wrapper. */
type Route = {
  horizontal: boolean;
  /** Centre of the first circle. */
  x: number;
  y: number;
  /** Distance from the first circle's centre to the last one's. */
  length: number;
  /** Progress (0 to 1) at which the parcel reaches each circle. */
  stops: number[];
};

/** Offset of an element inside a positioned ancestor. Uses layout offsets, so transforms are ignored. */
function offsetWithin(element: HTMLElement, container: HTMLElement) {
  let x = 0;
  let y = 0;
  let node: HTMLElement | null = element;
  while (node && node !== container) {
    x += node.offsetLeft;
    y += node.offsetTop;
    node = node.offsetParent as HTMLElement | null;
  }
  return { x, y };
}

function stepState(step: number, reached: number): StepState {
  if (step === reached) return "current";
  return step < reached ? "active" : "inactive";
}

export function HowItWorks() {
  const stepsRef = useRef<HTMLDivElement>(null);
  const circles = useRef<(HTMLSpanElement | null)[]>([]);
  const [route, setRoute] = useState<Route | null>(null);
  const routeRef = useRef<Route | null>(null);
  const [reached, setReached] = useState(0);
  const reachedRef = useRef(0);
  const reducedMotion = useMediaQuery(REDUCED_MOTION_QUERY);

  const { scrollYProgress } = useScroll({ target: stepsRef, offset: ["start 75%", "end 60%"] });
  const progress = useSpring(scrollYProgress, { stiffness: 60, damping: 20 });
  const markerAlong = useTransform(progress, [0, 1], ["0%", "100%"]);

  // Circles move with the layout (breakpoints, wrapping text, late fonts), so the route is
  // re-measured whenever the steps wrapper resizes. The observer also fires once on start.
  useEffect(() => {
    const wrapper = stepsRef.current;
    if (!wrapper) return;

    const measure = () => {
      const centres = circles.current.map((circle) => {
        if (!circle) return { x: 0, y: 0 };
        const { x, y } = offsetWithin(circle, wrapper);
        return { x: x + circle.offsetWidth / 2, y: y + circle.offsetHeight / 2 };
      });
      const horizontal = window.matchMedia(DESKTOP_QUERY).matches;
      const axis = horizontal ? "x" : "y";
      const start = centres[0][axis];
      const length = centres[LAST][axis] - start;
      const next: Route = {
        horizontal,
        x: centres[0].x,
        y: centres[0].y,
        length,
        stops: centres.map((centre) => (length > 0 ? (centre[axis] - start) / length : 0)),
      };
      routeRef.current = next;
      setRoute(next);
    };

    const observer = new ResizeObserver(measure);
    observer.observe(wrapper);
    return () => observer.disconnect();
  }, []);

  // A step lights up when the parcel reaches its circle; state changes only when the step does.
  useMotionValueEvent(progress, "change", (value) => {
    const stops = routeRef.current?.stops ?? STEPS.map((_, i) => i / LAST);
    let next = 0;
    stops.forEach((stop, i) => {
      if (value >= stop - 0.01) next = i;
    });
    if (next !== reachedRef.current) {
      reachedRef.current = next;
      setReached(next);
    }
  });

  const current = reducedMotion ? LAST : reached;
  const setCircle = useCallback(
    (index: number) => (element: HTMLSpanElement | null) => {
      circles.current[index] = element;
    },
    []
  );

  return (
    <MotionConfig reducedMotion="user">
      <section
        id="how-it-works"
        aria-labelledby="how-it-works-heading"
        // The parcel's carrier layer slides by its own length; clip keeps it from widening the page.
        className="overflow-clip border-y border-line bg-white py-24 font-body md:py-32"
      >
        <div className="mx-auto max-w-[1240px] px-6">
          <SectionHeader
            id="how-it-works-heading"
            eyebrow={HOW_IT_WORKS_COPY.eyebrow}
            title={HOW_IT_WORKS_COPY.heading}
            description={HOW_IT_WORKS_COPY.intro}
          />

          <div ref={stepsRef} className="relative mt-16">
            {route && route.length > 0 && (
              <div
                aria-hidden="true"
                className="pointer-events-none absolute"
                style={
                  route.horizontal
                    ? { left: route.x, top: route.y, width: route.length }
                    : { left: route.x, top: route.y, height: route.length }
                }
              >
                <div
                  className={cn(
                    "absolute border-dashed border-line",
                    route.horizontal
                      ? "inset-x-0 top-0 -translate-y-1/2 border-t-2"
                      : "inset-y-0 left-0 -translate-x-1/2 border-l-2"
                  )}
                />
                <motion.div
                  className={cn(
                    "absolute top-0 left-0 rounded-full bg-brand",
                    route.horizontal
                      ? "h-[3px] w-full -translate-y-1/2 origin-left"
                      : "h-full w-[3px] -translate-x-1/2 origin-top"
                  )}
                  style={
                    route.horizontal
                      ? { scaleX: reducedMotion ? 1 : progress }
                      : { scaleY: reducedMotion ? 1 : progress }
                  }
                />
                {/* Percent offsets resolve against this full-length layer, so 100% is the last circle. */}
                <motion.div
                  className="absolute inset-0 z-[5]"
                  style={
                    route.horizontal
                      ? { x: reducedMotion ? "100%" : markerAlong }
                      : { y: reducedMotion ? "100%" : markerAlong }
                  }
                >
                  <span className="absolute top-0 left-0 grid size-9 -translate-x-1/2 -translate-y-1/2 place-items-center rounded-xl bg-brand text-navy ring-4 ring-brand/20">
                    <Package className="size-[18px]" strokeWidth={2.25} />
                  </span>
                </motion.div>
              </div>
            )}

            <ol className="grid grid-cols-1 gap-10 lg:grid-cols-4 lg:gap-6">
              {STEPS.map((step, index) => (
                <StepCard
                  key={step.title}
                  step={step}
                  number={index + 1}
                  index={index}
                  state={stepState(index, current)}
                  circleRef={setCircle(index)}
                />
              ))}
            </ol>
          </div>

          <div className="mt-14 flex flex-wrap justify-center gap-3">
            <Link
              href={HOW_IT_WORKS_COPY.primaryCta.href}
              className="inline-flex min-h-12 items-center rounded-xl bg-brand px-6 font-bold text-navy transition-colors outline-none hover:bg-[#ff7f36] focus-visible:ring-3 focus-visible:ring-brand focus-visible:ring-offset-2"
            >
              {HOW_IT_WORKS_COPY.primaryCta.label}
            </Link>
            <Link
              href={HOW_IT_WORKS_COPY.secondaryCta.href}
              className="inline-flex min-h-12 items-center rounded-xl border border-navy/20 px-6 font-semibold text-navy transition-colors outline-none hover:bg-surface focus-visible:ring-3 focus-visible:ring-brand focus-visible:ring-offset-2"
            >
              {HOW_IT_WORKS_COPY.secondaryCta.label}
            </Link>
          </div>
        </div>
      </section>
    </MotionConfig>
  );
}

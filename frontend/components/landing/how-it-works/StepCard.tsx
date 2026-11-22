"use client";

import { motion } from "framer-motion";
import { cn } from "cn";
import type { Step } from "./data";

export type StepState = "inactive" | "active" | "current";

const CIRCLE_STATES: Record<StepState, string> = {
  inactive: "border-line bg-white text-ink-muted",
  active: "border-navy bg-navy text-white",
  current: "border-brand bg-brand text-navy ring-4 ring-brand/20",
};

type StepCardProps = {
  step: Step;
  number: number;
  state: StepState;
  index: number;
  /** Receives the number circle, so the route can be drawn through its centre. */
  circleRef: (element: HTMLSpanElement | null) => void;
};

export function StepCard({ step, number, state, index, circleRef }: StepCardProps) {
  const Icon = step.icon;
  const reached = state !== "inactive";

  return (
    <motion.li
      initial={{ opacity: 0, y: 24 }}
      whileInView={{ opacity: 1, y: 0 }}
      viewport={{ once: true, margin: "0px 0px -60px 0px" }}
      transition={{ duration: 0.55, delay: 0.1 * index, ease: [0.22, 1, 0.36, 1] }}
      // z-10 keeps the circle above the travelling parcel marker, which sits on the route at z-[5].
      className="group relative z-10 flex gap-5 lg:flex-col lg:items-center lg:gap-4 lg:text-center"
    >
      <span className="shrink-0 transition-transform duration-200 group-hover:-translate-y-0.5 motion-reduce:transition-none">
        <motion.span
          ref={circleRef}
          animate={reached ? { scale: [1, 1.08, 1] } : { scale: 1 }}
          transition={{ duration: 0.4, ease: "easeOut" }}
          className={cn(
            "grid size-14 place-items-center rounded-full border-2 font-display text-lg font-bold transition-colors duration-300",
            CIRCLE_STATES[state]
          )}
        >
          {number}
        </motion.span>
      </span>

      <div className="flex flex-col items-start gap-3 pt-1 lg:items-center lg:pt-0">
        <span className="grid size-11 place-items-center rounded-xl bg-surface">
          <Icon aria-hidden="true" className="size-[22px] text-navy" />
        </span>
        <h3 className="font-display text-xl font-semibold text-navy">{step.title}</h3>
        <p className="max-w-[260px] text-[15px] leading-relaxed text-ink-muted">{step.description}</p>
        <p className="text-sm font-semibold text-brand-text">{step.meta}</p>
      </div>
    </motion.li>
  );
}

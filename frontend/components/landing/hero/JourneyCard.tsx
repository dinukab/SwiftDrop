"use client";

import { useRef, useState } from "react";
import { motion, useMotionValueEvent, useTransform, type MotionValue } from "framer-motion";
import { ArrowRight, Clock3 } from "lucide-react";
import { cn } from "cn";
import { JOURNEY, type StageTone } from "./data";

const PILL_TONES: Record<StageTone, string> = {
  transit: "bg-status-transit text-white",
  brand: "bg-brand text-navy",
  delivered: "bg-status-delivered text-white",
};

const LAST_STAGE = JOURNEY.stages.length - 1;

function stageAt(progress: number): number {
  const index = JOURNEY.stages.findIndex((stage) => progress < stage.until);
  return index === -1 ? LAST_STAGE : index;
}

type JourneyCardProps = {
  progress: MotionValue<number>;
  /** Without scroll animation the card shows the finished journey. */
  animate: boolean;
};

export function JourneyCard({ progress, animate }: JourneyCardProps) {
  const [stage, setStage] = useState(0);
  const stageRef = useRef(0);

  useMotionValueEvent(progress, "change", (value) => {
    const next = stageAt(value);
    if (next !== stageRef.current) {
      stageRef.current = next;
      setStage(next);
    }
  });

  const opacity = useTransform(progress, JOURNEY.enter, [0, 1]);
  const y = useTransform(progress, JOURNEY.enter, [40, 0]);
  const barScale = useTransform(progress, JOURNEY.bar, [0.06, 1]);

  const current = JOURNEY.stages[animate ? stage : LAST_STAGE];

  return (
    // Illustration of a journey, not real parcel data, so it stays out of the accessibility tree.
    <motion.div
      aria-hidden="true"
      className="pointer-events-none absolute top-[18%] right-4 hidden w-72 rounded-2xl border border-white/15 bg-navy-800/90 p-5 text-white shadow-2xl shadow-black/40 backdrop-blur-md will-change-transform sm:right-6 lg:right-8 lg:block lg:motion-safe:opacity-0"
      style={animate ? { opacity, y } : undefined}
    >
      <div className="flex items-center justify-between gap-3">
        <span className="text-[11px] font-semibold tracking-[0.16em] text-ink-light uppercase">
          {JOURNEY.label}
        </span>
        <motion.span
          key={current.label}
          initial={animate ? { opacity: 0, scale: 0.92 } : false}
          animate={{ opacity: 1, scale: 1 }}
          transition={{ duration: 0.25 }}
          className={cn("rounded-full px-2.5 py-1 text-xs font-semibold", PILL_TONES[current.tone])}
        >
          {current.label}
        </motion.span>
      </div>

      <p className="mt-3 font-display text-xl font-semibold tracking-wide">{JOURNEY.trackingNumber}</p>
      <p className="mt-1 flex items-center gap-1.5 text-sm text-ink-light">
        {JOURNEY.from}
        <ArrowRight className="size-3.5" />
        {JOURNEY.to}
      </p>

      <div className="mt-5 h-1.5 overflow-hidden rounded-full bg-white/10">
        <motion.div
          className="h-full w-full origin-left rounded-full bg-brand"
          style={animate ? { scaleX: barScale } : undefined}
        />
      </div>

      <p className="mt-3 flex items-center gap-1.5 text-xs font-medium text-white/80">
        <Clock3 className="size-3.5 text-ink-light" />
        {JOURNEY.eta}
      </p>
    </motion.div>
  );
}

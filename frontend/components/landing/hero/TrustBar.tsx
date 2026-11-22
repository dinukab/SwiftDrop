"use client";

import { motion, useTransform, type MotionValue } from "framer-motion";
import { cn } from "cn";
import { TIMELINE, TRUST_BAR } from "./data";

type TrustBarProps = {
  progress: MotionValue<number>;
  animate: boolean;
  className?: string;
};

export function TrustBar({ progress, animate, className }: TrustBarProps) {
  const opacity = useTransform(progress, TIMELINE.trustBarEnter, [0, 1]);
  const y = useTransform(progress, TIMELINE.trustBarEnter, [80, 0]);

  return (
    <motion.ul
      className={cn(
        "grid gap-x-6 gap-y-5 rounded-2xl border border-white/15 bg-navy-800/85 p-5 backdrop-blur-md will-change-transform sm:grid-cols-2 lg:grid-cols-4 lg:motion-safe:opacity-0",
        className
      )}
      style={animate ? { opacity, y } : undefined}
    >
      {TRUST_BAR.map(({ icon: Icon, title, text }) => (
        <li key={title} className="flex items-start gap-3">
          <span className="grid size-10 shrink-0 place-items-center rounded-xl bg-brand/15 text-brand">
            <Icon aria-hidden="true" className="size-5" />
          </span>
          <span>
            <span className="block font-display text-base font-semibold text-white">{title}</span>
            <span className="mt-0.5 block text-sm leading-snug text-ink-light">{text}</span>
          </span>
        </li>
      ))}
    </motion.ul>
  );
}

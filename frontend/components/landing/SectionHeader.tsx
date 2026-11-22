"use client";

import { motion } from "framer-motion";
import { cn } from "cn";

type SectionHeaderProps = {
  /** Id for the heading, so the section can be `aria-labelledby` it. */
  id: string;
  eyebrow: string;
  title: string;
  description?: string;
  align?: "center" | "left";
  className?: string;
};

export function SectionHeader({ id, eyebrow, title, description, align = "center", className }: SectionHeaderProps) {
  const centered = align === "center";

  return (
    <motion.div
      initial={{ opacity: 0, y: 20 }}
      whileInView={{ opacity: 1, y: 0 }}
      viewport={{ once: true, margin: "0px 0px -80px 0px" }}
      transition={{ duration: 0.6, ease: [0.22, 1, 0.36, 1] }}
      className={cn(centered && "mx-auto max-w-2xl text-center", className)}
    >
      <p className="inline-flex items-center rounded-full bg-brand-tint px-3 py-1 text-xs font-semibold tracking-[0.14em] text-brand-text uppercase">
        {eyebrow}
      </p>
      <h2
        id={id}
        className="mt-4 font-display text-4xl leading-[1.08] font-bold tracking-tight text-navy sm:text-5xl"
      >
        {title}
      </h2>
      {description && <p className="mt-4 text-lg leading-relaxed text-ink-muted">{description}</p>}
    </motion.div>
  );
}

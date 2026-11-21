"use client";

import Image from "next/image";
import Link from "next/link";
import { motion, type MotionValue } from "framer-motion";
import { ArrowRight, Check } from "lucide-react";
import { cn } from "cn";
import type { CtaContent } from "./data";

const EASE = [0.2, 0.8, 0.2, 1] as const;

const THEMES = {
  // Text on brand orange is always navy.
  business: {
    block: "bg-surface border border-surface-border text-navy",
    eyebrow: "text-navy",
    text: "text-navy/85",
    checkCircle: "bg-navy/10 text-navy",
    primary: "bg-navy text-white hover:bg-navy-800",
    secondary: "text-navy underline-offset-4 hover:underline",
    focus: "focus-visible:ring-navy focus-visible:ring-offset-brand",
    decoration: "text-navy/10",
  },
  courier: {
    block: "bg-surface border border-surface-border text-navy",
    eyebrow: "text-navy",
    text: "text-navy/85",
    checkCircle: "bg-navy/10 text-navy",
    primary: "bg-navy text-white hover:bg-navy-800",
    secondary: "text-navy underline-offset-4 hover:underline",
    focus: "focus-visible:ring-navy focus-visible:ring-offset-brand",
    decoration: "text-navy/10",
  },
} as const;

type CtaBlockProps = {
  content: CtaContent;
  /** Scroll-linked offset for the corner icon; undefined keeps it still (reduced motion). */
  decorationY?: MotionValue<number>;
};

export function CtaBlock({ content, decorationY }: CtaBlockProps) {
  const theme = THEMES[content.variant];
  const Decoration = content.decoration;
  const fromLeft = content.variant === "business";

  return (
    <motion.article
      initial={{ opacity: 0, x: fromLeft ? -32 : 32 }}
      whileInView={{ opacity: 1, x: 0 }}
      viewport={{ once: true, amount: 0.3 }}
      transition={{ duration: 0.6, ease: EASE }}
      whileHover={{ y: -4 }}
      aria-labelledby={`cta-${content.variant}-title`}
      className={cn(
        "relative flex min-h-[420px] flex-col gap-5 overflow-hidden rounded-xl p-6 sm:p-8 md:p-12",
        theme.block
      )}
    >
      <motion.div
        aria-hidden="true"
        className={cn("pointer-events-none absolute -right-10 -bottom-12", theme.decoration)}
        style={decorationY ? { y: decorationY } : undefined}
      >
        <Decoration className="size-[220px]" strokeWidth={1.25} />
      </motion.div>

      {content.photo && (
        <div className="relative aspect-video overflow-hidden rounded-2xl">
          <Image
            src={content.photo.src}
            alt={content.photo.alt}
            fill
            sizes="(min-width: 1024px) 560px, 100vw"
            className="object-cover"
          />
        </div>
      )}

      <p className={cn("relative text-sm font-bold tracking-[0.08em] uppercase", theme.eyebrow)}>
        {content.eyebrow}
      </p>
      <h3
        id={`cta-${content.variant}-title`}
        className="relative max-w-md font-display text-3xl leading-tight font-bold tracking-tight md:text-4xl"
      >
        {content.title}
      </h3>
      <p className={cn("relative max-w-md text-base leading-relaxed md:text-lg", theme.text)}>{content.text}</p>

      <motion.ul
        className="relative space-y-3"
        initial="hidden"
        whileInView="shown"
        viewport={{ once: true, amount: 0.3 }}
        variants={{ shown: { transition: { delayChildren: 0.35, staggerChildren: 0.08 } } }}
      >
        {content.benefits.map((benefit) => (
          <motion.li
            key={benefit}
            variants={{ hidden: { opacity: 0, y: 8 }, shown: { opacity: 1, y: 0, transition: { duration: 0.4, ease: EASE } } }}
            className="flex items-start gap-3 font-medium"
          >
            <span className={cn("mt-0.5 grid size-6 shrink-0 place-items-center rounded-full", theme.checkCircle)}>
              <Check aria-hidden="true" className="size-3.5" strokeWidth={3} />
            </span>
            {benefit}
          </motion.li>
        ))}
      </motion.ul>

      <div className="relative mt-auto flex flex-wrap items-center gap-x-6 gap-y-3 pt-3">
        <Link
          href={content.primary.href}
          className={cn(
            "group/cta inline-flex min-h-12 items-center gap-2 rounded-xl px-6 font-bold transition-colors outline-none focus-visible:ring-3 focus-visible:ring-offset-2",
            theme.primary,
            theme.focus
          )}
        >
          {content.primary.label}
          <ArrowRight
            aria-hidden="true"
            className="size-4 transition-transform group-hover/cta:translate-x-1 motion-reduce:transition-none motion-reduce:group-hover/cta:translate-x-0"
          />
        </Link>
        <Link
          href={content.secondary.href}
          className={cn(
            "rounded-md font-semibold transition-colors outline-none focus-visible:ring-3 focus-visible:ring-offset-2",
            theme.secondary,
            theme.focus
          )}
        >
          {content.secondary.label}
        </Link>
      </div>
    </motion.article>
  );
}

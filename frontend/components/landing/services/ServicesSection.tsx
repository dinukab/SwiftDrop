"use client";

import Image from "next/image";
import Link from "next/link";
import { MotionConfig, motion } from "framer-motion";
import { ArrowRight, Check } from "lucide-react";
import { SERVICES, SERVICES_COPY, type Service } from "./data";

const EASE_OUT = [0.22, 1, 0.36, 1] as const;

function ServiceCard({ service, index }: { service: Service; index: number }) {
  return (
    <motion.article
      initial={{ opacity: 0, y: 24 }}
      whileInView={{ opacity: 1, y: 0 }}
      viewport={{ once: true, margin: "0px 0px -80px 0px" }}
      transition={{ duration: 0.6, delay: 0.08 * index, ease: EASE_OUT }}
      className="flex flex-col rounded-lg border border-line bg-white p-4 shadow-[0_1px_2px_rgb(10_22_48/0.04)]"
    >
      <h3 className="font-display text-xl font-semibold tracking-tight text-navy">{service.title}</h3>

      {/* Fixed 3:2 box, so the card keeps its height while the photo loads. */}
      <div className="relative mt-4 aspect-[3/2] rounded-md bg-surface">
        <Image
          src={service.image.src}
          alt={service.image.alt}
          fill
          sizes="(min-width: 1024px) 290px, (min-width: 640px) 45vw, 100vw"
          className="object-cover"
        />
      </div>

      <p className="mt-4 leading-relaxed text-ink-muted">{service.description}</p>
      <ul className="mt-4 space-y-2">
        {service.points.map((point) => (
          <li key={point} className="flex items-start gap-2.5 text-sm font-medium text-navy">
            <Check aria-hidden="true" className="mt-0.5 size-4 shrink-0 text-status-delivered" strokeWidth={2.5} />
            {point}
          </li>
        ))}
      </ul>

      {/* Pinned to the bottom so the links line up across the row. */}
      {service.cta && (
        <div className="mt-auto pt-5">
          <Link
            href={service.cta.href}
            className="group/link inline-flex items-center gap-2 rounded-lg font-semibold text-brand-text outline-none hover:text-navy focus-visible:ring-3 focus-visible:ring-brand"
          >
            {service.cta.label}
            <ArrowRight
              aria-hidden="true"
              className="size-4 transition-transform group-hover/link:translate-x-0.5 motion-reduce:transition-none"
            />
          </Link>
        </div>
      )}
    </motion.article>
  );
}

export function ServicesSection() {
  return (
    // "user": honours prefers-reduced-motion by dropping the slide and keeping only the fade.
    <MotionConfig reducedMotion="user">
      <section id="services" aria-labelledby="services-heading" className="bg-surface py-20 font-body sm:py-24 lg:py-28">
        <div className="mx-auto max-w-7xl px-4 sm:px-6 lg:px-8">
          <div className="grid gap-6 lg:grid-cols-2 lg:items-end lg:gap-12">
            <div>
              <p className="inline-flex items-center rounded-full bg-brand-tint px-3 py-1 text-xs font-semibold tracking-[0.14em] text-brand-text uppercase">
                {SERVICES_COPY.eyebrow}
              </p>
              <h2
                id="services-heading"
                className="mt-4 font-display text-4xl leading-[1.08] font-bold tracking-tight text-navy sm:text-5xl"
              >
                {SERVICES_COPY.heading}
              </h2>
            </div>
            <p className="text-lg leading-relaxed text-ink-muted">{SERVICES_COPY.intro}</p>
          </div>

          <div className="mt-12 grid gap-5 sm:grid-cols-2 lg:mt-16 lg:grid-cols-4">
            {SERVICES.map((service, index) => (
              <ServiceCard key={service.id} service={service} index={index} />
            ))}
          </div>
        </div>
      </section>
    </MotionConfig>
  );
}

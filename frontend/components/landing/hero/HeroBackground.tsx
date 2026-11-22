"use client";

import Image from "next/image";
import { motion, useTransform, type MotionValue } from "framer-motion";
import { cn } from "cn";
import { BACKGROUND, SCENE_LAYERS, type SceneLayer } from "./data";

type HeroBackgroundProps = {
  progress: MotionValue<number>;
  /** False below the `lg` breakpoint and for reduced motion: everything stays still. */
  animate: boolean;
};

function VehicleLayer({ layer, progress, animate }: { layer: SceneLayer } & HeroBackgroundProps) {
  const x = useTransform(progress, [0, 1], layer.x);
  const y = useTransform(progress, [0, 1], layer.y);
  const rotate = useTransform(progress, layer.rotate?.at ?? [0, 1], layer.rotate?.deg ?? [0, 0]);

  return (
    <motion.div
      className={cn(
        "absolute will-change-transform",
        layer.mobile === "hidden" && "hidden md:block"
      )}
      style={{
        left: layer.left,
        top: layer.top,
        width: layer.width,
        aspectRatio: layer.aspectRatio,
        ...(animate ? { x, y, rotate } : {}),
      }}
    >
      <motion.div
        className="relative size-full"
        animate={animate && layer.bob ? { y: [0, -10, 0] } : undefined}
        transition={animate && layer.bob ? { duration: 3, ease: "easeInOut", repeat: Infinity } : undefined}
      >
        <Image src={layer.src} alt="" fill sizes={layer.width} className="object-contain" />
      </motion.div>
    </motion.div>
  );
}

export function HeroBackground({ progress, animate }: HeroBackgroundProps) {
  const scale = useTransform(progress, [0, 1], BACKGROUND.scale);
  const y = useTransform(progress, [0, 1], BACKGROUND.y);

  return (
    <div aria-hidden="true" className="pointer-events-none absolute inset-0 overflow-hidden">
      <motion.div
        className="absolute inset-0 origin-bottom-right will-change-transform"
        style={animate ? { scale, y } : undefined}
      >
        <Image
          src={BACKGROUND.src}
          alt=""
          fill
          preload
          sizes="100vw"
          className="object-cover object-right"
        />
      </motion.div>

      {SCENE_LAYERS.filter((layer) => layer.enabled).map((layer) => (
        <VehicleLayer key={layer.id} layer={layer} progress={progress} animate={animate} />
      ))}

      {/* Keeps the copy column on solid navy; text contrast depends on it. */}
      {/* The gradient is used to create a subtle effect that makes the text more readable. */}
      <div className="absolute inset-0 bg-linear-to-b from-gray from-55% to-gray/80 lg:bg-linear-to-r lg:from-navy lg:from-30% lg:via-navy/70 lg:via-30% lg:to-transparent lg:to-50% xl:from-10% xl:via-58% xl:to-82%" />
      <div className="absolute inset-x-0 top-0 h-32 bg-linear-to-b from-gray/70 to-transparent" />
    </div>
  );
}

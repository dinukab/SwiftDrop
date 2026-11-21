import {
  BadgeCheck,
  FileCheck2,
  KeyRound,
  MapPinned,
  ShieldCheck,
  UserCheck,
  type LucideIcon,
} from "lucide-react";

/** Scroll progress runs from 0 (hero top at the viewport top) to 1 (hero fully scrolled). */
export type ProgressRange = [number, number];

export const HERO_COPY = {
  headingLead: "Delivered",
  rotatingWords: ["fast.", "safely.", "on time."],
  /** Read once by screen readers instead of the rotating word. */
  headingAccessible: "Delivered fast, safely and on time.",
  tagline: "Trusted at every step.",
  subtitle:
    "Verified couriers, insured parcels and live tracking from pickup to doorstep, so you always know where your parcel is and who's carrying it.",
  scrollHint: "Scroll to follow the parcel",
};

export const WORD_ROTATION_MS = 2400;

export const TRUST_ROW: { icon: LucideIcon; label: string }[] = [
  { icon: ShieldCheck, label: "Insured parcels" },
  { icon: UserCheck, label: "Background-checked couriers" },
  { icon: KeyRound, label: "OTP-secured handover" },
];

export const TRACKING_FORM = {
  label: "Tracking number",
  placeholder: "SD7K3M9Q2XWB",
  submit: "Track parcel",
  errorEmpty: "Enter a tracking number to see where your parcel is.",
  errorFormat:
    "Tracking numbers start with SD followed by 10 letters or numbers, for example SD7K3M9Q2XWB.",
};

/**
 * Mirrors the backend TrackingNumberGenerator: "SD" + 10 Crockford base32 characters
 * (no I, L, O or U). The backend still validates; this only gives faster feedback.
 */
export const TRACKING_NUMBER_PATTERN = /^SD[0-9A-HJKMNP-TV-Z]{10}$/;

export type StageTone = "transit" | "brand" | "delivered";

export const JOURNEY = {
  label: "Shipment",
  trackingNumber: "SD4M8Q2XKB7T",
  from: "Colombo 03",
  to: "Kandy",
  eta: "ETA 4:30 PM",
  /** A stage is shown while progress is below `until`. */
  stages: [
    { until: 0.25, label: "Picked up", tone: "transit" },
    { until: 0.5, label: "Sorted at hub", tone: "transit" },
    { until: 0.8, label: "Out for delivery", tone: "brand" },
    { until: Infinity, label: "Delivered", tone: "delivered" },
  ] satisfies { until: number; label: string; tone: StageTone }[],
  enter: [0.05, 0.25] as ProgressRange,
  bar: [0.05, 0.85] as ProgressRange,
};

export const TRUST_BAR: { icon: LucideIcon; title: string; text: string }[] = [
  { icon: ShieldCheck, title: "Insured parcels", text: "Every parcel covered from pickup to doorstep." },
  { icon: BadgeCheck, title: "Verified couriers", text: "Background-checked riders with ID on every run." },
  { icon: MapPinned, title: "Live GPS tracking", text: "Follow your parcel on the map in real time." },
  { icon: FileCheck2, title: "Proof of delivery", text: "Photo and OTP confirmation at every handover." },
];

/** Timeline of the pinned hero, as scroll-progress ranges. */
export const TIMELINE = {
  copyExit: [0.5, 1] as ProgressRange,
  trustBarEnter: [0.6, 0.88] as ProgressRange,
  hintExit: [0, 0.08] as ProgressRange,
};

export const BACKGROUND = {
  src: "/images/hero/bg.webp",
  scale: [1.06, 1] as [number, number],
  y: ["0%", "8%"] as [string, string],
};

export type SceneLayer = {
  id: "van" | "bike" | "drone";
  src: string;
  /**
   * Turn on once the cut-out PNG exists in public/images/hero/. The current bg.webp already
   * contains the van, bike and drone, so enabling a layer also needs a background without them.
   */
  enabled: boolean;
  /** Width divided by height of the PNG. */
  aspectRatio: number;
  /** Position and size where the vehicle sits in the original artwork. */
  left: string;
  top: string;
  width: string;
  x: [string, string];
  y: [string, string];
  rotate?: { at: number[]; deg: number[] };
  /** Gentle idle hover loop, on top of the scroll movement. */
  bob?: boolean;
  /** Below 768px a layer is either kept still or hidden. */
  mobile: "static" | "hidden";
};

export const SCENE_LAYERS: SceneLayer[] = [
  {
    id: "van",
    src: "/images/hero/van.png",
    enabled: false,
    aspectRatio: 390 / 235,
    left: "48%",
    top: "61%",
    width: "23vw",
    x: ["0vw", "-22vw"],
    y: ["0vh", "11vh"],
    mobile: "static",
  },
  {
    id: "bike",
    src: "/images/hero/bike.png",
    enabled: false,
    aspectRatio: 100 / 135,
    left: "87%",
    top: "61%",
    width: "6vw",
    x: ["0vw", "16vw"],
    y: ["0vh", "9vh"],
    mobile: "hidden",
  },
  {
    id: "drone",
    src: "/images/hero/drone.png",
    enabled: false,
    aspectRatio: 210 / 115,
    left: "83%",
    top: "7%",
    width: "12vw",
    x: ["0vw", "-30vw"],
    y: ["0vh", "-6vh"],
    rotate: { at: [0, 0.5, 1], deg: [0, -6, -3] },
    bob: true,
    mobile: "hidden",
  },
];

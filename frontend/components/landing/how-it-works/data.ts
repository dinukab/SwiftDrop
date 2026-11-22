import { MapPin, MousePointerClick, PackageCheck, Truck, type LucideIcon } from "lucide-react";

/**
 * Copy for the "How it works" section. Like the services copy, every step describes what the
 * system does today. Not implemented, so not claimed: online or cash-on-delivery payment,
 * service tiers, automatic nearest-courier assignment, scan events, GPS, real SMS, OTP and
 * signatures.
 */
export const HOW_IT_WORKS_COPY = {
  eyebrow: "How it works",
  heading: "Send a parcel in four simple steps",
  intro: "No paperwork, no queues. Book online and we handle the rest.",
  primaryCta: { label: "Send a parcel", href: "/customer/new" },
  secondaryCta: { label: "Track a parcel", href: "/track" },
};

export type Step = {
  title: string;
  icon: LucideIcon;
  description: string;
  meta: string;
};

export const STEPS: Step[] = [
  {
    title: "Book online",
    icon: MousePointerClick,
    description:
      "Enter the pickup and drop-off details and the parcel size, and see the delivery fee before you confirm.",
    meta: "Fee shown before you book",
  },
  {
    title: "We pick it up",
    icon: Truck,
    description: "A dispatcher assigns a courier, who collects the parcel from your pickup address.",
    meta: "Pickup from your address",
  },
  {
    title: "Track in real time",
    icon: MapPin,
    description:
      "Follow every status change, from arriving at the hub to out for delivery, with notifications in your account.",
    meta: "Live status updates",
  },
  {
    title: "Delivered",
    icon: PackageCheck,
    description:
      "The courier records the handover with the recipient's name and an optional photo, so you have proof of delivery.",
    meta: "Name + optional photo",
  },
];

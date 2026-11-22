import { Bike, Store, type LucideIcon } from "lucide-react";

/**
 * Copy for the business and courier blocks. Every claim describes what the system does today.
 * Not implemented, so not claimed: CSV bulk upload, cash on delivery and settlements, scheduled
 * pickups, courier self-applications, payouts and working-hour choices. Couriers cannot sign
 * up: an admin creates courier accounts, so the courier block points existing couriers to sign in.
 */
export type CtaVariant = "business" | "courier";

export type CtaContent = {
  variant: CtaVariant;
  eyebrow: string;
  title: string;
  text: string;
  benefits: string[];
  primary: { label: string; href: string };
  secondary: { label: string; href: string };
  /** Large faint outline icon in the corner. */
  decoration: LucideIcon;
  /**
   * Optional photo above the eyebrow. Set it once the file exists in public/images/cta/
   * (16:9 WebP); until then the block shows only the icon decoration.
   */
  photo?: { src: string; alt: string };
};

export const AUDIENCE_CTA_LABEL = "Business and courier sign up";

export const CTA_BLOCKS: CtaContent[] = [
  {
    variant: "business",
    eyebrow: "For business",
    title: "Grow your online store with delivery you can rely on",
    text: "Send orders straight from your store, follow every parcel from one dashboard and get each status change pushed back to you. New business accounts are activated after a quick review.",
    benefits: [
      "Create orders through our REST API with API keys",
      "Webhook updates on every status change",
      "Hold or cancel orders from your dashboard",
    ],
    primary: { label: "Open a business account", href: "/signup?type=merchant" },
    secondary: { label: "Business sign in", href: "/login" },
    decoration: Store,
  },
  {
    variant: "courier",
    eyebrow: "Ride with us",
    title: "Deliver with the SwiftDrop courier app",
    text: "Everything a courier needs on the road is in one app, from the day's assigned parcels to proof of delivery at the door. Courier accounts are set up by our operations team.",
    benefits: [
      "See the parcels assigned to you",
      "Update each parcel's status from pickup to delivery",
      "Record the recipient's name and a photo at handover",
    ],
    primary: { label: "Courier sign in", href: "/login" },
    secondary: { label: "How deliveries work", href: "/#how-it-works" },
    decoration: Bike,
  },
];

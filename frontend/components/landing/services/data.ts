/**
 * Copy for the landing-page services section. Every claim describes a feature the system
 * implements today; check the backend before adding one. Not implemented, so not claimed:
 * cash on delivery, express or same-day tiers, courier background checks, OTP handover,
 * signatures, insurance, GPS and scan-based tracking.
 */
export const SERVICES_COPY = {
  eyebrow: "Services",
  heading: "One network for every parcel you send",
  intro:
    "Sending a single parcel or shipping your store's orders every day, SwiftDrop handles pickup, hub transfers and doorstep delivery on one tracked system.",
};

export type Service = {
  id: string;
  title: string;
  /** Photo shown under the title; files live in public/images/services/ (960x640 WebP). */
  image: { src: string; alt: string };
  description: string;
  points: string[];
  cta?: { label: string; href: string };
};

export const SERVICES: Service[] = [
  {
    id: "door-to-door",
    title: "Door-to-door delivery",
    image: {
      src: "/images/services/door-to-door.webp",
      alt: "A SwiftDrop courier handing a parcel to a smiling customer at her front door.",
    },
    description:
      "Book a delivery online and a SwiftDrop courier collects the parcel from your pickup address and delivers it to your recipient.",
    points: [
      "See the delivery fee before you book",
      "Pickup from the address you enter",
      "Small, medium and large parcels, up to 30 kg",
    ],
    cta: { label: "Book a pickup", href: "/customer/new" },
  },
  {
    id: "hub-network",
    title: "Hub network",
    image: {
      src: "/images/services/hub-network.webp",
      alt: "Staff sorting parcels on conveyor belts inside a delivery hub, with vans loading at the bays.",
    },
    description:
      "Parcels travelling further move between hubs, then a local courier makes the final delivery.",
    points: ["Fees set by pickup and drop-off zone", "Hub-to-hub transfers managed by dispatchers"],
  },
  {
    id: "tracking",
    title: "Real-time tracking",
    image: {
      src: "/images/services/tracking.webp",
      alt: "A customer at a seaside cafe following her parcel's progress on her phone.",
    },
    description: "Every status change shows up the moment it happens. All you need is the tracking number.",
    points: ["No account needed", "Updates live, no need to refresh"],
    cta: { label: "Track a parcel", href: "/track" },
  },
  {
    id: "proof",
    title: "Proof of delivery",
    image: {
      src: "/images/services/proof-of-delivery.webp",
      alt: "A recipient confirming a delivery on the courier's handheld device while holding her parcel.",
    },
    description:
      "Couriers record each handover in the app, so there is a record of when and to whom a parcel was delivered.",
    points: ["Recipient name recorded at handover", "Optional doorstep photo"],
  },
];

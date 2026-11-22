/**
 * Footer copy and links. Every link points at a page or section that exists today. Not included
 * until they exist: contact details (no phone, email or address is defined), Pricing and Contact
 * sections, privacy and terms pages, and social media accounts.
 */
export const FOOTER_COPY = {
  tagline: "Parcel pickup, hub transfers and doorstep delivery, tracked from booking to handover.",
  trackingHeading: "Track a parcel",
  brand: "SwiftDrop",
  rights: "All rights reserved.",
  backToTop: "Back to top",
  staffPortal: { label: "Staff portal", href: "/staff" },
};

export type FooterColumn = {
  title: string;
  links: { label: string; href: string }[];
};

export const FOOTER_COLUMNS: FooterColumn[] = [
  {
    title: "Send",
    links: [
      { label: "Book a pickup", href: "/customer/new" },
      { label: "Track a parcel", href: "/track" },
      { label: "Services", href: "/#services" },
      { label: "How it works", href: "/#how-it-works" },
    ],
  },
  {
    title: "Business",
    links: [
      { label: "Open a business account", href: "/signup?type=merchant" },
      { label: "Business sign in", href: "/login" },
      { label: "For couriers", href: "/#business" },
    ],
  },
  {
    title: "Account",
    links: [
      { label: "Log in", href: "/login" },
      { label: "Create an account", href: "/signup" },
      { label: "My deliveries", href: "/customer" },
    ],
  },
];

import type { AccountType } from "../AccountTabs";

/**
 * Copy and images for the sign-in page. The tab only changes this presentation; the account's
 * role always comes from the backend after sign-in. Not offered because the system has no
 * support for them yet: password reset, and staying signed in beyond the browser session.
 */
export type LoginViewKey = AccountType | "staff";

export type LoginView = {
  image: string;
  tagline: string;
  description: string;
  /** Sign-up prompt under the form; staff accounts are created by an administrator instead. */
  signup?: { prompt: string; label: string; href: string };
};

export const LOGIN_VIEWS: Record<LoginViewKey, LoginView> = {
  customer: {
    image: "/images/auth/customer-login.webp",
    tagline: "Your parcels, tracked from door to door.",
    description: "Sign in to book pickups and follow your parcels.",
    signup: { prompt: "New to SwiftDrop?", label: "Create a customer account", href: "/signup?type=customer" },
  },
  merchant: {
    image: "/images/auth/merchant-login.webp",
    tagline: "Ship more orders. Worry less.",
    description: "Sign in to manage your store's orders and API keys.",
    signup: { prompt: "New to SwiftDrop?", label: "Create a merchant account", href: "/signup?type=merchant" },
  },
  staff: {
    image: "/images/auth/customer-login.webp",
    tagline: "Every parcel, from pickup to handover.",
    description: "Sign in with your staff account to open your portal.",
  },
};

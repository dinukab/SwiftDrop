import type { AccountType } from "../AccountTabs";

/** Copy and images for the sign-up page, per account type. */
export type SignupView = {
  image: string;
  tagline: string;
  title: string;
  description: string;
  submit: string;
};

export const SIGNUP_VIEWS: Record<AccountType, SignupView> = {
  customer: {
    image: "/images/auth/customer-login.webp",
    tagline: "Book a pickup. Follow it to the door.",
    title: "Create your account",
    description: "Book parcel pickups, see the delivery fee before you confirm and follow every delivery.",
    submit: "Create customer account",
  },
  merchant: {
    image: "/images/auth/merchant-login.webp",
    tagline: "Ship more orders. Worry less.",
    title: "Open a business account",
    description: "Register your store. Your account is activated after an administrator approves your application.",
    submit: "Submit merchant application",
  },
};

/** Shown after a merchant application is submitted; merchant accounts start as pending approval. */
export const MERCHANT_PENDING = {
  image: "/images/auth/merchant-login.webp",
  tagline: "Ship more orders. Worry less.",
  eyebrow: "Pending admin approval",
  title: "Application received",
  description:
    "Your merchant registration has been submitted. Your account will be activated once an administrator approves your application.",
};

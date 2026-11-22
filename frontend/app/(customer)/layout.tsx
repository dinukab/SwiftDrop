import React from "react";
import { PortalShell, type PortalNavItem } from "@/components/portal/portal-shell";
import { CustomerChatProvider } from "@/components/customer/assistant/chat-context";

const CUSTOMER_NAV: PortalNavItem[] = [
  { href: "/customer", label: "Overview", exact: true },
  { href: "/customer/new", label: "Book Delivery", exact: true },
  { href: "/customer/history", label: "Delivery History", exact: true },
  { href: "/customer/assistant", label: "Assistant", exact: true },
];

export default function CustomerPortalLayout({ children }: { children: React.ReactNode }) {
  return (
    <PortalShell
      portalName="Customer Portal"
      role="CUSTOMER"
      workspaceBadge="Customer Workspace · WS2"
      navItems={CUSTOMER_NAV}
    >
      <CustomerChatProvider>{children}</CustomerChatProvider>
    </PortalShell>
  );
}

import React from "react";
import { PortalShell, type PortalNavItem } from "@/components/portal/portal-shell";
import { CourierApprovalGuard } from "@/components/portal/courier-approval-guard";

const COURIER_NAV: PortalNavItem[] = [
  { href: "/courier", label: "Courier Runs", exact: true },
  { href: "/courier/proof", label: "Proof of Delivery", exact: true },
];

export default function CourierPortalLayout({ children }: { children: React.ReactNode }) {
  return (
    <PortalShell
      portalName="Courier Portal"
      role="COURIER"
      workspaceBadge="Courier Workspace · WS6"
      navItems={COURIER_NAV}
    >
      <CourierApprovalGuard>{children}</CourierApprovalGuard>
    </PortalShell>
  );
}

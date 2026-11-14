import React from "react";
import { PortalShell } from "@/components/portal/portal-shell";
import { DISPATCHER_NAV } from "@/components/portal/nav";

export default function DispatchPortalLayout({ children }: { children: React.ReactNode }) {
  return (
    <PortalShell
      portalName="Dispatcher Portal"
      role="DISPATCHER"
      workspaceBadge="Delivery Operations Workspace"
      navItems={DISPATCHER_NAV}
    >
      {children}
    </PortalShell>
  );
}

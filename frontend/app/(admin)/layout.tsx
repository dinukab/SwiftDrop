import React from "react";
import { PortalShell } from "@/components/portal/portal-shell";
import { ADMIN_NAV } from "@/components/portal/nav";

export default function AdminPortalLayout({ children }: { children: React.ReactNode }) {
  return (
    <PortalShell
      portalName="Admin Portal"
      role="ADMIN"
      workspaceBadge="Administration Workspace"
      navItems={ADMIN_NAV}
    >
      {children}
    </PortalShell>
  );
}

/**
 * Navigation configuration shared by portal layouts (server components) and the client
 * PortalShell. Kept in a plain module — data exports from a "use client" file do not cross
 * the server/client boundary and would arrive as undefined.
 */
export interface PortalNavItem {
  href: string;
  label: string;
  badge?: string;
  exact?: boolean;
}

export const DISPATCHER_NAV: PortalNavItem[] = [
  { href: "/dispatcher", label: "Dispatch Fleet", exact: true },
];

export const ADMIN_NAV: PortalNavItem[] = [
  { href: "/admin", label: "Overview", exact: true },
  { href: "/admin/users", label: "User Directory", exact: true },
  { href: "/admin/pricing", label: "Pricing & Zones", exact: true },
  { href: "/admin/reports", label: "Reports", exact: true },
];

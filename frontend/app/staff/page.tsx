import type { Metadata } from "next";
import Link from "next/link";
import { buttonVariants } from "@/components/ui/button";
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card";
import { SiteNavbar } from "@/components/landing/site-navbar";

export const metadata: Metadata = {
  title: "Staff portal | SwiftDrop",
  description: "Sign-in portals for SwiftDrop couriers, dispatchers and administrators.",
};

/**
 * Each portal's role guard sends signed-out visitors to /login?redirect=<portal> and back again,
 * so these links double as the sign-in entry points. Descriptions match the pages each portal has.
 */
const STAFF_PORTALS = [
  {
    title: "Courier Portal",
    role: "COURIER",
    description: "Your assigned parcels, status updates and proof of delivery.",
    href: "/courier",
    action: "Access Courier Portal",
  },
  {
    title: "Dispatcher Portal",
    role: "DISPATCHER",
    description: "Assign couriers to parcels and manage hub-to-hub transfers.",
    href: "/dispatcher",
    action: "Access Dispatch Portal",
  },
  {
    title: "Admin Portal",
    role: "ADMIN",
    description: "Users and staff accounts, delivery zones, pricing and reports.",
    href: "/admin",
    action: "Access Admin Portal",
  },
];

export default function StaffPortalPage() {
  return (
    <>
    <SiteNavbar />
    <main className="container mx-auto flex-1 px-4 py-12 md:py-16 space-y-10 max-w-6xl">
      <section aria-labelledby="staff-portal-heading" className="text-center space-y-2 max-w-2xl mx-auto">
        <div className="inline-flex items-center gap-1.5 text-xs font-semibold uppercase tracking-wider text-primary">
          <span className="size-1.5 rounded-full bg-primary" />
          SwiftDrop staff
        </div>
        <h1 id="staff-portal-heading" className="text-3xl font-bold tracking-tight text-foreground sm:text-4xl">
          Staff portal
        </h1>
        <p className="text-muted-foreground">
          Choose your portal and sign in with your staff account. Staff accounts are created by an
          administrator.
        </p>
      </section>

      <ul className="grid grid-cols-1 md:grid-cols-3 gap-6">
        {STAFF_PORTALS.map((portal) => (
          <li key={portal.href} className="flex">
            <Card className="flex w-full flex-col justify-between rounded-xl bg-card border-border shadow-xs hover:border-primary/40 hover:shadow-sm transition-all duration-200">
              <CardHeader>
                <div className="flex items-center justify-between gap-2">
                  <CardTitle className="text-base font-bold text-foreground">
                    <h2>{portal.title}</h2>
                  </CardTitle>
                  <span className="text-[10px] font-semibold px-2 py-0.5 rounded-full bg-accent text-primary border border-primary/20 uppercase tracking-wider">
                    {portal.role}
                  </span>
                </div>
                <CardDescription className="text-sm text-muted-foreground">{portal.description}</CardDescription>
              </CardHeader>
              <CardContent className="pt-0">
                <Link
                  href={portal.href}
                  className={
                    buttonVariants({ variant: "outline", size: "sm" }) +
                    " w-full font-medium hover:border-primary hover:text-primary transition-colors"
                  }
                >
                  {portal.action}
                </Link>
              </CardContent>
            </Card>
          </li>
        ))}
      </ul>

      <p className="text-center text-sm text-muted-foreground">
        Not staff?{" "}
        <Link href="/login" className="font-semibold text-primary underline-offset-4 hover:underline">
          Customer and business sign in
        </Link>
      </p>
    </main>
    </>
  );
}

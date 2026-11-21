import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card";
import { CheckCircle2, Hammer, Server, Users, Store, MapPin, BarChart3 } from "lucide-react";

const implementedAreas = [
  {
    icon: Users,
    title: "User Management",
    description: "Directory of all accounts with role filtering, staff account creation (COURIER, DISPATCHER, ADMIN), and enable/disable controls.",
    endpoints: [
      "GET /api/v1/users",
      "POST /api/v1/users/staff",
      "POST /api/v1/staff-users",
      "PATCH /api/v1/users/{id}",
    ],
  },
  {
    icon: Store,
    title: "Merchant Approvals",
    description: "Review pending merchant registrations and approve or reject them with a reason.",
    endpoints: [
      "GET /api/v1/merchants",
      "POST /api/v1/merchants/{id}/approval",
      "POST /api/v1/merchants/{id}/rejection",
    ],
  },
  {
    icon: MapPin,
    title: "Delivery Zones",
    description: "Read access to configured delivery zones. Zone and pricing-rule authoring is still being implemented.",
    endpoints: ["GET /api/v1/zones"],
  },
];

const plannedAreas = [
  {
    icon: Hammer,
    title: "Pricing Rule Management",
    description: "Create and update pricing rules and zone definitions.",
    endpoints: ["POST /api/v1/zones", "POST/PUT /api/v1/pricing-rules (planned)"],
  },
  {
    icon: BarChart3,
    title: "Operational Reports",
    description: "Daily delivery volumes, outcome breakdowns, and courier performance analytics.",
    endpoints: ["GET /api/v1/reports/daily-deliveries (planned)"],
  },
];

export default function AdminPortalPage() {
  return (
    <div className="space-y-6">
      <div>
        <h2 className="text-xl font-bold text-foreground">Administration Overview</h2>
        <p className="text-sm text-muted-foreground mt-1">
          System administration: user accounts, staff onboarding, merchant approvals, zones, pricing, and reports.
        </p>
      </div>

      <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
        {implementedAreas.map((area) => (
          <Card key={area.title} className="rounded-xl border-border bg-card shadow-xs">
            <CardHeader className="space-y-2">
              <div className="flex items-center gap-2">
                <area.icon className="size-4 text-primary" aria-hidden="true" />
                <CardTitle className="text-base font-semibold">{area.title}</CardTitle>
                <span className="ml-auto inline-flex items-center gap-1 text-[10px] font-semibold uppercase tracking-wider text-primary">
                  <CheckCircle2 className="size-3" aria-hidden="true" /> Implemented
                </span>
              </div>
              <CardDescription className="text-xs">{area.description}</CardDescription>
            </CardHeader>
            <CardContent className="pt-0">
              <div className="flex flex-wrap gap-1.5">
                {area.endpoints.map((endpoint) => (
                  <code
                    key={endpoint}
                    className="rounded bg-muted px-2 py-1 text-[11px] font-mono text-foreground border border-border"
                  >
                    {endpoint}
                  </code>
                ))}
              </div>
            </CardContent>
          </Card>
        ))}
      </div>

      <div>
        <h3 className="text-sm font-semibold text-muted-foreground uppercase tracking-wider mb-3">
          Planned — Not Yet Available
        </h3>
        <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
          {plannedAreas.map((area) => (
            <Card key={area.title} className="rounded-xl border-dashed border-border bg-muted/30">
              <CardHeader className="space-y-2">
                <div className="flex items-center gap-2">
                  <area.icon className="size-4 text-muted-foreground" aria-hidden="true" />
                  <CardTitle className="text-base font-semibold text-muted-foreground">{area.title}</CardTitle>
                  <span className="ml-auto inline-flex items-center gap-1 text-[10px] font-semibold uppercase tracking-wider text-muted-foreground">
                    <Hammer className="size-3" aria-hidden="true" /> Planned
                  </span>
                </div>
                <CardDescription className="text-xs">{area.description}</CardDescription>
              </CardHeader>
              <CardContent className="pt-0">
                <div className="flex flex-wrap gap-1.5">
                  {area.endpoints.map((endpoint) => (
                    <code
                      key={endpoint}
                      className="rounded bg-muted px-2 py-1 text-[11px] font-mono text-muted-foreground border border-border"
                    >
                      <Server className="inline size-3 mr-1 -mt-0.5" aria-hidden="true" />
                      {endpoint}
                    </code>
                  ))}
                </div>
              </CardContent>
            </Card>
          ))}
        </div>
      </div>
    </div>
  );
}

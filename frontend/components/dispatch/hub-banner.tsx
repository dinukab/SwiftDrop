import React from "react";
import { AlertCircle, Building2 } from "lucide-react";
import type { Hub } from "@/lib/dispatch";

/**
 * Shows which hub the signed-in dispatcher works for. `hub` is `undefined` while it loads and `null`
 * when an admin has not assigned one; without a hub the queue and assignment are refused by the server.
 */
export function HubBanner({ hub }: { hub: Hub | null | undefined }) {
  if (hub === undefined) {
    return (
      <div role="status" className="h-10 w-64 animate-pulse rounded-lg bg-muted" aria-label="Loading your hub" />
    );
  }

  if (hub === null) {
    return (
      <div
        role="alert"
        className="flex items-start gap-3 rounded-lg border border-amber-300 bg-amber-50 p-4 text-amber-900"
      >
        <AlertCircle className="mt-0.5 h-5 w-5 flex-shrink-0" />
        <div className="space-y-1 text-sm">
          <p className="font-medium">You are not assigned to a hub yet</p>
          <p>
            The parcel queue is limited to your hub, so it stays empty until an administrator assigns you one.
            Ask an administrator, then reload this page.
          </p>
        </div>
      </div>
    );
  }

  return (
    <div className="inline-flex items-center gap-2 rounded-lg border bg-muted/40 px-3 py-2 text-sm">
      <Building2 className="h-4 w-4 text-muted-foreground" />
      <span className="font-medium">{hub.name}</span>
      {hub.main && (
        <span className="rounded-full bg-primary/10 px-2 py-0.5 text-xs font-medium text-primary">Main hub</span>
      )}
    </div>
  );
}

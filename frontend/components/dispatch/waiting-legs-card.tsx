"use client";

import React, { useEffect, useState } from "react";
import { AlertCircle, ChevronLeft, ChevronRight, History, Loader2, PackageCheck } from "lucide-react";
import { formatApiError } from "@/lib/api";
import {
  assignLegCourier,
  getWaitingLegs,
  legName,
  provinceName,
  type CourierWorkload,
  type WaitingLeg,
} from "@/lib/dispatch";
import { Button } from "@/components/ui/button";
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card";
import { AssignCourierDialog } from "@/components/dispatch/assign-courier-dialog";
import { AssignmentHistoryDialog } from "@/components/dispatch/assignment-history-dialog";

const PAGE_SIZE = 10;

/**
 * Parcels that have arrived at the dispatcher's hub and wait for their next leg: hub to hub, or the final
 * delivery. The server lists only legs that start at this hub and whose previous leg is complete, and
 * refuses everything else, so this card only shows and sends what the dispatcher chooses.
 */
export function WaitingLegsCard({
  hubName,
  couriers,
  refreshKey,
  onAssigned,
}: {
  hubName: string;
  couriers: CourierWorkload[];
  /** Changes when the dispatcher refreshes the board, so this card reloads with it. */
  refreshKey: number;
  onAssigned: (message: string) => void;
}) {
  const [legs, setLegs] = useState<WaitingLeg[]>([]);
  const [page, setPage] = useState(0);
  const [totalPages, setTotalPages] = useState(0);
  const [totalItems, setTotalItems] = useState(0);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [reloads, setReloads] = useState(0);
  const [assigning, setAssigning] = useState<WaitingLeg | null>(null);
  const [historyFor, setHistoryFor] = useState<WaitingLeg | null>(null);

  // State is only set inside the promise callbacks, after the fetch has completed.
  useEffect(() => {
    let current = true;
    getWaitingLegs(page, PAGE_SIZE)
      .then((result) => {
        if (!current) return;
        setLegs(result.items);
        setTotalPages(result.totalPages);
        setTotalItems(result.totalItems);
        setError(null);
      })
      .catch((err: unknown) => {
        if (current) setError(formatApiError(err));
      })
      .finally(() => {
        if (current) setLoading(false);
      });
    return () => {
      current = false;
    };
  }, [page, refreshKey, reloads]);

  return (
    <Card>
      <CardHeader>
        <CardTitle>Waiting for the next leg</CardTitle>
        <CardDescription>
          {totalItems} parcel{totalItems === 1 ? "" : "s"} at {hubName} hub waiting to continue its journey
        </CardDescription>
      </CardHeader>
      <CardContent>
        {error && (
          <div role="alert" className="mb-4 flex items-start gap-2 rounded-md bg-destructive/10 px-3 py-2 text-sm text-destructive">
            <AlertCircle className="mt-0.5 h-4 w-4 flex-shrink-0" />
            <span>{error}</span>
          </div>
        )}

        {loading && legs.length === 0 && (
          <div role="status" className="flex justify-center py-8">
            <Loader2 className="h-6 w-6 animate-spin text-muted-foreground" />
            <span className="sr-only">Loading parcels waiting at the hub</span>
          </div>
        )}

        {!loading && legs.length === 0 && !error && (
          <div className="space-y-2 py-8 text-center">
            <PackageCheck className="mx-auto h-10 w-10 text-muted-foreground" />
            <p className="text-sm font-medium">No parcels are waiting at your hub</p>
            <p className="text-xs text-muted-foreground">
              A parcel appears here once its courier confirms arrival at {hubName} hub.
            </p>
          </div>
        )}

        {legs.length > 0 && (
          <div className="space-y-4">
            <div className="overflow-x-auto rounded-lg border">
              <table className="w-full text-left text-sm">
                <thead className="bg-muted/50 text-xs uppercase text-muted-foreground">
                  <tr>
                    <th className="px-4 py-3">Tracking</th>
                    <th className="px-4 py-3">Next leg</th>
                    <th className="px-4 py-3">Size / Weight</th>
                    <th className="px-4 py-3"><span className="sr-only">Actions</span></th>
                  </tr>
                </thead>
                <tbody className="divide-y">
                  {legs.map((leg) => (
                    <tr key={leg.legId} className="hover:bg-muted/30">
                      <td className="px-4 py-3 font-mono font-medium">{leg.trackingNumber}</td>
                      <td className="whitespace-nowrap px-4 py-3 text-muted-foreground">
                        {legName(leg.legType)}
                        {leg.legType === "FINAL_MILE"
                          ? ""
                          : `: ${provinceName(leg.sourceHubCode)} → ${provinceName(leg.destinationHubCode)}`}
                        <span className="ml-2 text-xs">(leg {leg.sequence})</span>
                      </td>
                      <td className="px-4 py-3 text-muted-foreground">
                        {leg.size} ({(leg.weightGrams / 1000).toFixed(1)} kg)
                      </td>
                      <td className="whitespace-nowrap px-4 py-3 text-right">
                        <Button
                          size="xs"
                          variant="ghost"
                          onClick={() => setHistoryFor(leg)}
                          aria-label={`Assignment history of ${leg.trackingNumber}`}
                        >
                          <History className="h-3 w-3" />
                        </Button>{" "}
                        <Button size="xs" variant="outline" onClick={() => setAssigning(leg)} disabled={couriers.length === 0}>
                          Assign
                        </Button>
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>

            <div className="flex items-center justify-between text-xs text-muted-foreground">
              <span>Page {page + 1} of {Math.max(1, totalPages)}</span>
              <div className="flex gap-2">
                <Button variant="outline" size="sm" disabled={page === 0 || loading} onClick={() => setPage((p) => Math.max(0, p - 1))}>
                  <ChevronLeft className="mr-1 h-4 w-4" />
                  Previous
                </Button>
                <Button variant="outline" size="sm" disabled={page + 1 >= totalPages || loading} onClick={() => setPage((p) => p + 1)}>
                  Next
                  <ChevronRight className="ml-1 h-4 w-4" />
                </Button>
              </div>
            </div>
          </div>
        )}
      </CardContent>

      {assigning && (
        <AssignCourierDialog
          title="Assign a courier to the next leg"
          subject={`${assigning.trackingNumber} · ${legName(assigning.legType)}`}
          couriers={couriers}
          assign={(courierUserId) => assignLegCourier(assigning.parcelId, assigning.legId, courierUserId)}
          onClose={() => setAssigning(null)}
          onAssigned={(courier) => {
            const leg = assigning;
            setAssigning(null);
            setReloads((n) => n + 1);
            onAssigned(`${leg.trackingNumber} (${legName(leg.legType).toLowerCase()}) assigned to ${courier.name}.`);
          }}
        />
      )}

      {historyFor && (
        <AssignmentHistoryDialog
          parcelId={historyFor.parcelId}
          trackingNumber={historyFor.trackingNumber}
          onClose={() => setHistoryFor(null)}
        />
      )}
    </Card>
  );
}

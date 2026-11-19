"use client";

import React, { useEffect, useState } from "react";
import { AlertCircle, CheckCircle2, ChevronLeft, ChevronRight, History, Loader2, RefreshCw, Truck } from "lucide-react";
import { formatApiError, type PageResponse } from "@/lib/api";
import {
  assignCourier,
  getCourierWorkloads,
  getMyHub,
  getUnassignedParcels,
  provinceName,
  type CourierWorkload,
  type Hub,
  type UnassignedParcel,
} from "@/lib/dispatch";
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card";
import { Button } from "@/components/ui/button";
import { HubBanner } from "@/components/dispatch/hub-banner";
import { AssignmentHistoryDialog } from "@/components/dispatch/assignment-history-dialog";
import { AssignCourierDialog } from "@/components/dispatch/assign-courier-dialog";
import { WaitingLegsCard } from "@/components/dispatch/waiting-legs-card";
import { HubTransfersCard } from "@/components/dispatch/hub-transfers-card";

const PAGE_SIZE = 10;

interface Board {
  queue: PageResponse<UnassignedParcel>;
  couriers: CourierWorkload[];
}

/** The queue for one page and the courier loads, fetched together. */
function fetchBoard(page: number): Promise<Board> {
  return Promise.all([getUnassignedParcels(page, PAGE_SIZE), getCourierWorkloads()]).then(([queue, couriers]) => ({
    queue,
    couriers,
  }));
}

export default function DispatcherPage() {
  const [parcels, setParcels] = useState<UnassignedParcel[]>([]);
  const [page, setPage] = useState(0);
  const [totalPages, setTotalPages] = useState(0);
  const [totalItems, setTotalItems] = useState(0);
  const [workloads, setWorkloads] = useState<CourierWorkload[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [notice, setNotice] = useState<string | null>(null);
  const [assigning, setAssigning] = useState<UnassignedParcel | null>(null);
  const [historyFor, setHistoryFor] = useState<UnassignedParcel | null>(null);
  // undefined while loading, null when an admin has not assigned a hub yet
  const [hub, setHub] = useState<Hub | null | undefined>(undefined);
  // Bumped by Refresh so the waiting-legs card reloads together with the queue
  const [refreshKey, setRefreshKey] = useState(0);

  /** Applies one loaded board to the screen. Called only after the fetch has completed. */
  const apply = (board: Board) => {
    setParcels(board.queue.items);
    setTotalPages(board.queue.totalPages);
    setTotalItems(board.queue.totalItems);
    setWorkloads(board.couriers);
    setError(null);
  };

  // The dispatcher's own hub comes first: the queue is limited to it, so there is nothing to load without one.
  useEffect(() => {
    let current = true;
    getMyHub()
      .then((result) => {
        if (!current) return;
        setHub(result.hub);
        if (!result.hub) setLoading(false);
      })
      .catch((err: unknown) => {
        if (!current) return;
        setError(formatApiError(err));
        setHub(null);
        setLoading(false);
      });
    return () => {
      current = false;
    };
  }, []);

  // Reload whenever the page changes (once the hub is known). State is only set inside the promise callbacks.
  useEffect(() => {
    if (!hub) return;
    let current = true;
    fetchBoard(page)
      .then((board) => {
        if (current) apply(board);
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
  }, [page, hub]);

  /** Reloads on demand (after the Refresh button or an assignment), from an event handler. */
  const reload = () => {
    if (!hub) return;
    setLoading(true);
    fetchBoard(page)
      .then(apply)
      .catch((err: unknown) => setError(formatApiError(err)))
      .finally(() => setLoading(false));
  };

  const refresh = () => {
    setNotice(null);
    setRefreshKey((n) => n + 1);
    reload();
  };

  const onAssigned = (parcel: UnassignedParcel, courier: CourierWorkload) => {
    setAssigning(null);
    setNotice(`${parcel.trackingNumber} assigned to ${courier.name}.`);
    reload();
  };

  return (
    <div className="space-y-6">
      <div className="flex flex-col sm:flex-row justify-between items-start sm:items-center gap-4">
        <div>
          <h1 className="text-2xl font-bold tracking-tight">Dispatch</h1>
          <p className="text-muted-foreground text-sm">
            Assign couriers to parcels waiting for pickup and for final delivery. Parcels move between hubs without a
            courier: send them on and confirm what arrives. A parcel is held or cancelled by its merchant, and drops
            out of the pickup queue automatically.
          </p>
        </div>
        <Button variant="outline" size="sm" onClick={refresh} disabled={loading || !hub}>
          <RefreshCw className={`w-4 h-4 mr-2 ${loading ? "animate-spin" : ""}`} />
          Refresh
        </Button>
      </div>

      <HubBanner hub={hub} />

      {error && (
        <div role="alert" className="p-4 bg-destructive/10 border border-destructive/20 text-destructive rounded-lg flex items-center gap-3">
          <AlertCircle className="w-5 h-5 flex-shrink-0" />
          <p className="text-sm">{error}</p>
        </div>
      )}
      {notice && (
        <div role="status" className="p-4 bg-emerald-50 border border-emerald-200 text-emerald-800 rounded-lg flex items-center gap-3">
          <CheckCircle2 className="w-5 h-5 flex-shrink-0" />
          <p className="text-sm">{notice}</p>
        </div>
      )}

      {hub && (
      <div className="grid gap-6 lg:grid-cols-3">
        <Card className="lg:col-span-2">
          <CardHeader>
            <CardTitle>Waiting for a courier</CardTitle>
            <CardDescription>
              {totalItems} parcel{totalItems === 1 ? "" : "s"} to pick up in {provinceName(hub.code)}, oldest first
            </CardDescription>
          </CardHeader>
          <CardContent>
            {loading && parcels.length === 0 && (
              <div className="flex justify-center py-12">
                <Loader2 className="w-8 h-8 animate-spin text-muted-foreground" />
              </div>
            )}

            {!loading && parcels.length === 0 && !error && (
              <div className="text-center py-12 space-y-2">
                <Truck className="w-10 h-10 text-muted-foreground mx-auto" />
                <p className="text-sm font-medium">Nothing waiting for pickup in your hub</p>
                <p className="text-xs text-muted-foreground">New bookings from {provinceName(hub.code)} appear here.</p>
              </div>
            )}

            {parcels.length > 0 && (
              <div className="space-y-4">
                <div className="overflow-x-auto border rounded-lg">
                  <table className="w-full text-sm text-left">
                    <thead className="bg-muted/50 text-xs text-muted-foreground uppercase">
                      <tr>
                        <th className="px-4 py-3">Tracking</th>
                        <th className="px-4 py-3">Source</th>
                        <th className="px-4 py-3">Route</th>
                        <th className="px-4 py-3">Size / Weight</th>
                        <th className="px-4 py-3">Created</th>
                        <th className="px-4 py-3"><span className="sr-only">Actions</span></th>
                      </tr>
                    </thead>
                    <tbody className="divide-y">
                      {parcels.map((parcel) => (
                        <tr key={parcel.id} className="hover:bg-muted/30">
                          <td className="px-4 py-3 font-mono font-medium">{parcel.trackingNumber}</td>
                          <td className="px-4 py-3 text-muted-foreground">
                            {parcel.source === "MERCHANT"
                              ? `Store · ${parcel.merchantOrderReference ?? ""}`
                              : "Customer"}
                          </td>
                          <td className="px-4 py-3 text-muted-foreground whitespace-nowrap">
                            {provinceName(parcel.pickupProvinceCode)} → {provinceName(parcel.dropoffProvinceCode)}
                          </td>
                          <td className="px-4 py-3 text-muted-foreground">
                            {parcel.size} ({(parcel.weightGrams / 1000).toFixed(1)} kg)
                          </td>
                          <td className="px-4 py-3 text-muted-foreground text-xs">
                            {new Date(parcel.createdAt).toLocaleString()}
                          </td>
                          <td className="px-4 py-3 text-right whitespace-nowrap">
                            <Button
                              size="xs"
                              variant="ghost"
                              onClick={() => setHistoryFor(parcel)}
                              aria-label={`Assignment history of ${parcel.trackingNumber}`}
                            >
                              <History className="w-3 h-3" />
                            </Button>{" "}
                            <Button
                              size="xs"
                              variant="outline"
                              onClick={() => setAssigning(parcel)}
                              disabled={workloads.length === 0}
                            >
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
                      <ChevronLeft className="w-4 h-4 mr-1" />
                      Previous
                    </Button>
                    <Button variant="outline" size="sm" disabled={page + 1 >= totalPages || loading} onClick={() => setPage((p) => p + 1)}>
                      Next
                      <ChevronRight className="w-4 h-4 ml-1" />
                    </Button>
                  </div>
                </div>
              </div>
            )}
          </CardContent>
        </Card>

        <Card>
          <CardHeader>
            <CardTitle>Courier workload</CardTitle>
            <CardDescription>Least busy first</CardDescription>
          </CardHeader>
          <CardContent>
            {workloads.length === 0 && !loading && (
              <p className="text-sm text-muted-foreground">No active couriers yet.</p>
            )}
            <ul className="space-y-2">
              {workloads.map((courier) => (
                <li key={courier.courierUserId} className="flex items-center justify-between rounded-lg border px-3 py-2">
                  <div>
                    <p className="text-sm font-medium">{courier.name}</p>
                    <p className="text-xs text-muted-foreground">{courier.vehicleType.toLowerCase()}</p>
                  </div>
                  <span className="text-sm font-semibold tabular-nums">{courier.activeParcels}</span>
                </li>
              ))}
            </ul>
          </CardContent>
        </Card>
      </div>
      )}

      {hub && (
        <HubTransfersCard
          hubName={provinceName(hub.code)}
          refreshKey={refreshKey}
          onDone={(message) => {
            setNotice(message);
            // A received parcel may now wait for a delivery courier here, so every list reloads
            setRefreshKey((n) => n + 1);
          }}
        />
      )}

      {hub && (
        <WaitingLegsCard
          hubName={provinceName(hub.code)}
          couriers={workloads}
          refreshKey={refreshKey}
          onAssigned={(message) => {
            setNotice(message);
            reload();
          }}
        />
      )}

      {assigning && (
        <AssignCourierDialog
          title="Assign a courier"
          subject={
            assigning.trackingNumber +
            (assigning.source === "MERCHANT" && assigning.merchantOrderReference ? ` · ${assigning.merchantOrderReference}` : "")
          }
          couriers={workloads}
          assign={(courierUserId) => assignCourier(assigning.id, courierUserId)}
          onClose={() => setAssigning(null)}
          onAssigned={(courier) => onAssigned(assigning, courier)}
        />
      )}

      {historyFor && (
        <AssignmentHistoryDialog
          parcelId={historyFor.id}
          trackingNumber={historyFor.trackingNumber}
          onClose={() => setHistoryFor(null)}
        />
      )}
    </div>
  );
}


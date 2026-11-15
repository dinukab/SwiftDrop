"use client";

import React, { useEffect, useState } from "react";
import { AlertCircle, CheckCircle2, ChevronLeft, ChevronRight, Loader2, RefreshCw, Truck, X } from "lucide-react";
import { formatApiError, type PageResponse } from "@/lib/api";
import {
  assignCourier,
  getCourierWorkloads,
  getUnassignedParcels,
  type CourierWorkload,
  type UnassignedParcel,
} from "@/lib/dispatch";
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card";
import { Button } from "@/components/ui/button";

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

  /** Applies one loaded board to the screen. Called only after the fetch has completed. */
  const apply = (board: Board) => {
    setParcels(board.queue.items);
    setTotalPages(board.queue.totalPages);
    setTotalItems(board.queue.totalItems);
    setWorkloads(board.couriers);
    setError(null);
  };

  // Reload whenever the page changes. State is only set inside the promise callbacks, after the fetch.
  useEffect(() => {
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
  }, [page]);

  /** Reloads on demand (after the Refresh button or an assignment), from an event handler. */
  const reload = () => {
    setLoading(true);
    fetchBoard(page)
      .then(apply)
      .catch((err: unknown) => setError(formatApiError(err)))
      .finally(() => setLoading(false));
  };

  const refresh = () => {
    setNotice(null);
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
            Assign couriers to parcels waiting for pickup. A parcel is held or cancelled by its merchant,
            and drops out of this queue automatically.
          </p>
        </div>
        <Button variant="outline" size="sm" onClick={refresh} disabled={loading}>
          <RefreshCw className={`w-4 h-4 mr-2 ${loading ? "animate-spin" : ""}`} />
          Refresh
        </Button>
      </div>

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

      <div className="grid gap-6 lg:grid-cols-3">
        <Card className="lg:col-span-2">
          <CardHeader>
            <CardTitle>Waiting for a courier</CardTitle>
            <CardDescription>{totalItems} parcel{totalItems === 1 ? "" : "s"} to assign, oldest first</CardDescription>
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
                <p className="text-sm font-medium">Nothing waiting for a courier</p>
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
                          <td className="px-4 py-3 text-muted-foreground">
                            {parcel.size} ({(parcel.weightGrams / 1000).toFixed(1)} kg)
                          </td>
                          <td className="px-4 py-3 text-muted-foreground text-xs">
                            {new Date(parcel.createdAt).toLocaleString()}
                          </td>
                          <td className="px-4 py-3 text-right">
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

      {assigning && (
        <AssignDialog
          parcel={assigning}
          couriers={workloads}
          onClose={() => setAssigning(null)}
          onAssigned={onAssigned}
        />
      )}
    </div>
  );
}

/** Picks a courier for one parcel. Shows each courier's load so the least busy is an easy choice. */
function AssignDialog({
  parcel,
  couriers,
  onClose,
  onAssigned,
}: {
  parcel: UnassignedParcel;
  couriers: CourierWorkload[];
  onClose: () => void;
  onAssigned: (parcel: UnassignedParcel, courier: CourierWorkload) => void;
}) {
  const [selected, setSelected] = useState<string | null>(couriers[0]?.courierUserId ?? null);
  const [busy, setBusy] = useState(false);
  const [problem, setProblem] = useState<string | null>(null);

  useEffect(() => {
    const onKeyDown = (event: KeyboardEvent) => {
      if (event.key === "Escape" && !busy) onClose();
    };
    window.addEventListener("keydown", onKeyDown);
    return () => window.removeEventListener("keydown", onKeyDown);
  }, [busy, onClose]);

  const confirm = async () => {
    const courier = couriers.find((c) => c.courierUserId === selected);
    if (!courier) return;
    setBusy(true);
    setProblem(null);
    try {
      await assignCourier(parcel.id, courier.courierUserId);
      onAssigned(parcel, courier);
    } catch (err) {
      // 409 carries the reason (not eligible, or already picked up); show it as it is
      setProblem(formatApiError(err));
      setBusy(false);
    }
  };

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/50 p-4" onClick={() => !busy && onClose()}>
      <div
        role="dialog"
        aria-modal="true"
        aria-labelledby="assign-title"
        className="w-full max-w-md space-y-4 rounded-xl border bg-background p-6 shadow-lg"
        onClick={(event) => event.stopPropagation()}
      >
        <div className="flex items-start justify-between gap-4">
          <div className="space-y-1">
            <h2 id="assign-title" className="text-lg font-semibold">Assign a courier</h2>
            <p className="text-sm text-muted-foreground">
              <span className="font-mono">{parcel.trackingNumber}</span>
              {parcel.source === "MERCHANT" && parcel.merchantOrderReference ? ` · ${parcel.merchantOrderReference}` : ""}
            </p>
          </div>
          <button type="button" aria-label="Close" onClick={onClose} disabled={busy} className="text-muted-foreground hover:text-foreground">
            <X className="w-5 h-5" />
          </button>
        </div>

        {couriers.length === 0 ? (
          <p className="text-sm text-muted-foreground">There are no active couriers to assign.</p>
        ) : (
          <fieldset className="space-y-2">
            <legend className="sr-only">Courier</legend>
            {couriers.map((courier) => (
              <label
                key={courier.courierUserId}
                className={`flex cursor-pointer items-center justify-between rounded-lg border px-3 py-2 ${
                  selected === courier.courierUserId ? "border-primary bg-primary/5" : ""
                }`}
              >
                <span className="flex items-center gap-3">
                  <input
                    type="radio"
                    name="courier"
                    value={courier.courierUserId}
                    checked={selected === courier.courierUserId}
                    onChange={() => setSelected(courier.courierUserId)}
                  />
                  <span className="text-sm font-medium">{courier.name}</span>
                  <span className="text-xs text-muted-foreground">{courier.vehicleType.toLowerCase()}</span>
                </span>
                <span className="text-xs text-muted-foreground">{courier.activeParcels} active</span>
              </label>
            ))}
          </fieldset>
        )}

        {problem && (
          <p role="alert" className="rounded-md bg-destructive/10 px-3 py-2 text-sm text-destructive">{problem}</p>
        )}

        <div className="flex justify-end gap-2">
          <Button variant="outline" size="sm" onClick={onClose} disabled={busy}>Cancel</Button>
          <Button size="sm" onClick={confirm} disabled={busy || !selected}>
            {busy && <Loader2 className="w-4 h-4 mr-2 animate-spin" />}
            Assign
          </Button>
        </div>
      </div>
    </div>
  );
}

"use client";

import React, { useEffect, useRef, useState } from "react";
import Link from "next/link";
import { api, formatApiError, type PageResponse } from "@/lib/api";
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card";
import { Button } from "@/components/ui/button";
import {
  AlertCircle,
  ChevronLeft,
  ChevronRight,
  Loader2,
  Package,
  Pause,
  Play,
  RefreshCw,
  XCircle,
} from "lucide-react";

type OrderAction = "PAUSE" | "RESUME" | "CANCEL";

interface MerchantOrder {
  id: string;
  trackingNumber: string;
  status: string;
  size: string;
  weightGrams: number;
  merchantOrderReference: string;
  createdAt: string;
  /** What the server allows right now, so the UI never has to know the lifecycle rules. */
  allowedActions: OrderAction[];
}

const STATUS_FILTERS = [
  "ALL",
  "CREATED",
  "PAUSED",
  "PICKED_UP",
  "IN_TRANSIT",
  "OUT_FOR_DELIVERY",
  "DELIVERED",
  "FAILED",
  "CANCELLED",
] as const;

function formatStatus(status: string): string {
  if (status === "PAUSED") return "ON HOLD";
  return status.replaceAll("_", " ");
}

function orderUrl(order: MerchantOrder, resource: "holds" | "cancellations"): string {
  return `/api/v1/merchants/me/orders/${encodeURIComponent(order.merchantOrderReference)}/${resource}`;
}

export default function MerchantOrdersPage() {
  const [orders, setOrders] = useState<MerchantOrder[]>([]);
  const [page, setPage] = useState(0);
  const [totalPages, setTotalPages] = useState(0);
  const [totalItems, setTotalItems] = useState(0);
  const [statusFilter, setStatusFilter] = useState<string>("ALL");
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  // The order an action is running on, and the order a dialog is open for
  const [busyId, setBusyId] = useState<string | null>(null);
  const [dialog, setDialog] = useState<{ order: MerchantOrder; action: "PAUSE" | "CANCEL" } | null>(null);

  // Bumped to load the list again (Refresh, and after an order action)
  const [reloadKey, setReloadKey] = useState(0);

  /** Loads the list again, showing the spinner. Keeps the current error when asked, so a failed action stays visible. */
  const fetchOrders = (clearError = true) => {
    setLoading(true);
    if (clearError) setError(null);
    setReloadKey((key) => key + 1);
  };

  /** Changes what is listed (filter or page): shows the spinner and clears the old error; the effect loads it. */
  const showList = (change: () => void) => {
    setLoading(true);
    setError(null);
    change();
  };

  // State is only set inside the promise callbacks, after the fetch
  useEffect(() => {
    let current = true;
    const params = new URLSearchParams({
      page: page.toString(),
      size: "10",
    });
    if (statusFilter !== "ALL") {
      params.set("status", statusFilter);
    }
    api
      .get<PageResponse<MerchantOrder>>(`/api/v1/merchants/me/orders?${params.toString()}`)
      .then((data) => {
        if (!current) return;
        setOrders(data.items);
        setTotalPages(data.totalPages);
        setTotalItems(data.totalItems);
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
  }, [page, statusFilter, reloadKey]);

  /** Runs one action on one order, then reloads so the list shows what the server now says. */
  const runAction = async (order: MerchantOrder, action: OrderAction, reason?: string) => {
    setBusyId(order.id);
    setError(null);
    try {
      const body = reason ? { reason } : undefined;
      if (action === "PAUSE") {
        await api.post(orderUrl(order, "holds"), body);
      } else if (action === "RESUME") {
        await api.delete(orderUrl(order, "holds"));
      } else {
        await api.post(orderUrl(order, "cancellations"), body);
      }
      setDialog(null);
      fetchOrders();
    } catch (err) {
      // A conflict (for example the courier picked it up first) arrives with its own message
      setDialog(null);
      setError(formatApiError(err));
      fetchOrders(false); // reload, but keep that message on screen
    } finally {
      setBusyId(null);
    }
  };

  const getStatusBadge = (status: string) => {
    switch (status) {
      case "DELIVERED":
        return "bg-emerald-100 text-emerald-800 dark:bg-emerald-950 dark:text-emerald-300";
      case "FAILED":
        return "bg-rose-100 text-rose-800 dark:bg-rose-950 dark:text-rose-300";
      case "OUT_FOR_DELIVERY":
      case "PAUSED":
        return "bg-amber-100 text-amber-800 dark:bg-amber-950 dark:text-amber-300";
      case "IN_TRANSIT":
      case "PICKED_UP":
        return "bg-blue-100 text-blue-800 dark:bg-blue-950 dark:text-blue-300";
      case "CANCELLED":
        return "bg-zinc-200 text-zinc-700 dark:bg-zinc-800 dark:text-zinc-300 line-through decoration-zinc-400";
      default:
        return "bg-slate-100 text-slate-800 dark:bg-slate-800 dark:text-slate-300";
    }
  };

  return (
    <div className="space-y-6">
      <div className="flex flex-col sm:flex-row justify-between items-start sm:items-center gap-4">
        <div>
          <h1 className="text-2xl font-bold tracking-tight">Shipment Orders</h1>
          <p className="text-muted-foreground text-sm">
            Monitor and track deliveries created via your merchant API and online store. You can hold or
            cancel an order until the courier picks it up.
          </p>
        </div>
        <Button variant="outline" size="sm" onClick={() => fetchOrders()} disabled={loading}>
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

      {/* Status Filter Tabs */}
      <div className="flex overflow-x-auto gap-2 pb-2">
        {STATUS_FILTERS.map((s) => (
          <button
            key={s}
            onClick={() =>
              showList(() => {
                setStatusFilter(s);
                setPage(0);
              })
            }
            className={`px-3 py-1.5 text-xs font-medium rounded-full transition-colors whitespace-nowrap ${
              statusFilter === s
                ? "bg-primary text-primary-foreground"
                : "bg-muted text-muted-foreground hover:bg-muted/80"
            }`}
          >
            {s === "ALL" ? "ALL" : formatStatus(s)}
          </button>
        ))}
      </div>

      <Card>
        <CardHeader>
          <CardTitle>Shipment Records</CardTitle>
          <CardDescription>
            Total shipments found: {totalItems}
          </CardDescription>
        </CardHeader>
        <CardContent>
          {loading && (
            <div className="flex justify-center items-center py-12">
              <Loader2 className="w-8 h-8 animate-spin text-muted-foreground" />
            </div>
          )}

          {!loading && orders.length === 0 && (
            <div className="text-center py-12 space-y-3">
              <Package className="w-10 h-10 text-muted-foreground mx-auto" />
              <p className="text-sm font-medium">No shipment orders found</p>
              <p className="text-xs text-muted-foreground">
                Orders submitted through the merchant API will appear here with live tracking milestones.
              </p>
            </div>
          )}

          {!loading && orders.length > 0 && (
            <div className="space-y-4">
              <div className="overflow-x-auto border rounded-lg">
                <table className="w-full text-sm text-left">
                  <thead className="bg-muted/50 text-xs text-muted-foreground uppercase">
                    <tr>
                      <th className="px-4 py-3">Tracking Number</th>
                      <th className="px-4 py-3">Order Ref</th>
                      <th className="px-4 py-3">Status</th>
                      <th className="px-4 py-3">Size / Weight</th>
                      <th className="px-4 py-3">Created</th>
                      <th className="px-4 py-3">Actions</th>
                    </tr>
                  </thead>
                  <tbody className="divide-y">
                    {orders.map((order) => {
                      const busy = busyId === order.id;
                      const can = (action: OrderAction) => order.allowedActions?.includes(action);
                      return (
                        <tr key={order.id} className="hover:bg-muted/30 transition-colors">
                          <td className="px-4 py-3 font-mono font-medium">
                            <Link
                              href={`/track?code=${encodeURIComponent(order.trackingNumber)}`}
                              className="underline-offset-2 hover:underline"
                            >
                              {order.trackingNumber}
                            </Link>
                          </td>
                          <td className="px-4 py-3 text-muted-foreground">{order.merchantOrderReference}</td>
                          <td className="px-4 py-3">
                            <span className={`inline-flex px-2 py-0.5 rounded text-xs font-medium ${getStatusBadge(order.status)}`}>
                              {formatStatus(order.status)}
                            </span>
                          </td>
                          <td className="px-4 py-3 text-muted-foreground">
                            {order.size} ({(order.weightGrams / 1000).toFixed(1)} kg)
                          </td>
                          <td className="px-4 py-3 text-muted-foreground text-xs">
                            {new Date(order.createdAt).toLocaleDateString()} {new Date(order.createdAt).toLocaleTimeString([], { hour: "2-digit", minute: "2-digit" })}
                          </td>
                          <td className="px-4 py-3">
                            <div className="flex flex-wrap items-center gap-2">
                              {busy && <Loader2 className="w-4 h-4 animate-spin text-muted-foreground" aria-label="Working" />}
                              {can("PAUSE") && (
                                <Button
                                  variant="outline"
                                  size="xs"
                                  disabled={busyId !== null}
                                  onClick={() => setDialog({ order, action: "PAUSE" })}
                                >
                                  <Pause className="w-3 h-3 mr-1" />
                                  Pause
                                </Button>
                              )}
                              {can("RESUME") && (
                                <Button
                                  variant="outline"
                                  size="xs"
                                  disabled={busyId !== null}
                                  onClick={() => runAction(order, "RESUME")}
                                >
                                  <Play className="w-3 h-3 mr-1" />
                                  Resume
                                </Button>
                              )}
                              {can("CANCEL") && (
                                <Button
                                  variant="destructive"
                                  size="xs"
                                  disabled={busyId !== null}
                                  onClick={() => setDialog({ order, action: "CANCEL" })}
                                >
                                  <XCircle className="w-3 h-3 mr-1" />
                                  Cancel
                                </Button>
                              )}
                              {!busy && (order.allowedActions?.length ?? 0) === 0 && (
                                <span className="text-xs text-muted-foreground">—</span>
                              )}
                            </div>
                          </td>
                        </tr>
                      );
                    })}
                  </tbody>
                </table>
              </div>

              {/* Pagination controls */}
              <div className="flex items-center justify-between text-xs text-muted-foreground pt-2">
                <span>
                  Page {page + 1} of {Math.max(1, totalPages)}
                </span>
                <div className="flex gap-2">
                  <Button
                    variant="outline"
                    size="sm"
                    disabled={page === 0 || loading}
                    onClick={() => showList(() => setPage((p) => Math.max(0, p - 1)))}
                  >
                    <ChevronLeft className="w-4 h-4 mr-1" />
                    Previous
                  </Button>
                  <Button
                    variant="outline"
                    size="sm"
                    disabled={page + 1 >= totalPages || loading}
                    onClick={() => showList(() => setPage((p) => p + 1))}
                  >
                    Next
                    <ChevronRight className="w-4 h-4 ml-1" />
                  </Button>
                </div>
              </div>
            </div>
          )}
        </CardContent>
      </Card>

      {dialog && (
        <ActionDialog
          order={dialog.order}
          action={dialog.action}
          busy={busyId !== null}
          onClose={() => setDialog(null)}
          onConfirm={(reason) => runAction(dialog.order, dialog.action, reason)}
        />
      )}
    </div>
  );
}

const REASON_LIMIT = 500;

/** Confirms a pause or a cancellation, with an optional reason kept in the order's history. */
function ActionDialog({
  order,
  action,
  busy,
  onClose,
  onConfirm,
}: {
  order: MerchantOrder;
  action: "PAUSE" | "CANCEL";
  busy: boolean;
  onClose: () => void;
  onConfirm: (reason: string) => void;
}) {
  const [reason, setReason] = useState("");
  const reasonRef = useRef<HTMLTextAreaElement>(null);
  const cancelling = action === "CANCEL";

  useEffect(() => {
    reasonRef.current?.focus();
    const onKeyDown = (event: KeyboardEvent) => {
      if (event.key === "Escape" && !busy) onClose();
    };
    window.addEventListener("keydown", onKeyDown);
    return () => window.removeEventListener("keydown", onKeyDown);
  }, [busy, onClose]);

  return (
    <div
      className="fixed inset-0 z-50 flex items-center justify-center bg-black/50 p-4"
      onClick={() => !busy && onClose()}
    >
      <div
        role="dialog"
        aria-modal="true"
        aria-labelledby="order-action-title"
        className="w-full max-w-md space-y-4 rounded-xl border bg-background p-6 shadow-lg"
        onClick={(event) => event.stopPropagation()}
      >
        <div className="space-y-1">
          <h2 id="order-action-title" className="text-lg font-semibold">
            {cancelling ? "Cancel this order?" : "Put this order on hold?"}
          </h2>
          <p className="text-sm text-muted-foreground">
            Order <span className="font-mono">{order.merchantOrderReference}</span>
            {" · "}
            <span className="font-mono">{order.trackingNumber}</span>
          </p>
        </div>

        <p className="text-sm">
          {cancelling
            ? "The delivery is called off and cannot be reopened. This is only possible before the courier picks the parcel up."
            : "The courier cannot pick the parcel up while it is on hold. You can resume it at any time before pickup."}
        </p>

        <div className="space-y-1">
          <label htmlFor="order-action-reason" className="text-sm font-medium">
            Reason <span className="font-normal text-muted-foreground">(optional, kept in your order history)</span>
          </label>
          <textarea
            id="order-action-reason"
            ref={reasonRef}
            value={reason}
            onChange={(event) => setReason(event.target.value.slice(0, REASON_LIMIT))}
            rows={3}
            className="w-full rounded-md border bg-background px-3 py-2 text-sm"
            placeholder={cancelling ? "For example: out of stock" : "For example: still packing"}
          />
          <p className="text-right text-xs text-muted-foreground">
            {reason.length}/{REASON_LIMIT}
          </p>
        </div>

        <div className="flex justify-end gap-2">
          <Button variant="outline" size="sm" onClick={onClose} disabled={busy}>
            Keep as is
          </Button>
          <Button
            variant={cancelling ? "destructive" : "default"}
            size="sm"
            onClick={() => onConfirm(reason.trim())}
            disabled={busy}
          >
            {busy && <Loader2 className="w-4 h-4 mr-2 animate-spin" />}
            {cancelling ? "Cancel order" : "Put on hold"}
          </Button>
        </div>
      </div>
    </div>
  );
}

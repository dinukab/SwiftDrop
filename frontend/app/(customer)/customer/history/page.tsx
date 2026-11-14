"use client";

import React, { useEffect, useState } from "react";
import Link from "next/link";
import { useAuth } from "@/lib/auth-context";
import { api, formatApiError, type PageResponse, type ParcelHistoryItem, type ParcelStatus } from "@/lib/api";
import { Button } from "@/components/ui/button";

const PAGE_SIZE = 20;

const STATUS_STYLES: Record<ParcelStatus, string> = {
  CREATED: "bg-muted text-muted-foreground",
  PICKED_UP: "bg-blue-100 text-blue-800",
  IN_TRANSIT: "bg-blue-100 text-blue-800",
  OUT_FOR_DELIVERY: "bg-amber-100 text-amber-800",
  DELIVERED: "bg-emerald-100 text-emerald-800",
  FAILED: "bg-red-100 text-red-800",
};

function formatStatus(status: ParcelStatus): string {
  return status.replaceAll("_", " ");
}

function formatDate(value: string): string {
  return new Date(value).toLocaleString();
}

export default function CustomerHistoryPage() {
  const { token, isAuthenticated } = useAuth();
  const [history, setHistory] = useState<PageResponse<ParcelHistoryItem> | null>(null);
  const [page, setPage] = useState(0);
  const [requestError, setRequestError] = useState<{ page: number; message: string } | null>(null);
  const error = requestError?.page === page ? requestError.message : null;
  const loading = Boolean(isAuthenticated && token && !error && (!history || history.page !== page));

  useEffect(() => {
    if (!isAuthenticated || !token) {
      return;
    }

    let cancelled = false;

    api
      .get<PageResponse<ParcelHistoryItem>>(`/api/v1/parcels?page=${page}&size=${PAGE_SIZE}`, { token })
      .then((result) => {
        if (!cancelled) setHistory(result);
      })
      .catch((err) => {
        if (!cancelled) setRequestError({ page, message: formatApiError(err) });
      });

    return () => {
      cancelled = true;
    };
  }, [isAuthenticated, page, token]);

  return (
    <div className="mx-auto max-w-5xl space-y-6">
      <div>
        <h1 className="text-xl font-semibold">Delivery history</h1>
        <p className="text-sm text-muted-foreground">
          View every parcel you have booked, including deliveries confirmed by the courier.
        </p>
      </div>

      {error && (
        <p role="alert" className="rounded-lg border border-destructive/40 bg-destructive/10 p-3 text-sm text-destructive">
          Could not load delivery history: {error}
        </p>
      )}

      <section className="overflow-hidden rounded-xl border bg-card">
        <div className="border-b px-4 py-4 sm:px-6">
          <h2 className="font-semibold">Your deliveries</h2>
          {history && (
            <p className="mt-1 text-sm text-muted-foreground">
              {history.totalItems} {history.totalItems === 1 ? "delivery" : "deliveries"} total
            </p>
          )}
        </div>

        {loading ? (
          <p className="px-4 py-10 text-center text-sm text-muted-foreground sm:px-6">Loading delivery history...</p>
        ) : !history || history.items.length === 0 ? (
          <div className="px-4 py-10 text-center sm:px-6">
            <p className="text-sm text-muted-foreground">No deliveries found.</p>
            <Link href="/customer/new" className="mt-3 inline-flex text-sm font-medium text-primary hover:underline">
              Book a delivery
            </Link>
          </div>
        ) : (
          <>
            <div className="overflow-x-auto">
              <table className="w-full min-w-[700px] text-left text-sm">
                <thead className="bg-muted/50 text-xs uppercase tracking-wide text-muted-foreground">
                  <tr>
                    <th scope="col" className="px-4 py-3 sm:px-6">Tracking</th>
                    <th scope="col" className="px-4 py-3">Route</th>
                    <th scope="col" className="px-4 py-3">Status</th>
                    <th scope="col" className="px-4 py-3">Fee</th>
                    <th scope="col" className="px-4 py-3">Last updated</th>
                  </tr>
                </thead>
                <tbody className="divide-y">
                  {history.items.map((parcel) => (
                    <tr key={parcel.id} className="align-top">
                      <td className="px-4 py-4 sm:px-6">
                        <Link
                          href={`/track?code=${encodeURIComponent(parcel.trackingNumber)}`}
                          className="font-mono font-medium text-primary hover:underline"
                        >
                          {parcel.trackingNumber}
                        </Link>
                        <p className="mt-1 text-xs text-muted-foreground">Booked {formatDate(parcel.createdAt)}</p>
                      </td>
                      <td className="px-4 py-4">
                        <p>{parcel.pickupCity} → {parcel.dropoffCity}</p>
                        <p className="mt-1 text-xs text-muted-foreground">
                          {parcel.size} · {parcel.weightGrams.toLocaleString()} g
                        </p>
                      </td>
                      <td className="px-4 py-4">
                        <span className={`inline-flex rounded-full px-2 py-1 text-xs font-medium ${STATUS_STYLES[parcel.status]}`}>
                          {formatStatus(parcel.status)}
                        </span>
                      </td>
                      <td className="whitespace-nowrap px-4 py-4">
                        {parcel.fee.currency} {parcel.fee.amount}
                      </td>
                      <td className="whitespace-nowrap px-4 py-4 text-muted-foreground">
                        {formatDate(parcel.updatedAt)}
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>

            {history.totalPages > 1 && (
              <div className="flex items-center justify-between border-t px-4 py-3 sm:px-6">
                <p className="text-xs text-muted-foreground">
                  Page {history.page + 1} of {history.totalPages}
                </p>
                <div className="flex gap-2">
                  <Button
                    variant="outline"
                    size="sm"
                    disabled={page === 0 || loading}
                    onClick={() => setPage((current) => current - 1)}
                  >
                    Previous
                  </Button>
                  <Button
                    variant="outline"
                    size="sm"
                    disabled={page >= history.totalPages - 1 || loading}
                    onClick={() => setPage((current) => current + 1)}
                  >
                    Next
                  </Button>
                </div>
              </div>
            )}
          </>
        )}
      </section>
    </div>
  );
}

"use client";

import { useEffect, useState } from "react";
import { Printer, X } from "lucide-react";
import { api, formatApiError, type DeliveryReceipt, type ParcelHistoryItem } from "@/lib/api";
import { Button } from "@/components/ui/button";

const STATUS_LABELS: Record<string, string> = {
  CREATED: "Booked",
  PICKED_UP: "Picked Up",
  IN_TRANSIT: "In Transit",
  OUT_FOR_DELIVERY: "Out for Delivery",
  DELIVERED: "Delivered",
  FAILED: "Failed",
};

function formatDate(iso: string | null): string {
  if (!iso) return "—";
  return new Date(iso).toLocaleString("en-GB", {
    day: "numeric",
    month: "short",
    year: "numeric",
    hour: "2-digit",
    minute: "2-digit",
  });
}

function formatFee(fee: { amount: string; currency: string }): string {
  const amount = Number(fee.amount).toLocaleString("en-LK", {
    minimumFractionDigits: 2,
    maximumFractionDigits: 2,
  });
  return fee.currency === "LKR" ? `Rs. ${amount}` : `${fee.currency} ${amount}`;
}

function formatWeight(grams: number): string {
  return grams >= 1000 ? `${(grams / 1000).toFixed(2)} kg` : `${grams} g`;
}

function formatAddress(address: DeliveryReceipt["pickup"]): string {
  const parts = [address.line1, address.line2, address.city, address.postalCode].filter(Boolean);
  return parts.length > 0 ? parts.join(", ") : "—";
}

interface ReceiptModalProps {
  parcel: ParcelHistoryItem | null;
  token: string;
  onClose: () => void;
}

export function ReceiptModal({ parcel, token, onClose }: ReceiptModalProps) {
  if (!parcel) return null;
  return <ReceiptDialog key={parcel.id} parcel={parcel} token={token} onClose={onClose} />;
}

function ReceiptDialog({
  parcel,
  token,
  onClose,
}: {
  parcel: ParcelHistoryItem;
  token: string;
  onClose: () => void;
}) {
  const [receipt, setReceipt] = useState<DeliveryReceipt | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    let cancelled = false;
    api
      .get<DeliveryReceipt>(`/api/v1/parcels/${parcel.id}/receipt`, { token })
      .then((data) => {
        if (!cancelled) setReceipt(data);
      })
      .catch((err: unknown) => {
        if (!cancelled) setError(formatApiError(err));
      })
      .finally(() => {
        if (!cancelled) setLoading(false);
      });
    return () => {
      cancelled = true;
    };
  }, [parcel.id, token]);

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/50 p-4">
      {/* Print-only stylesheet: only the receipt is visible when printing */}
      <style>{`
        @media print {
          body * { visibility: hidden; }
          .receipt-print-area, .receipt-print-area * { visibility: visible; }
          .receipt-print-area {
            position: fixed;
            inset: 0;
            margin: auto;
            width: 100%;
            max-width: 640px;
            background: white;
          }
        }
      `}</style>

      <div className="w-full max-w-lg rounded-xl border bg-card p-5 shadow-lg receipt-print-area">
        <div className="flex items-start justify-between">
          <div>
            <h2 className="text-lg font-semibold">Delivery Receipt</h2>
            <p className="text-sm text-muted-foreground">SwiftDrop · {parcel.trackingNumber}</p>
          </div>
          <Button
            variant="ghost"
            size="icon"
            onClick={onClose}
            aria-label="Close receipt"
            className="print:hidden"
          >
            <X />
          </Button>
        </div>

        {loading && <p className="mt-4 text-sm text-muted-foreground">Loading receipt…</p>}
        {error && <p className="mt-4 text-sm text-destructive">{error}</p>}

        {receipt && (
          <div className="mt-4 space-y-4">
            <dl className="grid grid-cols-2 gap-x-4 gap-y-2 text-sm">
              <div>
                <dt className="text-muted-foreground">Tracking Number</dt>
                <dd className="font-medium">{receipt.trackingNumber}</dd>
              </div>
              <div>
                <dt className="text-muted-foreground">Status</dt>
                <dd className="font-medium">{STATUS_LABELS[receipt.status] ?? receipt.status}</dd>
              </div>
              <div>
                <dt className="text-muted-foreground">Booked</dt>
                <dd>{formatDate(receipt.createdAt)}</dd>
              </div>
              <div>
                <dt className="text-muted-foreground">Size / Weight</dt>
                <dd>
                  {receipt.size} · {formatWeight(receipt.weightGrams)}
                </dd>
              </div>
              <div className="col-span-2">
                <dt className="text-muted-foreground">Pickup Address</dt>
                <dd>{formatAddress(receipt.pickup)}</dd>
              </div>
              <div className="col-span-2">
                <dt className="text-muted-foreground">Delivery Address</dt>
                <dd>{formatAddress(receipt.dropoff)}</dd>
              </div>
              {receipt.description && (
                <div className="col-span-2">
                  <dt className="text-muted-foreground">Description</dt>
                  <dd>{receipt.description}</dd>
                </div>
              )}
              <div>
                <dt className="text-muted-foreground">Total Fee</dt>
                <dd className="text-base font-semibold">{formatFee(receipt.fee)}</dd>
              </div>
              {receipt.status === "DELIVERED" && (
                <>
                  <div>
                    <dt className="text-muted-foreground">Delivered At</dt>
                    <dd>{formatDate(receipt.deliveredAt)}</dd>
                  </div>
                  <div>
                    <dt className="text-muted-foreground">Received By</dt>
                    <dd>{receipt.recipientName ?? "—"}</dd>
                  </div>
                </>
              )}
              {receipt.status === "FAILED" && (
                <div className="col-span-2">
                  <dt className="text-muted-foreground">Failure</dt>
                  <dd>
                    {formatDate(receipt.failedAt)}
                    {receipt.failureReason ? ` · ${receipt.failureReason.replaceAll("_", " ")}` : ""}
                  </dd>
                </div>
              )}
            </dl>
            <div className="flex justify-end gap-2 border-t pt-4 print:hidden">
              <Button variant="outline" onClick={onClose}>
                Close
              </Button>
              <Button onClick={() => window.print()}>
                <Printer data-icon="inline-start" />
                Print / Save as PDF
              </Button>
            </div>
          </div>
        )}
      </div>
    </div>
  );
}

"use client";

import { useEffect, useState } from "react";
import { Check, Copy, Package, Printer, X, MapPin, Calendar, CreditCard } from "lucide-react";
import { api, formatApiError, type DeliveryReceipt, type ParcelHistoryItem } from "@/lib/api";
import { Button } from "@/components/ui/button";

const STATUS_LABELS: Record<string, { label: string; badgeClass: string }> = {
  CREATED: { label: "Booked", badgeClass: "bg-orange-100 text-orange-800" },
  PICKED_UP: { label: "Picked Up", badgeClass: "bg-blue-100 text-blue-800" },
  IN_TRANSIT: { label: "In Transit", badgeClass: "bg-blue-100 text-blue-800" },
  OUT_FOR_DELIVERY: { label: "Out for Delivery", badgeClass: "bg-amber-100 text-amber-800" },
  DELIVERED: { label: "Delivered", badgeClass: "bg-emerald-100 text-emerald-800" },
  FAILED: { label: "Failed", badgeClass: "bg-red-100 text-red-800" },
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
  const [copied, setCopied] = useState(false);

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

  const copyTracking = async () => {
    if (parcel.trackingNumber && "clipboard" in navigator) {
      await navigator.clipboard.writeText(parcel.trackingNumber);
      setCopied(true);
      setTimeout(() => setCopied(false), 2000);
    }
  };

  const statusInfo = receipt ? STATUS_LABELS[receipt.status] ?? { label: receipt.status, badgeClass: "bg-muted text-muted-foreground" } : null;

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/60 p-4 backdrop-blur-sm">
      <style>{`
        @media print {
          body * { visibility: hidden; }
          .receipt-print-area, .receipt-print-area * { visibility: visible; }
          .receipt-print-area {
            position: fixed;
            inset: 0;
            margin: auto;
            width: 100%;
            max-width: 680px;
            background: white;
            box-shadow: none;
            border: none;
          }
          .print-hide { display: none !important; }
        }
      `}</style>

      <div className="w-full max-w-xl rounded-2xl border bg-card p-6 shadow-2xl receipt-print-area">
        {/* Top Header */}
          <div className="flex items-center justify-between pb-4 border-b">
            <div className="flex items-center gap-3">
              <div>
                <h2 className="text-xl font-extrabold tracking-wide">
                  <span className="text-foreground">Swift</span>
                  <span className="text-orange-600">Drop</span>
                </h2>
              </div>
            </div>
          <div className="flex items-center gap-4">
            <div className="text-right">
              <span className="text-xs font-bold uppercase tracking-wider text-muted-foreground">Delivery Receipt</span>
              <p className="text-xs text-muted-foreground flex items-center gap-1.5 justify-end mt-0.5">
                Tracking: <span className="font-mono font-semibold text-foreground">{parcel.trackingNumber}</span>
                <button onClick={copyTracking} className="text-primary hover:text-primary/80 inline-flex items-center gap-0.5 font-medium ml-1">
                  {copied ? <Check className="size-3.5 text-emerald-600 inline" /> : <Copy className="size-3.5 inline" />} Copy
                </button>
              </p>
            </div>
            <Button
              variant="ghost"
              size="icon"
              onClick={onClose}
              aria-label="Close receipt"
              className="print-hide rounded-full hover:bg-muted"
            >
              <X className="size-5" />
            </Button>
          </div>
        </div>

        {loading && <p className="mt-6 text-center text-sm text-muted-foreground animate-pulse">Loading receipt details…</p>}
        {error && <p className="mt-6 text-sm text-destructive bg-destructive/10 p-3 rounded-lg">{error}</p>}

        {receipt && statusInfo && (
          <div className="mt-5 space-y-5">
            {/* Status & Date Banner */}
            <div className="flex items-center justify-between rounded-xl bg-muted/40 p-4 border border-border/60">
              <div className={`inline-flex items-center gap-1.5 rounded-lg px-3 py-1 text-xs font-semibold ${statusInfo.badgeClass}`}>
                ✓ {statusInfo.label}
              </div>
              <div className="text-sm font-medium text-muted-foreground">
                {formatDate(receipt.createdAt)}
              </div>
            </div>

            {/* Parcel Details Section */}
            <div className="rounded-xl border border-border/60 p-4 space-y-3 bg-card">
              <h3 className="text-xs font-bold uppercase tracking-wider text-muted-foreground">Parcel Details</h3>
              <div className="grid grid-cols-2 gap-4 text-sm">
                <div className="flex items-start gap-2.5">
                  <Package className="size-4 text-muted-foreground mt-0.5" />
                  <div>
                    <span className="text-xs text-muted-foreground block">Size / Weight</span>
                    <span className="font-medium text-card-foreground">{receipt.size} · {formatWeight(receipt.weightGrams)}</span>
                  </div>
                </div>
                <div className="flex items-start gap-2.5">
                  <Calendar className="size-4 text-muted-foreground mt-0.5" />
                  <div>
                    <span className="text-xs text-muted-foreground block">Tracking Number</span>
                    <span className="font-mono font-medium text-card-foreground">{receipt.trackingNumber}</span>
                  </div>
                </div>
              </div>
            </div>

            {/* Addresses Section */}
            <div className="rounded-xl border border-border/60 p-4 space-y-3 bg-card">
              <h3 className="text-xs font-bold uppercase tracking-wider text-muted-foreground">Addresses</h3>
              <div className="grid grid-cols-1 sm:grid-cols-2 gap-4 text-sm">
                <div className="flex items-start gap-2.5 border-b sm:border-b-0 sm:border-r border-border/40 pb-3 sm:pb-0 sm:pr-3">
                  <MapPin className="size-4 text-orange-500 mt-0.5 shrink-0" />
                  <div>
                    <span className="text-xs text-muted-foreground block font-medium">Pickup Address</span>
                    <span className="text-card-foreground mt-0.5 block">{formatAddress(receipt.pickup)}</span>
                  </div>
                </div>
                <div className="flex items-start gap-2.5">
                  <MapPin className="size-4 text-blue-500 mt-0.5 shrink-0" />
                  <div>
                    <span className="text-xs text-muted-foreground block font-medium">Delivery Address</span>
                    <span className="text-card-foreground mt-0.5 block">{formatAddress(receipt.dropoff)}</span>
                  </div>
                </div>
              </div>
            </div>

            {receipt.description && (
              <div className="rounded-xl border border-border/60 p-3 bg-muted/20 text-xs">
                <span className="font-semibold text-muted-foreground block mb-0.5">Description:</span>
                <span className="italic text-card-foreground">{receipt.description}</span>
              </div>
            )}

            {/* Payment Summary Box */}
            <div className="rounded-xl bg-orange-50/50 border border-orange-200/60 p-4 space-y-2">
              <h3 className="text-xs font-bold uppercase tracking-wider text-muted-foreground">Payment Summary</h3>
              <div className="flex items-center justify-between">
                <div>
                  <span className="text-base font-bold text-card-foreground block">Total Fee</span>
                  <span className="text-xs text-muted-foreground flex items-center gap-1 mt-0.5">
                    <CreditCard className="size-3.5" /> Paid via Card or Cash on Pickup
                  </span>
                </div>
                <div className="text-right">
                  <span className="text-2xl font-extrabold text-card-foreground">{formatFee(receipt.fee)}</span>
                  <span className="text-xs font-semibold text-emerald-600 block mt-0.5">✓ Success</span>
                </div>
              </div>
            </div>

            {/* Modal Actions */}
            <div className="flex justify-end gap-3 pt-2 print-hide">
              <Button variant="outline" onClick={onClose} className="rounded-xl px-5">
                Close
              </Button>
              <Button onClick={() => window.print()} className="rounded-xl px-5 bg-orange-600 hover:bg-orange-700 text-white">
                <Printer className="size-4 mr-2" />
                Print / Save as PDF
              </Button>
            </div>
          </div>
        )}
      </div>
    </div>
  );
}
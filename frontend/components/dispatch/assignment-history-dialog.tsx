"use client";

import React, { useEffect, useState } from "react";
import { AlertCircle, Loader2, X } from "lucide-react";
import { formatApiError } from "@/lib/api";
import {
  getAssignmentHistory,
  legName,
  provinceName,
  type AssignmentHistoryEntry,
} from "@/lib/dispatch";
import { Button } from "@/components/ui/button";

/** Who was assigned to a parcel, by whom and when. Loads when opened; shows loading, empty and error states. */
export function AssignmentHistoryDialog({
  parcelId,
  trackingNumber,
  onClose,
}: {
  parcelId: string;
  trackingNumber: string;
  onClose: () => void;
}) {
  // null while loading; state is only set inside the promise callbacks, after the fetch
  const [entries, setEntries] = useState<AssignmentHistoryEntry[] | null>(null);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    let current = true;
    getAssignmentHistory(parcelId)
      .then((result) => {
        if (current) setEntries(result);
      })
      .catch((err: unknown) => {
        if (current) setError(formatApiError(err));
      });
    return () => {
      current = false;
    };
  }, [parcelId]);

  useEffect(() => {
    const onKeyDown = (event: KeyboardEvent) => {
      if (event.key === "Escape") onClose();
    };
    window.addEventListener("keydown", onKeyDown);
    return () => window.removeEventListener("keydown", onKeyDown);
  }, [onClose]);

  const loading = entries === null && error === null;

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/50 p-4" onClick={onClose}>
      <div
        role="dialog"
        aria-modal="true"
        aria-labelledby="history-title"
        className="max-h-[85vh] w-full max-w-lg space-y-4 overflow-y-auto rounded-xl border bg-background p-6 shadow-lg"
        onClick={(event) => event.stopPropagation()}
      >
        <div className="flex items-start justify-between gap-4">
          <div className="space-y-1">
            <h2 id="history-title" className="text-lg font-semibold">Assignment history</h2>
            <p className="text-sm text-muted-foreground">
              <span className="font-mono">{trackingNumber}</span>
            </p>
          </div>
          <button type="button" aria-label="Close" onClick={onClose} className="text-muted-foreground hover:text-foreground">
            <X className="h-5 w-5" />
          </button>
        </div>

        {loading && (
          <div role="status" className="flex justify-center py-8">
            <Loader2 className="h-6 w-6 animate-spin text-muted-foreground" />
            <span className="sr-only">Loading history</span>
          </div>
        )}

        {error && (
          <div role="alert" className="flex items-start gap-2 rounded-md bg-destructive/10 px-3 py-2 text-sm text-destructive">
            <AlertCircle className="mt-0.5 h-4 w-4 flex-shrink-0" />
            <span>{error}</span>
          </div>
        )}

        {entries && entries.length === 0 && (
          <p className="py-4 text-center text-sm text-muted-foreground">
            No assignments recorded yet. Parcels assigned before history was introduced have no entries.
          </p>
        )}

        {entries && entries.length > 0 && (
          <ol className="space-y-3">
            {entries.map((entry) => (
              <li key={entry.id} className="rounded-lg border p-3 text-sm">
                <div className="flex flex-wrap items-center justify-between gap-2">
                  <span className="font-medium">{entry.courierName}</span>
                  <span
                    className={`rounded-full px-2 py-0.5 text-xs font-medium ${
                      entry.active ? "bg-emerald-100 text-emerald-800" : "bg-muted text-muted-foreground"
                    }`}
                  >
                    {entry.active ? "Current courier" : "Replaced"}
                  </span>
                </div>
                <p className="mt-1 text-xs text-muted-foreground">
                  Leg {entry.legSequence}: {legName(entry.legType)} · {provinceName(entry.hubCode)} hub · assigned by {entry.dispatcherName}
                </p>
                <p className="mt-1 text-xs text-muted-foreground">
                  {new Date(entry.assignedAt).toLocaleString()}
                  {entry.endedAt ? ` → replaced ${new Date(entry.endedAt).toLocaleString()}` : ""}
                </p>
              </li>
            ))}
          </ol>
        )}

        <div className="flex justify-end">
          <Button variant="outline" size="sm" onClick={onClose}>Close</Button>
        </div>
      </div>
    </div>
  );
}

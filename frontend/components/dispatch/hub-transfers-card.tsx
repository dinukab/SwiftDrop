"use client";

import React, { useEffect, useState } from "react";
import { AlertCircle, ArrowRight, ChevronLeft, ChevronRight, Loader2, PackageCheck, Send, X } from "lucide-react";
import { formatApiError, type PageResponse } from "@/lib/api";
import {
  confirmParcelReceipt,
  getIncomingTransfers,
  getReadyTransfers,
  provinceName,
  transferParcel,
  type HubTransfer,
} from "@/lib/dispatch";
import { Button } from "@/components/ui/button";
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card";

const PAGE_SIZE = 10;

type Mode = "send" | "receive";

/**
 * Hub-to-hub transfers of the dispatcher's hub. No courier is involved: this hub sends a parcel it has received
 * straight to the next hub of its route, and receives parcels other hubs sent, confirming that they physically
 * arrived. The server decides what is ready, takes the hub from the token and refuses everything else.
 */
export function HubTransfersCard({
  hubName,
  refreshKey,
  onDone,
}: {
  hubName: string;
  /** Changes when the dispatcher refreshes the board, so this card reloads with it. */
  refreshKey: number;
  /** Called after a transfer or a receipt succeeded, so the rest of the screen can reload. */
  onDone: (message: string) => void;
}) {
  const [confirming, setConfirming] = useState<{ mode: Mode; transfer: HubTransfer } | null>(null);

  const done = (mode: Mode, transfer: HubTransfer) => {
    setConfirming(null);
    onDone(
      mode === "send"
        ? `${transfer.trackingNumber} sent to ${provinceName(transfer.destinationHubCode)} hub. It is on its way until they confirm receipt.`
        : `${transfer.trackingNumber} received from ${provinceName(transfer.sourceHubCode)} hub. It can now be given a final-delivery courier here, or sent on.`,
    );
  };

  return (
    <div className="grid gap-6 lg:grid-cols-2">
      <TransferList
        title="Ready to send to the next hub"
        description={`Parcels received at ${hubName} hub that continue to another hub. No courier is needed.`}
        emptyTitle="No parcels are ready to send"
        emptyHint={`A parcel appears here once it is received at ${hubName} hub and its route continues to another hub.`}
        mode="send"
        load={getReadyTransfers}
        reloadKey={refreshKey}
        onAction={(transfer) => setConfirming({ mode: "send", transfer })}
      />
      <TransferList
        title="Incoming from other hubs"
        description={`Parcels sent to ${hubName} hub. Confirm each one when it physically arrives.`}
        emptyTitle="No parcels are on their way here"
        emptyHint="Parcels appear here as soon as another hub sends them."
        mode="receive"
        load={getIncomingTransfers}
        reloadKey={refreshKey}
        onAction={(transfer) => setConfirming({ mode: "receive", transfer })}
      />

      {confirming && (
        <ConfirmTransferDialog
          mode={confirming.mode}
          transfer={confirming.transfer}
          onClose={() => setConfirming(null)}
          onDone={() => done(confirming.mode, confirming.transfer)}
        />
      )}
    </div>
  );
}

function TransferList({
  title,
  description,
  emptyTitle,
  emptyHint,
  mode,
  load,
  reloadKey,
  onAction,
}: {
  title: string;
  description: string;
  emptyTitle: string;
  emptyHint: string;
  mode: Mode;
  load: (page: number, size: number) => Promise<PageResponse<HubTransfer>>;
  reloadKey: number;
  onAction: (transfer: HubTransfer) => void;
}) {
  const [items, setItems] = useState<HubTransfer[]>([]);
  const [page, setPage] = useState(0);
  const [totalPages, setTotalPages] = useState(0);
  const [totalItems, setTotalItems] = useState(0);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  // State is only set inside the promise callbacks, after the fetch has completed.
  useEffect(() => {
    let current = true;
    load(page, PAGE_SIZE)
      .then((result) => {
        if (!current) return;
        setItems(result.items);
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
  }, [load, page, reloadKey]);

  return (
    <Card>
      <CardHeader>
        <CardTitle>{title}</CardTitle>
        <CardDescription>
          {totalItems} parcel{totalItems === 1 ? "" : "s"}. {description}
        </CardDescription>
      </CardHeader>
      <CardContent>
        {error && (
          <div role="alert" className="mb-4 flex items-start gap-2 rounded-md bg-destructive/10 px-3 py-2 text-sm text-destructive">
            <AlertCircle className="mt-0.5 h-4 w-4 flex-shrink-0" />
            <span>{error}</span>
          </div>
        )}

        {loading && items.length === 0 && (
          <div role="status" className="flex justify-center py-8">
            <Loader2 className="h-6 w-6 animate-spin text-muted-foreground" />
            <span className="sr-only">Loading transfers</span>
          </div>
        )}

        {!loading && items.length === 0 && !error && (
          <div className="space-y-2 py-8 text-center">
            <PackageCheck className="mx-auto h-10 w-10 text-muted-foreground" />
            <p className="text-sm font-medium">{emptyTitle}</p>
            <p className="text-xs text-muted-foreground">{emptyHint}</p>
          </div>
        )}

        {items.length > 0 && (
          <div className="space-y-4">
            <ul className="space-y-2">
              {items.map((transfer) => (
                <li key={transfer.legId} className="flex flex-wrap items-center justify-between gap-3 rounded-lg border px-3 py-2">
                  <div className="space-y-0.5">
                    <p className="font-mono text-sm font-medium">{transfer.trackingNumber}</p>
                    <p className="flex flex-wrap items-center gap-1 text-xs text-muted-foreground">
                      {provinceName(transfer.sourceHubCode)}
                      <ArrowRight className="h-3 w-3" aria-label="to" />
                      {provinceName(transfer.destinationHubCode)}
                      <span>
                        · {transfer.size} ({(transfer.weightGrams / 1000).toFixed(1)} kg)
                      </span>
                    </p>
                    {transfer.transferredAt && (
                      <p className="text-xs text-muted-foreground">
                        Sent {new Date(transfer.transferredAt).toLocaleString()}
                      </p>
                    )}
                  </div>
                  <Button size="xs" variant="outline" onClick={() => onAction(transfer)}>
                    {mode === "send" ? (
                      <>
                        <Send className="mr-1 h-3 w-3" />
                        Send to {provinceName(transfer.destinationHubCode)}
                      </>
                    ) : (
                      "Confirm received"
                    )}
                  </Button>
                </li>
              ))}
            </ul>

            {totalPages > 1 && (
              <div className="flex items-center justify-between text-xs text-muted-foreground">
                <span>Page {page + 1} of {totalPages}</span>
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
            )}
          </div>
        )}
      </CardContent>
    </Card>
  );
}

/** Asks the dispatcher to confirm before a parcel is sent or marked received; shows the server's refusal as it is. */
function ConfirmTransferDialog({
  mode,
  transfer,
  onClose,
  onDone,
}: {
  mode: Mode;
  transfer: HubTransfer;
  onClose: () => void;
  onDone: () => void;
}) {
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
    setBusy(true);
    setProblem(null);
    try {
      await (mode === "send"
        ? transferParcel(transfer.parcelId, transfer.legId)
        : confirmParcelReceipt(transfer.parcelId, transfer.legId));
      onDone();
    } catch (err) {
      setProblem(formatApiError(err));
      setBusy(false);
    }
  };

  const from = provinceName(transfer.sourceHubCode);
  const to = provinceName(transfer.destinationHubCode);

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/50 p-4" onClick={() => !busy && onClose()}>
      <div
        role="dialog"
        aria-modal="true"
        aria-labelledby="transfer-title"
        className="w-full max-w-md space-y-4 rounded-xl border bg-background p-6 shadow-lg"
        onClick={(event) => event.stopPropagation()}
      >
        <div className="flex items-start justify-between gap-4">
          <div className="space-y-1">
            <h2 id="transfer-title" className="text-lg font-semibold">
              {mode === "send" ? `Send to ${to} hub` : `Confirm receipt from ${from} hub`}
            </h2>
            <p className="font-mono text-sm text-muted-foreground">{transfer.trackingNumber}</p>
          </div>
          <button type="button" aria-label="Close" onClick={onClose} disabled={busy} className="text-muted-foreground hover:text-foreground">
            <X className="h-5 w-5" />
          </button>
        </div>

        <p className="text-sm text-muted-foreground">
          {mode === "send"
            ? `The parcel leaves ${from} hub for ${to} hub. No courier is assigned. It counts as arrived only when ${to} hub confirms receipt.`
            : `Confirm only when the parcel has physically arrived at ${to} hub. After this it can be given a courier for delivery, or sent on.`}
        </p>

        {problem && (
          <p role="alert" className="rounded-md bg-destructive/10 px-3 py-2 text-sm text-destructive">{problem}</p>
        )}

        <div className="flex justify-end gap-2">
          <Button variant="outline" size="sm" onClick={onClose} disabled={busy}>Cancel</Button>
          <Button size="sm" onClick={confirm} disabled={busy}>
            {busy && <Loader2 className="mr-2 h-4 w-4 animate-spin" />}
            {mode === "send" ? "Send parcel" : "Confirm received"}
          </Button>
        </div>
      </div>
    </div>
  );
}

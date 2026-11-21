"use client";

import React, { useEffect, useState } from "react";
import { Loader2, X } from "lucide-react";
import { formatApiError } from "@/lib/api";
import type { CourierWorkload } from "@/lib/dispatch";
import { Button } from "@/components/ui/button";

/**
 * Picks a courier from the eligible couriers and shows each one's load, so the least busy is an easy choice.
 * Used for pickups and for later legs: the caller supplies the title, the subject line and the call to make.
 * A refusal (not eligible, not based at this hub, parcel not ready) is shown as the server words it.
 */
export function AssignCourierDialog({
  title,
  subject,
  couriers,
  assign,
  onClose,
  onAssigned,
}: {
  title: string;
  subject: string;
  couriers: CourierWorkload[];
  assign: (courierUserId: string) => Promise<unknown>;
  onClose: () => void;
  onAssigned: (courier: CourierWorkload) => void;
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
      await assign(courier.courierUserId);
      onAssigned(courier);
    } catch (err) {
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
        className="max-h-[85vh] w-full max-w-md space-y-4 overflow-y-auto rounded-xl border bg-background p-6 shadow-lg"
        onClick={(event) => event.stopPropagation()}
      >
        <div className="flex items-start justify-between gap-4">
          <div className="space-y-1">
            <h2 id="assign-title" className="text-lg font-semibold">{title}</h2>
            <p className="text-sm text-muted-foreground">{subject}</p>
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

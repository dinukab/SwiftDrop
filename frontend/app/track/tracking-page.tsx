"use client";

import { FormEvent, useEffect, useRef, useState } from "react";
import { ApiError, getApiBaseUrl, formatApiError } from "@/lib/api";
import { Button } from "@/components/ui/button";
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";

const PARCEL_STATUSES = [
  "CREATED",
  "PICKED_UP",
  "IN_TRANSIT",
  "OUT_FOR_DELIVERY",
  "DELIVERED",
  "FAILED",
] as const;

const MILESTONE_STATUSES = [
  "CREATED",
  "PICKED_UP",
  "IN_TRANSIT",
  "OUT_FOR_DELIVERY",
  "DELIVERED",
] as const;

type ParcelStatus = (typeof PARCEL_STATUSES)[number];

interface PublicStatusEvent {
  status: ParcelStatus;
  occurredAt: string;
}

interface PublicTrackingView {
  trackingNumber: string;
  status: ParcelStatus;
  pickupCity: string;
  dropoffCity: string;
  history: PublicStatusEvent[];
}

interface StatusUpdate {
  status: ParcelStatus;
  occurredAt: string;
  sequence: number;
}

type ConnectionState = "idle" | "loading" | "connecting" | "connected" | "reconnecting" | "complete" | "error";

function isParcelStatus(value: unknown): value is ParcelStatus {
  return typeof value === "string" && PARCEL_STATUSES.some((status) => status === value);
}

function isPublicStatusEvent(value: unknown): value is PublicStatusEvent {
  if (typeof value !== "object" || value === null) {
    return false;
  }

  const event = value as Record<string, unknown>;
  return isParcelStatus(event.status) && typeof event.occurredAt === "string";
}

function isPublicTrackingView(value: unknown): value is PublicTrackingView {
  if (typeof value !== "object" || value === null) {
    return false;
  }

  const view = value as Record<string, unknown>;
  return (
    typeof view.trackingNumber === "string" &&
    isParcelStatus(view.status) &&
    typeof view.pickupCity === "string" &&
    typeof view.dropoffCity === "string" &&
    Array.isArray(view.history) &&
    view.history.every(isPublicStatusEvent)
  );
}

function isStatusUpdate(value: unknown): value is StatusUpdate {
  if (typeof value !== "object" || value === null) {
    return false;
  }

  const update = value as Record<string, unknown>;
  return (
    isParcelStatus(update.status) &&
    typeof update.occurredAt === "string" &&
    typeof update.sequence === "number" &&
    Number.isInteger(update.sequence)
  );
}

function formatStatus(status: ParcelStatus): string {
  return status
    .split("_")
    .map((word) => word.charAt(0) + word.slice(1).toLowerCase())
    .join(" ");
}

function formatDate(value: string): string {
  const date = new Date(value);
  return Number.isNaN(date.getTime())
    ? value
    : new Intl.DateTimeFormat(undefined, { dateStyle: "medium", timeStyle: "short" }).format(date);
}

function isFinalStatus(status: ParcelStatus): boolean {
  return status === "DELIVERED" || status === "FAILED";
}

function getProgressIndex(view: PublicTrackingView): number {
  const currentIndex = MILESTONE_STATUSES.findIndex((status) => status === view.status);
  return view.history.reduce(
    (furthestIndex, event) =>
      Math.max(furthestIndex, MILESTONE_STATUSES.findIndex((status) => status === event.status)),
    Math.max(currentIndex, 0),
  );
}

export function TrackingPage({ initialCode }: { initialCode: string }) {
  const [trackingInput, setTrackingInput] = useState(initialCode);
  const [requestedNumber, setRequestedNumber] = useState(initialCode.trim().toUpperCase());
  const [requestId, setRequestId] = useState(initialCode ? 1 : 0);
  const [trackingView, setTrackingView] = useState<PublicTrackingView | null>(null);
  const [connectionState, setConnectionState] = useState<ConnectionState>(
    initialCode ? "loading" : "idle",
  );
  const [errorMessage, setErrorMessage] = useState<string | null>(null);
  const lastSequence = useRef(0);

  useEffect(() => {
    if (!requestedNumber) {
      return;
    }

    const controller = new AbortController();
    let disposed = false;
    let eventSource: EventSource | null = null;
    lastSequence.current = 0;
    const failStream = (message: string) => {
      if (!disposed) {
        setConnectionState("error");
        setErrorMessage(message);
      }
      eventSource?.close();
    };

    const connect = async () => {
      try {
        const baseUrl = getApiBaseUrl().replace(/\/+$/, "");
        const encodedNumber = encodeURIComponent(requestedNumber);
        const snapshotResponse = await fetch(
          `${baseUrl}/api/v1/tracked-parcels/${encodedNumber}`,
          { signal: controller.signal, headers: { Accept: "application/json" } },
        );

        if (!snapshotResponse.ok) {
          if (snapshotResponse.status === 404) {
            throw new ApiError(404, "No parcel was found with that tracking number.");
          }
          if (snapshotResponse.status === 503) {
            const retryAfter = snapshotResponse.headers.get("Retry-After");
            throw new ApiError(
              503,
              `Tracking is temporarily unavailable.${retryAfter ? ` Try again in ${retryAfter} seconds.` : ""}`,
            );
          }
          throw new ApiError(snapshotResponse.status, "Unable to look up this tracking number.");
        }

        const initialView: unknown = await snapshotResponse.json();
        if (!isPublicTrackingView(initialView)) {
          failStream("The tracking service returned an unexpected response. Please try again later.");
          return;
        }
        if (disposed) {
          return;
        }

        setTrackingView(initialView);
        lastSequence.current = initialView.history.length;
        if (isFinalStatus(initialView.status)) {
          setConnectionState("complete");
          return;
        }

        setConnectionState("connecting");
        eventSource = new EventSource(
          `${baseUrl}/api/v1/tracked-parcels/${encodedNumber}/events`,
        );

        eventSource.onopen = () => {
          if (disposed) {
            return;
          }
          setConnectionState("connected");
          setErrorMessage(null);
        };

        eventSource.addEventListener("snapshot", (event) => {
          if (disposed) {
            return;
          }
          let data: unknown;
          try {
            data = JSON.parse((event as MessageEvent<string>).data);
          } catch {
            failStream("Could not read the initial live tracking update.");
            return;
          }

          if (!isPublicTrackingView(data)) {
            failStream("The tracking service returned an unexpected live update.");
            return;
          }

          setTrackingView(data);
          lastSequence.current = data.history.length;
          if (isFinalStatus(data.status)) {
            setConnectionState("complete");
            eventSource?.close();
          }
        });

        eventSource.addEventListener("status", (event) => {
          if (disposed) {
            return;
          }
          let data: unknown;
          try {
            data = JSON.parse((event as MessageEvent<string>).data);
          } catch {
            failStream("Could not read a live status update.");
            return;
          }

          if (!isStatusUpdate(data)) {
            failStream("The tracking service returned an unexpected status update.");
            return;
          }
          if (data.sequence <= lastSequence.current) {
            return;
          }

          lastSequence.current = data.sequence;
          setTrackingView((current) =>
            current
              ? {
                  ...current,
                  status: data.status,
                  history: [...current.history, { status: data.status, occurredAt: data.occurredAt }],
                }
              : current,
          );
          if (isFinalStatus(data.status)) {
            setConnectionState("complete");
            eventSource?.close();
          }
        });

        eventSource.onerror = () => {
          if (disposed) {
            return;
          }
          if (eventSource?.readyState === EventSource.CONNECTING) {
            setConnectionState("reconnecting");
            setErrorMessage(
              "The live connection was interrupted. Your browser is reconnecting automatically. If the server is at capacity (503), wait 30 seconds before trying again.",
            );
          } else {
            setConnectionState("error");
            setErrorMessage(
              "The live tracking stream could not be opened. Please try again; a temporary capacity limit asks clients to wait 30 seconds.",
            );
          }
        };
      } catch (error) {
        if (disposed || (error instanceof DOMException && error.name === "AbortError")) {
          return;
        }

        setConnectionState("error");
        setErrorMessage(
          error instanceof ApiError && error.status === 404
            ? "No parcel was found with that tracking number."
            : formatApiError(error),
        );
      }
    };

    void connect();

    return () => {
      disposed = true;
      controller.abort();
      eventSource?.close();
    };
  }, [requestedNumber, requestId]);

  function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    const normalized = trackingInput.trim().toUpperCase();
    if (!normalized) {
      setErrorMessage("Enter a tracking number to continue.");
      return;
    }

    setRequestedNumber(normalized);
    setRequestId((current) => current + 1);
    setTrackingView(null);
    setErrorMessage(null);
    setConnectionState("loading");
  }

  const connectionLabel: Record<ConnectionState, string> = {
    idle: "Ready to track",
    loading: "Looking up parcel",
    connecting: "Connecting to live updates",
    connected: "Live updates connected",
    reconnecting: "Reconnecting",
    complete: "Delivery complete",
    error: "Tracking unavailable",
  };
  const progressIndex = trackingView ? getProgressIndex(trackingView) : 0;
  const progressPercent = (progressIndex / (MILESTONE_STATUSES.length - 1)) * 100;

  return (
    <main className="container mx-auto flex-1 max-w-2xl space-y-4 px-4 py-12">
      <div className="inline-flex items-center gap-2 rounded-full border border-primary/20 bg-accent px-3 py-1 text-xs font-semibold text-primary">
        <span className="size-1.5 rounded-full bg-primary" />
        Live Tracking · SSE
      </div>

      <Card className="rounded-xl border-border bg-card shadow-xs">
        <CardHeader>
          <CardTitle className="text-xl font-bold text-foreground">Public Parcel Tracking</CardTitle>
          <CardDescription className="text-sm text-muted-foreground">
            Enter your tracking number to see parcel status changes as they happen.
          </CardDescription>
        </CardHeader>
        <CardContent className="space-y-4">
          <form onSubmit={handleSubmit} className="flex flex-col gap-2 sm:flex-row">
            <div className="flex-1 space-y-1">
              <Label htmlFor="trackingNumber" className="sr-only">
                Tracking number
              </Label>
              <Input
                id="trackingNumber"
                name="code"
                value={trackingInput}
                onChange={(event) => setTrackingInput(event.target.value)}
                placeholder="Enter tracking number (e.g. SD7K3M9Q2XWB)"
                aria-label="Tracking number"
                autoComplete="off"
                required
                className="bg-background text-sm"
              />
            </div>
            <Button type="submit" className="font-semibold shadow-xs">
              Track parcel
            </Button>
          </form>

          <p className="text-xs text-muted-foreground">
            Status and milestone timestamps are shown here. This page updates without needing a refresh.
          </p>

          {!trackingView && connectionState === "loading" && (
            <p role="status" aria-live="polite" className="text-sm text-muted-foreground">
              Looking up parcel…
            </p>
          )}

          {errorMessage && (
            <p role="alert" className="rounded-lg border border-destructive/30 bg-destructive/5 px-3 py-2 text-sm text-destructive">
              {errorMessage}
            </p>
          )}

          {trackingView && (
            <section aria-label="Parcel tracking details" className="space-y-5 border-t border-border pt-5">
              <div className="flex flex-wrap items-start justify-between gap-3">
                <div>
                  <p className="text-xs text-muted-foreground">Tracking number</p>
                  <p className="font-semibold">{trackingView.trackingNumber}</p>
                </div>
                <span
                  aria-live="polite"
                  className="rounded-full bg-accent px-3 py-1 text-sm font-semibold text-primary"
                >
                  {formatStatus(trackingView.status)}
                </span>
              </div>

              <p
                aria-live="polite"
                className="text-sm text-muted-foreground"
              >
                {connectionLabel[connectionState]}
              </p>

              <div className="grid gap-3 text-sm sm:grid-cols-2">
                <p>
                  <span className="text-muted-foreground">Pickup: </span>
                  {trackingView.pickupCity}
                </p>
                <p>
                  <span className="text-muted-foreground">Destination: </span>
                  {trackingView.dropoffCity}
                </p>
              </div>

              <div className="space-y-3">
                <h2 className="text-sm font-semibold">Delivery progress</h2>
                <div className="relative pt-1">
                  <div
                    aria-hidden="true"
                    className="absolute inset-x-[10%] top-3 h-0.5 rounded-full bg-muted"
                  >
                    <div
                      className="h-full rounded-full bg-primary transition-[width] duration-300"
                      style={{ width: `${progressPercent}%` }}
                    />
                  </div>
                  <ol aria-label="Delivery milestones" className="relative grid grid-cols-5 gap-1">
                    {MILESTONE_STATUSES.map((status, index) => {
                      const reached = index <= progressIndex;
                      const current = trackingView.status === status;

                      return (
                        <li
                          key={status}
                          aria-current={current ? "step" : undefined}
                          className="flex min-w-0 flex-col items-center gap-2 text-center text-xs"
                        >
                          <span
                            aria-hidden="true"
                            className={`size-3 rounded-full border ${
                              reached
                                ? "border-primary bg-primary"
                                : "border-muted-foreground/40 bg-background"
                            } ${current ? "ring-4 ring-primary/15" : ""}`}
                          />
                          <span className={reached ? "font-medium" : "text-muted-foreground"}>
                            {formatStatus(status)}
                          </span>
                        </li>
                      );
                    })}
                  </ol>
                </div>
              </div>

              <div className="space-y-3">
                <h2 className="text-sm font-semibold">Status history</h2>
                <ol className="space-y-3">
                  {trackingView.history.map((item, index) => {
                    const failed = item.status === "FAILED";

                    return (
                      <li key={`${item.occurredAt}-${index}`} className="flex gap-3 text-sm">
                        <span
                          className={`mt-1.5 size-2 shrink-0 rounded-full ${
                            failed ? "bg-destructive" : "bg-primary"
                          }`}
                        />
                        <span className="flex flex-wrap items-baseline justify-between gap-x-3 gap-y-1">
                          <span className={`font-medium ${failed ? "text-destructive" : ""}`}>
                            {formatStatus(item.status)}
                          </span>
                          <time dateTime={item.occurredAt} className="text-muted-foreground">
                            {formatDate(item.occurredAt)}
                          </time>
                        </span>
                      </li>
                    );
                  })}
                </ol>
              </div>
            </section>
          )}
        </CardContent>
      </Card>
    </main>
  );
}

"use client";

import React, { useEffect, useState } from "react";
import Link from "next/link";
import { AlertCircle, BellOff, CheckCheck, ChevronLeft, ChevronRight, Loader2 } from "lucide-react";
import { useAuth } from "@/lib/auth-context";
import { formatApiError, type PageResponse } from "@/lib/api";
import {
  getNotifications,
  markAllNotificationsRead,
  markNotificationRead,
  type AppNotification,
} from "@/lib/notifications";
import { Button } from "@/components/ui/button";

const PAGE_SIZE = 20;

export default function CustomerNotificationsPage() {
  const { token, isAuthenticated } = useAuth();
  const [inbox, setInbox] = useState<PageResponse<AppNotification> | null>(null);
  const [page, setPage] = useState(0);
  const [reloads, setReloads] = useState(0);
  const [error, setError] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);

  // State is only set inside the promise callbacks, after the fetch has completed.
  useEffect(() => {
    if (!isAuthenticated || !token) return;
    let current = true;
    getNotifications(token, page, PAGE_SIZE)
      .then((result) => {
        if (!current) return;
        setInbox(result);
        setError(null);
      })
      .catch((err: unknown) => {
        if (current) setError(formatApiError(err));
      });
    return () => {
      current = false;
    };
  }, [isAuthenticated, token, page, reloads]);

  const loading = inbox === null && error === null;
  const items = inbox?.items ?? [];
  const unread = items.filter((item) => !item.read).length;

  const readOne = (item: AppNotification) => {
    if (!token || item.read) return;
    markNotificationRead(token, item.id)
      .then(() => setReloads((n) => n + 1))
      .catch((err: unknown) => setError(formatApiError(err)));
  };

  const readAll = () => {
    if (!token) return;
    setBusy(true);
    markAllNotificationsRead(token)
      .then(() => setReloads((n) => n + 1))
      .catch((err: unknown) => setError(formatApiError(err)))
      .finally(() => setBusy(false));
  };

  return (
    <div className="space-y-6">
      <div className="flex flex-col items-start justify-between gap-3 sm:flex-row sm:items-center">
        <div>
          <h2 className="text-xl font-bold tracking-tight">Notifications</h2>
          <p className="text-sm text-muted-foreground">
            Messages about your parcels: pickup, out for delivery, delivered, or a failed delivery.
          </p>
        </div>
        <Button variant="outline" size="sm" onClick={readAll} disabled={busy || unread === 0}>
          {busy ? <Loader2 className="mr-2 h-4 w-4 animate-spin" /> : <CheckCheck className="mr-2 h-4 w-4" />}
          Mark all as read
        </Button>
      </div>

      {error && (
        <div role="alert" className="flex items-center gap-3 rounded-lg border border-destructive/20 bg-destructive/10 p-4 text-destructive">
          <AlertCircle className="h-5 w-5 flex-shrink-0" />
          <p className="text-sm">{error}</p>
        </div>
      )}

      {loading && (
        <div role="status" className="flex justify-center py-12">
          <Loader2 className="h-8 w-8 animate-spin text-muted-foreground" />
          <span className="sr-only">Loading notifications</span>
        </div>
      )}

      {inbox && items.length === 0 && !error && (
        <div className="space-y-2 py-12 text-center">
          <BellOff className="mx-auto h-10 w-10 text-muted-foreground" />
          <p className="text-sm font-medium">No notifications yet</p>
          <p className="text-xs text-muted-foreground">
            You will see a message here when a courier picks up your parcel, when it is out for delivery, and when it is delivered.
          </p>
        </div>
      )}

      {items.length > 0 && (
        <ul className="space-y-2">
          {items.map((item) => (
            <li
              key={item.id}
              className={`flex flex-wrap items-start justify-between gap-3 rounded-lg border p-4 ${
                item.read ? "bg-background" : "border-primary/30 bg-primary/5"
              }`}
            >
              <div className="space-y-1">
                <p className={`text-sm ${item.read ? "text-muted-foreground" : "font-semibold text-foreground"}`}>
                  {!item.read && <span className="mr-2 inline-block size-2 rounded-full bg-primary" aria-label="Unread" />}
                  {item.message}
                </p>
                <p className="text-xs text-muted-foreground">
                  {new Date(item.createdAt).toLocaleString()} ·{" "}
                  <Link href={`/track?code=${encodeURIComponent(item.trackingNumber)}`} className="underline underline-offset-2">
                    Track {item.trackingNumber}
                  </Link>
                </p>
              </div>
              {!item.read && (
                <Button size="xs" variant="ghost" onClick={() => readOne(item)}>
                  Mark as read
                </Button>
              )}
            </li>
          ))}
        </ul>
      )}

      {inbox && inbox.totalPages > 1 && (
        <div className="flex items-center justify-between text-xs text-muted-foreground">
          <span>Page {page + 1} of {inbox.totalPages}</span>
          <div className="flex gap-2">
            <Button variant="outline" size="sm" disabled={page === 0} onClick={() => setPage((p) => Math.max(0, p - 1))}>
              <ChevronLeft className="mr-1 h-4 w-4" />
              Previous
            </Button>
            <Button variant="outline" size="sm" disabled={page + 1 >= inbox.totalPages} onClick={() => setPage((p) => p + 1)}>
              Next
              <ChevronRight className="ml-1 h-4 w-4" />
            </Button>
          </div>
        </div>
      )}
    </div>
  );
}

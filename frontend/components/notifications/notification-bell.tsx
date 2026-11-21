"use client";

import React, { useEffect, useState } from "react";
import Link from "next/link";
import { Bell } from "lucide-react";
import { useAuth } from "@/lib/auth-context";
import { getUnreadCount } from "@/lib/notifications";

const POLL_MS = 30_000;

/**
 * The notification bell with the unread count. It links to the inbox and refreshes the count every 30 seconds
 * while the page is open. A failed refresh keeps the last known count: the bell is a convenience and never shows
 * an error.
 */
export function NotificationBell() {
  const { token, isAuthenticated } = useAuth();
  const [count, setCount] = useState(0);

  // State is only set inside the promise callback, after the fetch has completed.
  useEffect(() => {
    if (!isAuthenticated || !token) return;
    let current = true;
    const refresh = () => {
      getUnreadCount(token)
        .then((result) => {
          if (current) setCount(result.count);
        })
        .catch(() => {
          /* keep the last known count */
        });
    };
    refresh();
    const timer = window.setInterval(refresh, POLL_MS);
    return () => {
      current = false;
      window.clearInterval(timer);
    };
  }, [isAuthenticated, token]);

  const label = count > 0 ? `Notifications, ${count} unread` : "Notifications";

  return (
    <Link
      href="/customer/notifications"
      aria-label={label}
      className="relative inline-flex size-8 items-center justify-center rounded-full border border-border bg-background text-muted-foreground transition-colors hover:text-foreground"
    >
      <Bell className="size-4" aria-hidden="true" />
      {count > 0 && (
        <span className="absolute -right-1 -top-1 min-w-4 rounded-full bg-primary px-1 text-center text-[10px] font-bold leading-4 text-primary-foreground">
          {count > 99 ? "99+" : count}
        </span>
      )}
    </Link>
  );
}

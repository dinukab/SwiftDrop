"use client";

import React, { useEffect, useState } from "react";
import Link from "next/link";
import { api, ApiError, formatApiError, type CourierApprovalResponse } from "@/lib/api";
import { Card, CardDescription, CardFooter, CardHeader, CardTitle } from "@/components/ui/card";
import { Clock3, ShieldAlert } from "lucide-react";

/**
 * Blocks the courier workspace UI until the courier registration is APPROVED.
 * Admin-created couriers have no approval record (404) and are allowed through.
 * Backend token authority remains the security control.
 */
export function CourierApprovalGuard({ children }: { children: React.ReactNode }) {
  const [approval, setApproval] = useState<CourierApprovalResponse | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [isLoading, setIsLoading] = useState(true);

  useEffect(() => {
    let ignore = false;

    async function loadApproval() {
      try {
        const record = await api.get<CourierApprovalResponse>("/api/v1/courier-registrations/me");
        if (!ignore) {
          setApproval(record);
          setError(null);
          setIsLoading(false);
        }
      } catch (err: unknown) {
        if (!ignore) {
          if (err instanceof ApiError && err.status === 404) {
            // Admin-created courier: no approval record means approved.
            setApproval(null);
            setError(null);
          } else {
            setError(formatApiError(err));
          }
          setIsLoading(false);
        }
      }
    }

    loadApproval();
    return () => {
      ignore = true;
    };
  }, []);

  if (isLoading) {
    return (
      <div className="flex min-h-[40vh] items-center justify-center" role="status" aria-live="polite">
        <div className="flex flex-col items-center gap-3 text-center">
          <div className="size-8 animate-spin rounded-full border-2 border-primary border-t-transparent" />
          <p className="text-sm text-muted-foreground">Checking courier approval status...</p>
        </div>
      </div>
    );
  }

  if (error) {
    return (
      <Card className="mx-auto max-w-lg rounded-xl border-destructive/20 bg-card shadow-sm">
        <CardHeader className="text-center space-y-2">
          <div className="mx-auto inline-flex size-12 items-center justify-center rounded-full bg-destructive/10 text-destructive">
            <ShieldAlert className="size-6" aria-hidden="true" />
          </div>
          <CardTitle className="text-xl font-bold text-foreground">Unable to verify courier status</CardTitle>
          <CardDescription className="text-sm text-muted-foreground">{error}</CardDescription>
        </CardHeader>
        <CardFooter className="justify-center">
          <Link
            href="/"
            className="inline-flex h-9 items-center justify-center rounded-md border border-border bg-background px-4 text-sm font-medium hover:bg-accent"
          >
            Return to Home
          </Link>
        </CardFooter>
      </Card>
    );
  }

  if (approval && approval.status !== "APPROVED") {
    return (
      <Card className="mx-auto max-w-lg rounded-xl border-amber-500/20 bg-card shadow-sm">
        <CardHeader className="text-center space-y-2">
          <div className="mx-auto inline-flex size-12 items-center justify-center rounded-full bg-amber-500/10 text-amber-700 dark:text-amber-400">
            <Clock3 className="size-6" aria-hidden="true" />
          </div>
          <div className="inline-flex mx-auto items-center px-2.5 py-0.5 rounded-full text-xs font-semibold uppercase tracking-wider bg-amber-500/10 text-amber-700 dark:text-amber-400 border border-amber-500/20">
            {approval.status}
          </div>
          <CardTitle className="text-xl font-bold text-foreground">Courier access pending</CardTitle>
          <CardDescription className="text-sm text-muted-foreground">
            {approval.status === "PENDING"
              ? "Your courier registration is awaiting administrator approval. Courier features stay locked until a ADMIN approves your account."
              : `Your courier registration was rejected${approval.rejectionReason ? `: ${approval.rejectionReason}` : "."}`}
          </CardDescription>
        </CardHeader>
        <CardFooter className="justify-center">
          <Link
            href="/"
            className="inline-flex h-9 items-center justify-center rounded-md border border-border bg-background px-4 text-sm font-medium hover:bg-accent"
          >
            Return to Home
          </Link>
        </CardFooter>
      </Card>
    );
  }

  return <>{children}</>;
}

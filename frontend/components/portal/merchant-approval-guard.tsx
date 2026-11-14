"use client";

import React, { useEffect, useState } from "react";
import Link from "next/link";
import { api, formatApiError, type MerchantResponse } from "@/lib/api";
import { Card, CardContent, CardDescription, CardFooter, CardHeader, CardTitle } from "@/components/ui/card";
import { Clock3, ShieldAlert } from "lucide-react";

/**
 * Blocks the merchant workspace UI until the merchant profile is APPROVED.
 * Backend capability checks remain authoritative (D-22).
 */
export function MerchantApprovalGuard({ children }: { children: React.ReactNode }) {
  const [merchant, setMerchant] = useState<MerchantResponse | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [isLoading, setIsLoading] = useState(true);

  useEffect(() => {
    let ignore = false;

    async function loadMerchant() {
      try {
        const profile = await api.get<MerchantResponse>("/api/v1/merchants/me");
        if (!ignore) {
          setMerchant(profile);
          setError(null);
          setIsLoading(false);
        }
      } catch (err: unknown) {
        if (!ignore) {
          setError(formatApiError(err));
          setIsLoading(false);
        }
      }
    }

    loadMerchant();
    return () => {
      ignore = true;
    };
  }, []);

  if (isLoading) {
    return (
      <div className="flex min-h-[40vh] items-center justify-center" role="status" aria-live="polite">
        <div className="flex flex-col items-center gap-3 text-center">
          <div className="size-8 animate-spin rounded-full border-2 border-primary border-t-transparent" />
          <p className="text-sm text-muted-foreground">Checking merchant approval status...</p>
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
          <CardTitle className="text-xl font-bold text-foreground">Unable to verify merchant status</CardTitle>
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

  if (merchant && merchant.status !== "APPROVED") {
    return (
      <Card className="mx-auto max-w-lg rounded-xl border-amber-500/20 bg-card shadow-sm">
        <CardHeader className="text-center space-y-2">
          <div className="mx-auto inline-flex size-12 items-center justify-center rounded-full bg-amber-500/10 text-amber-700 dark:text-amber-400">
            <Clock3 className="size-6" aria-hidden="true" />
          </div>
          <div className="inline-flex mx-auto items-center px-2.5 py-0.5 rounded-full text-xs font-semibold uppercase tracking-wider bg-amber-500/10 text-amber-700 dark:text-amber-400 border border-amber-500/20">
            {merchant.status}
          </div>
          <CardTitle className="text-xl font-bold text-foreground">Merchant access pending</CardTitle>
          <CardDescription className="text-sm text-muted-foreground">
            {merchant.status === "PENDING"
              ? "Your merchant application is awaiting administrator approval. Portal features stay locked until a ADMIN approves your account."
              : merchant.status === "REJECTED"
                ? `Your merchant application was rejected${merchant.rejectionReason ? `: ${merchant.rejectionReason}` : "."}`
                : "Your merchant account is suspended. Contact an administrator for help."}
          </CardDescription>
        </CardHeader>
        <CardContent className="text-xs text-muted-foreground text-center">
          Business: <span className="font-semibold text-foreground">{merchant.businessName}</span>
        </CardContent>
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

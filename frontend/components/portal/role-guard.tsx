"use client";

import React, { useEffect } from "react";
import { useRouter, usePathname } from "next/navigation";
import { useAuth } from "@/lib/auth-context";
import { sanitizeInternalRedirect } from "@/lib/redirect";
import { Button } from "@/components/ui/button";
import { Card, CardContent, CardDescription, CardFooter, CardHeader, CardTitle } from "@/components/ui/card";

export type BackendRole = "CUSTOMER" | "MERCHANT" | "COURIER" | "DISPATCHER" | "ADMIN";

export interface RoleGuardProps {
  requiredRole: BackendRole;
  roleLabel?: string;
  children: React.ReactNode;
}

/**
 * Client-side route guard component for role-restricted portals.
 *
 * <p>Security Notice: This client guard manages UI routing convenience only.
 * System security and resource authorization are enforced strictly by the backend REST API
 * (docs/architecture.md §5, ADR 0001).
 */
export function RoleGuard({ requiredRole, children }: RoleGuardProps) {
  const { user, isAuthenticated, isLoading, hasRole, logout } = useAuth();
  const router = useRouter();
  const pathname = usePathname();

  useEffect(() => {
    if (!isLoading && !isAuthenticated) {
      const safeDestination = sanitizeInternalRedirect(pathname);
      const loginUrl = `/login?redirect=${encodeURIComponent(safeDestination)}`;
      router.replace(loginUrl);
    }
  }, [isLoading, isAuthenticated, pathname, router]);

  // Loading state while auth context initializes
  if (isLoading) {
    return (
      <main className="container mx-auto flex min-h-[50vh] max-w-4xl items-center justify-center px-4 py-12">
        <div className="flex flex-col items-center gap-3 text-center" role="status" aria-live="polite">
          <div className="size-8 animate-spin rounded-full border-2 border-primary border-t-transparent" />
          <p className="text-sm text-muted-foreground animate-pulse">Verifying workspace permissions...</p>
        </div>
      </main>
    );
  }

  // Unauthenticated: redirecting to login (destination preserved via ?redirect=)
  if (!isAuthenticated) {
    return (
      <main className="container mx-auto flex min-h-[50vh] max-w-4xl items-center justify-center px-4 py-12">
        <div className="flex flex-col items-center gap-2 text-center" role="status" aria-live="polite">
          <p className="text-sm text-muted-foreground">Redirecting to sign in...</p>
        </div>
      </main>
    );
  }

  // Authenticated but lacks the required role — minimal account-switch prompt.
  // Sign Out clears the session; the unauthenticated effect above then navigates to
  // /login?redirect=<this portal>, preserving the intended destination for the next login.
  if (!hasRole(requiredRole)) {
    return (
      <main className="container mx-auto flex min-h-[50vh] max-w-md items-center justify-center px-4 py-12">
        <Card className="w-full rounded-xl bg-card border-border shadow-xs text-center">
          <CardHeader className="space-y-2">
            <CardTitle className="text-lg font-bold text-foreground">
              You are signed in with a different account.
            </CardTitle>
            <CardDescription className="text-sm text-muted-foreground">
              Please sign out to continue.
            </CardDescription>
          </CardHeader>
          <CardContent>
            <p className="text-sm font-medium text-foreground break-all">{user?.email}</p>
          </CardContent>
          <CardFooter className="flex flex-col gap-2">
            <Button className="w-full" onClick={logout}>
              Sign Out
            </Button>
            <Button variant="outline" className="w-full" onClick={() => router.push("/")}>
              Return to Home
            </Button>
          </CardFooter>
        </Card>
      </main>
    );
  }

  // Authorized
  return <>{children}</>;
}

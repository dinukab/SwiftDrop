"use client";

import React, { useState } from "react";
import Link from "next/link";
import { useRouter, useSearchParams } from "next/navigation";
import { Eye, EyeOff } from "lucide-react";
import { api, formatApiError, getApiBaseUrl, type AccessTokenResponse } from "@/lib/api";
import { useAuth } from "@/lib/auth-context";
import { resolvePostLoginDestination, sanitizeInternalRedirect } from "@/lib/redirect";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import {
  AccountTabs,
  accountPanelId,
  accountTabId,
  parseAccountType,
  type AccountType,
} from "../AccountTabs";
import { AuthSplitLayout } from "../AuthSplitLayout";
import { LOGIN_VIEWS } from "./data";

const STAFF_PORTAL_PATHS = ["/admin", "/dispatcher", "/courier"];

export function LoginScreen() {
  const router = useRouter();
  const searchParams = useSearchParams();
  const { login } = useAuth();

  const [tab, setTab] = useState<AccountType>(() => parseAccountType(searchParams.get("tab")));
  const [email, setEmail] = useState("");
  const [password, setPassword] = useState("");
  const [showPassword, setShowPassword] = useState(false);
  const [isLoading, setIsLoading] = useState(false);
  const [errorMessage, setErrorMessage] = useState<string | null>(null);

  const registered = searchParams.get("registered") === "true";
  const expired = searchParams.get("expired") === "true";
  const redirectParam = searchParams.get("redirect");

  // A sign-in that returns to a staff portal (from the staff page or a portal's sign-in
  // redirect) is a staff sign-in: no Customer/Merchant tabs. This only changes what the page
  // shows; roles still come from the backend after sign-in.
  const isStaffSignIn = STAFF_PORTAL_PATHS.some((path) =>
    sanitizeInternalRedirect(redirectParam, "").startsWith(path)
  );
  const view = LOGIN_VIEWS[isStaffSignIn ? "staff" : tab];

  // Google button is enabled only when explicitly configured via environment flag (AC-2),
  // and only for customers: Google sign-in creates or finds customer accounts, never staff
  // or merchant accounts.
  const isGoogleLoginEnabled = process.env.NEXT_PUBLIC_ENABLE_GOOGLE_LOGIN === "true";
  const showGoogleLogin = isGoogleLoginEnabled && !isStaffSignIn && tab === "customer";

  function changeTab(next: AccountType) {
    setTab(next);
    // Keeps the tab in the URL (?tab=merchant) so links and reloads open the same tab.
    const params = new URLSearchParams(searchParams.toString());
    if (next === "customer") params.delete("tab");
    else params.set("tab", next);
    const query = params.toString();
    router.replace(query ? `/login?${query}` : "/login", { scroll: false });
  }

  async function handleSubmit(e: React.FormEvent<HTMLFormElement>) {
    e.preventDefault();
    if (isLoading) return;

    setErrorMessage(null);

    const trimmedEmail = email.trim();
    if (!trimmedEmail) {
      setErrorMessage("Please enter your email address.");
      return;
    }
    if (!password) {
      setErrorMessage("Please enter your password.");
      return;
    }

    setIsLoading(true);

    try {
      const response = await api.post<AccessTokenResponse>("/api/v1/auth/tokens", {
        email: trimmedEmail,
        password,
      });

      // Roles come from GET /api/v1/users/me after token issue — never from a client-submitted role.
      // A customer who signed in on the Merchant tab (or the reverse) still lands in their own portal.
      const user = await login(response.accessToken);
      router.push(resolvePostLoginDestination(redirectParam, user.roles));
    } catch (err: unknown) {
      setErrorMessage(formatApiError(err));
    } finally {
      setIsLoading(false);
    }
  }

  const form = (
    <form onSubmit={handleSubmit} noValidate className="space-y-5">
      {registered && (
        <div role="status" className="rounded-xl border border-brand/25 bg-brand-tint p-3 text-sm font-medium text-brand-text">
          Account created successfully! Please sign in with your email and password.
        </div>
      )}

      {expired && (
        <div role="alert" className="rounded-xl border border-line bg-surface p-3 text-sm font-medium text-ink-muted">
          Your session has expired. Please sign in again to continue.
        </div>
      )}

      {errorMessage && (
        <div
          role="alert"
          className="rounded-xl border border-destructive/20 bg-destructive/10 p-3 text-sm font-medium text-destructive"
        >
          {errorMessage}
        </div>
      )}

      <div className="space-y-1.5">
        <Label htmlFor="login-email" className="text-navy">
          Email address
        </Label>
        <Input
          id="login-email"
          type="email"
          autoComplete="email"
          placeholder="name@example.com"
          value={email}
          onChange={(e) => setEmail(e.target.value)}
          disabled={isLoading}
          required
          className="h-11 bg-white text-base sm:text-sm"
        />
      </div>

      <div className="space-y-1.5">
        <Label htmlFor="login-password" className="text-navy">
          Password
        </Label>
        <div className="relative">
          <Input
            id="login-password"
            type={showPassword ? "text" : "password"}
            autoComplete="current-password"
            placeholder="••••••••"
            value={password}
            onChange={(e) => setPassword(e.target.value)}
            disabled={isLoading}
            required
            className="h-11 bg-white pr-11 text-base sm:text-sm"
          />
          <button
            type="button"
            onClick={() => setShowPassword((shown) => !shown)}
            aria-label={showPassword ? "Hide password" : "Show password"}
            aria-pressed={showPassword}
            aria-controls="login-password"
            className="absolute inset-y-0 right-0 grid w-11 place-items-center rounded-r-lg text-ink-muted outline-none hover:text-navy focus-visible:ring-3 focus-visible:ring-brand"
          >
            {showPassword ? <EyeOff aria-hidden="true" className="size-4" /> : <Eye aria-hidden="true" className="size-4" />}
          </button>
        </div>
      </div>

      <button
        type="submit"
        disabled={isLoading}
        className="h-12 w-full rounded-xl bg-navy font-semibold text-white transition-colors outline-none hover:bg-navy-800 focus-visible:ring-3 focus-visible:ring-brand focus-visible:ring-offset-2 disabled:opacity-60"
      >
        {isLoading ? "Signing in..." : "Sign in"}
      </button>

      {showGoogleLogin && (
        <div>
          <div className="my-4 flex items-center gap-3 text-xs uppercase text-ink-muted">
            <span className="h-px flex-1 bg-line" />
            or
            <span className="h-px flex-1 bg-line" />
          </div>
          <Button
            type="button"
            variant="outline"
            className="h-11 w-full rounded-xl font-medium"
            onClick={() => {
              if (typeof window !== "undefined") {
                const targetPath = sanitizeInternalRedirect(redirectParam, "");
                if (targetPath && targetPath !== "/" && targetPath !== "/login") {
                  window.sessionStorage.setItem("swiftdrop_auth_redirect", targetPath);
                } else {
                  window.sessionStorage.removeItem("swiftdrop_auth_redirect");
                }
              }
              const target = new URL("/oauth2/authorization/google", getApiBaseUrl()).href;
              window.location.assign(target);
            }}
          >
            Sign in with Google
          </Button>
        </div>
      )}

      <p className="pt-1 text-center text-sm text-ink-muted">
        {view.signup ? (
          <>
            {view.signup.prompt}{" "}
            <Link
              href={view.signup.href}
              className="rounded-sm font-semibold text-brand-text underline-offset-4 outline-none hover:underline focus-visible:ring-3 focus-visible:ring-brand"
            >
              {view.signup.label}
            </Link>
          </>
        ) : (
          "Staff accounts are created by an administrator."
        )}
      </p>
    </form>
  );

  return (
    <AuthSplitLayout visual={view}>
      <h1 className="font-display text-3xl font-bold tracking-tight text-navy">
        {isStaffSignIn ? "Staff sign in" : "Welcome back"}
      </h1>
      <p className="mt-2 text-ink-muted">{view.description}</p>

      {isStaffSignIn ? (
        <div className="mt-8">{form}</div>
      ) : (
        <>
          <div className="mt-8">
            <AccountTabs idPrefix="login" active={tab} onChange={changeTab} disabled={isLoading} />
          </div>
          <div
            id={accountPanelId("login")}
            role="tabpanel"
            aria-labelledby={accountTabId("login", tab)}
            className="mt-6"
          >
            {form}
          </div>
        </>
      )}
    </AuthSplitLayout>
  );
}

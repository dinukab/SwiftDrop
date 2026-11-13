"use client";

import React, { Suspense, useState } from "react";
import Link from "next/link";
import { useRouter, useSearchParams } from "next/navigation";
import { api, ApiError, formatApiError, type MerchantResponse, type UserResponse } from "@/lib/api";
import { Button } from "@/components/ui/button";
import { Card, CardContent, CardDescription, CardFooter, CardHeader, CardTitle } from "@/components/ui/card";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { Eye, EyeOff } from "lucide-react";

const EMAIL_REGEX = /^[^\s@]+@[^\s@]+\.[^\s@]+$/;
const MAX_PASSWORD_BYTES = 72;

type AccountType = "customer" | "merchant";

function initialAccountType(typeParam: string | null): AccountType {
  return typeParam?.trim().toLowerCase() === "merchant" ? "merchant" : "customer";
}

function SignupForm() {
  const router = useRouter();
  const searchParams = useSearchParams();

  const [accountType, setAccountType] = useState<AccountType>(() =>
    initialAccountType(searchParams.get("type"))
  );
  const [fullName, setFullName] = useState("");
  const [email, setEmail] = useState("");
  const [password, setPassword] = useState("");
  const [confirmPassword, setConfirmPassword] = useState("");
  const [showPassword, setShowPassword] = useState(false);
  const [showConfirmPassword, setShowConfirmPassword] = useState(false);
  const [businessName, setBusinessName] = useState("");
  const [contactPhone, setContactPhone] = useState("");
  const [isLoading, setIsLoading] = useState(false);
  const [errorMessage, setErrorMessage] = useState<string | null>(null);
  const [isConflict, setIsConflict] = useState(false);
  const [merchantSubmitted, setMerchantSubmitted] = useState(false);

  async function handleSubmit(e: React.FormEvent<HTMLFormElement>) {
    e.preventDefault();
    if (isLoading) return;

    setErrorMessage(null);
    setIsConflict(false);

    const trimmedName = fullName.trim();
    const trimmedEmail = email.trim().toLowerCase();
    const trimmedBusinessName = businessName.trim();
    const trimmedPhone = contactPhone.trim();

    if (!trimmedName) {
      setErrorMessage("Please enter your full name.");
      return;
    }
    if (trimmedName.length > 200) {
      setErrorMessage("Full name must be at most 200 characters.");
      return;
    }

    if (!trimmedEmail) {
      setErrorMessage("Please enter your email address.");
      return;
    }
    if (!EMAIL_REGEX.test(trimmedEmail) || trimmedEmail.length > 320) {
      setErrorMessage("Please enter a valid email address (max 320 characters).");
      return;
    }

    if (accountType === "merchant") {
      if (!trimmedBusinessName) {
        setErrorMessage("Please enter your business / store name.");
        return;
      }
      if (trimmedBusinessName.length > 200) {
        setErrorMessage("Business / store name must be at most 200 characters.");
        return;
      }
      if (trimmedPhone.length > 20) {
        setErrorMessage("Contact phone must be at most 20 characters.");
        return;
      }
    }

    if (!password) {
      setErrorMessage("Please enter a password.");
      return;
    }
    if (password.length < 8) {
      setErrorMessage("Password must be at least 8 characters long.");
      return;
    }
    const passwordBytes = new TextEncoder().encode(password).length;
    if (passwordBytes > MAX_PASSWORD_BYTES) {
      setErrorMessage(`Password exceeds the maximum length of ${MAX_PASSWORD_BYTES} bytes.`);
      return;
    }

    if (password !== confirmPassword) {
      setErrorMessage("Passwords do not match. Please re-enter your password.");
      return;
    }

    setIsLoading(true);

    try {
      if (accountType === "customer") {
        await api.post<UserResponse>("/api/v1/users", {
          fullName: trimmedName,
          email: trimmedEmail,
          password,
        });
        router.push("/login?registered=true");
        return;
      }

      await api.post<MerchantResponse>("/api/v1/merchants", {
        fullName: trimmedName,
        email: trimmedEmail,
        password,
        businessName: trimmedBusinessName,
        ...(trimmedPhone ? { contactPhone: trimmedPhone } : {}),
      });
      setMerchantSubmitted(true);
    } catch (err: unknown) {
      if (err instanceof ApiError && err.status === 409) {
        setIsConflict(true);
        setErrorMessage(
          "An account with this email address already exists. Please sign in with your credentials."
        );
      } else {
        setErrorMessage(formatApiError(err));
      }
    } finally {
      setIsLoading(false);
    }
  }

  if (merchantSubmitted) {
    return (
      <main className="container mx-auto flex-1 flex items-center justify-center px-4 py-12 min-h-[calc(100vh-3.5rem)]">
        <Card className="w-full max-w-md rounded-xl bg-card border-border shadow-xs">
          <CardHeader className="space-y-1 text-center">
            <div className="inline-flex items-center justify-center gap-1.5 mx-auto mb-2 text-xs font-semibold uppercase tracking-wider text-primary">
              <span className="size-1.5 rounded-full bg-primary" />
              Pending Admin Approval
            </div>
            <CardTitle className="text-2xl font-bold tracking-tight text-foreground">
              Application received
            </CardTitle>
            <CardDescription className="text-sm text-muted-foreground">
              Merchant registration submitted! Your account will be activated once an Administrator
              approves your application.
            </CardDescription>
          </CardHeader>
          <CardFooter className="flex flex-col gap-2 pt-2 border-t border-border/60 text-center text-xs text-muted-foreground">
            <p>
              After approval,{" "}
              <Link
                href="/login"
                className="font-semibold text-primary underline-offset-4 hover:underline"
              >
                sign in
              </Link>{" "}
              to open the merchant portal.
            </p>
          </CardFooter>
        </Card>
      </main>
    );
  }

  return (
    <main className="container mx-auto flex-1 flex items-center justify-center px-4 py-12 min-h-[calc(100vh-3.5rem)]">
      <Card className="w-full max-w-md rounded-xl bg-card border-border shadow-xs">
        <CardHeader className="space-y-1 text-center">
          <div className="inline-flex items-center justify-center gap-1.5 mx-auto mb-2 text-xs font-semibold uppercase tracking-wider text-primary">
            <span className="size-1.5 rounded-full bg-primary" />
            {accountType === "merchant" ? "Merchant Registration" : "Customer Registration"}
          </div>
          <CardTitle className="text-2xl font-bold tracking-tight text-foreground">
            Create an account
          </CardTitle>
          <CardDescription className="text-sm text-muted-foreground">
            {accountType === "merchant"
              ? "Register your business. Access starts after an administrator approves your application."
              : "Get started with SwiftDrop parcel bookings and live delivery tracking."}
          </CardDescription>
        </CardHeader>

        <form onSubmit={handleSubmit} noValidate>
          <CardContent className="space-y-4">
            <div className="space-y-2">
              <Label id="account-type-label">Account type</Label>
              <div
                role="tablist"
                aria-labelledby="account-type-label"
                className="grid grid-cols-2 gap-2 rounded-lg border border-border bg-muted/40 p-1"
              >
                <button
                  type="button"
                  role="tab"
                  aria-selected={accountType === "customer"}
                  onClick={() => setAccountType("customer")}
                  disabled={isLoading}
                  className={`rounded-md px-3 py-2 text-xs font-semibold transition-colors ${
                    accountType === "customer"
                      ? "bg-card text-foreground shadow-xs"
                      : "text-muted-foreground hover:text-foreground"
                  }`}
                >
                  Customer
                </button>
                <button
                  type="button"
                  role="tab"
                  aria-selected={accountType === "merchant"}
                  onClick={() => setAccountType("merchant")}
                  disabled={isLoading}
                  className={`rounded-md px-3 py-2 text-xs font-semibold transition-colors ${
                    accountType === "merchant"
                      ? "bg-card text-foreground shadow-xs"
                      : "text-muted-foreground hover:text-foreground"
                  }`}
                >
                  Merchant (Business)
                </button>
              </div>
            </div>

            {errorMessage && (
              <div
                role="alert"
                className="rounded-lg border border-destructive/20 bg-destructive/10 p-3 text-xs text-destructive font-medium space-y-1.5"
              >
                <p>{errorMessage}</p>
                {isConflict && (
                  <p>
                    <Link
                      href="/login"
                      className="font-semibold underline underline-offset-4 hover:text-foreground"
                    >
                      Click here to sign in &rarr;
                    </Link>
                  </p>
                )}
              </div>
            )}

            <div className="space-y-1.5">
              <Label htmlFor="signup-name">Full name</Label>
              <Input
                id="signup-name"
                type="text"
                autoComplete="name"
                placeholder="Jane Doe"
                value={fullName}
                onChange={(e) => setFullName(e.target.value)}
                disabled={isLoading}
                maxLength={200}
                required
                className="bg-background text-sm"
              />
            </div>

            {accountType === "merchant" && (
              <>
                <div className="space-y-1.5">
                  <Label htmlFor="signup-business-name">Business / Store Name</Label>
                  <Input
                    id="signup-business-name"
                    type="text"
                    autoComplete="organization"
                    placeholder="Ada's Parcel Shop"
                    value={businessName}
                    onChange={(e) => setBusinessName(e.target.value)}
                    disabled={isLoading}
                    maxLength={200}
                    required
                    className="bg-background text-sm"
                  />
                </div>
                <div className="space-y-1.5">
                  <Label htmlFor="signup-contact-phone">Contact phone (optional)</Label>
                  <Input
                    id="signup-contact-phone"
                    type="tel"
                    autoComplete="tel"
                    placeholder="+94771234567"
                    value={contactPhone}
                    onChange={(e) => setContactPhone(e.target.value)}
                    disabled={isLoading}
                    maxLength={20}
                    className="bg-background text-sm"
                  />
                </div>
              </>
            )}

            <div className="space-y-1.5">
              <Label htmlFor="signup-email">Email address</Label>
              <Input
                id="signup-email"
                type="email"
                autoComplete="email"
                placeholder="name@example.com"
                value={email}
                onChange={(e) => setEmail(e.target.value)}
                disabled={isLoading}
                maxLength={320}
                required
                className="bg-background text-sm"
              />
            </div>

            <div className="space-y-1.5">
              <Label htmlFor="signup-password">Password</Label>
              <div className="relative">
                <Input
                  id="signup-password"
                  type={showPassword ? "text" : "password"}
                  autoComplete="new-password"
                  placeholder="At least 8 characters"
                  value={password}
                  onChange={(e) => setPassword(e.target.value)}
                  disabled={isLoading}
                  required
                  className="bg-background text-sm pr-10"
                />
                <button
                  type="button"
                  className="absolute inset-y-0 right-0 flex items-center px-3 text-muted-foreground hover:text-foreground"
                  onClick={() => setShowPassword((prev) => !prev)}
                  disabled={isLoading}
                  aria-label={showPassword ? "Hide password" : "Show password"}
                >
                  {showPassword ? <EyeOff className="size-4" /> : <Eye className="size-4" />}
                </button>
              </div>
              <p className="text-[11px] text-muted-foreground">
                Minimum 8 characters, maximum 72 bytes.
              </p>
            </div>

            <div className="space-y-1.5">
              <Label htmlFor="signup-confirm-password">Confirm password</Label>
              <div className="relative">
                <Input
                  id="signup-confirm-password"
                  type={showConfirmPassword ? "text" : "password"}
                  autoComplete="new-password"
                  placeholder="Re-enter your password"
                  value={confirmPassword}
                  onChange={(e) => setConfirmPassword(e.target.value)}
                  disabled={isLoading}
                  required
                  className="bg-background text-sm pr-10"
                />
                <button
                  type="button"
                  className="absolute inset-y-0 right-0 flex items-center px-3 text-muted-foreground hover:text-foreground"
                  onClick={() => setShowConfirmPassword((prev) => !prev)}
                  disabled={isLoading}
                  aria-label={showConfirmPassword ? "Hide confirm password" : "Show confirm password"}
                >
                  {showConfirmPassword ? <EyeOff className="size-4" /> : <Eye className="size-4" />}
                </button>
              </div>
            </div>

            <Button
              type="submit"
              disabled={isLoading}
              className="w-full font-semibold shadow-xs mt-2"
            >
              {isLoading
                ? "Submitting..."
                : accountType === "merchant"
                  ? "Submit merchant application"
                  : "Create customer account"}
            </Button>
          </CardContent>

          <CardFooter className="flex flex-col gap-2 pt-2 border-t border-border/60 text-center text-xs text-muted-foreground">
            <p>
              Already have an account?{" "}
              <Link
                href="/login"
                className="font-semibold text-primary underline-offset-4 hover:underline"
              >
                Sign in
              </Link>
            </p>
          </CardFooter>
        </form>
      </Card>
    </main>
  );
}

export default function SignupPage() {
  return (
    <Suspense
      fallback={
        <main className="container mx-auto flex min-h-[50vh] items-center justify-center px-4 py-12">
          <p className="text-sm text-muted-foreground">Loading registration...</p>
        </main>
      }
    >
      <SignupForm />
    </Suspense>
  );
}

"use client";

import React, { useState } from "react";
import Link from "next/link";
import { useRouter, useSearchParams } from "next/navigation";
import { Eye, EyeOff } from "lucide-react";
import { api, ApiError, formatApiError, type MerchantResponse, type UserResponse } from "@/lib/api";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import {
  EMPTY_PICKUP_ADDRESS,
  PickupAddressFields,
  toPickupAddressRequest,
  useZones,
  validatePickupAddress,
  type PickupAddressForm,
} from "@/components/merchant/pickup-address-fields";
import {
  AccountTabs,
  accountPanelId,
  accountTabId,
  parseAccountType,
  type AccountType,
} from "../AccountTabs";
import { AuthSplitLayout } from "../AuthSplitLayout";
import { MERCHANT_PENDING, SIGNUP_VIEWS } from "./data";

const EMAIL_REGEX = /^[^\s@]+@[^\s@]+\.[^\s@]+$/;
const MAX_PASSWORD_BYTES = 72;

const INPUT_CLASS = "h-11 bg-white text-base sm:text-sm";
const LABEL_CLASS = "text-navy";
const LINK_CLASS =
  "rounded-sm font-semibold text-brand-text underline-offset-4 outline-none hover:underline focus-visible:ring-3 focus-visible:ring-brand";

function PasswordToggle({
  shown,
  onToggle,
  controls,
  label,
  disabled,
}: {
  shown: boolean;
  onToggle: () => void;
  controls: string;
  label: string;
  disabled: boolean;
}) {
  return (
    <button
      type="button"
      onClick={onToggle}
      disabled={disabled}
      aria-label={shown ? `Hide ${label}` : `Show ${label}`}
      aria-pressed={shown}
      aria-controls={controls}
      className="absolute inset-y-0 right-0 grid w-11 place-items-center rounded-r-lg text-ink-muted outline-none hover:text-navy focus-visible:ring-3 focus-visible:ring-brand"
    >
      {shown ? <EyeOff aria-hidden="true" className="size-4" /> : <Eye aria-hidden="true" className="size-4" />}
    </button>
  );
}

export function SignupScreen() {
  const router = useRouter();
  const searchParams = useSearchParams();

  const [accountType, setAccountType] = useState<AccountType>(() => parseAccountType(searchParams.get("type")));
  const [fullName, setFullName] = useState("");
  const [email, setEmail] = useState("");
  const [password, setPassword] = useState("");
  const [confirmPassword, setConfirmPassword] = useState("");
  const [showPassword, setShowPassword] = useState(false);
  const [showConfirmPassword, setShowConfirmPassword] = useState(false);
  const [businessName, setBusinessName] = useState("");
  const [contactPhone, setContactPhone] = useState("");
  const [registrationNumber, setRegistrationNumber] = useState("");
  const [pickupAddress, setPickupAddress] = useState<PickupAddressForm>(EMPTY_PICKUP_ADDRESS);
  const { zones, zonesError } = useZones();
  const [isLoading, setIsLoading] = useState(false);
  const [errorMessage, setErrorMessage] = useState<string | null>(null);
  const [isConflict, setIsConflict] = useState(false);
  const [submittedAs, setSubmittedAs] = useState<AccountType | null>(null);

  const view = SIGNUP_VIEWS[accountType];
  const signInHref = accountType === "merchant" ? "/login?tab=merchant" : "/login";

  function changeAccountType(next: AccountType) {
    setAccountType(next);
    setErrorMessage(null);
    setIsConflict(false);
    // Keeps the type in the URL (?type=merchant), the format existing sign-up links already use.
    const params = new URLSearchParams(searchParams.toString());
    if (next === "customer") params.delete("type");
    else params.set("type", next);
    const query = params.toString();
    router.replace(query ? `/signup?${query}` : "/signup", { scroll: false });
  }

  async function handleSubmit(e: React.FormEvent<HTMLFormElement>) {
    e.preventDefault();
    if (isLoading) return;

    setErrorMessage(null);
    setIsConflict(false);

    const trimmedName = fullName.trim();
    const trimmedEmail = email.trim().toLowerCase();
    const trimmedBusinessName = businessName.trim();
    const trimmedPhone = contactPhone.trim();
    const trimmedRegistrationNumber = registrationNumber.trim();

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
      if (trimmedBusinessName.length > 150) {
        setErrorMessage("Business / store name must be at most 150 characters.");
        return;
      }
      if (!trimmedRegistrationNumber) {
        setErrorMessage("Please enter your business registration number.");
        return;
      }
      if (trimmedRegistrationNumber.length > 50) {
        setErrorMessage("Business registration number must be at most 50 characters.");
        return;
      }
      if (!trimmedPhone) {
        setErrorMessage("Please enter a contact phone number.");
        return;
      }
      if (trimmedPhone.length > 30) {
        setErrorMessage("Contact phone must be at most 30 characters.");
        return;
      }
      if (trimmedEmail.length > 254) {
        setErrorMessage("Email address must be at most 254 characters.");
        return;
      }
      const addressProblem = validatePickupAddress(pickupAddress);
      if (addressProblem) {
        setErrorMessage(addressProblem);
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

      if (accountType === "merchant") {
        await api.post<MerchantResponse>("/api/v1/merchants", {
          fullName: trimmedName,
          contactEmail: trimmedEmail,
          password,
          businessName: trimmedBusinessName,
          registrationNumber: trimmedRegistrationNumber,
          contactPhone: trimmedPhone,
          pickupAddress: toPickupAddressRequest(pickupAddress),
        });
        setSubmittedAs("merchant");
        return;
      }
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

  if (submittedAs) {
    return (
      <AuthSplitLayout visual={MERCHANT_PENDING}>
        <div role="status">
          <p className="inline-flex items-center gap-2 rounded-full bg-brand-tint px-3 py-1 text-xs font-semibold tracking-[0.12em] text-brand-text uppercase">
            <span className="size-1.5 rounded-full bg-brand" />
            {MERCHANT_PENDING.eyebrow}
          </p>
          <h1 className="mt-4 font-display text-3xl font-bold tracking-tight text-navy">{MERCHANT_PENDING.title}</h1>
          <p className="mt-2 text-ink-muted">{MERCHANT_PENDING.description}</p>
        </div>
        <p className="mt-8 text-sm text-ink-muted">
          After approval,{" "}
          <Link href="/login?tab=merchant" className={LINK_CLASS}>
            sign in
          </Link>{" "}
          to open the merchant portal.
        </p>
      </AuthSplitLayout>
    );
  }

  return (
    <AuthSplitLayout visual={view}>
      <h1 className="font-display text-3xl font-bold tracking-tight text-navy">{view.title}</h1>
      <p className="mt-2 text-ink-muted">{view.description}</p>

      <div className="mt-8">
        <AccountTabs idPrefix="signup" active={accountType} onChange={changeAccountType} disabled={isLoading} />
      </div>

      <div id={accountPanelId("signup")} role="tabpanel" aria-labelledby={accountTabId("signup", accountType)} className="mt-6">
        <form onSubmit={handleSubmit} noValidate className="space-y-5">
          {errorMessage && (
            <div
              role="alert"
              className="space-y-1.5 rounded-xl border border-destructive/20 bg-destructive/10 p-3 text-sm font-medium text-destructive"
            >
              <p>{errorMessage}</p>
              {isConflict && (
                <p>
                  <Link href={signInHref} className="font-semibold underline underline-offset-4 hover:text-navy">
                    Click here to sign in &rarr;
                  </Link>
                </p>
              )}
            </div>
          )}

          <div className="space-y-1.5">
            <Label htmlFor="signup-name" className={LABEL_CLASS}>
              Full name
            </Label>
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
              className={INPUT_CLASS}
            />
          </div>

          {accountType === "merchant" && (
            <>
              <div className="space-y-1.5">
                <Label htmlFor="signup-business-name" className={LABEL_CLASS}>
                  Business / store name
                </Label>
                <Input
                  id="signup-business-name"
                  type="text"
                  autoComplete="organization"
                  placeholder="Ada's Parcel Shop"
                  value={businessName}
                  onChange={(e) => setBusinessName(e.target.value)}
                  disabled={isLoading}
                  maxLength={150}
                  required
                  className={INPUT_CLASS}
                />
              </div>
              <div className="grid gap-5 sm:grid-cols-2">
                <div className="space-y-1.5">
                  <Label htmlFor="signup-registration-number" className={LABEL_CLASS}>
                    Registration number
                  </Label>
                  <Input
                    id="signup-registration-number"
                    type="text"
                    placeholder="PV00123456"
                    value={registrationNumber}
                    onChange={(e) => setRegistrationNumber(e.target.value)}
                    disabled={isLoading}
                    maxLength={50}
                    required
                    className={INPUT_CLASS}
                  />
                </div>
                <div className="space-y-1.5">
                  <Label htmlFor="signup-contact-phone" className={LABEL_CLASS}>
                    Contact phone
                  </Label>
                  <Input
                    id="signup-contact-phone"
                    type="tel"
                    autoComplete="tel"
                    placeholder="+94771234567"
                    value={contactPhone}
                    onChange={(e) => setContactPhone(e.target.value)}
                    disabled={isLoading}
                    maxLength={30}
                    required
                    className={INPUT_CLASS}
                  />
                </div>
              </div>
              <fieldset className="space-y-3 rounded-xl border border-line bg-surface p-4">
                <legend className="px-1 text-sm font-semibold text-navy">Pickup / warehouse address</legend>
                <p className="text-xs text-ink-muted">
                  Where SwiftDrop couriers collect your parcels. You can change it later in the merchant portal.
                </p>
                <PickupAddressFields
                  idPrefix="signup-pickup"
                  value={pickupAddress}
                  onChange={setPickupAddress}
                  zones={zones}
                  zonesError={zonesError}
                  disabled={isLoading}
                />
              </fieldset>
            </>
          )}

          <div className="space-y-1.5">
            <Label htmlFor="signup-email" className={LABEL_CLASS}>
              Email address
            </Label>
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
              className={INPUT_CLASS}
            />
          </div>

          <div className="space-y-1.5">
            <Label htmlFor="signup-password" className={LABEL_CLASS}>
              Password
            </Label>
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
                aria-describedby="signup-password-hint"
                className={`${INPUT_CLASS} pr-11`}
              />
              <PasswordToggle
                shown={showPassword}
                onToggle={() => setShowPassword((prev) => !prev)}
                controls="signup-password"
                label="password"
                disabled={isLoading}
              />
            </div>
            <p id="signup-password-hint" className="text-xs text-ink-muted">
              Minimum 8 characters, maximum 72 bytes.
            </p>
          </div>

          <div className="space-y-1.5">
            <Label htmlFor="signup-confirm-password" className={LABEL_CLASS}>
              Confirm password
            </Label>
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
                className={`${INPUT_CLASS} pr-11`}
              />
              <PasswordToggle
                shown={showConfirmPassword}
                onToggle={() => setShowConfirmPassword((prev) => !prev)}
                controls="signup-confirm-password"
                label="confirm password"
                disabled={isLoading}
              />
            </div>
          </div>

          <button
            type="submit"
            disabled={isLoading}
            className="h-12 w-full rounded-xl bg-navy font-semibold text-white transition-colors outline-none hover:bg-navy-800 focus-visible:ring-3 focus-visible:ring-brand focus-visible:ring-offset-2 disabled:opacity-60"
          >
            {isLoading ? "Submitting..." : view.submit}
          </button>

          <p className="pt-1 text-center text-sm text-ink-muted">
            Already have an account?{" "}
            <Link href={signInHref} className={LINK_CLASS}>
              Sign in
            </Link>
          </p>
        </form>
      </div>
    </AuthSplitLayout>
  );
}

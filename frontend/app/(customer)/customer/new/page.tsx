"use client";

import React, { useEffect, useState } from "react";
import Link from "next/link";
import { useAuth } from "@/lib/auth-context";
import {
  api,
  formatApiError,
  type BookingAddress,
  type CreateParcelRequest,
  type ParcelCreatedResponse,
  type ParcelSize,
  type PriceQuoteRequest,
  type PriceQuoteResponse,
  type ZoneResponse,
} from "@/lib/api";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";

const PHONE_PATTERN = /^\+[1-9][0-9]{7,14}$/;
const MAX_WEIGHT_GRAMS = 30_000;
const QUOTE_DEBOUNCE_MS = 400;

const SIZE_OPTIONS: { value: ParcelSize; label: string }[] = [
  { value: "SMALL", label: "Small" },
  { value: "MEDIUM", label: "Medium" },
  { value: "LARGE", label: "Large" },
];

interface AddressForm {
  contactName: string;
  phone: string;
  line1: string;
  line2: string;
  city: string;
  zoneId: string;
  postalCode: string;
}

const EMPTY_ADDRESS: AddressForm = {
  contactName: "",
  phone: "",
  line1: "",
  line2: "",
  city: "",
  zoneId: "",
  postalCode: "",
};

/** Mirrors the server rules in AddressRequest, so most mistakes are caught before a round trip. */
function validateAddress(address: AddressForm, label: string): string[] {
  const problems: string[] = [];
  if (!address.contactName.trim()) problems.push(`${label}: enter a contact name.`);
  if (!PHONE_PATTERN.test(address.phone.trim())) {
    problems.push(`${label}: phone must be in international format, e.g. +94771234567.`);
  }
  if (!address.line1.trim()) problems.push(`${label}: enter the street address.`);
  if (!address.city.trim()) problems.push(`${label}: enter the city.`);
  if (!address.zoneId) problems.push(`${label}: choose a delivery zone.`);
  return problems;
}

function toBookingAddress(address: AddressForm): BookingAddress {
  return {
    contactName: address.contactName.trim(),
    phone: address.phone.trim(),
    line1: address.line1.trim(),
    line2: address.line2.trim() || undefined,
    city: address.city.trim(),
    zoneId: address.zoneId,
    postalCode: address.postalCode.trim() || undefined,
  };
}

function parseWeight(raw: string): number | null {
  const value = Number(raw);
  return Number.isInteger(value) && value >= 1 && value <= MAX_WEIGHT_GRAMS ? value : null;
}

export default function BookDeliveryPage() {
  const { token, isAuthenticated } = useAuth();

  const [zones, setZones] = useState<ZoneResponse[]>([]);
  const [zonesError, setZonesError] = useState<string | null>(null);

  const [pickup, setPickup] = useState<AddressForm>(EMPTY_ADDRESS);
  const [dropoff, setDropoff] = useState<AddressForm>(EMPTY_ADDRESS);
  const [size, setSize] = useState<ParcelSize | "">("");
  const [weight, setWeight] = useState("");
  const [description, setDescription] = useState("");

  // Quote results are tagged with the inputs they were made for, so a stale result is never shown.
  const [quoteResult, setQuoteResult] = useState<{ key: string; quote: PriceQuoteResponse } | null>(null);
  const [quoteFailure, setQuoteFailure] = useState<{ key: string; message: string } | null>(null);
  const [quoting, setQuoting] = useState(false);

  const [formErrors, setFormErrors] = useState<string[]>([]);
  const [submitting, setSubmitting] = useState(false);
  const [submitError, setSubmitError] = useState<string | null>(null);
  const [booked, setBooked] = useState<ParcelCreatedResponse | null>(null);

  // Zones feed both zone dropdowns; loaded once the user is signed in.
  useEffect(() => {
    if (!isAuthenticated || !token) return;
    let cancelled = false;
    api
      .get<ZoneResponse[]>("/api/v1/zones", { token })
      .then((data) => {
        if (!cancelled) setZones(data);
      })
      .catch((err) => {
        if (!cancelled) setZonesError(formatApiError(err));
      });
    return () => {
      cancelled = true;
    };
  }, [token, isAuthenticated]);

  // Live estimate: re-quote after the customer pauses, only when every input is valid.
  const weightGrams = parseWeight(weight);
  const pickupZoneId = pickup.zoneId;
  const dropoffZoneId = dropoff.zoneId;

  const quoteKey = [pickupZoneId, dropoffZoneId, size, weightGrams ?? ""].join("|");
  const quoteReady = Boolean(token && pickupZoneId && dropoffZoneId && size && weightGrams !== null);
  const currentQuote = quoteResult?.key === quoteKey ? quoteResult.quote : null;
  const currentQuoteError = quoteFailure?.key === quoteKey ? quoteFailure.message : null;

  useEffect(() => {
    if (!quoteReady || !token || !pickupZoneId || !dropoffZoneId || !size || weightGrams === null) {
      return;
    }
    const key = quoteKey;
    let cancelled = false;
    const body: PriceQuoteRequest = {
      pickupZoneId,
      dropoffZoneId,
      size,
      weightGrams,
    };
    const timer = window.setTimeout(async () => {
      setQuoting(true);
      try {
        const result = await api.post<PriceQuoteResponse>("/api/v1/price-quotes", body, { token });
        if (!cancelled) setQuoteResult({ key, quote: result });
      } catch (err) {
        if (!cancelled) setQuoteFailure({ key, message: formatApiError(err) });
      } finally {
        if (!cancelled) setQuoting(false);
      }
    }, QUOTE_DEBOUNCE_MS);
    return () => {
      cancelled = true;
      window.clearTimeout(timer);
    };
  }, [quoteReady, quoteKey, token, pickupZoneId, dropoffZoneId, size, weightGrams]);

  async function handleSubmit(event: React.FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (!token) return;

    const problems = [
      ...validateAddress(pickup, "Pickup"),
      ...validateAddress(dropoff, "Drop-off"),
    ];
    if (!size) problems.push("Choose a parcel size.");
    if (weightGrams === null) {
      problems.push(`Weight must be a whole number of grams between 1 and ${MAX_WEIGHT_GRAMS.toLocaleString()}.`);
    }
    if (problems.length > 0 || !size || weightGrams === null) {
      setFormErrors(problems);
      return;
    }

    // The fee is never sent: the server recalculates it on confirm.
    const payload: CreateParcelRequest = {
      pickup: toBookingAddress(pickup),
      dropoff: toBookingAddress(dropoff),
      size,
      weightGrams,
      description: description.trim() || undefined,
    };

    setFormErrors([]);
    setSubmitError(null);
    setSubmitting(true);
    try {
      const created = await api.post<ParcelCreatedResponse>("/api/v1/parcels", payload, { token });
      setBooked(created);
    } catch (err) {
      setSubmitError(formatApiError(err));
    } finally {
      setSubmitting(false);
    }
  }

  function resetForm() {
    setPickup(EMPTY_ADDRESS);
    setDropoff(EMPTY_ADDRESS);
    setSize("");
    setWeight("");
    setDescription("");
    setQuoteResult(null);
    setQuoteFailure(null);
    setFormErrors([]);
    setSubmitError(null);
    setBooked(null);
  }

  if (booked) {
    return (
      <div className="mx-auto max-w-2xl space-y-6">
        <section className="rounded-xl border bg-card p-6 space-y-4">
          <h1 className="text-xl font-semibold">Delivery booked</h1>
          <dl className="grid grid-cols-2 gap-4 text-sm">
            <div>
              <dt className="text-muted-foreground">Tracking number</dt>
              <dd className="font-mono text-base font-semibold">{booked.trackingNumber}</dd>
            </div>
            <div>
              <dt className="text-muted-foreground">Status</dt>
              <dd className="font-medium">{booked.status}</dd>
            </div>
            <div>
              <dt className="text-muted-foreground">Fee (calculated by the server)</dt>
              <dd className="font-medium">
                {booked.fee.currency} {booked.fee.amount}
              </dd>
            </div>
          </dl>
          <div className="flex flex-wrap gap-3 pt-2">
            <Link
              href={`/track?code=${encodeURIComponent(booked.trackingNumber)}`}
              className="inline-flex h-8 items-center rounded-lg border px-2.5 text-sm font-medium hover:bg-muted"
            >
              Track this parcel
            </Link>
            <Button variant="outline" onClick={resetForm}>
              Book another parcel
            </Button>
          </div>
        </section>
      </div>
    );
  }

  return (
    <div className="mx-auto max-w-3xl space-y-6">
      <div>
        <h1 className="text-xl font-semibold">Book a new parcel delivery</h1>
        <p className="text-sm text-muted-foreground">
          Enter the pickup and drop-off details. The fee updates as you type.
        </p>
      </div>

      {zonesError && (
        <p role="alert" className="rounded-lg border border-destructive/40 bg-destructive/10 p-3 text-sm text-destructive">
          Could not load delivery zones: {zonesError}
        </p>
      )}

      <form onSubmit={handleSubmit} noValidate className="space-y-6">
        <AddressSection
          idPrefix="pickup"
          title="Pickup address"
          value={pickup}
          zones={zones}
          onChange={setPickup}
        />
        <AddressSection
          idPrefix="dropoff"
          title="Drop-off address"
          value={dropoff}
          zones={zones}
          onChange={setDropoff}
        />

        <section className="rounded-xl border bg-card p-6 space-y-4">
          <h2 className="font-semibold">Parcel</h2>
          <fieldset className="space-y-2">
            <legend className="text-sm font-medium">Size</legend>
            <div className="flex flex-wrap gap-2">
              {SIZE_OPTIONS.map((option) => (
                <label
                  key={option.value}
                  className="flex cursor-pointer items-center gap-2 rounded-lg border px-3 py-2 text-sm has-checked:border-primary has-checked:bg-muted"
                >
                  <input
                    type="radio"
                    name="size"
                    value={option.value}
                    checked={size === option.value}
                    onChange={() => setSize(option.value)}
                  />
                  {option.label}
                </label>
              ))}
            </div>
          </fieldset>

          <div className="space-y-2">
            <Label htmlFor="weight">Weight (grams)</Label>
            <Input
              id="weight"
              type="number"
              inputMode="numeric"
              min={1}
              max={MAX_WEIGHT_GRAMS}
              step={1}
              value={weight}
              onChange={(e) => setWeight(e.target.value)}
              placeholder="e.g. 1500"
            />
          </div>

          <div className="space-y-2">
            <Label htmlFor="description">Description (optional)</Label>
            <Input
              id="description"
              maxLength={200}
              value={description}
              onChange={(e) => setDescription(e.target.value)}
              placeholder="e.g. Fragile, handle with care"
            />
          </div>
        </section>

        <section className="rounded-xl border bg-card p-6 space-y-3" aria-live="polite">
          <h2 className="font-semibold">Estimated fee</h2>
          {quoting && <p className="text-sm text-muted-foreground">Calculating…</p>}
          {!quoting && currentQuoteError && (
            <p className="text-sm text-destructive">{currentQuoteError}</p>
          )}
          {!quoting && !currentQuoteError && currentQuote && (
            <div className="space-y-1 text-sm">
              <p className="text-2xl font-semibold">
                {currentQuote.currency} {currentQuote.amount}
              </p>
              <p className="text-muted-foreground">
                Base {currentQuote.breakdown.baseFee} · Weight {currentQuote.breakdown.weightFee} · Size{" "}
                {currentQuote.breakdown.sizeSurcharge}
              </p>
            </div>
          )}
          {!quoting && !currentQuoteError && !currentQuote && (
            <p className="text-sm text-muted-foreground">
              Choose both zones, a size and a weight to see the fee.
            </p>
          )}
          <p className="text-xs text-muted-foreground">
            This is an estimate. The final fee is recalculated by the server when you confirm.
          </p>
        </section>

        {formErrors.length > 0 && (
          <ul role="alert" className="space-y-1 rounded-lg border border-destructive/40 bg-destructive/10 p-3 text-sm text-destructive">
            {formErrors.map((message) => (
              <li key={message}>{message}</li>
            ))}
          </ul>
        )}
        {submitError && (
          <p role="alert" className="rounded-lg border border-destructive/40 bg-destructive/10 p-3 text-sm text-destructive">
            {submitError}
          </p>
        )}

        <div className="flex justify-end">
          <Button type="submit" size="lg" disabled={submitting}>
            {submitting ? "Booking…" : "Confirm booking"}
          </Button>
        </div>
      </form>
    </div>
  );
}

interface AddressSectionProps {
  idPrefix: string;
  title: string;
  value: AddressForm;
  zones: ZoneResponse[];
  onChange: React.Dispatch<React.SetStateAction<AddressForm>>;
}

function AddressSection({ idPrefix, title, value, zones, onChange }: AddressSectionProps) {
  const set = (field: keyof AddressForm) => (e: React.ChangeEvent<HTMLInputElement | HTMLSelectElement>) =>
    onChange((prev) => ({ ...prev, [field]: e.target.value }));

  return (
    <section className="rounded-xl border bg-card p-6 space-y-4">
      <h2 className="font-semibold">{title}</h2>
      <div className="grid gap-4 sm:grid-cols-2">
        <div className="space-y-2">
          <Label htmlFor={`${idPrefix}-name`}>Contact name</Label>
          <Input id={`${idPrefix}-name`} value={value.contactName} onChange={set("contactName")} maxLength={200} />
        </div>
        <div className="space-y-2">
          <Label htmlFor={`${idPrefix}-phone`}>Phone</Label>
          <Input
            id={`${idPrefix}-phone`}
            type="tel"
            value={value.phone}
            onChange={set("phone")}
            placeholder="+94771234567"
          />
        </div>
        <div className="space-y-2 sm:col-span-2">
          <Label htmlFor={`${idPrefix}-line1`}>Address line 1</Label>
          <Input id={`${idPrefix}-line1`} value={value.line1} onChange={set("line1")} maxLength={200} />
        </div>
        <div className="space-y-2 sm:col-span-2">
          <Label htmlFor={`${idPrefix}-line2`}>Address line 2 (optional)</Label>
          <Input id={`${idPrefix}-line2`} value={value.line2} onChange={set("line2")} maxLength={200} />
        </div>
        <div className="space-y-2">
          <Label htmlFor={`${idPrefix}-city`}>City</Label>
          <Input id={`${idPrefix}-city`} value={value.city} onChange={set("city")} maxLength={100} />
        </div>
        <div className="space-y-2">
          <Label htmlFor={`${idPrefix}-postal`}>Postal code (optional)</Label>
          <Input id={`${idPrefix}-postal`} value={value.postalCode} onChange={set("postalCode")} maxLength={10} />
        </div>
        <div className="space-y-2 sm:col-span-2">
          <Label htmlFor={`${idPrefix}-zone`}>Delivery zone</Label>
          <select
            id={`${idPrefix}-zone`}
            value={value.zoneId}
            onChange={set("zoneId")}
            className="h-8 w-full rounded-lg border border-input bg-transparent px-2.5 text-sm outline-none focus-visible:border-ring focus-visible:ring-3 focus-visible:ring-ring/50 dark:bg-input/30"
          >
            <option value="">Select a zone</option>
            {zones.map((zone) => (
              <option key={zone.id} value={zone.id}>
                {zone.name}
              </option>
            ))}
          </select>
        </div>
      </div>
    </section>
  );
}

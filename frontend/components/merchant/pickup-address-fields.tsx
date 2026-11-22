"use client";

import React, { useEffect, useState } from "react";
import { api, formatApiError, type MerchantPickupAddress, type ZoneResponse } from "@/lib/api";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";

/** Form state of the merchant's pickup (warehouse) address; every field is a string while editing. */
export interface PickupAddressForm {
  contactName: string;
  phone: string;
  line1: string;
  line2: string;
  city: string;
  postalCode: string;
  zoneId: string;
}

export const EMPTY_PICKUP_ADDRESS: PickupAddressForm = {
  contactName: "",
  phone: "",
  line1: "",
  line2: "",
  city: "",
  postalCode: "",
  zoneId: "",
};

/** Same rule as the backend: international format, for example +94771234567. */
const PHONE = /^\+[1-9][0-9]{7,14}$/;

export function pickupAddressFormFrom(address: MerchantPickupAddress | null): PickupAddressForm {
  if (!address) return EMPTY_PICKUP_ADDRESS;
  return {
    contactName: address.contactName,
    phone: address.phone,
    line1: address.line1,
    line2: address.line2 ?? "",
    city: address.city,
    postalCode: address.postalCode ?? "",
    zoneId: address.zoneId,
  };
}

/** The first problem with the address as a message for the user, or null. The backend checks again. */
export function validatePickupAddress(form: PickupAddressForm): string | null {
  if (!form.contactName.trim()) return "Please enter the pickup contact name (for example your warehouse name).";
  if (form.contactName.trim().length > 200) return "Pickup contact name must be at most 200 characters.";
  if (!PHONE.test(form.phone.trim())) return "Pickup phone must be in international format, for example +94771234567.";
  if (!form.line1.trim()) return "Please enter the pickup street address.";
  if (form.line1.trim().length > 200 || form.line2.trim().length > 200) return "Address lines must be at most 200 characters.";
  if (!form.city.trim()) return "Please enter the pickup city.";
  if (form.city.trim().length > 100) return "Pickup city must be at most 100 characters.";
  if (form.postalCode.trim().length > 10) return "Postal code must be at most 10 characters.";
  if (!form.zoneId) return "Please select the province of your warehouse.";
  return null;
}

/** The request body for the backend; empty optional parts become null. */
export function toPickupAddressRequest(form: PickupAddressForm): MerchantPickupAddress {
  return {
    contactName: form.contactName.trim(),
    phone: form.phone.trim(),
    line1: form.line1.trim(),
    line2: form.line2.trim() || null,
    city: form.city.trim(),
    postalCode: form.postalCode.trim() || null,
    zoneId: form.zoneId,
  };
}

/** Active provinces from the public zones list (no login needed). */
export function useZones(): { zones: ZoneResponse[]; zonesError: string | null } {
  const [zones, setZones] = useState<ZoneResponse[]>([]);
  const [zonesError, setZonesError] = useState<string | null>(null);
  useEffect(() => {
    let cancelled = false;
    api
      .get<ZoneResponse[]>("/api/v1/zones")
      .then((data) => {
        if (!cancelled) setZones(data);
      })
      .catch((err) => {
        if (!cancelled) setZonesError(formatApiError(err));
      });
    return () => {
      cancelled = true;
    };
  }, []);
  return { zones, zonesError };
}

/** The province name for a zone ID, for read-only display. */
export function zoneName(zones: ZoneResponse[], zoneId: string | null | undefined): string {
  return zones.find((zone) => zone.id === zoneId)?.name ?? "Unknown province";
}

export function PickupAddressFields({
  idPrefix,
  value,
  onChange,
  zones,
  zonesError,
  disabled,
}: {
  idPrefix: string;
  value: PickupAddressForm;
  onChange: (next: PickupAddressForm) => void;
  zones: ZoneResponse[];
  zonesError: string | null;
  disabled?: boolean;
}) {
  const set =
    (field: keyof PickupAddressForm) =>
    (event: React.ChangeEvent<HTMLInputElement | HTMLSelectElement>) =>
      onChange({ ...value, [field]: event.target.value });

  return (
    <div className="grid gap-3 sm:grid-cols-2">
      <div className="space-y-1.5 sm:col-span-2">
        <Label htmlFor={`${idPrefix}-contact`}>Pickup contact / warehouse name</Label>
        <Input
          id={`${idPrefix}-contact`}
          placeholder="Ada's Warehouse"
          value={value.contactName}
          onChange={set("contactName")}
          disabled={disabled}
          maxLength={200}
          className="bg-background text-sm"
        />
      </div>
      <div className="space-y-1.5 sm:col-span-2">
        <Label htmlFor={`${idPrefix}-phone`}>Pickup phone</Label>
        <Input
          id={`${idPrefix}-phone`}
          type="tel"
          placeholder="+94912234567"
          value={value.phone}
          onChange={set("phone")}
          disabled={disabled}
          maxLength={20}
          className="bg-background text-sm"
        />
      </div>
      <div className="space-y-1.5 sm:col-span-2">
        <Label htmlFor={`${idPrefix}-line1`}>Street address</Label>
        <Input
          id={`${idPrefix}-line1`}
          autoComplete="address-line1"
          placeholder="25 Lighthouse Street"
          value={value.line1}
          onChange={set("line1")}
          disabled={disabled}
          maxLength={200}
          className="bg-background text-sm"
        />
      </div>
      <div className="space-y-1.5 sm:col-span-2">
        <Label htmlFor={`${idPrefix}-line2`}>Address line 2 (optional)</Label>
        <Input
          id={`${idPrefix}-line2`}
          autoComplete="address-line2"
          value={value.line2}
          onChange={set("line2")}
          disabled={disabled}
          maxLength={200}
          className="bg-background text-sm"
        />
      </div>
      <div className="space-y-1.5">
        <Label htmlFor={`${idPrefix}-city`}>City</Label>
        <Input
          id={`${idPrefix}-city`}
          autoComplete="address-level2"
          placeholder="Galle"
          value={value.city}
          onChange={set("city")}
          disabled={disabled}
          maxLength={100}
          className="bg-background text-sm"
        />
      </div>
      <div className="space-y-1.5">
        <Label htmlFor={`${idPrefix}-postal`}>Postal code (optional)</Label>
        <Input
          id={`${idPrefix}-postal`}
          autoComplete="postal-code"
          placeholder="80000"
          value={value.postalCode}
          onChange={set("postalCode")}
          disabled={disabled}
          maxLength={10}
          className="bg-background text-sm"
        />
      </div>
      <div className="space-y-1.5 sm:col-span-2">
        <Label htmlFor={`${idPrefix}-zone`}>Province</Label>
        <select
          id={`${idPrefix}-zone`}
          value={value.zoneId}
          onChange={set("zoneId")}
          disabled={disabled || zones.length === 0}
          className="h-8 w-full rounded-lg border border-input bg-background px-2.5 text-sm outline-none focus-visible:border-ring focus-visible:ring-3 focus-visible:ring-ring/50 dark:bg-input/30"
        >
          <option value="">{zones.length === 0 ? "Loading provinces..." : "Select a province"}</option>
          {zones.map((zone) => (
            <option key={zone.id} value={zone.id}>
              {zone.name}
            </option>
          ))}
        </select>
        {zonesError && <p className="text-[11px] text-destructive">Could not load provinces: {zonesError}</p>}
        <p className="text-[11px] text-muted-foreground">
          The province decides which SwiftDrop hub collects your parcels.
        </p>
      </div>
    </div>
  );
}

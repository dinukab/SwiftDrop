"use client";

import React, { useState } from "react";
import { AlertTriangle, Loader2, MapPin, Pencil } from "lucide-react";
import { api, formatApiError, type MerchantPickupAddress, type MerchantResponse } from "@/lib/api";
import { Button } from "@/components/ui/button";
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card";
import {
  PickupAddressFields,
  pickupAddressFormFrom,
  toPickupAddressRequest,
  useZones,
  validatePickupAddress,
  zoneName,
  type PickupAddressForm,
} from "@/components/merchant/pickup-address-fields";

/**
 * The merchant's pickup (warehouse) address with an edit form. A missing address is shown as a warning: until one is
 * registered, API orders must carry their own pickup and are not checked.
 */
export function PickupAddressCard({
  address,
  onSaved,
}: {
  address: MerchantPickupAddress | null;
  onSaved: (profile: MerchantResponse) => void;
}) {
  const { zones, zonesError } = useZones();
  const [editing, setEditing] = useState(false);
  const [form, setForm] = useState<PickupAddressForm>(() => pickupAddressFormFrom(address));
  const [saving, setSaving] = useState(false);
  const [problem, setProblem] = useState<string | null>(null);

  const startEditing = () => {
    setForm(pickupAddressFormFrom(address));
    setProblem(null);
    setEditing(true);
  };

  const save = async (event: React.FormEvent<HTMLFormElement>) => {
    event.preventDefault();
    const invalid = validatePickupAddress(form);
    if (invalid) {
      setProblem(invalid);
      return;
    }
    setSaving(true);
    setProblem(null);
    try {
      const profile = await api.put<MerchantResponse>(
        "/api/v1/merchants/me/pickup-address",
        toPickupAddressRequest(form)
      );
      onSaved(profile);
      setEditing(false);
    } catch (err) {
      setProblem(formatApiError(err));
    } finally {
      setSaving(false);
    }
  };

  return (
    <Card>
      <CardHeader className="flex flex-row items-start justify-between gap-4 space-y-0">
        <div className="space-y-1">
          <CardTitle className="text-base flex items-center gap-2">
            <MapPin className="w-4 h-4 text-primary" /> Pickup address
          </CardTitle>
          <CardDescription className="text-xs">
            Where SwiftDrop couriers collect your parcels. API orders may leave out <code>pickup</code> to use it.
          </CardDescription>
        </div>
        {!editing && (
          <Button variant="outline" size="sm" onClick={startEditing}>
            <Pencil className="w-3.5 h-3.5 mr-1.5" />
            {address ? "Edit" : "Add address"}
          </Button>
        )}
      </CardHeader>
      <CardContent className="space-y-3">
        {!editing && !address && (
          <div className="p-3 bg-amber-500/10 border border-amber-500/20 text-amber-800 dark:text-amber-200 rounded-lg flex items-start gap-2 text-xs">
            <AlertTriangle className="w-4 h-4 flex-shrink-0 mt-0.5" />
            <p>
              No pickup address registered. Until you add one, every API order must include its own pickup address,
              and SwiftDrop cannot check that it is really yours.
            </p>
          </div>
        )}

        {!editing && address && (
          <div className="text-sm space-y-0.5">
            <p className="font-medium">{address.contactName}</p>
            <p>
              {address.line1}
              {address.line2 ? `, ${address.line2}` : ""}
            </p>
            <p>
              {address.city}
              {address.postalCode ? ` ${address.postalCode}` : ""} · {zoneName(zones, address.zoneId)}
            </p>
            <p className="text-muted-foreground">{address.phone}</p>
          </div>
        )}

        {editing && (
          <form onSubmit={save} noValidate className="space-y-3">
            <PickupAddressFields
              idPrefix="portal-pickup"
              value={form}
              onChange={setForm}
              zones={zones}
              zonesError={zonesError}
              disabled={saving}
            />
            {problem && (
              <p role="alert" className="rounded-md bg-destructive/10 px-3 py-2 text-xs text-destructive">
                {problem}
              </p>
            )}
            <div className="flex justify-end gap-2">
              <Button type="button" variant="outline" size="sm" onClick={() => setEditing(false)} disabled={saving}>
                Cancel
              </Button>
              <Button type="submit" size="sm" disabled={saving}>
                {saving && <Loader2 className="w-4 h-4 mr-2 animate-spin" />}
                Save address
              </Button>
            </div>
          </form>
        )}
      </CardContent>
    </Card>
  );
}

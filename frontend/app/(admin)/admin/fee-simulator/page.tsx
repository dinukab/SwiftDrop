"use client";

import React, { useEffect, useState } from "react";
import { useAuth } from "@/lib/auth-context";
import {
  api,
  formatApiError,
  type AdminZoneResponse,
  type ParcelSize,
  type PriceQuoteRequest,
  type PriceQuoteResponse,
} from "@/lib/api";
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { ZoneSelect } from "@/components/admin/pricing-parts";
import { AlertCircle, Loader2 } from "lucide-react";

export default function AdminFeeSimulatorPage() {
  const { token } = useAuth();

  const [zones, setZones] = useState<AdminZoneResponse[]>([]);
  const [loading, setLoading] = useState(true);
  const [loadError, setLoadError] = useState<string | null>(null);

  const [pickupZoneId, setPickupZoneId] = useState("");
  const [dropoffZoneId, setDropoffZoneId] = useState("");
  const [size, setSize] = useState<ParcelSize | "">("");
  const [weight, setWeight] = useState("1000");
  const [result, setResult] = useState<PriceQuoteResponse | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);

  useEffect(() => {
    if (!token) return;
    let cancelled = false;
    api
      .get<AdminZoneResponse[]>("/api/v1/admin/zones", { token })
      .then((zoneList) => {
        if (!cancelled) setZones(zoneList);
      })
      .catch((err) => {
        if (!cancelled) setLoadError(formatApiError(err));
      })
      .finally(() => {
        if (!cancelled) setLoading(false);
      });
    return () => {
      cancelled = true;
    };
  }, [token]);

  async function handleSimulate(event: React.FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (!token) return;

    const grams = Number(weight);
    if (!pickupZoneId || !dropoffZoneId) {
      setError("Choose both zones.");
      return;
    }
    if (!size) {
      setError("Choose a parcel size.");
      return;
    }
    if (!Number.isInteger(grams) || grams < 1 || grams > 30_000) {
      setError("Weight must be a whole number of grams between 1 and 30,000.");
      return;
    }

    const body: PriceQuoteRequest = { pickupZoneId, dropoffZoneId, size, weightGrams: grams };

    setBusy(true);
    setError(null);
    setResult(null);
    try {
      setResult(await api.post<PriceQuoteResponse>("/api/v1/price-quotes", body, { token }));
    } catch (err) {
      setError(formatApiError(err));
    } finally {
      setBusy(false);
    }
  }

  return (
    <Card>
      <CardHeader>
        <CardTitle>Fee simulator</CardTitle>
        <CardDescription>
          Calculates the fee exactly as a customer would be quoted, using the same server calculation.
        </CardDescription>
      </CardHeader>

      <CardContent className="space-y-4">
        {loadError && (
          <p role="alert" className="flex items-center gap-2 rounded-lg border border-destructive/40 bg-destructive/10 p-3 text-sm text-destructive">
            <AlertCircle className="size-4" />
            {loadError}
          </p>
        )}

        {loading ? (
          <p className="flex items-center gap-2 text-sm text-muted-foreground">
            <Loader2 className="size-4 animate-spin" /> Loading zones…
          </p>
        ) : (
          <form onSubmit={handleSimulate} noValidate className="space-y-4">
            <div className="grid gap-4 sm:grid-cols-2">
              <div className="space-y-2">
                <Label htmlFor="sim-pickup">Pickup zone</Label>
                <ZoneSelect id="sim-pickup" value={pickupZoneId} zones={zones} onChange={setPickupZoneId} />
              </div>
              <div className="space-y-2">
                <Label htmlFor="sim-dropoff">Drop-off zone</Label>
                <ZoneSelect id="sim-dropoff" value={dropoffZoneId} zones={zones} onChange={setDropoffZoneId} />
              </div>
              <div className="space-y-2">
                <Label htmlFor="sim-size">Size</Label>
                <select
                  id="sim-size"
                  value={size}
                  onChange={(e) => setSize(e.target.value as ParcelSize | "")}
                  className="h-8 w-full rounded-lg border border-input bg-transparent px-2.5 text-sm outline-none focus-visible:border-ring focus-visible:ring-3 focus-visible:ring-ring/50 dark:bg-input/30"
                >
                  <option value="">Select a size</option>
                  <option value="SMALL">Small</option>
                  <option value="MEDIUM">Medium</option>
                  <option value="LARGE">Large</option>
                </select>
              </div>
              <div className="space-y-2">
                <Label htmlFor="sim-weight">Weight (grams)</Label>
                <Input
                  id="sim-weight"
                  type="number"
                  inputMode="numeric"
                  min={1}
                  max={30000}
                  step={1}
                  value={weight}
                  onChange={(e) => setWeight(e.target.value)}
                />
              </div>
            </div>

            <div className="flex justify-end">
              <Button type="submit" disabled={busy}>
                {busy && <Loader2 className="size-4 animate-spin" />}
                Calculate fee
              </Button>
            </div>
          </form>
        )}

        {error && (
          <p role="alert" className="rounded-lg border border-destructive/40 bg-destructive/10 p-3 text-sm text-destructive">
            {error}
          </p>
        )}

        {result && (
          <div className="space-y-1 rounded-xl border p-4 text-sm">
            <p className="text-2xl font-semibold">
              {result.currency} {result.amount}
            </p>
            <p className="text-muted-foreground">
              Base {result.breakdown.baseFee} · Weight {result.breakdown.weightFee} · Size {result.breakdown.sizeSurcharge}
            </p>
          </div>
        )}
      </CardContent>
    </Card>
  );
}

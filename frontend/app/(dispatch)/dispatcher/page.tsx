"use client";

import React, { useEffect, useState } from "react";
import { useAuth } from "@/lib/auth-context";
import { formatApiError, type PageResponse } from "@/lib/api";
import {
  VEHICLE_TYPES,
  assignParcel,
  createCourierProfile,
  getCourierProfiles,
  getCourierWorkloads,
  getUnassignedParcels,
  updateCourierProfile,
  type CourierProfile,
  type CourierWorkload,
  type UnassignedParcel,
  type VehicleType,
} from "@/lib/dispatch";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";

const PAGE_SIZE = 20;
type Tab = "unassigned" | "couriers";

function formatDate(value: string): string {
  return new Date(value).toLocaleString();
}

function Alert({ children }: { children: React.ReactNode }) {
  return (
    <p role="alert" className="rounded-lg border border-destructive/40 bg-destructive/10 p-3 text-sm text-destructive">
      {children}
    </p>
  );
}

export default function DispatchPortalPage() {
  const { token, isAuthenticated } = useAuth();
  const [tab, setTab] = useState<Tab>("unassigned");
  const [reloadKey, setReloadKey] = useState(0);
  const [notice, setNotice] = useState<string | null>(null);
  const reload = () => setReloadKey((key) => key + 1);

  return (
    <div className="mx-auto max-w-5xl space-y-6">
      <div>
        <h1 className="text-xl font-semibold">Fleet dispatch</h1>
        <p className="text-sm text-muted-foreground">
          Assign waiting parcels to the least busy courier and manage courier profiles.
        </p>
      </div>

      {notice && (
        <p role="status" className="rounded-lg border border-emerald-300 bg-emerald-50 p-3 text-sm text-emerald-800">
          {notice}
        </p>
      )}

      <div role="tablist" aria-label="Dispatch sections" className="flex gap-2 border-b">
        {(["unassigned", "couriers"] as Tab[]).map((id) => (
          <button
            key={id}
            role="tab"
            aria-selected={tab === id}
            onClick={() => setTab(id)}
            className={`-mb-px border-b-2 px-3 py-2 text-sm font-medium ${
              tab === id ? "border-primary text-foreground" : "border-transparent text-muted-foreground"
            }`}
          >
            {id === "unassigned" ? "Unassigned parcels" : "Couriers"}
          </button>
        ))}
      </div>

      {isAuthenticated && token ? (
        tab === "unassigned" ? (
          <UnassignedTab token={token} reloadKey={reloadKey} onChanged={reload} onNotice={setNotice} />
        ) : (
          <CouriersTab token={token} reloadKey={reloadKey} onChanged={reload} onNotice={setNotice} />
        )
      ) : null}
    </div>
  );
}

interface TabProps {
  token: string;
  reloadKey: number;
  onChanged: () => void;
  onNotice: (message: string | null) => void;
}

function UnassignedTab({ token, reloadKey, onChanged, onNotice }: TabProps) {
  const [page, setPage] = useState(0);
  const [parcels, setParcels] = useState<PageResponse<UnassignedParcel> | null>(null);
  const [workloads, setWorkloads] = useState<CourierWorkload[] | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [choice, setChoice] = useState<Record<string, string>>({});
  const [busyId, setBusyId] = useState<string | null>(null);
  const [rowError, setRowError] = useState<{ id: string; message: string } | null>(null);

  useEffect(() => {
    let cancelled = false;
    Promise.all([getUnassignedParcels(token, page, PAGE_SIZE), getCourierWorkloads(token)])
      .then(([list, load]) => {
        if (cancelled) return;
        setParcels(list);
        setWorkloads(load);
        setError(null);
      })
      .catch((err) => {
        if (!cancelled) setError(formatApiError(err));
      });
    return () => {
      cancelled = true;
    };
  }, [token, page, reloadKey]);

  async function assign(parcel: UnassignedParcel) {
    const courierUserId = choice[parcel.id] ?? workloads?.[0]?.courierUserId;
    if (!courierUserId) return;
    setBusyId(parcel.id);
    setRowError(null);
    try {
      await assignParcel(token, parcel.id, courierUserId);
      const courier = workloads?.find((w) => w.courierUserId === courierUserId);
      onNotice(`${parcel.trackingNumber} assigned to ${courier?.fullName ?? "the courier"}.`);
      onChanged();
    } catch (err) {
      // 409 (courier no longer eligible, parcel already picked up) is shown on the row
      setRowError({ id: parcel.id, message: formatApiError(err) });
      onChanged();
    } finally {
      setBusyId(null);
    }
  }

  return (
    <div className="space-y-6">
      {error && <Alert>Could not load dispatch data: {error}</Alert>}

      <section className="rounded-xl border bg-card p-4 sm:p-6">
        <h2 className="font-semibold">Courier workload</h2>
        {!workloads ? (
          <p className="mt-2 text-sm text-muted-foreground">Loading couriers...</p>
        ) : workloads.length === 0 ? (
          <p className="mt-2 text-sm text-muted-foreground">
            No eligible couriers. Create or activate a courier profile on the Couriers tab.
          </p>
        ) : (
          <ul className="mt-3 grid gap-2 sm:grid-cols-2 lg:grid-cols-3">
            {workloads.map((w) => (
              <li key={w.courierUserId} className="flex items-center justify-between rounded-lg border px-3 py-2 text-sm">
                <span>
                  {w.fullName}
                  <span className="ml-2 text-xs text-muted-foreground">{w.vehicleType}</span>
                </span>
                <span className="font-medium">{w.activeParcels} active</span>
              </li>
            ))}
          </ul>
        )}
      </section>

      <section className="overflow-hidden rounded-xl border bg-card">
        <div className="border-b px-4 py-4 sm:px-6">
          <h2 className="font-semibold">Waiting for a courier</h2>
          {parcels && (
            <p className="mt-1 text-sm text-muted-foreground">
              {parcels.totalItems} {parcels.totalItems === 1 ? "parcel" : "parcels"}, oldest first
            </p>
          )}
        </div>

        {!parcels ? (
          <p className="px-4 py-10 text-center text-sm text-muted-foreground">Loading parcels...</p>
        ) : parcels.items.length === 0 ? (
          <p className="px-4 py-10 text-center text-sm text-muted-foreground">Every parcel has a courier.</p>
        ) : (
          <>
            <ul className="divide-y">
              {parcels.items.map((parcel) => (
                <li key={parcel.id} className="space-y-3 px-4 py-4 sm:px-6">
                  <div className="flex flex-wrap items-baseline justify-between gap-2">
                    <span className="font-mono font-medium">{parcel.trackingNumber}</span>
                    <span className="text-xs text-muted-foreground">Booked {formatDate(parcel.createdAt)}</span>
                  </div>
                  <p className="text-sm text-muted-foreground">
                    {parcel.size} · {parcel.weightGrams.toLocaleString()} g · {parcel.source.toLowerCase()}
                    {parcel.merchantOrderReference ? ` · order ${parcel.merchantOrderReference}` : ""}
                  </p>
                  <div className="flex flex-wrap items-center gap-2">
                    <label className="sr-only" htmlFor={`courier-${parcel.id}`}>
                      Courier for {parcel.trackingNumber}
                    </label>
                    <select
                      id={`courier-${parcel.id}`}
                      className="h-8 min-w-0 flex-1 rounded-lg border border-input bg-transparent px-2 text-sm sm:max-w-xs"
                      value={choice[parcel.id] ?? workloads?.[0]?.courierUserId ?? ""}
                      onChange={(event) => setChoice((current) => ({ ...current, [parcel.id]: event.target.value }))}
                      disabled={!workloads || workloads.length === 0}
                    >
                      {(workloads ?? []).map((w) => (
                        <option key={w.courierUserId} value={w.courierUserId}>
                          {w.fullName} ({w.activeParcels} active)
                        </option>
                      ))}
                    </select>
                    <Button
                      size="sm"
                      onClick={() => assign(parcel)}
                      disabled={busyId === parcel.id || !workloads || workloads.length === 0}
                    >
                      {busyId === parcel.id ? "Assigning..." : "Assign"}
                    </Button>
                  </div>
                  {rowError?.id === parcel.id && <Alert>{rowError.message}</Alert>}
                </li>
              ))}
            </ul>

            {parcels.totalPages > 1 && (
              <div className="flex items-center justify-between border-t px-4 py-3 sm:px-6">
                <p className="text-xs text-muted-foreground">
                  Page {parcels.page + 1} of {parcels.totalPages}
                </p>
                <div className="flex gap-2">
                  <Button variant="outline" size="sm" disabled={page === 0} onClick={() => setPage((p) => p - 1)}>
                    Previous
                  </Button>
                  <Button
                    variant="outline"
                    size="sm"
                    disabled={page + 1 >= parcels.totalPages}
                    onClick={() => setPage((p) => p + 1)}
                  >
                    Next
                  </Button>
                </div>
              </div>
            )}
          </>
        )}
      </section>
    </div>
  );
}

function CouriersTab({ token, reloadKey, onChanged, onNotice }: TabProps) {
  const [page, setPage] = useState(0);
  const [profiles, setProfiles] = useState<PageResponse<CourierProfile> | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [actionError, setActionError] = useState<string | null>(null);
  const [userId, setUserId] = useState("");
  const [phone, setPhone] = useState("");
  const [vehicleType, setVehicleType] = useState<VehicleType>("BIKE");
  const [saving, setSaving] = useState(false);

  useEffect(() => {
    let cancelled = false;
    getCourierProfiles(token, page, PAGE_SIZE)
      .then((result) => {
        if (cancelled) return;
        setProfiles(result);
        setError(null);
      })
      .catch((err) => {
        if (!cancelled) setError(formatApiError(err));
      });
    return () => {
      cancelled = true;
    };
  }, [token, page, reloadKey]);

  async function create(event: React.FormEvent) {
    event.preventDefault();
    setSaving(true);
    setActionError(null);
    try {
      await createCourierProfile(token, { userId: userId.trim(), phone: phone.trim(), vehicleType });
      setUserId("");
      setPhone("");
      onNotice("Courier profile created.");
      onChanged();
    } catch (err) {
      setActionError(formatApiError(err));
    } finally {
      setSaving(false);
    }
  }

  async function toggle(profile: CourierProfile) {
    setActionError(null);
    try {
      await updateCourierProfile(token, profile.id, { active: !profile.active });
      onNotice(`${profile.fullName ?? "Courier"} ${profile.active ? "deactivated" : "activated"}.`);
      onChanged();
    } catch (err) {
      setActionError(formatApiError(err));
    }
  }

  return (
    <div className="space-y-6">
      {error && <Alert>Could not load couriers: {error}</Alert>}
      {actionError && <Alert>{actionError}</Alert>}

      <section className="rounded-xl border bg-card p-4 sm:p-6">
        <h2 className="font-semibold">Add a courier profile</h2>
        <p className="mt-1 text-sm text-muted-foreground">
          The courier account must already exist with the COURIER role (an admin creates it). Paste the account&apos;s
          user ID.
        </p>
        <form onSubmit={create} className="mt-4 grid gap-3 sm:grid-cols-2">
          <div className="space-y-1 sm:col-span-2">
            <Label htmlFor="courier-user-id">User ID</Label>
            <Input id="courier-user-id" value={userId} onChange={(e) => setUserId(e.target.value)} required />
          </div>
          <div className="space-y-1">
            <Label htmlFor="courier-phone">Phone (international format)</Label>
            <Input
              id="courier-phone"
              type="tel"
              placeholder="+94771234567"
              value={phone}
              onChange={(e) => setPhone(e.target.value)}
              required
            />
          </div>
          <div className="space-y-1">
            <Label htmlFor="courier-vehicle">Vehicle</Label>
            <select
              id="courier-vehicle"
              className="h-8 w-full rounded-lg border border-input bg-transparent px-2 text-sm"
              value={vehicleType}
              onChange={(e) => setVehicleType(e.target.value as VehicleType)}
            >
              {VEHICLE_TYPES.map((v) => (
                <option key={v} value={v}>
                  {v}
                </option>
              ))}
            </select>
          </div>
          <div className="sm:col-span-2">
            <Button type="submit" disabled={saving}>
              {saving ? "Saving..." : "Create profile"}
            </Button>
          </div>
        </form>
      </section>

      <section className="overflow-hidden rounded-xl border bg-card">
        <div className="border-b px-4 py-4 sm:px-6">
          <h2 className="font-semibold">Courier profiles</h2>
          {profiles && <p className="mt-1 text-sm text-muted-foreground">{profiles.totalItems} total</p>}
        </div>
        {!profiles ? (
          <p className="px-4 py-10 text-center text-sm text-muted-foreground">Loading couriers...</p>
        ) : profiles.items.length === 0 ? (
          <p className="px-4 py-10 text-center text-sm text-muted-foreground">No courier profiles yet.</p>
        ) : (
          <>
            <ul className="divide-y">
              {profiles.items.map((profile) => (
                <li key={profile.id} className="flex flex-wrap items-center justify-between gap-3 px-4 py-4 sm:px-6">
                  <div className="min-w-0">
                    <p className="font-medium">{profile.fullName ?? "Unknown account"}</p>
                    <p className="truncate text-xs text-muted-foreground">
                      {profile.email} · {profile.phone} · {profile.vehicleType}
                    </p>
                  </div>
                  <div className="flex items-center gap-3">
                    <span
                      className={`rounded-full px-2 py-1 text-xs font-medium ${
                        profile.active ? "bg-emerald-100 text-emerald-800" : "bg-muted text-muted-foreground"
                      }`}
                    >
                      {profile.active ? "Active" : "Inactive"}
                    </span>
                    <Button variant="outline" size="sm" onClick={() => toggle(profile)}>
                      {profile.active ? "Deactivate" : "Activate"}
                    </Button>
                  </div>
                </li>
              ))}
            </ul>
            {profiles.totalPages > 1 && (
              <div className="flex items-center justify-between border-t px-4 py-3 sm:px-6">
                <p className="text-xs text-muted-foreground">
                  Page {profiles.page + 1} of {profiles.totalPages}
                </p>
                <div className="flex gap-2">
                  <Button variant="outline" size="sm" disabled={page === 0} onClick={() => setPage((p) => p - 1)}>
                    Previous
                  </Button>
                  <Button
                    variant="outline"
                    size="sm"
                    disabled={page + 1 >= profiles.totalPages}
                    onClick={() => setPage((p) => p + 1)}
                  >
                    Next
                  </Button>
                </div>
              </div>
            )}
          </>
        )}
      </section>
    </div>
  );
}

"use client";

import React, { useEffect, useState } from "react";
import { useAuth } from "@/lib/auth-context";
import {
  api,
  formatApiError,
  type AdminZoneRequest,
  type AdminZoneResponse,
} from "@/lib/api";
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { PortalPlaceholderCard } from "@/components/portal";
import { AlertCircle, CheckCircle2, Loader2, Pencil, Plus, RefreshCw, X } from "lucide-react";

const MAX_CODE_LENGTH = 50;
const MAX_NAME_LENGTH = 200;

/** `null` = panel closed, `"new"` = creating, otherwise the zone being edited. */
type Editing = null | "new" | AdminZoneResponse;

interface ZoneForm {
  code: string;
  name: string;
  active: boolean;
}

const EMPTY_FORM: ZoneForm = { code: "", name: "", active: true };

function validateZone(form: ZoneForm): string | null {
  if (!form.code.trim()) return "Enter a zone code, for example LK-NP.";
  if (form.code.trim().length > MAX_CODE_LENGTH) return `The code can be at most ${MAX_CODE_LENGTH} characters.`;
  if (!form.name.trim()) return "Enter a zone name.";
  if (form.name.trim().length > MAX_NAME_LENGTH) return `The name can be at most ${MAX_NAME_LENGTH} characters.`;
  return null;
}

export default function AdminPricingPage() {
  const { token } = useAuth();

  const [zones, setZones] = useState<AdminZoneResponse[]>([]);
  const [loading, setLoading] = useState(true);
  const [loadError, setLoadError] = useState<string | null>(null);
  const [notice, setNotice] = useState<string | null>(null);

  const [editing, setEditing] = useState<Editing>(null);
  const [form, setForm] = useState<ZoneForm>(EMPTY_FORM);
  const [formError, setFormError] = useState<string | null>(null);
  const [saving, setSaving] = useState(false);
  const [togglingId, setTogglingId] = useState<string | null>(null);

  // Bumping reloadKey re-runs the fetch below; only event handlers change it.
  const [reloadKey, setReloadKey] = useState(0);

  useEffect(() => {
    if (!token) return;
    let cancelled = false;
    api
      .get<AdminZoneResponse[]>("/api/v1/admin/zones", { token })
      .then((data) => {
        if (!cancelled) setZones(data);
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
  }, [token, reloadKey]);

  function reload() {
    setLoading(true);
    setLoadError(null);
    setReloadKey((key) => key + 1);
  }

  function openCreate() {
    setNotice(null);
    setFormError(null);
    setForm(EMPTY_FORM);
    setEditing("new");
  }

  function openEdit(zone: AdminZoneResponse) {
    setNotice(null);
    setFormError(null);
    setForm({ code: zone.code, name: zone.name, active: zone.active });
    setEditing(zone);
  }

  function closeForm() {
    setEditing(null);
    setFormError(null);
  }

  async function handleSave(event: React.FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (!token || editing === null) return;

    const problem = validateZone(form);
    if (problem) {
      setFormError(problem);
      return;
    }

    const body: AdminZoneRequest = {
      code: form.code.trim(),
      name: form.name.trim(),
      active: form.active,
    };

    setSaving(true);
    setFormError(null);
    try {
      if (editing === "new") {
        await api.post<AdminZoneResponse>("/api/v1/admin/zones", body, { token });
        setNotice(`Zone ${body.code} created.`);
      } else {
        await api.put<AdminZoneResponse>(`/api/v1/admin/zones/${editing.id}`, body, { token });
        setNotice(`Zone ${body.code} updated.`);
      }
      setEditing(null);
      reload();
    } catch (err) {
      setFormError(formatApiError(err));
    } finally {
      setSaving(false);
    }
  }

  async function handleToggleActive(zone: AdminZoneResponse) {
    if (!token) return;
    setNotice(null);
    setTogglingId(zone.id);
    try {
      await api.put<AdminZoneResponse>(
        `/api/v1/admin/zones/${zone.id}`,
        { code: zone.code, name: zone.name, active: !zone.active } satisfies AdminZoneRequest,
        { token },
      );
      setNotice(`Zone ${zone.code} ${zone.active ? "deactivated" : "activated"}.`);
      reload();
    } catch (err) {
      setLoadError(formatApiError(err));
    } finally {
      setTogglingId(null);
    }
  }

  const activeCount = zones.filter((zone) => zone.active).length;

  return (
    <div className="space-y-6">
      <Card>
        <CardHeader className="flex flex-row items-start justify-between gap-4">
          <div className="space-y-1">
            <CardTitle>Delivery zones</CardTitle>
            <CardDescription>
              {zones.length} zones, {activeCount} active. Customers can only book between active zones.
            </CardDescription>
          </div>
          <div className="flex gap-2">
            <Button variant="outline" size="sm" onClick={reload} disabled={loading}>
              <RefreshCw className="size-4" />
              Refresh
            </Button>
            <Button size="sm" onClick={openCreate}>
              <Plus className="size-4" />
              Add zone
            </Button>
          </div>
        </CardHeader>

        <CardContent className="space-y-4">
          {notice && (
            <p role="status" className="flex items-center gap-2 rounded-lg border border-emerald-500/40 bg-emerald-500/10 p-3 text-sm">
              <CheckCircle2 className="size-4" />
              {notice}
            </p>
          )}
          {loadError && (
            <p role="alert" className="flex items-center gap-2 rounded-lg border border-destructive/40 bg-destructive/10 p-3 text-sm text-destructive">
              <AlertCircle className="size-4" />
              {loadError}
            </p>
          )}

          {editing !== null && (
            <form onSubmit={handleSave} noValidate className="space-y-4 rounded-xl border p-4">
              <div className="flex items-center justify-between">
                <h2 className="font-semibold">{editing === "new" ? "New zone" : `Edit ${editing.code}`}</h2>
                <Button type="button" variant="ghost" size="icon-sm" onClick={closeForm} aria-label="Close">
                  <X className="size-4" />
                </Button>
              </div>

              <div className="grid gap-4 sm:grid-cols-2">
                <div className="space-y-2">
                  <Label htmlFor="zone-code">Code</Label>
                  <Input
                    id="zone-code"
                    value={form.code}
                    maxLength={MAX_CODE_LENGTH}
                    onChange={(e) => setForm({ ...form, code: e.target.value })}
                    placeholder="LK-NP"
                  />
                </div>
                <div className="space-y-2">
                  <Label htmlFor="zone-name">Name</Label>
                  <Input
                    id="zone-name"
                    value={form.name}
                    maxLength={MAX_NAME_LENGTH}
                    onChange={(e) => setForm({ ...form, name: e.target.value })}
                    placeholder="Northern Province"
                  />
                </div>
              </div>

              <label className="flex items-center gap-2 text-sm">
                <input
                  type="checkbox"
                  checked={form.active}
                  onChange={(e) => setForm({ ...form, active: e.target.checked })}
                />
                Active (customers can book to and from this zone)
              </label>

              {formError && (
                <p role="alert" className="text-sm text-destructive">
                  {formError}
                </p>
              )}

              <div className="flex justify-end gap-2">
                <Button type="button" variant="outline" onClick={closeForm} disabled={saving}>
                  Cancel
                </Button>
                <Button type="submit" disabled={saving}>
                  {saving && <Loader2 className="size-4 animate-spin" />}
                  {editing === "new" ? "Create zone" : "Save changes"}
                </Button>
              </div>
            </form>
          )}

          {loading ? (
            <p className="flex items-center gap-2 text-sm text-muted-foreground">
              <Loader2 className="size-4 animate-spin" /> Loading zones…
            </p>
          ) : zones.length === 0 ? (
            <p className="text-sm text-muted-foreground">No zones yet. Add the first one.</p>
          ) : (
            <div className="overflow-x-auto">
              <table className="w-full text-sm">
                <thead className="border-b text-left text-muted-foreground">
                  <tr>
                    <th className="py-2 pr-4 font-medium">Code</th>
                    <th className="py-2 pr-4 font-medium">Name</th>
                    <th className="py-2 pr-4 font-medium">Status</th>
                    <th className="py-2 text-right font-medium">Actions</th>
                  </tr>
                </thead>
                <tbody>
                  {zones.map((zone) => (
                    <tr key={zone.id} className="border-b last:border-0">
                      <td className="py-2 pr-4 font-mono">{zone.code}</td>
                      <td className="py-2 pr-4">{zone.name}</td>
                      <td className="py-2 pr-4">
                        <span
                          className={
                            zone.active
                              ? "rounded-full bg-emerald-500/15 px-2 py-0.5 text-xs font-medium text-emerald-700 dark:text-emerald-400"
                              : "rounded-full bg-muted px-2 py-0.5 text-xs font-medium text-muted-foreground"
                          }
                        >
                          {zone.active ? "Active" : "Inactive"}
                        </span>
                      </td>
                      <td className="py-2 text-right">
                        <div className="flex justify-end gap-2">
                          <Button variant="ghost" size="sm" onClick={() => openEdit(zone)}>
                            <Pencil className="size-4" />
                            Edit
                          </Button>
                          <Button
                            variant="outline"
                            size="sm"
                            onClick={() => handleToggleActive(zone)}
                            disabled={togglingId === zone.id}
                          >
                            {togglingId === zone.id && <Loader2 className="size-4 animate-spin" />}
                            {zone.active ? "Deactivate" : "Activate"}
                          </Button>
                        </div>
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          )}
        </CardContent>
      </Card>

      <PortalPlaceholderCard
        title="Pricing rules"
        description="Base fees, per-kg rates and size surcharges for each origin → destination pair. Next up in sub-part 8.4."
        workstream="WS8 — Pricing Administration & Zones"
        contractRef="ADM-3 · Contract §5.2"
        plannedEndpoints={[
          "GET /api/v1/pricing-rules (planned)",
          "POST /api/v1/pricing-rules (planned)",
          "PUT /api/v1/pricing-rules/{id} (planned)",
        ]}
        plannedFeatures={[
          "Rule table per origin and destination zone with add and edit dialogs",
          "Missing-route warning for active zone pairs with no rule",
          "Fee simulator that calls the price-quote endpoint",
        ]}
      />
    </div>
  );
}

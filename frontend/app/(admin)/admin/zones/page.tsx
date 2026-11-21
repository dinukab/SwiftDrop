"use client";

import React, { useEffect, useState } from "react";
import { useAuth } from "@/lib/auth-context";
import { api, formatApiError, type AdminZoneRequest, type AdminZoneResponse } from "@/lib/api";
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { ScrollArea } from "@/components/ui/scroll-area";
import { StatusBadge } from "@/components/admin/pricing-parts";
import { AlertCircle, CheckCircle2, Loader2, Pencil, Plus, RefreshCw, X } from "lucide-react";

const MAX_CODE_LENGTH = 50;
const MAX_NAME_LENGTH = 200;

/** `null` = panel closed, `"new"` = creating, otherwise the zone being edited. */
type EditingZone = null | "new" | AdminZoneResponse;

interface ZoneForm {
  code: string;
  name: string;
  active: boolean;
}

const EMPTY_ZONE: ZoneForm = { code: "", name: "", active: true };

function validateZone(form: ZoneForm): string | null {
  if (!form.code.trim()) return "Enter a zone code, for example LK-NP.";
  if (form.code.trim().length > MAX_CODE_LENGTH) return `The code can be at most ${MAX_CODE_LENGTH} characters.`;
  if (!form.name.trim()) return "Enter a zone name.";
  if (form.name.trim().length > MAX_NAME_LENGTH) return `The name can be at most ${MAX_NAME_LENGTH} characters.`;
  return null;
}

export default function AdminZonesPage() {
  const { token } = useAuth();

  const [zones, setZones] = useState<AdminZoneResponse[]>([]);
  const [loading, setLoading] = useState(true);
  const [loadError, setLoadError] = useState<string | null>(null);
  const [notice, setNotice] = useState<string | null>(null);

  // Bumping reloadKey re-runs the fetch below; only event handlers change it.
  const [reloadKey, setReloadKey] = useState(0);

  const [editingZone, setEditingZone] = useState<EditingZone>(null);
  const [zoneForm, setZoneForm] = useState<ZoneForm>(EMPTY_ZONE);
  const [zoneFormError, setZoneFormError] = useState<string | null>(null);
  const [savingZone, setSavingZone] = useState(false);
  const [togglingZoneId, setTogglingZoneId] = useState<string | null>(null);

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
  }, [token, reloadKey]);

  function reload() {
    setLoading(true);
    setLoadError(null);
    setReloadKey((key) => key + 1);
  }

  const activeCount = zones.filter((zone) => zone.active).length;

  function openCreateZone() {
    setNotice(null);
    setZoneFormError(null);
    setZoneForm(EMPTY_ZONE);
    setEditingZone("new");
  }

  function openEditZone(zone: AdminZoneResponse) {
    setNotice(null);
    setZoneFormError(null);
    setZoneForm({ code: zone.code, name: zone.name, active: zone.active });
    setEditingZone(zone);
  }

  async function handleSaveZone(event: React.FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (!token || editingZone === null) return;

    const problem = validateZone(zoneForm);
    if (problem) {
      setZoneFormError(problem);
      return;
    }

    const body: AdminZoneRequest = {
      code: zoneForm.code.trim(),
      name: zoneForm.name.trim(),
      active: zoneForm.active,
    };

    setSavingZone(true);
    setZoneFormError(null);
    try {
      if (editingZone === "new") {
        await api.post<AdminZoneResponse>("/api/v1/admin/zones", body, { token });
        setNotice(`Zone ${body.code} created.`);
      } else {
        await api.put<AdminZoneResponse>(`/api/v1/admin/zones/${editingZone.id}`, body, { token });
        setNotice(`Zone ${body.code} updated.`);
      }
      setEditingZone(null);
      reload();
    } catch (err) {
      setZoneFormError(formatApiError(err));
    } finally {
      setSavingZone(false);
    }
  }

  async function handleToggleZone(zone: AdminZoneResponse) {
    if (!token) return;
    setNotice(null);
    setTogglingZoneId(zone.id);
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
      setTogglingZoneId(null);
    }
  }

  return (
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
          <Button size="sm" onClick={openCreateZone}>
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

        {editingZone !== null && (
          <form onSubmit={handleSaveZone} noValidate className="space-y-4 rounded-xl border p-4">
            <div className="flex items-center justify-between">
              <h2 className="font-semibold">{editingZone === "new" ? "New zone" : `Edit ${editingZone.code}`}</h2>
              <Button type="button" variant="ghost" size="icon-sm" onClick={() => setEditingZone(null)} aria-label="Close">
                <X className="size-4" />
              </Button>
            </div>

            <div className="grid gap-4 sm:grid-cols-2">
              <div className="space-y-2">
                <Label htmlFor="zone-code">Code</Label>
                <Input
                  id="zone-code"
                  value={zoneForm.code}
                  maxLength={MAX_CODE_LENGTH}
                  onChange={(e) => setZoneForm({ ...zoneForm, code: e.target.value })}
                  placeholder="LK-NP"
                />
              </div>
              <div className="space-y-2">
                <Label htmlFor="zone-name">Name</Label>
                <Input
                  id="zone-name"
                  value={zoneForm.name}
                  maxLength={MAX_NAME_LENGTH}
                  onChange={(e) => setZoneForm({ ...zoneForm, name: e.target.value })}
                  placeholder="Northern Province"
                />
              </div>
            </div>

            <label className="flex items-center gap-2 text-sm">
              <input
                type="checkbox"
                checked={zoneForm.active}
                onChange={(e) => setZoneForm({ ...zoneForm, active: e.target.checked })}
              />
              Active (customers can book to and from this zone)
            </label>

            {zoneFormError && (
              <p role="alert" className="text-sm text-destructive">
                {zoneFormError}
              </p>
            )}

            <div className="flex justify-end gap-2">
              <Button type="button" variant="outline" onClick={() => setEditingZone(null)} disabled={savingZone}>
                Cancel
              </Button>
              <Button type="submit" disabled={savingZone}>
                {savingZone && <Loader2 className="size-4 animate-spin" />}
                {editingZone === "new" ? "Create zone" : "Save changes"}
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
          <ScrollArea className="h-[480px] w-full rounded-lg border">
            <table className="w-full text-sm">
              <thead className="sticky top-0 border-b bg-background text-left text-muted-foreground">
                <tr>
                  <th className="py-2 pl-3 pr-4 font-medium">Code</th>
                  <th className="py-2 pr-4 font-medium">Name</th>
                  <th className="py-2 pr-4 font-medium">Status</th>
                  <th className="py-2 pr-3 text-right font-medium">Actions</th>
                </tr>
              </thead>
              <tbody>
                {zones.map((zone) => (
                  <tr key={zone.id} className="border-b last:border-0">
                    <td className="py-2 pl-3 pr-4 font-mono">{zone.code}</td>
                    <td className="py-2 pr-4">{zone.name}</td>
                    <td className="py-2 pr-4">
                      <StatusBadge active={zone.active} />
                    </td>
                    <td className="py-2 pr-3 text-right">
                      <div className="flex justify-end gap-2">
                        <Button variant="ghost" size="sm" onClick={() => openEditZone(zone)}>
                          <Pencil className="size-4" />
                          Edit
                        </Button>
                        <Button
                          variant="outline"
                          size="sm"
                          onClick={() => handleToggleZone(zone)}
                          disabled={togglingZoneId === zone.id}
                        >
                          {togglingZoneId === zone.id && <Loader2 className="size-4 animate-spin" />}
                          {zone.active ? "Deactivate" : "Activate"}
                        </Button>
                      </div>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </ScrollArea>
        )}
      </CardContent>
    </Card>
  );
}

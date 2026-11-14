"use client";

import React, { useEffect, useMemo, useState } from "react";
import { useAuth } from "@/lib/auth-context";
import {
  api,
  formatApiError,
  type AdminZoneRequest,
  type AdminZoneResponse,
  type PricingRuleRequest,
  type PricingRuleResponse,
} from "@/lib/api";
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { AlertCircle, CheckCircle2, Loader2, Pencil, Plus, RefreshCw, X } from "lucide-react";

const MAX_CODE_LENGTH = 50;
const MAX_NAME_LENGTH = 200;
const AMOUNT_PATTERN = /^\d{1,10}(\.\d{1,2})?$/;
const CURRENCY_PATTERN = /^[A-Za-z]{3}$/;
const MISSING_ROUTE_PREVIEW = 5;

/** `null` = panel closed, `"new"` = creating, otherwise the zone being edited. */
type EditingZone = null | "new" | AdminZoneResponse;
/** `null` = panel closed, `"new"` = creating, otherwise the rule being edited. */
type EditingRule = null | "new" | PricingRuleResponse;

interface ZoneForm {
  code: string;
  name: string;
  active: boolean;
}

interface RuleForm {
  originZoneId: string;
  destinationZoneId: string;
  baseFee: string;
  perKgFee: string;
  smallSizeSurcharge: string;
  mediumSizeSurcharge: string;
  largeSizeSurcharge: string;
  currency: string;
  active: boolean;
}

const EMPTY_ZONE: ZoneForm = { code: "", name: "", active: true };
const EMPTY_RULE: RuleForm = {
  originZoneId: "",
  destinationZoneId: "",
  baseFee: "",
  perKgFee: "",
  smallSizeSurcharge: "0.00",
  mediumSizeSurcharge: "0.00",
  largeSizeSurcharge: "0.00",
  currency: "LKR",
  active: true,
};

function validateZone(form: ZoneForm): string | null {
  if (!form.code.trim()) return "Enter a zone code, for example LK-NP.";
  if (form.code.trim().length > MAX_CODE_LENGTH) return `The code can be at most ${MAX_CODE_LENGTH} characters.`;
  if (!form.name.trim()) return "Enter a zone name.";
  if (form.name.trim().length > MAX_NAME_LENGTH) return `The name can be at most ${MAX_NAME_LENGTH} characters.`;
  return null;
}

function validateRule(form: RuleForm): string | null {
  if (!form.originZoneId) return "Choose the origin zone.";
  if (!form.destinationZoneId) return "Choose the destination zone.";
  const amounts: [string, string][] = [
    ["Base fee", form.baseFee],
    ["Per-kg fee", form.perKgFee],
    ["Small surcharge", form.smallSizeSurcharge],
    ["Medium surcharge", form.mediumSizeSurcharge],
    ["Large surcharge", form.largeSizeSurcharge],
  ];
  for (const [label, value] of amounts) {
    if (!AMOUNT_PATTERN.test(value.trim())) {
      return `${label}: enter an amount with at most two decimals, for example 300.00.`;
    }
  }
  if (!CURRENCY_PATTERN.test(form.currency.trim())) return "Currency must be a 3-letter code, for example LKR.";
  return null;
}

export default function AdminPricingPage() {
  const { token } = useAuth();

  const [zones, setZones] = useState<AdminZoneResponse[]>([]);
  const [rules, setRules] = useState<PricingRuleResponse[]>([]);
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

  const [editingRule, setEditingRule] = useState<EditingRule>(null);
  const [ruleForm, setRuleForm] = useState<RuleForm>(EMPTY_RULE);
  const [ruleFormError, setRuleFormError] = useState<string | null>(null);
  const [savingRule, setSavingRule] = useState(false);

  useEffect(() => {
    if (!token) return;
    let cancelled = false;
    Promise.all([
      api.get<AdminZoneResponse[]>("/api/v1/admin/zones", { token }),
      api.get<PricingRuleResponse[]>("/api/v1/admin/pricing-rules", { token }),
    ])
      .then(([zoneList, ruleList]) => {
        if (!cancelled) {
          setZones(zoneList);
          setRules(ruleList);
        }
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

  const zoneName = useMemo(() => new Map(zones.map((zone) => [zone.id, zone.name])), [zones]);
  const zoneLabel = (id: string) => zoneName.get(id) ?? "Unknown zone";

  // Active zone pairs without an active rule: customers cannot be quoted for these routes.
  const missingRoutes = useMemo(() => {
    const priced = new Set(rules.filter((rule) => rule.active).map((rule) => `${rule.originZoneId}|${rule.destinationZoneId}`));
    const active = zones.filter((zone) => zone.active);
    const missing: { origin: string; destination: string }[] = [];
    for (const origin of active) {
      for (const destination of active) {
        if (!priced.has(`${origin.id}|${destination.id}`)) {
          missing.push({ origin: origin.name, destination: destination.name });
        }
      }
    }
    return missing;
  }, [zones, rules]);

  const activeCount = zones.filter((zone) => zone.active).length;

  // ---- Zones ----

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

  // ---- Rules ----

  function openCreateRule() {
    setNotice(null);
    setRuleFormError(null);
    setRuleForm(EMPTY_RULE);
    setEditingRule("new");
  }

  function openEditRule(rule: PricingRuleResponse) {
    setNotice(null);
    setRuleFormError(null);
    setRuleForm({
      originZoneId: rule.originZoneId,
      destinationZoneId: rule.destinationZoneId,
      baseFee: rule.baseFee,
      perKgFee: rule.perKgFee,
      smallSizeSurcharge: rule.smallSizeSurcharge,
      mediumSizeSurcharge: rule.mediumSizeSurcharge,
      largeSizeSurcharge: rule.largeSizeSurcharge,
      currency: rule.currency,
      active: rule.active,
    });
    setEditingRule(rule);
  }

  async function handleSaveRule(event: React.FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (!token || editingRule === null) return;

    const problem = validateRule(ruleForm);
    if (problem) {
      setRuleFormError(problem);
      return;
    }

    const body: PricingRuleRequest = {
      originZoneId: ruleForm.originZoneId,
      destinationZoneId: ruleForm.destinationZoneId,
      baseFee: ruleForm.baseFee.trim(),
      perKgFee: ruleForm.perKgFee.trim(),
      smallSizeSurcharge: ruleForm.smallSizeSurcharge.trim(),
      mediumSizeSurcharge: ruleForm.mediumSizeSurcharge.trim(),
      largeSizeSurcharge: ruleForm.largeSizeSurcharge.trim(),
      currency: ruleForm.currency.trim().toUpperCase(),
      active: ruleForm.active,
    };
    const route = `${zoneLabel(body.originZoneId)} → ${zoneLabel(body.destinationZoneId)}`;

    setSavingRule(true);
    setRuleFormError(null);
    try {
      if (editingRule === "new") {
        await api.post<PricingRuleResponse>("/api/v1/admin/pricing-rules", body, { token });
        setNotice(`Rule created: ${route}.`);
      } else {
        await api.put<PricingRuleResponse>(`/api/v1/admin/pricing-rules/${editingRule.id}`, body, { token });
        setNotice(`Rule updated: ${route}.`);
      }
      setEditingRule(null);
      reload();
    } catch (err) {
      setRuleFormError(formatApiError(err));
    } finally {
      setSavingRule(false);
    }
  }

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
                        <StatusBadge active={zone.active} />
                      </td>
                      <td className="py-2 text-right">
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
            </div>
          )}
        </CardContent>
      </Card>

      <Card>
        <CardHeader className="flex flex-row items-start justify-between gap-4">
          <div className="space-y-1">
            <CardTitle>Pricing rules</CardTitle>
            <CardDescription>
              Fees for each origin → destination pair. A pair without an active rule cannot be quoted or booked.
            </CardDescription>
          </div>
          <Button size="sm" onClick={openCreateRule} disabled={zones.length === 0}>
            <Plus className="size-4" />
            Add rule
          </Button>
        </CardHeader>

        <CardContent className="space-y-4">
          {missingRoutes.length > 0 && (
            <p role="status" className="flex items-start gap-2 rounded-lg border border-amber-500/40 bg-amber-500/10 p-3 text-sm">
              <AlertCircle className="mt-0.5 size-4 shrink-0" />
              <span>
                {missingRoutes.length} route{missingRoutes.length === 1 ? "" : "s"} between active zones have no
                active rule, so customers cannot book them:{" "}
                {missingRoutes
                  .slice(0, MISSING_ROUTE_PREVIEW)
                  .map((route) => `${route.origin} → ${route.destination}`)
                  .join("; ")}
                {missingRoutes.length > MISSING_ROUTE_PREVIEW ? "; …" : ""}
              </span>
            </p>
          )}

          {editingRule !== null && (
            <form onSubmit={handleSaveRule} noValidate className="space-y-4 rounded-xl border p-4">
              <div className="flex items-center justify-between">
                <h2 className="font-semibold">{editingRule === "new" ? "New pricing rule" : "Edit pricing rule"}</h2>
                <Button type="button" variant="ghost" size="icon-sm" onClick={() => setEditingRule(null)} aria-label="Close">
                  <X className="size-4" />
                </Button>
              </div>

              <div className="grid gap-4 sm:grid-cols-2">
                <div className="space-y-2">
                  <Label htmlFor="rule-origin">From</Label>
                  <ZoneSelect
                    id="rule-origin"
                    value={ruleForm.originZoneId}
                    zones={zones}
                    onChange={(value) => setRuleForm({ ...ruleForm, originZoneId: value })}
                  />
                </div>
                <div className="space-y-2">
                  <Label htmlFor="rule-destination">To</Label>
                  <ZoneSelect
                    id="rule-destination"
                    value={ruleForm.destinationZoneId}
                    zones={zones}
                    onChange={(value) => setRuleForm({ ...ruleForm, destinationZoneId: value })}
                  />
                </div>
              </div>

              <div className="grid gap-4 sm:grid-cols-3">
                <AmountField id="rule-base" label="Base fee" value={ruleForm.baseFee} onChange={(v) => setRuleForm({ ...ruleForm, baseFee: v })} />
                <AmountField id="rule-perkg" label="Per kg" value={ruleForm.perKgFee} onChange={(v) => setRuleForm({ ...ruleForm, perKgFee: v })} />
                <div className="space-y-2">
                  <Label htmlFor="rule-currency">Currency</Label>
                  <Input
                    id="rule-currency"
                    value={ruleForm.currency}
                    maxLength={3}
                    onChange={(e) => setRuleForm({ ...ruleForm, currency: e.target.value })}
                  />
                </div>
                <AmountField id="rule-small" label="Small surcharge" value={ruleForm.smallSizeSurcharge} onChange={(v) => setRuleForm({ ...ruleForm, smallSizeSurcharge: v })} />
                <AmountField id="rule-medium" label="Medium surcharge" value={ruleForm.mediumSizeSurcharge} onChange={(v) => setRuleForm({ ...ruleForm, mediumSizeSurcharge: v })} />
                <AmountField id="rule-large" label="Large surcharge" value={ruleForm.largeSizeSurcharge} onChange={(v) => setRuleForm({ ...ruleForm, largeSizeSurcharge: v })} />
              </div>

              <label className="flex items-center gap-2 text-sm">
                <input
                  type="checkbox"
                  checked={ruleForm.active}
                  onChange={(e) => setRuleForm({ ...ruleForm, active: e.target.checked })}
                />
                Active (used for quotes and bookings)
              </label>

              {ruleFormError && (
                <p role="alert" className="text-sm text-destructive">
                  {ruleFormError}
                </p>
              )}

              <div className="flex justify-end gap-2">
                <Button type="button" variant="outline" onClick={() => setEditingRule(null)} disabled={savingRule}>
                  Cancel
                </Button>
                <Button type="submit" disabled={savingRule}>
                  {savingRule && <Loader2 className="size-4 animate-spin" />}
                  {editingRule === "new" ? "Create rule" : "Save changes"}
                </Button>
              </div>
            </form>
          )}

          {loading ? (
            <p className="flex items-center gap-2 text-sm text-muted-foreground">
              <Loader2 className="size-4 animate-spin" /> Loading rules…
            </p>
          ) : rules.length === 0 ? (
            <p className="text-sm text-muted-foreground">No pricing rules yet.</p>
          ) : (
            <div className="overflow-x-auto">
              <table className="w-full text-sm">
                <thead className="border-b text-left text-muted-foreground">
                  <tr>
                    <th className="py-2 pr-4 font-medium">Route</th>
                    <th className="py-2 pr-4 text-right font-medium">Base</th>
                    <th className="py-2 pr-4 text-right font-medium">Per kg</th>
                    <th className="py-2 pr-4 text-right font-medium">S / M / L</th>
                    <th className="py-2 pr-4 font-medium">Status</th>
                    <th className="py-2 text-right font-medium">Actions</th>
                  </tr>
                </thead>
                <tbody>
                  {rules.map((rule) => (
                    <tr key={rule.id} className="border-b last:border-0">
                      <td className="py-2 pr-4">
                        {zoneLabel(rule.originZoneId)} → {zoneLabel(rule.destinationZoneId)}
                      </td>
                      <td className="py-2 pr-4 text-right font-mono">
                        {rule.currency} {rule.baseFee}
                      </td>
                      <td className="py-2 pr-4 text-right font-mono">{rule.perKgFee}</td>
                      <td className="py-2 pr-4 text-right font-mono">
                        {rule.smallSizeSurcharge} / {rule.mediumSizeSurcharge} / {rule.largeSizeSurcharge}
                      </td>
                      <td className="py-2 pr-4">
                        <StatusBadge active={rule.active} />
                      </td>
                      <td className="py-2 text-right">
                        <Button variant="ghost" size="sm" onClick={() => openEditRule(rule)}>
                          <Pencil className="size-4" />
                          Edit
                        </Button>
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          )}
        </CardContent>
      </Card>
    </div>
  );
}

function StatusBadge({ active }: { active: boolean }) {
  return (
    <span
      className={
        active
          ? "rounded-full bg-emerald-500/15 px-2 py-0.5 text-xs font-medium text-emerald-700 dark:text-emerald-400"
          : "rounded-full bg-muted px-2 py-0.5 text-xs font-medium text-muted-foreground"
      }
    >
      {active ? "Active" : "Inactive"}
    </span>
  );
}

function AmountField({
  id,
  label,
  value,
  onChange,
}: {
  id: string;
  label: string;
  value: string;
  onChange: (value: string) => void;
}) {
  return (
    <div className="space-y-2">
      <Label htmlFor={id}>{label}</Label>
      <Input id={id} inputMode="decimal" value={value} onChange={(e) => onChange(e.target.value)} placeholder="0.00" />
    </div>
  );
}

function ZoneSelect({
  id,
  value,
  zones,
  onChange,
}: {
  id: string;
  value: string;
  zones: AdminZoneResponse[];
  onChange: (value: string) => void;
}) {
  return (
    <select
      id={id}
      value={value}
      onChange={(e) => onChange(e.target.value)}
      className="h-8 w-full rounded-lg border border-input bg-transparent px-2.5 text-sm outline-none focus-visible:border-ring focus-visible:ring-3 focus-visible:ring-ring/50 dark:bg-input/30"
    >
      <option value="">Select a zone</option>
      {zones.map((zone) => (
        <option key={zone.id} value={zone.id}>
          {zone.name}
          {zone.active ? "" : " (inactive)"}
        </option>
      ))}
    </select>
  );
}

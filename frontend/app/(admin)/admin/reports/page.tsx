"use client";

import React, { useEffect, useState } from "react";
import { useAuth } from "@/lib/auth-context";
import { api, formatApiError, type DailyDeliveriesResponse, type DeliveryOutcomesResponse } from "@/lib/api";
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { ScrollArea } from "@/components/ui/scroll-area";
import { AlertCircle, Loader2, RefreshCw } from "lucide-react";

/** Same limit as the backend (D-08); checked here only to give quick feedback. */
const MAX_RANGE_DAYS = 92;
const PRESETS = [7, 30, 90];
const BUSINESS_TIME_ZONE = "Asia/Colombo";

interface Range {
  from: string;
  to: string;
}

/** Today as "YYYY-MM-DD" in the business time zone, not the browser's. */
function businessToday(): string {
  // en-CA formats dates as YYYY-MM-DD
  return new Intl.DateTimeFormat("en-CA", { timeZone: BUSINESS_TIME_ZONE }).format(new Date());
}

/** Date-only arithmetic in UTC, so the browser's time zone and DST never shift the day. */
function addDays(isoDate: string, days: number): string {
  const date = new Date(`${isoDate}T00:00:00Z`);
  date.setUTCDate(date.getUTCDate() + days);
  return date.toISOString().slice(0, 10);
}

function daysInRange(range: Range): number {
  const ms = Date.parse(`${range.to}T00:00:00Z`) - Date.parse(`${range.from}T00:00:00Z`);
  return Math.round(ms / 86_400_000) + 1;
}

function validateRange(range: Range): string | null {
  if (!range.from || !range.to) return "Choose both a start and an end date.";
  if (range.from > range.to) return "The start date must not be after the end date.";
  if (daysInRange(range) > MAX_RANGE_DAYS) return `Choose a range of at most ${MAX_RANGE_DAYS} days.`;
  return null;
}

function formatRate(rate: number | null): string {
  return rate === null ? "—" : `${(rate * 100).toFixed(1)}%`;
}

interface Reports {
  daily: DailyDeliveriesResponse;
  outcomes: DeliveryOutcomesResponse;
}

function formatDay(isoDate: string): string {
  return new Intl.DateTimeFormat("en-GB", {
    weekday: "short",
    day: "numeric",
    month: "short",
    year: "numeric",
    timeZone: "UTC",
  }).format(new Date(`${isoDate}T00:00:00Z`));
}

export default function AdminReportsPage() {
  const { token } = useAuth();

  // `null` asks the backend for its default range (the last 30 days); the inputs then show it.
  const [query, setQuery] = useState<Range | null>(null);
  const [form, setForm] = useState<Range>({ from: "", to: "" });
  const [formError, setFormError] = useState<string | null>(null);

  const [reports, setReports] = useState<Reports | null>(null);
  const [loading, setLoading] = useState(true);
  const [loadError, setLoadError] = useState<string | null>(null);
  // Bumping reloadKey re-runs the fetch below; only event handlers change it.
  const [reloadKey, setReloadKey] = useState(0);

  useEffect(() => {
    if (!token) return;
    let cancelled = false;
    // With no query both reports use the backend's default range, so they always cover the same days
    const params = query ? `?${new URLSearchParams({ from: query.from, to: query.to })}` : "";
    Promise.all([
      api.get<DailyDeliveriesResponse>(`/api/v1/reports/daily-deliveries${params}`, { token }),
      api.get<DeliveryOutcomesResponse>(`/api/v1/reports/delivery-outcomes${params}`, { token }),
    ])
      .then(([daily, outcomes]) => {
        if (cancelled) return;
        setReports({ daily, outcomes });
        setForm({ from: daily.from, to: daily.to });
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
  }, [token, query, reloadKey]);

  function load(range: Range | null) {
    setLoading(true);
    setLoadError(null);
    setFormError(null);
    setQuery(range);
    setReloadKey((key) => key + 1);
  }

  function handleApply(event: React.FormEvent<HTMLFormElement>) {
    event.preventDefault();
    const problem = validateRange(form);
    if (problem) {
      setFormError(problem);
      return;
    }
    load({ ...form });
  }

  function applyPreset(days: number) {
    const to = businessToday();
    const range = { from: addDays(to, -(days - 1)), to };
    setForm(range);
    load(range);
  }

  const daily = reports?.daily ?? null;
  const outcomes = reports?.outcomes ?? null;
  const maxCount = daily ? Math.max(0, ...daily.days.map((day) => day.count)) : 0;
  const outcomesByDate = new Map(outcomes?.days.map((day) => [day.date, day]) ?? []);

  return (
    <div className="space-y-6">
      <Card>
        <CardHeader className="flex flex-row items-start justify-between gap-4">
          <div className="space-y-1">
            <CardTitle>Reports</CardTitle>
            <CardDescription>
              Parcels created and delivery outcomes per day. Days are business days in{" "}
              {daily?.timeZone ?? BUSINESS_TIME_ZONE}; both dates are included, up to {MAX_RANGE_DAYS} days.
            </CardDescription>
          </div>
          <Button variant="outline" size="sm" onClick={() => load(query)} disabled={loading}>
            <RefreshCw className="size-4" />
            Refresh
          </Button>
        </CardHeader>

        <CardContent className="space-y-4">
          <form onSubmit={handleApply} noValidate className="flex flex-wrap items-end gap-4">
            <div className="space-y-2">
              <Label htmlFor="report-from">From</Label>
              <Input
                id="report-from"
                type="date"
                value={form.from}
                max={form.to || undefined}
                onChange={(e) => setForm({ ...form, from: e.target.value })}
              />
            </div>
            <div className="space-y-2">
              <Label htmlFor="report-to">To</Label>
              <Input
                id="report-to"
                type="date"
                value={form.to}
                min={form.from || undefined}
                onChange={(e) => setForm({ ...form, to: e.target.value })}
              />
            </div>
            <Button type="submit" disabled={loading}>
              {loading && <Loader2 className="size-4 animate-spin" />}
              Apply
            </Button>
            <div className="flex gap-2" role="group" aria-label="Quick ranges">
              {PRESETS.map((days) => (
                <Button key={days} type="button" variant="outline" size="sm" onClick={() => applyPreset(days)}
                  disabled={loading}>
                  Last {days} days
                </Button>
              ))}
            </div>
          </form>

          {formError && (
            <p role="alert" className="text-sm text-destructive">
              {formError}
            </p>
          )}
          {loadError && (
            <p role="alert" className="flex items-center gap-2 rounded-lg border border-destructive/40 bg-destructive/10 p-3 text-sm text-destructive">
              <AlertCircle className="size-4" />
              {loadError}
            </p>
          )}
        </CardContent>
      </Card>

      {loading && !daily ? (
        <p className="flex items-center gap-2 text-sm text-muted-foreground">
          <Loader2 className="size-4 animate-spin" /> Loading report…
        </p>
      ) : daily && outcomes && (
        <>
          <div className="grid gap-4 sm:grid-cols-2 lg:grid-cols-4">
            <SummaryCard label="Parcels created" value={daily.total.toLocaleString()} />
            <SummaryCard label="Delivered" value={outcomes.delivered.toLocaleString()} />
            <SummaryCard label="Failed" value={outcomes.failed.toLocaleString()} />
            <SummaryCard
              label="Success rate"
              value={formatRate(outcomes.successRate)}
              hint={outcomes.successRate === null ? "No deliveries finished in this range" : "Delivered ÷ (delivered + failed)"}
            />
          </div>

          <Card>
            <CardHeader>
              <CardTitle>Daily parcel volume</CardTitle>
              <CardDescription>
                {formatDay(daily.from)} to {formatDay(daily.to)}
              </CardDescription>
            </CardHeader>
            <CardContent>
              {daily.total === 0 ? (
                <p className="text-sm text-muted-foreground">No parcels were created in this range.</p>
              ) : (
                <VolumeChart days={daily.days} maxCount={maxCount} />
              )}
            </CardContent>
          </Card>

          <Card>
            <CardHeader>
              <CardTitle>Daily breakdown</CardTitle>
              <CardDescription>
                Created counts parcels by the day they were booked; delivered and failed count parcels by the day
                they finished. A row can therefore mix different parcels. Cancelled parcels are not outcomes.
              </CardDescription>
            </CardHeader>
            <CardContent>
              <ScrollArea className="h-[420px] w-full rounded-lg border">
                <table className="w-full text-sm">
                  <thead className="sticky top-0 border-b bg-background text-left text-muted-foreground">
                    <tr>
                      <th className="py-2 pl-3 pr-4 font-medium">Date</th>
                      <th className="py-2 pr-4 text-right font-medium">Created</th>
                      <th className="py-2 pr-4 text-right font-medium">Delivered</th>
                      <th className="py-2 pr-3 text-right font-medium">Failed</th>
                    </tr>
                  </thead>
                  <tbody>
                    {daily.days.map((day) => {
                      const outcome = outcomesByDate.get(day.date);
                      return (
                        <tr key={day.date} className="border-b last:border-0">
                          <td className="py-2 pl-3 pr-4">{formatDay(day.date)}</td>
                          <td className="py-2 pr-4 text-right tabular-nums">{day.count.toLocaleString()}</td>
                          <td className="py-2 pr-4 text-right tabular-nums">
                            {(outcome?.delivered ?? 0).toLocaleString()}
                          </td>
                          <td className="py-2 pr-3 text-right tabular-nums">
                            {(outcome?.failed ?? 0).toLocaleString()}
                          </td>
                        </tr>
                      );
                    })}
                  </tbody>
                </table>
              </ScrollArea>
            </CardContent>
          </Card>
        </>
      )}
    </div>
  );
}

function SummaryCard({ label, value, hint }: { label: string; value: string; hint?: string }) {
  return (
    <Card>
      <CardContent className="space-y-1 pt-6">
        <p className="text-sm text-muted-foreground">{label}</p>
        <p className="text-2xl font-semibold tabular-nums">{value}</p>
        {hint && <p className="text-xs text-muted-foreground">{hint}</p>}
      </CardContent>
    </Card>
  );
}

/** Plain CSS bars: no chart library is installed, and the table below carries the exact numbers. */
function VolumeChart({ days, maxCount }: { days: DailyDeliveriesResponse["days"]; maxCount: number }) {
  return (
    <figure className="space-y-2">
      <div
        className="flex h-48 items-end gap-px rounded-lg border p-2"
        role="img"
        aria-label={`Bar chart of parcels created per day, highest ${maxCount} in one day`}
      >
        {days.map((day) => (
          <div
            key={day.date}
            className="flex-1 rounded-t-sm bg-primary/80 hover:bg-primary"
            style={{ height: maxCount ? `${(day.count / maxCount) * 100}%` : "0%", minHeight: day.count ? 2 : 0 }}
            title={`${formatDay(day.date)}: ${day.count} parcel${day.count === 1 ? "" : "s"}`}
          />
        ))}
      </div>
      <figcaption className="flex justify-between text-xs text-muted-foreground">
        <span>{formatDay(days[0].date)}</span>
        <span>Highest: {maxCount.toLocaleString()} in one day</span>
        <span>{formatDay(days[days.length - 1].date)}</span>
      </figcaption>
    </figure>
  );
}

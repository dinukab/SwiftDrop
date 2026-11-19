"use client";

import React, { useEffect, useState } from "react";
import Link from "next/link";
import { AlertCircle, CheckCircle2, Loader2, RefreshCw } from "lucide-react";
import { api, formatApiError, type PageResponse, type UserResponse } from "@/lib/api";
import {
  assignDispatcherHub,
  getDispatcherHubs,
  getHubs,
  type Hub,
} from "@/lib/dispatch";
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card";
import { Button } from "@/components/ui/button";

interface Loaded {
  dispatchers: UserResponse[];
  hubs: Hub[];
  /** Current hub code per dispatcher user ID; absent when none is assigned. */
  current: Record<string, string>;
}

function load(): Promise<Loaded> {
  return Promise.all([
    api.get<PageResponse<UserResponse>>("/api/v1/users?role=DISPATCHER&page=0&size=100"),
    getHubs(),
    getDispatcherHubs(),
  ]).then(([users, hubs, bindings]) => {
    const current: Record<string, string> = {};
    for (const binding of bindings) {
      if (binding.hub) current[binding.userId] = binding.hub.code;
    }
    return { dispatchers: users.items, hubs, current };
  });
}

/**
 * Admin screen that assigns each dispatcher to a hub. A dispatcher only sees the parcels waiting for
 * pickup in their hub's province, and cannot work until one is assigned.
 */
export default function AdminDispatcherHubsPage() {
  const [data, setData] = useState<Loaded | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [reloadKey, setReloadKey] = useState(0);
  const [chosen, setChosen] = useState<Record<string, string>>({});
  const [busyId, setBusyId] = useState<string | null>(null);
  const [rowError, setRowError] = useState<{ id: string; message: string } | null>(null);
  const [notice, setNotice] = useState<string | null>(null);

  // State is only set inside the promise callbacks, after the fetch
  useEffect(() => {
    let current = true;
    load()
      .then((result) => {
        if (!current) return;
        setData(result);
        setError(null);
      })
      .catch((err: unknown) => {
        if (current) setError(formatApiError(err));
      });
    return () => {
      current = false;
    };
  }, [reloadKey]);

  const reload = () => {
    setData(null);
    setError(null);
    setReloadKey((key) => key + 1);
  };

  const save = async (dispatcher: UserResponse, hubCode: string) => {
    setBusyId(dispatcher.id);
    setRowError(null);
    setNotice(null);
    try {
      const result = await assignDispatcherHub(dispatcher.id, hubCode);
      setNotice(`${dispatcher.fullName} now works for ${result.hub?.name ?? hubCode}.`);
      setChosen((previous) => {
        const next = { ...previous };
        delete next[dispatcher.id];
        return next;
      });
      setData((previous) =>
        previous ? { ...previous, current: { ...previous.current, [dispatcher.id]: hubCode } } : previous,
      );
    } catch (err) {
      setRowError({ id: dispatcher.id, message: formatApiError(err) });
    } finally {
      setBusyId(null);
    }
  };

  const loading = data === null && error === null;

  return (
    <div className="mx-auto max-w-4xl space-y-6">
      <div className="flex flex-col items-start justify-between gap-4 sm:flex-row sm:items-center">
        <div>
          <h1 className="text-2xl font-bold tracking-tight">Dispatcher hubs</h1>
          <p className="text-sm text-muted-foreground">
            Assign each dispatcher to a provincial hub. A dispatcher sees and assigns only the parcels waiting for
            pickup in their hub&apos;s province, and cannot work until a hub is assigned.
          </p>
        </div>
        <Button variant="outline" size="sm" onClick={reload} disabled={loading}>
          <RefreshCw className={`mr-2 h-4 w-4 ${loading ? "animate-spin" : ""}`} />
          Refresh
        </Button>
      </div>

      {error && (
        <div role="alert" className="flex items-center gap-3 rounded-lg border border-destructive/20 bg-destructive/10 p-4 text-destructive">
          <AlertCircle className="h-5 w-5 flex-shrink-0" />
          <p className="text-sm">Could not load dispatchers: {error}</p>
        </div>
      )}
      {notice && (
        <div role="status" className="flex items-center gap-3 rounded-lg border border-emerald-200 bg-emerald-50 p-4 text-emerald-800">
          <CheckCircle2 className="h-5 w-5 flex-shrink-0" />
          <p className="text-sm">{notice}</p>
        </div>
      )}

      <Card>
        <CardHeader>
          <CardTitle>Dispatchers</CardTitle>
          <CardDescription>
            {data ? `${data.dispatchers.length} dispatcher account${data.dispatchers.length === 1 ? "" : "s"}` : "Loading…"}
          </CardDescription>
        </CardHeader>
        <CardContent>
          {loading && (
            <div role="status" className="flex justify-center py-10">
              <Loader2 className="h-7 w-7 animate-spin text-muted-foreground" />
              <span className="sr-only">Loading dispatchers</span>
            </div>
          )}

          {data && data.dispatchers.length === 0 && (
            <div className="space-y-2 py-10 text-center">
              <p className="text-sm font-medium">There are no dispatcher accounts yet</p>
              <p className="text-xs text-muted-foreground">
                Create one with &ldquo;Onboard Staff&rdquo; on the{" "}
                <Link href="/admin/users" className="font-medium text-primary hover:underline">User Directory</Link>{" "}
                page, then come back to assign a hub.
              </p>
            </div>
          )}

          {data && data.dispatchers.length > 0 && (
            <ul className="divide-y">
              {data.dispatchers.map((dispatcher) => {
                const currentCode = data.current[dispatcher.id] ?? "";
                const selected = chosen[dispatcher.id] ?? currentCode;
                const changed = selected !== "" && selected !== currentCode;
                const busy = busyId === dispatcher.id;
                const selectId = `hub-${dispatcher.id}`;
                return (
                  <li key={dispatcher.id} className="space-y-2 py-4">
                    <div className="flex flex-wrap items-center justify-between gap-3">
                      <div className="min-w-0">
                        <p className="font-medium">
                          {dispatcher.fullName}
                          {!dispatcher.enabled && (
                            <span className="ml-2 rounded-full bg-muted px-2 py-0.5 text-xs font-medium text-muted-foreground">
                              Disabled
                            </span>
                          )}
                        </p>
                        <p className="truncate text-xs text-muted-foreground">{dispatcher.email}</p>
                      </div>
                      <div className="flex flex-wrap items-center gap-2">
                        <label htmlFor={selectId} className="sr-only">Hub for {dispatcher.fullName}</label>
                        <select
                          id={selectId}
                          className="h-8 min-w-0 rounded-lg border border-input bg-transparent px-2 text-sm"
                          value={selected}
                          disabled={busy || !dispatcher.enabled}
                          onChange={(event) =>
                            setChosen((previous) => ({ ...previous, [dispatcher.id]: event.target.value }))
                          }
                        >
                          {currentCode === "" && <option value="">No hub assigned</option>}
                          {data.hubs.map((hub) => (
                            <option key={hub.code} value={hub.code}>
                              {hub.name}
                            </option>
                          ))}
                        </select>
                        <Button size="sm" disabled={!changed || busy} onClick={() => save(dispatcher, selected)}>
                          {busy && <Loader2 className="mr-2 h-4 w-4 animate-spin" />}
                          Save
                        </Button>
                      </div>
                    </div>
                    {currentCode === "" && dispatcher.enabled && (
                      <p className="text-xs text-amber-700">No hub yet: this dispatcher cannot see any parcels.</p>
                    )}
                    {rowError?.id === dispatcher.id && (
                      <p role="alert" className="rounded-md bg-destructive/10 px-3 py-2 text-sm text-destructive">
                        {rowError.message}
                      </p>
                    )}
                  </li>
                );
              })}
            </ul>
          )}
        </CardContent>
      </Card>
    </div>
  );
}

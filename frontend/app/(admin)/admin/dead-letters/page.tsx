"use client";

import React, { useEffect, useState } from "react";
import { AlertCircle, CheckCircle2, ChevronLeft, ChevronRight, Inbox, Loader2, RefreshCw, RotateCcw } from "lucide-react";
import { formatApiError, type PageResponse } from "@/lib/api";
import {
  describeStopReason,
  getDeadLetters,
  getReplayAudit,
  MAX_REPLAY,
  replayDeadLetters,
  type DeadLetterOverview,
  type ReplayAudit,
} from "@/lib/dead-letters";
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card";
import { Button } from "@/components/ui/button";

const AUDIT_PAGE_SIZE = 10;

function shortId(id: string | null): string {
  return id ? `${id.slice(0, 8)}…` : "unknown";
}

function formatTime(value: string | null): string {
  return value ? new Date(value).toLocaleString() : "—";
}

/**
 * Admin recovery for notification events the consumer could not process (ADR 0004 section 6). Shows what waits in the
 * dead-letter queue (never the message bodies) and moves the oldest ones back to the queue. Every replay is audited.
 * Replaying is safe to repeat: an event that was already stored creates no second notification.
 */
export default function AdminDeadLettersPage() {
  const [overview, setOverview] = useState<DeadLetterOverview | null>(null);
  const [audit, setAudit] = useState<PageResponse<ReplayAudit> | null>(null);
  const [auditPage, setAuditPage] = useState(0);
  const [error, setError] = useState<string | null>(null);
  const [reloadKey, setReloadKey] = useState(0);
  const [limit, setLimit] = useState(10);
  const [busy, setBusy] = useState(false);
  const [notice, setNotice] = useState<{ kind: "ok" | "warn"; text: string } | null>(null);

  // State is only set inside the promise callbacks, after the fetch
  useEffect(() => {
    let current = true;
    Promise.all([getDeadLetters(), getReplayAudit(auditPage, AUDIT_PAGE_SIZE)])
      .then(([queue, history]) => {
        if (!current) return;
        setOverview(queue);
        setAudit(history);
        setError(null);
      })
      .catch((err: unknown) => {
        if (current) setError(formatApiError(err));
      });
    return () => {
      current = false;
    };
  }, [auditPage, reloadKey]);

  const reload = () => setReloadKey((key) => key + 1);

  const loading = overview === null && error === null;
  const depth = overview?.depth ?? 0;
  const replayCount = Math.min(limit, depth);

  const replay = async () => {
    if (replayCount < 1) return;
    const message =
      `Move the oldest ${replayCount} dead-lettered message${replayCount === 1 ? "" : "s"} back to the notification queue?\n\n` +
      "Each one is recorded in the audit trail with your name. A message that fails again returns here.";
    if (!window.confirm(message)) return;
    setBusy(true);
    setNotice(null);
    try {
      const outcome = await replayDeadLetters(replayCount);
      if (outcome.stopReason) {
        setNotice({
          kind: "warn",
          text: `Moved ${outcome.replayed} message${outcome.replayed === 1 ? "" : "s"}, then stopped: ${describeStopReason(outcome.stopReason)}. The rest are still waiting.`,
        });
      } else {
        setNotice({
          kind: "ok",
          text: `Moved ${outcome.replayed} message${outcome.replayed === 1 ? "" : "s"} back to the queue. Any that fail again will return to this list.`,
        });
      }
      setAuditPage(0);
      reload();
    } catch (err) {
      setNotice({ kind: "warn", text: `Replay failed: ${formatApiError(err)}` });
    } finally {
      setBusy(false);
    }
  };

  return (
    <div className="mx-auto max-w-4xl space-y-6">
      <div className="flex flex-col items-start justify-between gap-4 sm:flex-row sm:items-center">
        <div>
          <h1 className="text-2xl font-bold tracking-tight">Dead letters</h1>
          <p className="text-sm text-muted-foreground">
            Notification events the system could not process. Fix the cause, then replay them. Message contents are
            never shown here.
          </p>
        </div>
        <Button variant="outline" size="sm" onClick={reload} disabled={loading || busy}>
          <RefreshCw className="mr-2 h-4 w-4" />
          Refresh
        </Button>
      </div>

      {error && (
        <div role="alert" className="flex items-center gap-3 rounded-lg border border-destructive/20 bg-destructive/10 p-4 text-destructive">
          <AlertCircle className="h-5 w-5 flex-shrink-0" />
          <p className="text-sm">Could not load dead letters: {error}</p>
        </div>
      )}
      {notice && (
        <div
          role="status"
          className={`flex items-center gap-3 rounded-lg border p-4 ${
            notice.kind === "ok"
              ? "border-emerald-200 bg-emerald-50 text-emerald-800"
              : "border-amber-200 bg-amber-50 text-amber-900"
          }`}
        >
          {notice.kind === "ok" ? (
            <CheckCircle2 className="h-5 w-5 flex-shrink-0" />
          ) : (
            <AlertCircle className="h-5 w-5 flex-shrink-0" />
          )}
          <p className="text-sm">{notice.text}</p>
        </div>
      )}

      <Card>
        <CardHeader>
          <CardTitle>Waiting for replay</CardTitle>
          <CardDescription>
            {overview ? `${overview.depth} message${overview.depth === 1 ? "" : "s"} in ${overview.queue}.dlq` : "Loading…"}
          </CardDescription>
        </CardHeader>
        <CardContent className="space-y-4">
          {loading && (
            <div role="status" className="flex justify-center py-10">
              <Loader2 className="h-7 w-7 animate-spin text-muted-foreground" />
              <span className="sr-only">Loading dead letters</span>
            </div>
          )}

          {overview && overview.depth === 0 && (
            <div className="space-y-2 py-8 text-center">
              <Inbox className="mx-auto h-9 w-9 text-muted-foreground" />
              <p className="text-sm font-medium">Nothing is waiting</p>
              <p className="text-xs text-muted-foreground">Every notification event has been processed.</p>
            </div>
          )}

          {overview && overview.oldest.length > 0 && (
            <>
              <div className="overflow-x-auto">
                <table className="w-full min-w-[32rem] text-left text-sm">
                  <thead className="text-xs uppercase tracking-wide text-muted-foreground">
                    <tr>
                      <th className="py-2 pr-3 font-semibold">Event</th>
                      <th className="py-2 pr-3 font-semibold">Status</th>
                      <th className="py-2 pr-3 font-semibold">Reason</th>
                      <th className="py-2 pr-3 font-semibold">Times</th>
                      <th className="py-2 font-semibold">Dead-lettered</th>
                    </tr>
                  </thead>
                  <tbody className="divide-y">
                    {overview.oldest.map((item, index) => (
                      <tr key={`${item.eventId ?? "unparsed"}-${index}`}>
                        <td className="py-2 pr-3 font-mono text-xs" title={item.eventId ?? undefined}>
                          {item.eventId ? shortId(item.eventId) : "unreadable"}
                        </td>
                        <td className="py-2 pr-3">{item.status ?? "—"}</td>
                        <td className="py-2 pr-3">{item.reason ?? "—"}</td>
                        <td className="py-2 pr-3">{item.deathCount}</td>
                        <td className="py-2 text-xs text-muted-foreground">{formatTime(item.deadLetteredAt)}</td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
              {overview.depth > overview.oldest.length && (
                <p className="text-xs text-muted-foreground">
                  Showing the oldest {overview.oldest.length} of {overview.depth}.
                </p>
              )}

              <div className="flex flex-col gap-3 border-t pt-4 sm:flex-row sm:items-end sm:justify-between">
                <div className="space-y-1">
                  <label htmlFor="replay-limit" className="text-sm font-medium">
                    Messages to replay (oldest first, up to {MAX_REPLAY})
                  </label>
                  <input
                    id="replay-limit"
                    type="number"
                    min={1}
                    max={MAX_REPLAY}
                    value={limit}
                    onChange={(event) => {
                      const next = Number.parseInt(event.target.value, 10);
                      setLimit(Number.isNaN(next) ? 1 : Math.min(MAX_REPLAY, Math.max(1, next)));
                    }}
                    className="h-9 w-28 rounded-md border bg-background px-3 text-sm"
                  />
                </div>
                <Button onClick={replay} disabled={busy || replayCount < 1}>
                  {busy ? <Loader2 className="mr-2 h-4 w-4 animate-spin" /> : <RotateCcw className="mr-2 h-4 w-4" />}
                  Replay oldest {replayCount}
                </Button>
              </div>
            </>
          )}
        </CardContent>
      </Card>

      <Card>
        <CardHeader>
          <CardTitle>Replay history</CardTitle>
          <CardDescription>Who replayed which event, and when. Newest first.</CardDescription>
        </CardHeader>
        <CardContent className="space-y-3">
          {audit && audit.items.length === 0 && (
            <p className="py-4 text-center text-sm text-muted-foreground">No replays yet.</p>
          )}
          {audit && audit.items.length > 0 && (
            <ul className="divide-y">
              {audit.items.map((row) => (
                <li key={row.id} className="flex flex-wrap items-baseline justify-between gap-2 py-2 text-sm">
                  <span>
                    <span className="font-mono text-xs" title={row.eventId ?? undefined}>
                      {row.eventId ? shortId(row.eventId) : "unreadable event"}
                    </span>
                    {row.originalReason && <span className="text-muted-foreground"> · was {row.originalReason}</span>}
                  </span>
                  <span className="text-xs text-muted-foreground">
                    {formatTime(row.replayedAt)} · by <span className="font-mono">{shortId(row.replayedBy)}</span>
                  </span>
                </li>
              ))}
            </ul>
          )}
          {audit && audit.totalPages > 1 && (
            <div className="flex items-center justify-between text-xs text-muted-foreground">
              <span>
                Page {auditPage + 1} of {audit.totalPages}
              </span>
              <div className="flex gap-2">
                <Button variant="outline" size="sm" disabled={auditPage === 0} onClick={() => setAuditPage((p) => Math.max(0, p - 1))}>
                  <ChevronLeft className="mr-1 h-4 w-4" />
                  Previous
                </Button>
                <Button variant="outline" size="sm" disabled={auditPage + 1 >= audit.totalPages} onClick={() => setAuditPage((p) => p + 1)}>
                  Next
                  <ChevronRight className="ml-1 h-4 w-4" />
                </Button>
              </div>
            </div>
          )}
        </CardContent>
      </Card>
    </div>
  );
}

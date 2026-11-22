import { api, type PageResponse } from "@/lib/api";

/** One dead-lettered message as an administrator sees it. The body is never sent by the server. */
export interface DeadLetterItem {
  eventId: string | null;
  status: string | null;
  reason: string | null;
  deathCount: number;
  deadLetteredAt: string | null;
}

export interface DeadLetterOverview {
  queue: string;
  depth: number;
  oldest: DeadLetterItem[];
}

export interface ReplayOutcome {
  replayed: number;
  eventIds: string[];
  /** Null when the replay ended normally; otherwise a short category such as NOT_CONFIRMED. */
  stopReason: string | null;
}

export interface ReplayAudit {
  id: string;
  queue: string;
  eventId: string | null;
  originalReason: string | null;
  replayedBy: string;
  replayedAt: string;
}

/** The most messages one replay request may move (the server enforces it too). */
export const MAX_REPLAY = 50;

/** What waits in the dead-letter queue: the depth and the oldest messages. */
export function getDeadLetters(): Promise<DeadLetterOverview> {
  return api.get<DeadLetterOverview>("/api/v1/admin/dead-letters");
}

/** Moves the oldest `limit` dead letters back to the source queue. */
export function replayDeadLetters(limit: number): Promise<ReplayOutcome> {
  return api.post<ReplayOutcome>("/api/v1/admin/dead-letters/replay", { limit });
}

/** The audit trail of replays, newest first. */
export function getReplayAudit(page: number, size: number): Promise<PageResponse<ReplayAudit>> {
  const params = new URLSearchParams({ page: String(page), size: String(size) });
  return api.get<PageResponse<ReplayAudit>>(`/api/v1/admin/dead-letters/replays?${params.toString()}`);
}

/** Plain-language text for the reason a replay stopped early. */
export function describeStopReason(reason: string): string {
  switch (reason) {
    case "NOT_CONFIRMED":
      return "the broker did not confirm the message";
    case "NOT_ROUTED":
      return "the source queue could not be reached";
    case "AUDIT_FAILED":
      return "the audit record could not be saved";
    case "BROKER_UNAVAILABLE":
      return "the message broker is not available";
    default:
      return "an unexpected problem";
  }
}

"use client";

import React, { useEffect, useId, useRef, useState } from "react";
import { X } from "lucide-react";
import { api, formatApiError, type UserResponse } from "@/lib/api";
import { getHubs, provinceName, type Hub } from "@/lib/dispatch";

/** Zone assigned to a user, from GET and PUT /api/v1/users/{id}/zone (admin only). */
export interface UserZone {
  userId: string;
  /** DISPATCHER_HUB, COURIER_HOME_HUB or NOT_APPLICABLE. */
  basis: "DISPATCHER_HUB" | "COURIER_HOME_HUB" | "NOT_APPLICABLE";
  zoneCode: string | null;
  zoneName: string | null;
}

/**
 * Soft-deletes a user (admin only): the account is disabled and anonymised and leaves the user list,
 * but parcels and history that reference it are kept. The backend refuses an administrator's own account.
 */
export function deleteUser(userId: string): Promise<void> {
  return api.delete<void>(`/api/v1/users/${encodeURIComponent(userId)}`);
}

/** Changes a courier's home hub or a dispatcher's hub. The backend rejects roles that have no zone (409). */
export function setUserZone(userId: string, hubCode: string): Promise<UserZone> {
  return api.put<UserZone>(`/api/v1/users/${encodeURIComponent(userId)}/zone`, { hubCode });
}

type ZoneState =
  | { status: "loading" }
  | { status: "error"; message: string }
  | { status: "loaded"; zone: UserZone };

const BASIS_LABEL: Record<Exclude<UserZone["basis"], "NOT_APPLICABLE">, string> = {
  DISPATCHER_HUB: "Dispatcher hub",
  COURIER_HOME_HUB: "Courier home hub",
};

const ROLE_LABEL: Record<string, string> = {
  CUSTOMER: "Customer",
  MERCHANT: "Merchant",
  COURIER: "Courier",
  DISPATCHER: "Dispatcher",
  ADMIN: "Admin",
};

function ZoneValue({ state, roles }: { state: ZoneState; roles: string[] }) {
  if (state.status === "loading") {
    return <span className="text-muted-foreground">Loading zone…</span>;
  }
  if (state.status === "error") {
    return <span className="text-destructive">Could not load zone: {state.message}</span>;
  }
  const { zone } = state;
  if (zone.basis === "NOT_APPLICABLE") {
    const roleText = roles.map((role) => ROLE_LABEL[role] ?? role).join(", ") || "This";
    return <span className="text-muted-foreground">Not applicable: {roleText} accounts are not assigned a zone.</span>;
  }
  if (!zone.zoneCode) {
    return (
      <span className="text-amber-700">
        Not assigned ({BASIS_LABEL[zone.basis]} not set)
      </span>
    );
  }
  return (
    <span>
      {zone.zoneName} <span className="text-muted-foreground">({zone.zoneCode})</span>
      <span className="ml-2 text-xs font-normal text-muted-foreground">· {BASIS_LABEL[zone.basis]}</span>
    </span>
  );
}

/** Admin control to move a courier or dispatcher to another zone. Mounted only for those roles. */
function ZoneEditor({
  userId,
  currentCode,
  onSaved,
}: {
  userId: string;
  currentCode: string | null;
  onSaved: (zone: UserZone) => void;
}) {
  const selectId = useId();
  const [hubs, setHubs] = useState<Hub[] | null>(null);
  const [hubsError, setHubsError] = useState<string | null>(null);
  // null means "show the current zone"; set only once the admin changes the selection
  const [picked, setPicked] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);
  const [message, setMessage] = useState<{ tone: "ok" | "error"; text: string } | null>(null);

  useEffect(() => {
    let current = true;
    getHubs()
      .then((list) => {
        if (current) setHubs(list);
      })
      .catch((err: unknown) => {
        if (current) setHubsError(formatApiError(err));
      });
    return () => {
      current = false;
    };
  }, []);

  const selected = picked ?? currentCode ?? "";
  const changed = selected !== "" && selected !== (currentCode ?? "");

  async function save() {
    setBusy(true);
    setMessage(null);
    try {
      const zone = await setUserZone(userId, selected);
      setPicked(null);
      setMessage({ tone: "ok", text: `Zone updated to ${zone.zoneName ?? zone.zoneCode}.` });
      onSaved(zone);
    } catch (err) {
      setMessage({ tone: "error", text: formatApiError(err) });
    } finally {
      setBusy(false);
    }
  }

  return (
    <div className="space-y-2 pt-4">
      <label htmlFor={selectId} className="text-sm font-medium">Change zone</label>
      <div className="flex flex-wrap items-center gap-2">
        <select
          id={selectId}
          className="h-8 min-w-0 flex-1 rounded-lg border border-input bg-transparent px-2 text-sm"
          value={selected}
          disabled={busy || hubs === null}
          onChange={(event) => setPicked(event.target.value)}
        >
          {selected === "" && <option value="">No zone set</option>}
          {(hubs ?? []).map((hub) => (
            <option key={hub.code} value={hub.code}>
              {hub.name} ({provinceName(hub.code)})
            </option>
          ))}
        </select>
        <button
          type="button"
          onClick={save}
          disabled={!changed || busy}
          className="h-8 rounded-lg bg-primary px-3 text-sm font-medium text-primary-foreground disabled:opacity-50"
        >
          {busy ? "Saving…" : "Save zone"}
        </button>
      </div>
      {hubsError && <p className="text-xs text-destructive">Could not load zones: {hubsError}</p>}
      {message && (
        <p role={message.tone === "error" ? "alert" : "status"} className={`text-xs ${message.tone === "error" ? "text-destructive" : "text-emerald-700"}`}>
          {message.text}
        </p>
      )}
    </div>
  );
}

/**
 * Read-only details of one user, opened from the user list. The zone is always shown: a dispatcher's
 * or courier's zone, "Not assigned" if none is set, or "Not applicable" for roles that have no zone.
 * Mount with `key={user.id}` so each user starts with fresh state.
 */
export function UserDetailsDialog({
  user,
  onClose,
  isSelf = false,
  onDeleted,
}: {
  user: UserResponse;
  onClose: () => void;
  /** True for the signed-in administrator's own account, which cannot be deleted. */
  isSelf?: boolean;
  onDeleted?: (user: UserResponse) => void;
}) {
  const titleId = useId();
  const dialogRef = useRef<HTMLDivElement | null>(null);
  const [zoneState, setZoneState] = useState<ZoneState>({ status: "loading" });
  const [confirmingDelete, setConfirmingDelete] = useState(false);
  const [deleting, setDeleting] = useState(false);
  const [deleteError, setDeleteError] = useState<string | null>(null);
  const canChangeZone = user.roles.includes("COURIER") || user.roles.includes("DISPATCHER");

  async function confirmDelete() {
    setDeleting(true);
    setDeleteError(null);
    try {
      await deleteUser(user.id);
      onDeleted?.(user);
    } catch (err) {
      setDeleteError(formatApiError(err));
      setDeleting(false);
    }
  }

  useEffect(() => {
    let current = true;
    api
      .get<UserZone>(`/api/v1/users/${encodeURIComponent(user.id)}/zone`)
      .then((zone) => {
        if (current) setZoneState({ status: "loaded", zone });
      })
      .catch((err: unknown) => {
        if (current) setZoneState({ status: "error", message: formatApiError(err) });
      });
    return () => {
      current = false;
    };
  }, [user.id]);

  // Focus moves into the dialog on open, returns to the previous element on close, and Escape closes it.
  useEffect(() => {
    const previous = document.activeElement as HTMLElement | null;
    const originalOverflow = document.body.style.overflow;
    document.body.style.overflow = "hidden";
    dialogRef.current?.focus();

    const onKeyDown = (event: KeyboardEvent) => {
      if (event.key === "Escape") onClose();
    };
    document.addEventListener("keydown", onKeyDown);
    return () => {
      document.removeEventListener("keydown", onKeyDown);
      document.body.style.overflow = originalOverflow;
      previous?.focus?.();
    };
  }, [onClose]);

  const currentCode = zoneState.status === "loaded" ? zoneState.zone.zoneCode : null;

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/50 p-4" onClick={onClose}>
      <div
        ref={dialogRef}
        role="dialog"
        aria-modal="true"
        aria-labelledby={titleId}
        tabIndex={-1}
        className="w-full max-w-lg rounded-xl border bg-background shadow-xl outline-none"
        onClick={(event) => event.stopPropagation()}
      >
        <div className="flex items-start justify-between gap-3 border-b p-5">
          <div className="min-w-0">
            <h2 id={titleId} className="truncate text-lg font-bold">{user.fullName}</h2>
            <p className="truncate text-sm text-muted-foreground">{user.email}</p>
          </div>
          <button
            type="button"
            onClick={onClose}
            className="rounded-md p-1 text-muted-foreground hover:bg-muted"
            aria-label="Close user details"
          >
            <X className="h-4 w-4" />
          </button>
        </div>

        <div className="max-h-[70vh] overflow-y-auto">
          <dl className="divide-y px-5">
            <div className="grid gap-1 py-3 sm:grid-cols-3 sm:gap-4">
              <dt className="text-sm text-muted-foreground">Roles</dt>
              <dd className="text-sm font-medium sm:col-span-2">
                {user.roles.map((role) => ROLE_LABEL[role] ?? role).join(", ") || "—"}
              </dd>
            </div>
            <div className="grid gap-1 py-3 sm:grid-cols-3 sm:gap-4">
              <dt className="text-sm text-muted-foreground">Account</dt>
              <dd className="text-sm font-medium sm:col-span-2">{user.enabled ? "Enabled" : "Disabled"}</dd>
            </div>
            <div className="grid gap-1 py-3 sm:grid-cols-3 sm:gap-4">
              <dt className="text-sm text-muted-foreground">Assigned zone</dt>
              <dd className="break-words text-sm font-medium sm:col-span-2">
                <ZoneValue state={zoneState} roles={user.roles} />
              </dd>
            </div>
            <div className="grid gap-1 py-3 sm:grid-cols-3 sm:gap-4">
              <dt className="text-sm text-muted-foreground">User ID</dt>
              <dd className="break-all font-mono text-xs sm:col-span-2">{user.id}</dd>
            </div>
          </dl>

          {canChangeZone && zoneState.status === "loaded" && (
            <div className="px-5 pb-2">
              <ZoneEditor
                userId={user.id}
                currentCode={currentCode}
                onSaved={(zone) => setZoneState({ status: "loaded", zone })}
              />
            </div>
          )}
        </div>

        {!isSelf && (
          <div className="space-y-2 border-t p-5">
            <p className="text-sm font-medium text-destructive">Delete user</p>
            <p className="text-xs text-muted-foreground">
              The account is disabled, its name and email are removed, and it leaves the user list. Parcels and
              delivery history that mention this user are kept.
            </p>
            {!confirmingDelete ? (
              <button
                type="button"
                onClick={() => setConfirmingDelete(true)}
                className="rounded-md border border-destructive/30 px-3 py-1.5 text-sm font-medium text-destructive hover:bg-destructive/10"
              >
                Delete this user…
              </button>
            ) : (
              <div className="flex flex-wrap items-center gap-2">
                <span className="text-sm">Delete {user.fullName}? This cannot be undone.</span>
                <button
                  type="button"
                  onClick={confirmDelete}
                  disabled={deleting}
                  className="rounded-md bg-destructive px-3 py-1.5 text-sm font-medium text-white disabled:opacity-50"
                >
                  {deleting ? "Deleting…" : "Yes, delete"}
                </button>
                <button
                  type="button"
                  onClick={() => setConfirmingDelete(false)}
                  disabled={deleting}
                  className="rounded-md border px-3 py-1.5 text-sm hover:bg-muted disabled:opacity-50"
                >
                  Cancel
                </button>
              </div>
            )}
            {deleteError && (
              <p role="alert" className="text-xs text-destructive">
                {deleteError}
              </p>
            )}
          </div>
        )}

        <div className="flex justify-end border-t p-4">
          <button
            type="button"
            onClick={onClose}
            className="rounded-md border px-4 py-2 text-sm font-medium hover:bg-muted"
          >
            Close
          </button>
        </div>
      </div>
    </div>
  );
}

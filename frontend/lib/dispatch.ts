import { api, type PageResponse } from "@/lib/api";

/** An order waiting for a courier. Holds no sender or recipient personal data. */
export interface UnassignedParcel {
  id: string;
  trackingNumber: string;
  status: string;
  source: "CUSTOMER" | "MERCHANT";
  size: "SMALL" | "MEDIUM" | "LARGE";
  weightGrams: number;
  merchantOrderReference: string | null;
  createdAt: string;
  /** Province code such as "LK-UP"; null if the zone no longer exists. */
  pickupProvinceCode: string | null;
  dropoffProvinceCode: string | null;
}

/** One active courier with the number of parcels they hold. No phone number. */
export interface CourierWorkload {
  courierUserId: string;
  name: string;
  vehicleType: "BIKE" | "CAR" | "VAN";
  activeParcels: number;
}

/** A provincial hub. `code` is the province code, for example "LK-UP". */
export interface Hub {
  code: string;
  name: string;
  main: boolean;
}

/** The signed-in dispatcher and their hub; `hub` is null until an admin assigns one. */
export interface DispatcherHub {
  userId: string;
  hub: Hub | null;
}

/** One assignment in a parcel's history; `endedAt` and `endReason` are null while it is active. */
export interface AssignmentHistoryEntry {
  id: string;
  legType: "PICKUP" | "INTER_HUB" | "FINAL_MILE";
  courierUserId: string;
  courierName: string;
  dispatcherUserId: string;
  dispatcherName: string;
  hubCode: string;
  assignedAt: string;
  endedAt: string | null;
  endReason: "REASSIGNED" | null;
  active: boolean;
}

/** Names for the nine province codes; unknown codes are shown as they are. */
const PROVINCE_NAMES: Record<string, string> = {
  "LK-WP": "Western",
  "LK-CP": "Central",
  "LK-SP": "Southern",
  "LK-NP": "Northern",
  "LK-EP": "Eastern",
  "LK-NWP": "North Western",
  "LK-NCP": "North Central",
  "LK-UP": "Uva",
  "LK-SGP": "Sabaragamuwa",
};

export function provinceName(code: string | null): string {
  if (!code) return "Unknown";
  return PROVINCE_NAMES[code] ?? code;
}

const LEG_NAMES: Record<AssignmentHistoryEntry["legType"], string> = {
  PICKUP: "Pickup",
  INTER_HUB: "Between hubs",
  FINAL_MILE: "Final delivery",
};

export function legName(leg: AssignmentHistoryEntry["legType"]): string {
  return LEG_NAMES[leg] ?? leg;
}

/** The dispatcher's own hub, taken from the token on the server. */
export function getMyHub(): Promise<DispatcherHub> {
  return api.get<DispatcherHub>("/api/v1/dispatchers/me");
}

export function getUnassignedParcels(page: number, size: number): Promise<PageResponse<UnassignedParcel>> {
  const params = new URLSearchParams({ page: String(page), size: String(size) });
  return api.get<PageResponse<UnassignedParcel>>(`/api/v1/unassigned-parcels?${params.toString()}`);
}

/**
 * Assigns a courier. The server checks that the courier is eligible, that the parcel waits for pickup in
 * the dispatcher's hub, and that it is still `CREATED`; a refusal arrives as a 409 (or 404) with the
 * reason in `detail`.
 */
export function assignCourier(parcelId: string, courierUserId: string): Promise<{ parcelId: string; courierUserId: string }> {
  return api.post(`/api/v1/parcel-assignments`, { parcelId, courierUserId });
}

export function getCourierWorkloads(): Promise<CourierWorkload[]> {
  return api.get<CourierWorkload[]>("/api/v1/courier-workloads");
}

/** Who was assigned to the parcel, by whom and when, oldest first. */
export function getAssignmentHistory(parcelId: string): Promise<AssignmentHistoryEntry[]> {
  return api.get<AssignmentHistoryEntry[]>(`/api/v1/parcels/${encodeURIComponent(parcelId)}/assignment-history`);
}

/** Active hubs, Main Hub first (dispatchers and admins). */
export function getHubs(): Promise<Hub[]> {
  return api.get<Hub[]>("/api/v1/hubs");
}

/** Every dispatcher that has a hub (admin only); dispatchers without one are not listed. */
export function getDispatcherHubs(): Promise<DispatcherHub[]> {
  return api.get<DispatcherHub[]>("/api/v1/dispatcher-hubs");
}

/** Binds a dispatcher to a hub, or moves them (admin only). 409 if the account is disabled or not a dispatcher. */
export function assignDispatcherHub(userId: string, hubCode: string): Promise<DispatcherHub> {
  return api.put<DispatcherHub>(`/api/v1/dispatchers/${encodeURIComponent(userId)}/hub`, { hubCode });
}

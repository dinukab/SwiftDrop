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
}

/** One active courier with the number of parcels they hold. No phone number. */
export interface CourierWorkload {
  courierUserId: string;
  name: string;
  vehicleType: "BIKE" | "CAR" | "VAN";
  activeParcels: number;
}

export function getUnassignedParcels(page: number, size: number): Promise<PageResponse<UnassignedParcel>> {
  const params = new URLSearchParams({ page: String(page), size: String(size) });
  return api.get<PageResponse<UnassignedParcel>>(`/api/v1/unassigned-parcels?${params.toString()}`);
}

/**
 * Assigns a courier. The server checks that the courier is eligible and that the parcel is still
 * waiting for pickup; a refusal arrives as a 409 with the reason in `detail`.
 */
export function assignCourier(parcelId: string, courierUserId: string): Promise<{ parcelId: string; courierUserId: string }> {
  return api.post(`/api/v1/parcel-assignments`, { parcelId, courierUserId });
}

export function getCourierWorkloads(): Promise<CourierWorkload[]> {
  return api.get<CourierWorkload[]>("/api/v1/courier-workloads");
}

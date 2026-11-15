/**
 * Typed calls for the dispatcher endpoints (backend `dispatch` module, SD-305 to SD-307).
 * Unassigned parcels, assignment and workloads need the DISPATCHER role; courier profiles accept
 * DISPATCHER or ADMIN.
 */
import { api, type PageResponse, type ParcelSize } from "@/lib/api";

export type VehicleType = "BIKE" | "CAR" | "VAN";

export interface UnassignedParcel {
  id: string;
  trackingNumber: string;
  size: ParcelSize;
  weightGrams: number;
  source: "CUSTOMER" | "MERCHANT";
  merchantOrderReference: string | null;
  createdAt: string;
}

export interface CourierWorkload {
  courierUserId: string;
  fullName: string;
  vehicleType: VehicleType;
  activeParcels: number;
}

export interface CourierProfile {
  id: string;
  userId: string;
  fullName: string | null;
  email: string | null;
  phone: string;
  vehicleType: VehicleType;
  active: boolean;
  createdAt: string;
  updatedAt: string;
}

export interface ParcelAssignment {
  parcelId: string;
  courierUserId: string;
}

export const VEHICLE_TYPES: VehicleType[] = ["BIKE", "CAR", "VAN"];

export function getUnassignedParcels(token: string, page: number, size: number) {
  return api.get<PageResponse<UnassignedParcel>>(`/api/v1/unassigned-parcels?page=${page}&size=${size}`, { token });
}

export function getCourierWorkloads(token: string) {
  return api.get<CourierWorkload[]>("/api/v1/courier-workloads", { token });
}

export function assignParcel(token: string, parcelId: string, courierUserId: string) {
  return api.post<ParcelAssignment>("/api/v1/parcel-assignments", { parcelId, courierUserId }, { token });
}

export function getCourierProfiles(token: string, page: number, size: number) {
  return api.get<PageResponse<CourierProfile>>(`/api/v1/couriers?page=${page}&size=${size}`, { token });
}

export function createCourierProfile(token: string, body: { userId: string; phone: string; vehicleType: VehicleType }) {
  return api.post<CourierProfile>("/api/v1/couriers", body, { token });
}

export function updateCourierProfile(
  token: string,
  id: string,
  body: { phone?: string; vehicleType?: VehicleType; active?: boolean },
) {
  return api.patch<CourierProfile>(`/api/v1/couriers/${id}`, body, { token });
}

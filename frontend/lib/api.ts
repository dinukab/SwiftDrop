/**
 * Typed HTTP client for the SwiftDrop Spring Boot REST API.
 * Handles base URL configuration, Bearer token injection, and RFC 9457 Problem Details error parsing.
 */

export interface ProblemDetail {
  type?: string;
  title?: string;
  status: number;
  detail?: string;
  invalidFields?: string[];
  [key: string]: unknown;
}

export interface UserResponse {
  id: string;
  email: string;
  fullName: string;
  roles: string[];
  enabled: boolean;
}

export interface PageResponse<T> {
  items: T[];
  page: number;
  size: number;
  totalItems: number;
  totalPages: number;
}

export interface CreateStaffUserRequest {
  fullName: string;
  email: string;
  password: string;
  role?: "COURIER" | "DISPATCHER_ADMIN";
}

export interface UpdateUserStatusRequest {
  enabled: boolean;
}

export interface AccessTokenResponse {
  accessToken: string;
  tokenType: string;
  expiresIn: number;
}

// Courier API types
export type ParcelStatus = "CREATED" | "PICKED_UP" | "IN_TRANSIT" | "OUT_FOR_DELIVERY" | "DELIVERED" | "FAILED";

export type FailureReason = "RECIPIENT_UNAVAILABLE" | "ADDRESS_NOT_FOUND" | "REFUSED" | "DAMAGED" | "OTHER";

export type ParcelSize = "SMALL" | "MEDIUM" | "LARGE";

export interface AddressResponse {
  contactName: string;
  phone: string;
  line1: string;
  line2: string | null;
  city: string;
  zoneId: string;
  postalCode: string | null;
}

export interface CourierParcelResponse {
  id: string;
  trackingNumber: string;
  status: ParcelStatus;
  nextStatuses: ParcelStatus[];
  pickup: AddressResponse;
  dropoff: AddressResponse;
  size: ParcelSize;
  weightGrams: number;
  description: string | null;
  proofCaptured: boolean;
  assignedAt: string | null;
  updatedAt: string;
}

export interface StatusChangeRequest {
  toStatus: ParcelStatus;
  failureReason?: string;
  note?: string;
}

// Customer booking API types (contract §4.2–4.3)
export interface ZoneResponse {
  id: string;
  code: string;
  name: string;
}

export interface BookingAddress {
  contactName: string;
  phone: string;
  line1: string;
  line2?: string;
  city: string;
  zoneId: string;
  postalCode?: string;
}

export interface CreateParcelRequest {
  pickup: BookingAddress;
  dropoff: BookingAddress;
  size: ParcelSize;
  weightGrams: number;
  description?: string;
}

export interface PriceQuoteRequest {
  pickupZoneId: string;
  dropoffZoneId: string;
  size: ParcelSize;
  weightGrams: number;
}

export interface PriceQuoteResponse {
  amount: string;
  currency: string;
  pricingRuleId: string;
  breakdown: {
    baseFee: string;
    weightFee: string;
    sizeSurcharge: string;
  };
}

export interface ParcelCreatedResponse {
  id: string;
  trackingNumber: string;
  status: ParcelStatus;
  fee: { amount: string; currency: string };
  assignedCourierId: string | null;
  createdAt: string;
}

export type ParcelSource = "CUSTOMER" | "MERCHANT";

export interface ParcelHistoryItem {
  id: string;
  trackingNumber: string;
  status: ParcelStatus;
  source: ParcelSource;
  size: ParcelSize;
  weightGrams: number;
  pickupCity: string;
  dropoffCity: string;
  fee: { amount: string; currency: string };
  createdAt: string;
  updatedAt: string;
}

// Proof of Delivery API types
export interface ProofResponse {
  parcelId: string;
  recipientName: string;
  hasPhoto: boolean;
  photoSizeBytes: number | null;
  capturedAt: string;
}

type UnauthorizedHandler = () => void;

let unauthorizedHandler: UnauthorizedHandler | null = null;

export class ApiError extends Error {
  readonly status: number;
  readonly problem?: ProblemDetail;

  constructor(status: number, message: string, problem?: ProblemDetail) {
    super(message);
    this.name = "ApiError";
    this.status = status;
    this.problem = problem;
  }
}

// Multipart file upload helper
export async function uploadProof(parcelId: string, recipientName: string, photoFile: File | null, token: string): Promise<ProofResponse> {
  const formData = new FormData();
  formData.append("recipientName", recipientName);
  if (photoFile) {
    formData.append("photo", photoFile);
  }

  const baseUrl = getApiBaseUrl().replace(/\/+$/, "");
  const url = `${baseUrl}/api/v1/parcels/${parcelId}/proof`;

  const headers = new Headers();
  if (token) {
    headers.set("Authorization", `Bearer ${token}`);
  }

  const response = await fetch(url, {
    method: "PUT",
    headers,
    body: formData,
  });

  if (!response.ok) {
    if (response.status === 401 && unauthorizedHandler) {
      unauthorizedHandler();
    }

    let problem: ProblemDetail | undefined;
    const contentType = response.headers.get("content-type") || "";

    if (contentType.includes("json")) {
      try {
        problem = (await response.json()) as ProblemDetail;
      } catch {
        // Fall back to status text if JSON parsing fails
      }
    }

    const message = problem?.detail || problem?.title || `Request failed with status ${response.status}`;
    throw new ApiError(response.status, message, problem);
  }

  return (await response.json()) as ProofResponse;
}

// Get proof photo as blob
export async function getProofPhoto(parcelId: string, token: string): Promise<Blob> {
  const baseUrl = getApiBaseUrl().replace(/\/+$/, "");
  const url = `${baseUrl}/api/v1/parcels/${parcelId}/proof/photo`;

  const headers = new Headers();
  if (token) {
    headers.set("Authorization", `Bearer ${token}`);
  }

  const response = await fetch(url, {
    method: "GET",
    headers,
  });

  if (!response.ok) {
    if (response.status === 401 && unauthorizedHandler) {
      unauthorizedHandler();
    }
    throw new ApiError(response.status, "Failed to fetch proof photo");
  }

  return await response.blob();
}

export type MerchantStatus = "PENDING" | "APPROVED" | "REJECTED" | "SUSPENDED";

export interface MerchantResponse {
  id: string;
  userId: string;
  businessName: string;
  contactPhone: string | null;
  status: MerchantStatus;
  rejectionReason: string | null;
  createdAt: string;
  reviewedAt: string | null;
  reviewedBy: string | null;
}

export interface RegisterMerchantRequest {
  fullName: string;
  email: string;
  password: string;
  businessName: string;
  contactPhone?: string;
}

export interface RejectMerchantRequest {
  reason: string;
}

export type CourierApprovalStatus = "PENDING" | "APPROVED" | "REJECTED";

export interface CourierApprovalResponse {
  userId: string;
  email: string;
  fullName: string;
  status: CourierApprovalStatus;
  rejectionReason: string | null;
  createdAt: string;
  reviewedAt: string | null;
  reviewedBy: string | null;
}

export interface RejectCourierRequest {
  reason: string;
}

export interface RequestOptions extends Omit<RequestInit, "body"> {
  token?: string | null;
  body?: unknown;
}

export function onUnauthorized(handler: UnauthorizedHandler): () => void {
  unauthorizedHandler = handler;
  return () => {
    if (unauthorizedHandler === handler) {
      unauthorizedHandler = null;
    }
  };
}

export function getApiBaseUrl(): string {
  return process.env.NEXT_PUBLIC_API_BASE_URL || "http://localhost:8080";
}

export async function apiFetch<T>(endpoint: string, options: RequestOptions = {}): Promise<T> {
  const baseUrl = getApiBaseUrl().replace(/\/+$/, "");
  const path = endpoint.startsWith("/") ? endpoint : `/${endpoint}`;
  const url = `${baseUrl}${path}`;

  const headers = new Headers(options.headers);
  if (!headers.has("Content-Type") && options.body !== undefined) {
    headers.set("Content-Type", "application/json");
  }

  // Inject token if provided or if present in sessionStorage
  const token =
    options.token ??
    (typeof window !== "undefined" ? window.sessionStorage.getItem("swiftdrop_token") : null);

  if (token && !headers.has("Authorization")) {
    headers.set("Authorization", `Bearer ${token}`);
  }

  const response = await fetch(url, {
    ...options,
    headers,
    body: options.body !== undefined ? JSON.stringify(options.body) : undefined,
  });

  if (!response.ok) {
    if (response.status === 401 && unauthorizedHandler) {
      unauthorizedHandler();
    }

    let problem: ProblemDetail | undefined;
    const contentType = response.headers.get("content-type") || "";

    if (contentType.includes("json")) {
      try {
        problem = (await response.json()) as ProblemDetail;
      } catch {
        // Fall back to status text if JSON parsing fails
      }
    }

    const message = problem?.detail || problem?.title || `Request failed with status ${response.status}`;
    throw new ApiError(response.status, message, problem);
  }

  if (response.status === 204) {
    return undefined as unknown as T;
  }

  return (await response.json()) as T;
}

export const api = {
  get<T>(endpoint: string, options?: RequestOptions): Promise<T> {
    return apiFetch<T>(endpoint, { ...options, method: "GET" });
  },
  post<T>(endpoint: string, body?: unknown, options?: RequestOptions): Promise<T> {
    return apiFetch<T>(endpoint, { ...options, method: "POST", body });
  },
  put<T>(endpoint: string, body?: unknown, options?: RequestOptions): Promise<T> {
    return apiFetch<T>(endpoint, { ...options, method: "PUT", body });
  },
  patch<T>(endpoint: string, body?: unknown, options?: RequestOptions): Promise<T> {
    return apiFetch<T>(endpoint, { ...options, method: "PATCH", body });
  },
  delete<T>(endpoint: string, options?: RequestOptions): Promise<T> {
    return apiFetch<T>(endpoint, { ...options, method: "DELETE" });
  },
};

/**
 * Returns a user-friendly error message from an unknown error without exposing raw error bodies.
 */
export function formatApiError(error: unknown): string {
  if (error instanceof ApiError) {
    if (error.problem?.detail) {
      return error.problem.detail;
    }
    if (error.status === 401) {
      return "Invalid email or password, or session expired.";
    }
    if (error.status === 403) {
      return "You do not have permission to access this resource.";
    }
    if (error.status === 404) {
      return "The requested resource was not found.";
    }
    if (error.status === 409) {
      return "An account with these details already exists or a conflict occurred.";
    }
    return `Error (${error.status}): ${error.message}`;
  }
  if (error instanceof Error) {
    return error.message;
  }
  return "An unexpected network error occurred. Please check your connection and try again.";
}

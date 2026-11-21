import { api, type PageResponse } from "@/lib/api";

/** One message of the signed-in customer's inbox (mock SMS stored by the server). */
export interface AppNotification {
  id: string;
  parcelId: string;
  trackingNumber: string;
  status: "PICKED_UP" | "OUT_FOR_DELIVERY" | "DELIVERED" | "FAILED" | string;
  message: string;
  createdAt: string;
  readAt: string | null;
  read: boolean;
}

/** The caller's notifications, newest first. */
export function getNotifications(token: string, page: number, size: number): Promise<PageResponse<AppNotification>> {
  const params = new URLSearchParams({ page: String(page), size: String(size) });
  return api.get<PageResponse<AppNotification>>(`/api/v1/notifications?${params.toString()}`, { token });
}

/** How many of the caller's notifications are unread (for the bell). */
export function getUnreadCount(token: string): Promise<{ count: number }> {
  return api.get<{ count: number }>("/api/v1/notifications/unread-count", { token });
}

/** Marks one notification read. 404 if it is not the caller's. */
export function markNotificationRead(token: string, id: string): Promise<AppNotification> {
  return api.patch<AppNotification>(`/api/v1/notifications/${encodeURIComponent(id)}/read`, undefined, { token });
}

/** Marks every notification of the caller read. */
export function markAllNotificationsRead(token: string): Promise<{ updated: number }> {
  return api.post<{ updated: number }>("/api/v1/notifications/read-all", undefined, { token });
}

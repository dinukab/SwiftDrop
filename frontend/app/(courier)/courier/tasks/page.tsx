"use client";

import React, { useState, useEffect } from "react";
import Link from "next/link";
import { useAuth } from "@/lib/auth-context";
import { api, type CourierParcelResponse, type ParcelStatus, formatApiError } from "@/lib/api";

export default function CourierTasksPage() {
  const { token, isAuthenticated } = useAuth();
  const [parcels, setParcels] = useState<CourierParcelResponse[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [filter, setFilter] = useState<"ALL" | "DELIVERED" | "FAILED">("ALL");

  // Fetch courier's parcels (includes today's completed)
  useEffect(() => {
    async function fetchCourierParcels() {
      if (!isAuthenticated || !token) {
        setError("Authentication required. Please log in.");
        setLoading(false);
        return;
      }

      try {
        setLoading(true);
        setError(null);
        const data = await api.get<CourierParcelResponse[]>("/api/v1/courier/parcels", { token });
        setParcels(data);
      } catch (err) {
        setError(formatApiError(err));
      } finally {
        setLoading(false);
      }
    }

    fetchCourierParcels();
  }, [token, isAuthenticated]);

  // Filter parcels based on status
  const filteredParcels = parcels.filter((parcel) => {
    if (filter === "ALL") {
      return parcel.status === "DELIVERED" || parcel.status === "FAILED";
    }
    return parcel.status === filter;
  });

  // Format address for display
  const formatAddress = (addr: { line1: string; line2: string | null; city: string }) => {
    const parts = [addr.line1, addr.line2, addr.city].filter(Boolean);
    return parts.join(", ");
  };

  // Get status badge color
  const getStatusColor = (status: ParcelStatus) => {
    switch (status) {
      case "DELIVERED":
        return "bg-green-100 text-green-800";
      case "FAILED":
        return "bg-red-100 text-red-800";
      default:
        return "bg-gray-100 text-gray-800";
    }
  };

  // Format date
  const formatDate = (dateString: string) => {
    return new Date(dateString).toLocaleString();
  };

  return (
    <div className="space-y-6 p-6 max-w-7xl mx-auto">
      <div className="flex justify-between items-center">
        <div>
          <h1 className="text-2xl font-bold tracking-tight">Courier Tasks History</h1>
          <p className="text-sm text-muted-foreground">
            Historical record of completed and failed delivery stops.
          </p>
        </div>
        <Link
          href="/courier"
          className="px-4 py-2 border rounded-md text-sm font-medium hover:bg-accent transition"
        >
          Back to Active Deliveries
        </Link>
      </div>

      {/* Filter Tabs */}
      <div className="flex gap-2 border-b">
        <button
          onClick={() => setFilter("ALL")}
          className={`px-4 py-2 text-sm font-medium border-b-2 transition ${
            filter === "ALL"
              ? "border-primary text-primary"
              : "border-transparent text-muted-foreground hover:text-foreground"
          }`}
        >
          All Completed
        </button>
        <button
          onClick={() => setFilter("DELIVERED")}
          className={`px-4 py-2 text-sm font-medium border-b-2 transition ${
            filter === "DELIVERED"
              ? "border-primary text-primary"
              : "border-transparent text-muted-foreground hover:text-foreground"
          }`}
        >
          Delivered
        </button>
        <button
          onClick={() => setFilter("FAILED")}
          className={`px-4 py-2 text-sm font-medium border-b-2 transition ${
            filter === "FAILED"
              ? "border-primary text-primary"
              : "border-transparent text-muted-foreground hover:text-foreground"
          }`}
        >
          Failed
        </button>
      </div>

      <div className="border rounded-lg bg-card p-6 shadow-sm">
        <h2 className="text-lg font-semibold mb-4">
          {filter === "ALL" ? "Completed & Failed Deliveries" : `${filter.replace(/_/g, " ")} Deliveries`}
        </h2>

        {loading ? (
          <p className="text-sm text-muted-foreground">Loading history...</p>
        ) : error ? (
          <div className="text-center py-12 border-2 border-dashed rounded-lg">
            <p className="text-sm text-red-600">{error}</p>
          </div>
        ) : filteredParcels.length === 0 ? (
          <div className="text-center py-12 border-2 border-dashed rounded-lg">
            <p className="text-sm text-muted-foreground">
              No {filter === "ALL" ? "completed" : filter.toLowerCase()} deliveries found.
            </p>
            <p className="text-xs text-muted-foreground mt-1">
              Note: This view shows today's completed deliveries. For full historical data, a backend endpoint
              with date range filtering may be needed.
            </p>
          </div>
        ) : (
          <div className="space-y-4">
            {filteredParcels.map((parcel) => (
              <div key={parcel.id} className="flex flex-col md:flex-row justify-between items-start md:items-center p-4 border rounded-md gap-4 bg-background">
                <div className="flex-1">
                  <div className="flex items-center gap-2 mb-2">
                    <span className="text-xs font-semibold px-2 py-1 bg-secondary text-secondary-foreground rounded">
                      {parcel.trackingNumber}
                    </span>
                    <span className={`text-xs font-medium px-2 py-1 rounded ${getStatusColor(parcel.status)}`}>
                      {parcel.status.replace(/_/g, " ")}
                    </span>
                  </div>

                  <div className="space-y-1 text-sm">
                    <div className="flex items-start gap-2">
                      <span className="font-medium text-muted-foreground w-20 shrink-0">Pickup:</span>
                      <span>{parcel.pickup.contactName}</span>
                    </div>
                    <div className="flex items-start gap-2">
                      <span className="w-20 shrink-0"></span>
                      <span className="text-muted-foreground">{formatAddress(parcel.pickup)}</span>
                    </div>

                    <div className="flex items-start gap-2 mt-2">
                      <span className="font-medium text-muted-foreground w-20 shrink-0">Dropoff:</span>
                      <span>{parcel.dropoff.contactName}</span>
                    </div>
                    <div className="flex items-start gap-2">
                      <span className="w-20 shrink-0"></span>
                      <span className="text-muted-foreground">{formatAddress(parcel.dropoff)}</span>
                    </div>
                  </div>

                  <div className="flex items-center gap-4 mt-3 text-xs text-muted-foreground">
                    <span>Size: {parcel.size}</span>
                    <span>Weight: {parcel.weightGrams}g</span>
                    <span>Updated: {formatDate(parcel.updatedAt)}</span>
                  </div>
                </div>

                <div className="flex items-center gap-2">
                  {parcel.status === "DELIVERED" && parcel.proofCaptured && (
                    <span className="text-xs text-green-600 font-medium">Proof Captured ✓</span>
                  )}
                </div>
              </div>
            ))}
          </div>
        )}
      </div>

      {/* Info Note */}
      <div className="p-4 bg-blue-50 border border-blue-200 rounded-md">
        <p className="text-sm text-blue-800">
          <strong>Note:</strong> This view currently shows today's completed and failed deliveries. 
          For full historical data spanning multiple days, a backend endpoint with date range filtering 
          would need to be implemented (e.g., GET /api/v1/courier/parcels/history?fromDate=&toDate=).
        </p>
      </div>
    </div>
  );
}

"use client";

import React, { useState, useEffect } from "react";
import Link from "next/link";
import { useAuth } from "@/lib/auth-context";
import { api, type CourierParcelResponse, type ParcelStatus, type FailureReason, formatApiError } from "@/lib/api";

export default function CourierPortalPage() {
  const { token, isAuthenticated } = useAuth();
  const [parcels, setParcels] = useState<CourierParcelResponse[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [updating, setUpdating] = useState<string | null>(null);
  const [showFailureModal, setShowFailureModal] = useState(false);
  const [selectedParcelId, setSelectedParcelId] = useState<string | null>(null);
  const [selectedFailureReason, setSelectedFailureReason] = useState<FailureReason | null>(null);

  // Fetch assigned parcels for the courier
  useEffect(() => {
    async function fetchAssignedParcels() {
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

    fetchAssignedParcels();
  }, [token, isAuthenticated]);

  // Update parcel status
  const handleStatusChange = async (id: string, toStatus: ParcelStatus) => {
    if (!token) {
      alert("Authentication required");
      return;
    }

    if (toStatus === "FAILED") {
      setSelectedParcelId(id);
      setSelectedFailureReason(null);
      setShowFailureModal(true);
      return;
    }

    try {
      setUpdating(id);
      await api.post(`/api/v1/parcels/${id}/status-changes`, { toStatus }, { token });
      
      // Refresh the parcel list to get updated status
      const data = await api.get<CourierParcelResponse[]>("/api/v1/courier/parcels", { token });
      setParcels(data);
    } catch (err) {
      alert(formatApiError(err));
    } finally {
      setUpdating(null);
    }
  };

  // Handle failed status with reason
  const handleFailedSubmit = async () => {
    if (!token || !selectedParcelId || !selectedFailureReason) {
      return;
    }

    try {
      setUpdating(selectedParcelId);
      await api.post(`/api/v1/parcels/${selectedParcelId}/status-changes`, 
        { toStatus: "FAILED", failureReason: selectedFailureReason }, { token });
      
      // Refresh the parcel list
      const data = await api.get<CourierParcelResponse[]>("/api/v1/courier/parcels", { token });
      setParcels(data);
      
      setShowFailureModal(false);
      setSelectedParcelId(null);
      setSelectedFailureReason(null);
    } catch (err) {
      alert(formatApiError(err));
    } finally {
      setUpdating(null);
    }
  };

  // Format address for display
  const formatAddress = (addr: { line1: string; line2: string | null; city: string }) => {
    const parts = [addr.line1, addr.line2, addr.city].filter(Boolean);
    return parts.join(", ");
  };

  // Get status badge color
  const getStatusColor = (status: ParcelStatus) => {
    switch (status) {
      case "CREATED":
        return "bg-gray-100 text-gray-800";
      case "PICKED_UP":
        return "bg-blue-100 text-blue-800";
      case "IN_TRANSIT":
        return "bg-yellow-100 text-yellow-800";
      case "OUT_FOR_DELIVERY":
        return "bg-orange-100 text-orange-800";
      case "DELIVERED":
        return "bg-green-100 text-green-800";
      case "FAILED":
        return "bg-red-100 text-red-800";
      default:
        return "bg-gray-100 text-gray-800";
    }
  };

  const failureReasons: { value: FailureReason; label: string }[] = [
    { value: "RECIPIENT_UNAVAILABLE", label: "Recipient Unavailable" },
    { value: "ADDRESS_NOT_FOUND", label: "Address Not Found" },
    { value: "REFUSED", label: "Refused by Recipient" },
    { value: "DAMAGED", label: "Package Damaged" },
    { value: "OTHER", label: "Other Reason" },
  ];

  return (
    <>
      {/* Failure Reason Modal */}
      {showFailureModal && (
        <div className="fixed inset-0 bg-black/50 flex items-center justify-center z-50 p-4">
          <div className="bg-white rounded-lg p-6 max-w-md w-full space-y-4">
            <h3 className="text-lg font-semibold">Select Failure Reason</h3>
            <p className="text-sm text-muted-foreground">
              Why was this delivery unable to be completed?
            </p>
            <div className="space-y-2">
              {failureReasons.map((reason) => (
                <button
                  key={reason.value}
                  onClick={() => setSelectedFailureReason(reason.value)}
                  className={`w-full text-left px-4 py-3 rounded-md border transition ${
                    selectedFailureReason === reason.value
                      ? "border-primary bg-primary/10"
                      : "border-gray-200 hover:bg-gray-50"
                  }`}
                >
                  {reason.label}
                </button>
              ))}
            </div>
            <div className="flex gap-3 pt-2">
              <button
                onClick={() => {
                  setShowFailureModal(false);
                  setSelectedParcelId(null);
                  setSelectedFailureReason(null);
                }}
                className="flex-1 px-4 py-2 border rounded-md hover:bg-accent transition"
              >
                Cancel
              </button>
              <button
                onClick={handleFailedSubmit}
                disabled={!selectedFailureReason || updating === selectedParcelId}
                className="flex-1 px-4 py-2 bg-red-600 text-white rounded-md hover:bg-red-700 transition disabled:opacity-50 disabled:cursor-not-allowed"
              >
                {updating === selectedParcelId ? "Submitting..." : "Mark as Failed"}
              </button>
            </div>
          </div>
        </div>
      )}

      <div className="space-y-6 p-6 max-w-7xl mx-auto">
        <div className="flex justify-between items-center">
          <div>
            <h1 className="text-2xl font-bold tracking-tight">Courier Run Sheet & Deliveries</h1>
            <p className="text-sm text-muted-foreground">
              Assigned parcel deliveries, sequence-optimized stops, and digital proof-of-delivery capture.
            </p>
          </div>
          <Link
            href="/courier/tasks"
            className="px-4 py-2 border rounded-md text-sm font-medium hover:bg-accent transition"
          >
            View History
          </Link>
        </div>

      <div className="border rounded-lg bg-card p-6 shadow-sm">
        <h2 className="text-lg font-semibold mb-4">Assigned Delivery Stops</h2>
        
        {loading ? (
          <p className="text-sm text-muted-foreground">Loading deliveries...</p>
        ) : error ? (
          <div className="text-center py-12 border-2 border-dashed rounded-lg">
            <p className="text-sm text-red-600">{error}</p>
          </div>
        ) : parcels.length === 0 ? (
          <div className="text-center py-12 border-2 border-dashed rounded-lg">
            <p className="text-sm text-muted-foreground">No active delivery runs currently assigned.</p>
            <p className="text-xs text-muted-foreground mt-1">Parcels assigned to you will appear here.</p>
          </div>
        ) : (
          <div className="space-y-4">
            {parcels.map((parcel) => (
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
                    <div className="flex items-start gap-2">
                      <span className="w-20 shrink-0"></span>
                      <span className="text-muted-foreground">{parcel.pickup.phone}</span>
                    </div>
                    
                    <div className="flex items-start gap-2 mt-2">
                      <span className="font-medium text-muted-foreground w-20 shrink-0">Dropoff:</span>
                      <span>{parcel.dropoff.contactName}</span>
                    </div>
                    <div className="flex items-start gap-2">
                      <span className="w-20 shrink-0"></span>
                      <span className="text-muted-foreground">{formatAddress(parcel.dropoff)}</span>
                    </div>
                    <div className="flex items-start gap-2">
                      <span className="w-20 shrink-0"></span>
                      <span className="text-muted-foreground">{parcel.dropoff.phone}</span>
                    </div>
                  </div>

                  <div className="flex items-center gap-4 mt-3 text-xs text-muted-foreground">
                    <span>Size: {parcel.size}</span>
                    <span>Weight: {parcel.weightGrams}g</span>
                    {parcel.description && <span>Note: {parcel.description}</span>}
                  </div>
                </div>
                
                <div className="flex items-center gap-2 flex-wrap">
                  {parcel.nextStatuses.map((nextStatus) => (
                    <button
                      key={nextStatus}
                      onClick={() => handleStatusChange(parcel.id, nextStatus)}
                      disabled={updating === parcel.id}
                      className="px-3 py-1.5 text-xs font-medium border rounded hover:bg-accent transition disabled:opacity-50 disabled:cursor-not-allowed"
                    >
                      {updating === parcel.id ? "Updating..." : nextStatus.replace(/_/g, " ")}
                    </button>
                  ))}
                  
                  {parcel.status === "OUT_FOR_DELIVERY" && (
                    <Link
                      href={`/courier/proof?parcelId=${parcel.id}`}
                      className="px-3 py-1.5 text-xs font-medium bg-green-600 text-white rounded hover:bg-green-700 transition"
                    >
                      Capture Proof
                    </Link>
                  )}
                  
                  {parcel.status === "DELIVERED" && parcel.proofCaptured && (
                    <span className="text-xs text-green-600 font-medium">Proof Captured ✓</span>
                  )}
                </div>
              </div>
            ))}
          </div>
        )}
      </div>
    </div>
    </>
  );
}
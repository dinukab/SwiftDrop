"use client";

import React, { useState, useRef, useEffect } from "react";
import { useRouter, useSearchParams } from "next/navigation";
import { useAuth } from "@/lib/auth-context";
import { api, uploadProof, formatApiError, type ParcelStatus } from "@/lib/api";

export default function CourierProofPage() {
  const { token, isAuthenticated } = useAuth();
  const router = useRouter();
  const searchParams = useSearchParams();
  const parcelId = searchParams.get("parcelId");

  const [recipientName, setRecipientName] = useState("");
  const [photoFile, setPhotoFile] = useState<File | null>(null);
  const [photoPreview, setPhotoPreview] = useState<string | null>(null);
  const [uploading, setUploading] = useState(false);
  const [error, setError] = useState<string | null>(
    parcelId ? null : "No parcel ID provided. Please select a parcel from the courier portal."
  );
  const [success, setSuccess] = useState(false);
  const fileInputRef = useRef<HTMLInputElement>(null);

  // Redirect if not authenticated or no parcel ID
  useEffect(() => {
    if (!isAuthenticated) {
      router.push("/login?redirect=/courier/proof");
    }
  }, [isAuthenticated, parcelId, router]);

  // Handle file selection
  const handleFileSelect = (e: React.ChangeEvent<HTMLInputElement>) => {
    const file = e.target.files?.[0];
    if (!file) return;

    // Validate file type
    if (!file.type.match(/image\/jpeg|image\/png/)) {
      setError("Please select a JPEG or PNG image.");
      return;
    }

    // Validate file size (2MB limit)
    if (file.size > 2 * 1024 * 1024) {
      setError("Photo must be less than 2MB.");
      return;
    }

    setError(null);
    setPhotoFile(file);

    // Create preview
    const reader = new FileReader();
    reader.onloadend = () => {
      setPhotoPreview(reader.result as string);
    };
    reader.readAsDataURL(file);
  };

  // Client-side image compression
  const compressImage = async (file: File, quality: number = 0.7): Promise<Blob> => {
    return new Promise((resolve, reject) => {
      const img = new Image();
      img.src = URL.createObjectURL(file);
      
      img.onload = () => {
        const canvas = document.createElement("canvas");
        const ctx = canvas.getContext("2d");
        
        if (!ctx) {
          reject(new Error("Failed to get canvas context"));
          return;
        }

        // Calculate new dimensions (max 1920px)
        const maxDimension = 1920;
        let width = img.width;
        let height = img.height;

        if (width > maxDimension || height > maxDimension) {
          if (width > height) {
            height = (height / width) * maxDimension;
            width = maxDimension;
          } else {
            width = (width / height) * maxDimension;
            height = maxDimension;
          }
        }

        canvas.width = width;
        canvas.height = height;
        ctx.drawImage(img, 0, 0, width, height);

        canvas.toBlob(
          (blob) => {
            if (blob) {
              resolve(blob);
            } else {
              reject(new Error("Failed to compress image"));
            }
            URL.revokeObjectURL(img.src);
          },
          file.type,
          quality
        );
      };

      img.onerror = () => {
        reject(new Error("Failed to load image"));
        URL.revokeObjectURL(img.src);
      };
    });
  };

  // Handle form submission
  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();

    if (!token || !parcelId) {
      setError("Authentication required or missing parcel ID.");
      return;
    }

    if (!recipientName.trim()) {
      setError("Please enter the recipient's name.");
      return;
    }

    try {
      setUploading(true);
      setError(null);

      // Compress image if provided
      let compressedFile: File | null = null;
      if (photoFile) {
        const compressedBlob = await compressImage(photoFile);
        compressedFile = new File([compressedBlob], photoFile.name, {
          type: photoFile.type,
          lastModified: Date.now(),
        });
      }

      // Upload proof
      await uploadProof(parcelId, recipientName.trim(), compressedFile, token);
      
      // Change status to DELIVERED (backend requires proof to exist first)
      await api.post(`/api/v1/parcels/${parcelId}/status-changes`, { toStatus: "DELIVERED" }, { token });
      
      setSuccess(true);
      
      // Redirect back to courier portal after 2 seconds
      setTimeout(() => {
        router.push("/courier");
      }, 2000);
    } catch (err) {
      setError(formatApiError(err));
    } finally {
      setUploading(false);
    }
  };

  // Clear photo
  const clearPhoto = () => {
    setPhotoFile(null);
    setPhotoPreview(null);
    if (fileInputRef.current) {
      fileInputRef.current.value = "";
    }
  };

  // Trigger camera on mobile
  const triggerCamera = () => {
    fileInputRef.current?.click();
  };

  if (success) {
    return (
      <div className="flex items-center justify-center min-h-[60vh]">
        <div className="text-center space-y-4">
          <div className="w-16 h-16 bg-green-100 rounded-full flex items-center justify-center mx-auto">
            <svg className="w-8 h-8 text-green-600" fill="none" stroke="currentColor" viewBox="0 0 24 24">
              <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M5 13l4 4L19 7" />
            </svg>
          </div>
          <h2 className="text-2xl font-bold">Proof Captured Successfully!</h2>
          <p className="text-muted-foreground">Redirecting to courier portal...</p>
        </div>
      </div>
    );
  }

  return (
    <div className="space-y-6 p-6 max-w-2xl mx-auto">
      <div>
        <h1 className="text-2xl font-bold tracking-tight">Proof of Delivery</h1>
        <p className="text-sm text-muted-foreground mt-1">
          Capture recipient confirmation and delivery proof photo.
        </p>
      </div>

      {!isAuthenticated ? (
        <div className="text-center py-12 border-2 border-dashed rounded-lg">
          <p className="text-sm text-muted-foreground">Please log in to continue.</p>
        </div>
      ) : !parcelId ? (
        <div className="text-center py-12 border-2 border-dashed rounded-lg">
          <p className="text-sm text-red-600">No parcel selected.</p>
          <button
            onClick={() => router.push("/courier")}
            className="mt-4 text-sm text-primary hover:underline"
          >
            Go to Courier Portal
          </button>
        </div>
      ) : (
        <form onSubmit={handleSubmit} className="space-y-6">
          {/* Recipient Name */}
          <div className="space-y-2">
            <label htmlFor="recipientName" className="text-sm font-medium">
              Recipient Name <span className="text-red-500">*</span>
            </label>
            <input
              id="recipientName"
              type="text"
              value={recipientName}
              onChange={(e) => setRecipientName(e.target.value)}
              placeholder="Enter recipient's full name"
              required
              maxLength={200}
              className="w-full px-3 py-2 border rounded-md focus:outline-none focus:ring-2 focus:ring-primary"
              disabled={uploading}
            />
            <p className="text-xs text-muted-foreground">
              This must match the person who received the parcel.
            </p>
          </div>

          {/* Photo Capture */}
          <div className="space-y-2">
            <label className="text-sm font-medium">
              Delivery Photo <span className="text-muted-foreground">(optional)</span>
            </label>
            
            <input
              ref={fileInputRef}
              type="file"
              accept="image/jpeg,image/png"
              onChange={handleFileSelect}
              className="hidden"
              disabled={uploading}
            />

            {!photoPreview ? (
              <div className="border-2 border-dashed rounded-lg p-8 text-center">
                <div className="space-y-4">
                  <div className="w-16 h-16 bg-secondary rounded-full flex items-center justify-center mx-auto">
                    <svg className="w-8 h-8 text-muted-foreground" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                      <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M3 9a2 2 0 012-2h.93a2 2 0 001.664-.89l.812-1.22A2 2 0 0110.07 4h3.86a2 2 0 011.664.89l.812 1.22A2 2 0 0018.07 7H19a2 2 0 012 2v9a2 2 0 01-2 2H5a2 2 0 01-2-2V9z" />
                      <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M15 13a3 3 0 11-6 0 3 3 0 016 0z" />
                    </svg>
                  </div>
                  <div className="space-y-2">
                    <p className="text-sm text-muted-foreground">
                      Take a photo of the delivered parcel or recipient
                    </p>
                    <button
                      type="button"
                      onClick={triggerCamera}
                      className="px-4 py-2 bg-primary text-primary-foreground rounded-md text-sm font-medium hover:bg-primary/90 transition"
                      disabled={uploading}
                    >
                      {uploading ? "Processing..." : "Take Photo"}
                    </button>
                    <p className="text-xs text-muted-foreground">
                      JPEG or PNG, max 2MB. Image will be compressed automatically.
                    </p>
                  </div>
                </div>
              </div>
            ) : (
              <div className="space-y-3">
                <div className="relative rounded-lg overflow-hidden border">
                  <img
                    src={photoPreview}
                    alt="Proof preview"
                    className="w-full max-h-96 object-contain bg-black/5"
                  />
                  <button
                    type="button"
                    onClick={clearPhoto}
                    className="absolute top-2 right-2 p-2 bg-black/50 text-white rounded-full hover:bg-black/70 transition"
                    disabled={uploading}
                  >
                    <svg className="w-5 h-5" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                      <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M6 18L18 6M6 6l12 12" />
                    </svg>
                  </button>
                </div>
                <button
                  type="button"
                  onClick={triggerCamera}
                  className="text-sm text-primary hover:underline"
                  disabled={uploading}
                >
                  Choose different photo
                </button>
              </div>
            )}
          </div>

          {/* Error Message */}
          {error && (
            <div className="p-4 bg-red-50 border border-red-200 rounded-md">
              <p className="text-sm text-red-600">{error}</p>
            </div>
          )}

          {/* Submit Button */}
          <div className="flex gap-3">
            <button
              type="button"
              onClick={() => router.back()}
              className="flex-1 px-4 py-2 border rounded-md text-sm font-medium hover:bg-accent transition"
              disabled={uploading}
            >
              Cancel
            </button>
            <button
              type="submit"
              className="flex-1 px-4 py-2 bg-primary text-primary-foreground rounded-md text-sm font-medium hover:bg-primary/90 transition disabled:opacity-50 disabled:cursor-not-allowed"
              disabled={uploading || !recipientName.trim()}
            >
              {uploading ? "Uploading..." : "Submit Proof"}
            </button>
          </div>

          {/* Info Note */}
          <div className="p-4 bg-blue-50 border border-blue-200 rounded-md">
            <p className="text-sm text-blue-800">
              <strong>Note:</strong> After submitting proof, the parcel status will automatically change to DELIVERED.
              Make sure you have handed over the parcel to the correct recipient before submitting.
            </p>
          </div>
        </form>
      )}
    </div>
  );
}

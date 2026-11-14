"use client";

import React, { useState, useEffect } from "react";
import Link from "next/link";
import { api, formatApiError } from "@/lib/api";
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card";
import { Button } from "@/components/ui/button";
import { Package, Key, Webhook, ArrowRight, ShieldCheck, Clock, AlertTriangle, Loader2 } from "lucide-react";

interface MerchantProfile {
  id: string;
  businessName: string;
  registrationNumber: string;
  contactEmail: string;
  status: string;
  rejectionReason?: string;
  createdAt: string;
}

export default function MerchantPortalPage() {
  const [profile, setProfile] = useState<MerchantProfile | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    async function loadProfile() {
      try {
        const data = await api.get<MerchantProfile>("/api/v1/merchants/me");
        setProfile(data);
      } catch (err) {
        setError(formatApiError(err));
      } finally {
        setLoading(false);
      }
    }
    loadProfile();
  }, []);

  const getStatusBadge = (status: string) => {
    switch (status) {
      case "APPROVED":
        return (
          <span className="inline-flex items-center gap-1.5 px-3 py-1 rounded-full text-xs font-medium bg-emerald-100 text-emerald-800 dark:bg-emerald-950 dark:text-emerald-300">
            <ShieldCheck className="w-3.5 h-3.5" /> Approved Partner
          </span>
        );
      case "REJECTED":
        return (
          <span className="inline-flex items-center gap-1.5 px-3 py-1 rounded-full text-xs font-medium bg-rose-100 text-rose-800 dark:bg-rose-950 dark:text-rose-300">
            <AlertTriangle className="w-3.5 h-3.5" /> Application Rejected
          </span>
        );
      default:
        return (
          <span className="inline-flex items-center gap-1.5 px-3 py-1 rounded-full text-xs font-medium bg-amber-100 text-amber-800 dark:bg-amber-950 dark:text-amber-300">
            <Clock className="w-3.5 h-3.5" /> Pending Admin Approval
          </span>
        );
    }
  };

  if (loading) {
    return (
      <div className="flex justify-center items-center py-24">
        <Loader2 className="w-8 h-8 animate-spin text-muted-foreground" />
      </div>
    );
  }

  return (
    <div className="space-y-6">
      {/* Welcome Banner */}
      <div className="bg-card border rounded-lg p-6 shadow-sm flex flex-col sm:flex-row justify-between items-start sm:items-center gap-4">
        <div className="space-y-1">
          <h1 className="text-2xl font-bold tracking-tight">
            {profile ? profile.businessName : "Merchant Portal"}
          </h1>
          <p className="text-sm text-muted-foreground">
            {profile ? `Business Reg: ${profile.registrationNumber}` : "Manage deliveries, API access, and webhooks."}
          </p>
        </div>
        {profile && getStatusBadge(profile.status)}
      </div>

      {profile?.status === "PENDING_APPROVAL" && (
        <div className="p-4 bg-amber-500/10 border border-amber-500/20 text-amber-800 dark:text-amber-200 rounded-lg flex items-start gap-3">
          <Clock className="w-5 h-5 flex-shrink-0 mt-0.5" />
          <div className="text-sm space-y-1">
            <p className="font-semibold">Your merchant account is awaiting dispatcher approval</p>
            <p className="text-xs opacity-90">
              API order intake and key generation will become active once an administrator verifies and approves your business registration.
            </p>
          </div>
        </div>
      )}

      {/* Navigation Quick Cards */}
      <div className="grid grid-cols-1 md:grid-cols-3 gap-6">
        <Card className="hover:border-primary/50 transition-colors">
          <CardHeader className="pb-3">
            <div className="w-10 h-10 rounded-lg bg-primary/10 text-primary flex items-center justify-center mb-2">
              <Package className="w-5 h-5" />
            </div>
            <CardTitle className="text-lg">Shipment Orders</CardTitle>
            <CardDescription className="text-xs">
              Monitor active parcels, status transitions, and delivery histories.
            </CardDescription>
          </CardHeader>
          <CardContent>
            <Button variant="ghost" size="sm" asChild className="p-0 text-primary hover:bg-transparent">
              <Link href="/merchant/orders" className="flex items-center gap-1 text-sm font-medium">
                View orders <ArrowRight className="w-4 h-4" />
              </Link>
            </Button>
          </CardContent>
        </Card>

        <Card className="hover:border-primary/50 transition-colors">
          <CardHeader className="pb-3">
            <div className="w-10 h-10 rounded-lg bg-primary/10 text-primary flex items-center justify-center mb-2">
              <Key className="w-5 h-5" />
            </div>
            <CardTitle className="text-lg">API Credentials</CardTitle>
            <CardDescription className="text-xs">
              Provision and manage secret keys for your external e-commerce platform.
            </CardDescription>
          </CardHeader>
          <CardContent>
            <Button variant="ghost" size="sm" asChild className="p-0 text-primary hover:bg-transparent">
              <Link href="/merchant/api-keys" className="flex items-center gap-1 text-sm font-medium">
                Manage keys <ArrowRight className="w-4 h-4" />
              </Link>
            </Button>
          </CardContent>
        </Card>

        <Card className="hover:border-primary/50 transition-colors">
          <CardHeader className="pb-3">
            <div className="w-10 h-10 rounded-lg bg-primary/10 text-primary flex items-center justify-center mb-2">
              <Webhook className="w-5 h-5" />
            </div>
            <CardTitle className="text-lg">Webhooks</CardTitle>
            <CardDescription className="text-xs">
              Configure real-time event callbacks for order fulfillment milestones.
            </CardDescription>
          </CardHeader>
          <CardContent>
            <Button variant="ghost" size="sm" asChild className="p-0 text-primary hover:bg-transparent">
              <Link href="/merchant/webhooks" className="flex items-center gap-1 text-sm font-medium">
                Configure webhooks <ArrowRight className="w-4 h-4" />
              </Link>
            </Button>
          </CardContent>
        </Card>
      </div>

      {/* Developer API Documentation Quick Snippet */}
      <Card>
        <CardHeader>
          <CardTitle className="text-base">Quick Start: Automated Order Intake (MER-2)</CardTitle>
          <CardDescription className="text-xs">
            Submit new parcels from your online checkout using your secret API key.
          </CardDescription>
        </CardHeader>
        <CardContent>
          <pre className="p-4 bg-muted rounded-md text-xs font-mono overflow-x-auto text-foreground/90">
{`curl -X POST http://localhost:8080/api/v1/merchant-api/orders \\
  -H "X-API-Key: sd_live_YOUR_API_KEY" \\
  -H "Content-Type: application/json" \\
  -d '{
    "merchantOrderReference": "STORE-9921",
    "pickup": {
      "contactName": "Warehouse Hub",
      "phone": "+94771234567",
      "line1": "100 Warehouse Way",
      "city": "Colombo",
      "zoneId": "00000000-0000-0000-0000-000000000001"
    },
    "dropoff": {
      "contactName": "Customer Name",
      "phone": "+94779876543",
      "line1": "45 Main Street",
      "city": "Kandy",
      "zoneId": "00000000-0000-0000-0000-000000000002"
    },
    "size": "SMALL",
    "weightGrams": 500,
    "description": "Apparel order #9921"
  }'`}
          </pre>
        </CardContent>
      </Card>
    </div>
  );
}

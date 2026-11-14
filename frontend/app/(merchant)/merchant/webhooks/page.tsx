"use client";

import React, { useState, useEffect } from "react";
import { api, formatApiError } from "@/lib/api";
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { Webhook, Plus, Trash2, RefreshCw, AlertCircle, Loader2, Globe } from "lucide-react";

interface WebhookItem {
  id: string;
  url: string;
  active: boolean;
  createdAt: string;
  updatedAt: string;
}

export default function MerchantWebhooksPage() {
  const [endpoints, setEndpoints] = useState<WebhookItem[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  // Register modal
  const [isRegisterOpen, setIsRegisterOpen] = useState(false);
  const [newUrl, setNewUrl] = useState("");
  const [newSecret, setNewSecret] = useState("");
  const [submitting, setSubmitting] = useState(false);

  // Deleting
  const [deletingId, setDeletingId] = useState<string | null>(null);

  const fetchEndpoints = async () => {
    setLoading(true);
    setError(null);
    try {
      const data = await api.get<WebhookItem[]>("/api/v1/merchants/me/webhooks");
      setEndpoints(data);
    } catch (err) {
      setError(formatApiError(err));
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    fetchEndpoints();
  }, []);

  const handleRegister = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!newUrl.trim() || !newSecret.trim()) return;

    setSubmitting(true);
    setError(null);
    try {
      await api.post("/api/v1/merchants/me/webhooks", {
        url: newUrl.trim(),
        secret: newSecret.trim(),
      });
      setIsRegisterOpen(false);
      setNewUrl("");
      setNewSecret("");
      fetchEndpoints();
    } catch (err) {
      setError(formatApiError(err));
    } finally {
      setSubmitting(false);
    }
  };

  const handleDelete = async (id: string) => {
    if (!window.confirm("Are you sure you want to delete this webhook endpoint? No further status events will be dispatched to it.")) {
      return;
    }
    setDeletingId(id);
    try {
      await api.delete(`/api/v1/merchants/me/webhooks/${id}`);
      fetchEndpoints();
    } catch (err) {
      setError(formatApiError(err));
    } finally {
      setDeletingId(null);
    }
  };

  return (
    <div className="space-y-6">
      <div className="flex flex-col sm:flex-row justify-between items-start sm:items-center gap-4">
        <div>
          <h1 className="text-2xl font-bold tracking-tight">Webhook Notifications</h1>
          <p className="text-muted-foreground text-sm">
            Configure automated HTTPS callbacks to receive real-time parcel status updates on your servers.
          </p>
        </div>
        <div className="flex gap-2">
          <Button variant="outline" size="sm" onClick={fetchEndpoints} disabled={loading}>
            <RefreshCw className={`w-4 h-4 mr-2 ${loading ? "animate-spin" : ""}`} />
            Refresh
          </Button>
          <Button size="sm" onClick={() => setIsRegisterOpen(true)}>
            <Plus className="w-4 h-4 mr-2" />
            Add Endpoint
          </Button>
        </div>
      </div>

      {error && (
        <div className="p-4 bg-destructive/10 border border-destructive/20 text-destructive rounded-lg flex items-center gap-3">
          <AlertCircle className="w-5 h-5 flex-shrink-0" />
          <p className="text-sm">{error}</p>
        </div>
      )}

      {/* Add Webhook Dialog */}
      {isRegisterOpen && (
        <div className="fixed inset-0 bg-black/50 z-50 flex items-center justify-center p-4">
          <div className="bg-background rounded-lg shadow-xl max-w-md w-full border p-6 space-y-4">
            <h2 className="text-lg font-semibold flex items-center gap-2">
              <Webhook className="w-5 h-5 text-primary" />
              Register Webhook Endpoint
            </h2>
            <p className="text-sm text-muted-foreground">
              Provide an HTTPS listener URL and a secret used to sign the payload via HMAC-SHA256.
            </p>
            <form onSubmit={handleRegister} className="space-y-4">
              <div className="space-y-2">
                <Label htmlFor="webhook-url">Target Endpoint URL</Label>
                <Input
                  id="webhook-url"
                  type="url"
                  placeholder="https://example.com/api/webhooks"
                  value={newUrl}
                  onChange={(e) => setNewUrl(e.target.value)}
                  required
                  autoFocus
                />
              </div>
              <div className="space-y-2">
                <Label htmlFor="webhook-secret">Signing Secret (min 16 characters)</Label>
                <Input
                  id="webhook-secret"
                  type="password"
                  placeholder="At least 16 random characters"
                  value={newSecret}
                  onChange={(e) => setNewSecret(e.target.value)}
                  minLength={16}
                  maxLength={128}
                  required
                />
              </div>
              <div className="flex justify-end gap-2 pt-2">
                <Button type="button" variant="outline" onClick={() => setIsRegisterOpen(false)} disabled={submitting}>
                  Cancel
                </Button>
                <Button type="submit" disabled={submitting || !newUrl.trim() || newSecret.length < 16}>
                  {submitting && <Loader2 className="w-4 h-4 mr-2 animate-spin" />}
                  Register Endpoint
                </Button>
              </div>
            </form>
          </div>
        </div>
      )}

      <Card>
        <CardHeader>
          <CardTitle>Active Subscriptions</CardTitle>
          <CardDescription>
            Events are dispatched via HTTP POST with <code className="bg-muted px-1.5 py-0.5 rounded text-xs">X-SwiftDrop-Signature</code>.
          </CardDescription>
        </CardHeader>
        <CardContent>
          {loading && (
            <div className="flex justify-center items-center py-12">
              <Loader2 className="w-8 h-8 animate-spin text-muted-foreground" />
            </div>
          )}

          {!loading && endpoints.length === 0 && (
            <div className="text-center py-12 space-y-3">
              <Globe className="w-10 h-10 text-muted-foreground mx-auto" />
              <p className="text-sm font-medium">No webhook endpoints registered</p>
              <p className="text-xs text-muted-foreground">
                Add an HTTPS URL to automatically receive delivery status transitions (e.g. PICKED_UP, DELIVERED).
              </p>
            </div>
          )}

          {!loading && endpoints.length > 0 && (
            <div className="divide-y border rounded-lg overflow-hidden">
              {endpoints.map((ep) => (
                <div key={ep.id} className="p-4 flex flex-col sm:flex-row justify-between items-start sm:items-center gap-4 bg-card">
                  <div className="space-y-1">
                    <div className="flex items-center gap-2">
                      <span className="font-mono text-sm break-all">{ep.url}</span>
                      <span className="inline-flex items-center px-2 py-0.5 rounded text-xs font-medium bg-emerald-100 text-emerald-800 dark:bg-emerald-950 dark:text-emerald-300 flex-shrink-0">
                        Active
                      </span>
                    </div>
                    <div className="text-xs text-muted-foreground">
                      Registered: {new Date(ep.createdAt).toLocaleDateString()}
                    </div>
                  </div>
                  <Button
                    variant="ghost"
                    size="sm"
                    className="text-destructive hover:bg-destructive/10 hover:text-destructive flex-shrink-0"
                    onClick={() => handleDelete(ep.id)}
                    disabled={deletingId === ep.id}
                  >
                    {deletingId === ep.id ? <Loader2 className="w-4 h-4 animate-spin" /> : <Trash2 className="w-4 h-4 mr-1" />}
                    Delete
                  </Button>
                </div>
              ))}
            </div>
          )}
        </CardContent>
      </Card>
    </div>
  );
}

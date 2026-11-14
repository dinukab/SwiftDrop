"use client";

import React, { useState, useEffect } from "react";
import { api, formatApiError } from "@/lib/api";
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { Key, Plus, Trash2, Copy, Check, AlertCircle, RefreshCw, Loader2, ShieldAlert } from "lucide-react";

interface ApiKeyItem {
  id: string;
  label: string;
  keyPrefix: string;
  active: boolean;
  lastUsedAt: string | null;
  createdAt: string;
}

interface GeneratedKeyData {
  id: string;
  label: string;
  keyPrefix: string;
  rawKey: string;
  createdAt: string;
}

export default function MerchantApiKeysPage() {
  const [keys, setKeys] = useState<ApiKeyItem[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  // New key dialog
  const [isCreateOpen, setIsCreateOpen] = useState(false);
  const [newLabel, setNewLabel] = useState("");
  const [creating, setCreating] = useState(false);

  // Secret display modal
  const [generatedKey, setGeneratedKey] = useState<GeneratedKeyData | null>(null);
  const [copied, setCopied] = useState(false);

  // Revoking state
  const [revokingId, setRevokingId] = useState<string | null>(null);

  const fetchKeys = async () => {
    setLoading(true);
    setError(null);
    try {
      const data = await api.get<ApiKeyItem[]>("/api/v1/merchants/me/api-keys");
      setKeys(data);
    } catch (err) {
      setError(formatApiError(err));
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    fetchKeys();
  }, []);

  const handleCreate = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!newLabel.trim()) return;

    setCreating(true);
    setError(null);
    try {
      const result = await api.post<GeneratedKeyData>("/api/v1/merchants/me/api-keys", {
        label: newLabel.trim(),
      });
      setGeneratedKey(result);
      setIsCreateOpen(false);
      setNewLabel("");
      fetchKeys();
    } catch (err) {
      setError(formatApiError(err));
    } finally {
      setCreating(false);
    }
  };

  const handleRevoke = async (id: string) => {
    if (!window.confirm("Are you sure you want to revoke this API key? External systems using it will immediately lose access.")) {
      return;
    }
    setRevokingId(id);
    try {
      await api.delete(`/api/v1/merchants/me/api-keys/${id}`);
      fetchKeys();
    } catch (err) {
      setError(formatApiError(err));
    } finally {
      setRevokingId(null);
    }
  };

  const handleCopy = () => {
    if (!generatedKey) return;
    navigator.clipboard.writeText(generatedKey.rawKey);
    setCopied(true);
    setTimeout(() => setCopied(false), 2000);
  };

  return (
    <div className="space-y-6">
      <div className="flex flex-col sm:flex-row justify-between items-start sm:items-center gap-4">
        <div>
          <h1 className="text-2xl font-bold tracking-tight">API Credentials</h1>
          <p className="text-muted-foreground text-sm">
            Manage API keys for automated e-commerce order intake, ERP synchronization, and logistics dispatch.
          </p>
        </div>
        <div className="flex gap-2">
          <Button variant="outline" size="sm" onClick={fetchKeys} disabled={loading}>
            <RefreshCw className={`w-4 h-4 mr-2 ${loading ? "animate-spin" : ""}`} />
            Refresh
          </Button>
          <Button size="sm" onClick={() => setIsCreateOpen(true)}>
            <Plus className="w-4 h-4 mr-2" />
            Generate New Key
          </Button>
        </div>
      </div>

      {error && (
        <div className="p-4 bg-destructive/10 border border-destructive/20 text-destructive rounded-lg flex items-center gap-3">
          <AlertCircle className="w-5 h-5 flex-shrink-0" />
          <p className="text-sm">{error}</p>
        </div>
      )}

      {/* API Key Modal / Dialog */}
      {isCreateOpen && (
        <div className="fixed inset-0 bg-black/50 z-50 flex items-center justify-center p-4">
          <div className="bg-background rounded-lg shadow-xl max-w-md w-full border p-6 space-y-4">
            <h2 className="text-lg font-semibold flex items-center gap-2">
              <Key className="w-5 h-5 text-primary" />
              Generate API Key
            </h2>
            <p className="text-sm text-muted-foreground">
              Provide a label to identify where this key is used (e.g., &quot;Shopify Store&quot;, &quot;Warehouse ERP&quot;).
            </p>
            <form onSubmit={handleCreate} className="space-y-4">
              <div className="space-y-2">
                <Label htmlFor="key-label">Key Label</Label>
                <Input
                  id="key-label"
                  placeholder="e.g. Production Webstore"
                  value={newLabel}
                  onChange={(e) => setNewLabel(e.target.value)}
                  required
                  autoFocus
                />
              </div>
              <div className="flex justify-end gap-2 pt-2">
                <Button type="button" variant="outline" onClick={() => setIsCreateOpen(false)} disabled={creating}>
                  Cancel
                </Button>
                <Button type="submit" disabled={creating || !newLabel.trim()}>
                  {creating && <Loader2 className="w-4 h-4 mr-2 animate-spin" />}
                  Generate Key
                </Button>
              </div>
            </form>
          </div>
        </div>
      )}

      {/* Secret Display Modal (Shown only once) */}
      {generatedKey && (
        <div className="fixed inset-0 bg-black/60 z-50 flex items-center justify-center p-4">
          <div className="bg-background rounded-lg shadow-2xl max-w-lg w-full border border-amber-500/30 p-6 space-y-4">
            <div className="flex items-center gap-2 text-amber-600 dark:text-amber-400">
              <ShieldAlert className="w-6 h-6" />
              <h2 className="text-lg font-semibold">Copy Your Secret API Key</h2>
            </div>
            <p className="text-sm text-muted-foreground">
              Please save this key securely. For security reasons, <strong>it will never be displayed again</strong>.
            </p>
            <div className="p-3 bg-muted rounded-md border font-mono text-xs break-all flex items-center justify-between gap-2">
              <span>{generatedKey.rawKey}</span>
              <Button size="icon" variant="ghost" onClick={handleCopy} className="flex-shrink-0">
                {copied ? <Check className="w-4 h-4 text-green-600" /> : <Copy className="w-4 h-4" />}
              </Button>
            </div>
            <div className="flex justify-end pt-2">
              <Button onClick={() => setGeneratedKey(null)}>I have securely saved this key</Button>
            </div>
          </div>
        </div>
      )}

      <Card>
        <CardHeader>
          <CardTitle>Active Credentials</CardTitle>
          <CardDescription>
            Pass your active key in the <code className="bg-muted px-1.5 py-0.5 rounded text-xs">X-API-Key</code> request header.
          </CardDescription>
        </CardHeader>
        <CardContent>
          {loading && (
            <div className="flex justify-center items-center py-12">
              <Loader2 className="w-8 h-8 animate-spin text-muted-foreground" />
            </div>
          )}

          {!loading && keys.length === 0 && (
            <div className="text-center py-12 space-y-3">
              <Key className="w-10 h-10 text-muted-foreground mx-auto" />
              <p className="text-sm font-medium">No API keys created yet</p>
              <p className="text-xs text-muted-foreground">
                Generate your first API key to connect your e-commerce storefront or backend automation.
              </p>
            </div>
          )}

          {!loading && keys.length > 0 && (
            <div className="divide-y border rounded-lg overflow-hidden">
              {keys.map((k) => (
                <div key={k.id} className="p-4 flex flex-col sm:flex-row justify-between items-start sm:items-center gap-4 bg-card">
                  <div className="space-y-1">
                    <div className="flex items-center gap-2">
                      <span className="font-medium text-sm">{k.label}</span>
                      <span className="inline-flex items-center px-2 py-0.5 rounded text-xs font-medium bg-emerald-100 text-emerald-800 dark:bg-emerald-950 dark:text-emerald-300">
                        Active
                      </span>
                    </div>
                    <div className="text-xs font-mono text-muted-foreground">
                      Prefix: sd_live_{k.keyPrefix}••••••••
                    </div>
                    <div className="text-xs text-muted-foreground flex gap-4 pt-1">
                      <span>Created: {new Date(k.createdAt).toLocaleDateString()}</span>
                      <span>Last used: {k.lastUsedAt ? new Date(k.lastUsedAt).toLocaleString() : "Never"}</span>
                    </div>
                  </div>
                  <Button
                    variant="ghost"
                    size="sm"
                    className="text-destructive hover:bg-destructive/10 hover:text-destructive"
                    onClick={() => handleRevoke(k.id)}
                    disabled={revokingId === k.id}
                  >
                    {revokingId === k.id ? <Loader2 className="w-4 h-4 animate-spin" /> : <Trash2 className="w-4 h-4 mr-1" />}
                    Revoke
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

"use client";

import { useAuth } from "@/lib/auth-context";
import { ChatPanel } from "@/components/customer/assistant/chat-panel";

export default function AssistantPage() {
  const { token, isLoading } = useAuth();

  return (
    <div className="mx-auto max-w-2xl space-y-6">
      <div>
        <h1 className="text-xl font-semibold">Delivery Assistant</h1>
        <p className="text-sm text-muted-foreground">
          Ask about your parcels, tracking, or delivery fees — powered by live SwiftDrop data.
        </p>
      </div>

      {isLoading ? (
        <p className="text-sm text-muted-foreground">Loading…</p>
      ) : token ? (
        <ChatPanel token={token} />
      ) : (
        <p className="text-sm text-muted-foreground">Please sign in to use the assistant.</p>
      )}
    </div>
  );
}

"use client";

import { useAuth } from "@/lib/auth-context";
import { ChatPanel } from "@/components/customer/assistant/chat-panel";
import { CustomerChatProvider } from "@/components/customer/assistant/chat-context";

export default function AssistantPage() {
  const { token, isLoading } = useAuth();

  return (
    <CustomerChatProvider>
      <div className="space-y-6">
        <div>
          <h1 className="text-xl font-semibold text-slate-900">Delivery Assistant</h1>
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
    </CustomerChatProvider>
  );
}
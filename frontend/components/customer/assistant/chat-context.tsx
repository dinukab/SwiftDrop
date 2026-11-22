"use client";

import React, { createContext, useCallback, useContext, useEffect, useMemo, useState } from "react";

export const ASSISTANT_CHATS_KEY = "swiftdrop_assistant_chats_persistent";
export const ASSISTANT_ACTIVE_ID_KEY = "swiftdrop_assistant_active_id";

export const ASSISTANT_WELCOME =
  "Hi! I'm the SwiftDrop assistant. Ask me where your parcel is or how much a delivery costs.";

export interface ChatMessage {
  id: number;
  role: "user" | "assistant";
  text: string;
}

export interface Conversation {
  id: string;
  title: string;
  messages: ChatMessage[];
  updatedAt: number;
}

interface ChatState {
  conversations: Conversation[];
  activeId: string | null;
}

interface CustomerChatContextValue {
  conversations: Conversation[];
  activeConversation: Conversation | null;
  selectConversation: (id: string) => void;
  startNewChat: () => void;
  deleteConversation: (id: string) => void;
  appendMessage: (role: "user" | "assistant", text: string) => void;
}

const CustomerChatContext = createContext<CustomerChatContextValue | undefined>(undefined);

function generateId(): string {
  if (typeof window !== "undefined" && window.crypto && typeof window.crypto.randomUUID === "function") {
    return window.crypto.randomUUID();
  }
  return Math.random().toString(36).substring(2, 15) + Date.now().toString(36);
}

function loadState(): ChatState {
  if (typeof window === "undefined") return { conversations: [], activeId: null };
  try {
    const rawChats = window.localStorage.getItem(ASSISTANT_CHATS_KEY);
    if (!rawChats) return { conversations: [], activeId: null };
    
    const parsed = JSON.parse(rawChats);
    
    
    let conversations: Conversation[] = [];
    if (Array.isArray(parsed)) {
      conversations = parsed;
    } else if (parsed && typeof parsed === "object" && Array.isArray((parsed as { conversations?: Conversation[] }).conversations)) {
      conversations = (parsed as { conversations?: Conversation[] }).conversations ?? [];
    }

    const activeId = window.sessionStorage.getItem(ASSISTANT_ACTIVE_ID_KEY);
    const validActiveId = activeId && conversations.some((c) => c.id === activeId) ? activeId : null;

    return { conversations, activeId: validActiveId };
  } catch {
    return { conversations: [], activeId: null };
  }
}

export function CustomerChatProvider({ children }: { children: React.ReactNode }) {
  const [state, setState] = useState<ChatState>(loadState);

  useEffect(() => {
    try {
      window.localStorage.setItem(ASSISTANT_CHATS_KEY, JSON.stringify(state.conversations));
      if (state.activeId) {
        window.sessionStorage.setItem(ASSISTANT_ACTIVE_ID_KEY, state.activeId);
      } else {
        window.sessionStorage.removeItem(ASSISTANT_ACTIVE_ID_KEY);
      }
    } catch {
      // Storage full or unavailable
    }
  }, [state]);

  const { conversations, activeId } = state;
  const activeConversation = useMemo(
    () => (Array.isArray(conversations) ? conversations.find((c) => c.id === activeId) ?? null : null),
    [conversations, activeId],
  );

  const selectConversation = useCallback((id: string) => {
    setState((prev) => ({ ...prev, activeId: id }));
  }, []);

  const startNewChat = useCallback(() => {
    const conversation: Conversation = {
      id: generateId(),
      title: "New chat",
      messages: [{ id: Date.now(), role: "assistant", text: ASSISTANT_WELCOME }],
      updatedAt: Date.now(),
    };
    setState((prev) => ({
      conversations: Array.isArray(prev.conversations) ? [conversation, ...prev.conversations] : [conversation],
      activeId: conversation.id,
    }));
  }, []);

  const deleteConversation = useCallback((id: string) => {
    setState((prev) => {
      const list = Array.isArray(prev.conversations) ? prev.conversations : [];
      return {
        conversations: list.filter((c) => c.id !== id),
        activeId: prev.activeId === id ? null : prev.activeId,
      };
    });
  }, []);

  const appendMessage = useCallback((role: "user" | "assistant", text: string) => {
    const message: ChatMessage = {
      id: Date.now() + Math.floor(Math.random() * 1000),
      role,
      text,
    };
    setState((prev) => {
      const list = Array.isArray(prev.conversations) ? prev.conversations : [];
      const target = list.find((c) => c.id === prev.activeId);
      
      if (target) {
        return {
          ...prev,
          conversations: list.map((c) =>
            c.id === target.id
              ? {
                  ...c,
                  title:
                    c.title === "New chat" && role === "user" ? text.slice(0, 60) : c.title,
                  messages: [...c.messages, message],
                  updatedAt: Date.now(),
                }
              : c,
          ),
        };
      }
      const conversation: Conversation = {
        id: generateId(),
        title: role === "user" ? text.slice(0, 60) : "New chat",
        messages: [message],
        updatedAt: Date.now(),
      };
      return { conversations: [conversation, ...list], activeId: conversation.id };
    });
  }, []);

  const value = useMemo(
    () => ({
      conversations: Array.isArray(conversations) ? conversations : [],
      activeConversation,
      selectConversation,
      startNewChat,
      deleteConversation,
      appendMessage,
    }),
    [conversations, activeConversation, selectConversation, startNewChat, deleteConversation, appendMessage],
  );

  return <CustomerChatContext.Provider value={value}>{children}</CustomerChatContext.Provider>;
}

export function useCustomerChat(): CustomerChatContextValue {
  const context = useContext(CustomerChatContext);
  if (!context) {
    throw new Error("useCustomerChat must be used within a CustomerChatProvider");
  }
  return context;
}
"use client";

import { useMemo, useState, useRef, useEffect } from "react";
import ReactMarkdown from "react-markdown";
import {
  Banknote,
  Map,
  MessageSquarePlus,
  Package,
  Search,
  SendHorizonal,
  Trash2,
} from "lucide-react";
import { Button } from "@/components/ui/button";
import { Card } from "@/components/ui/card";
import { Input } from "@/components/ui/input";
import { formatApiError } from "@/lib/api";
import { sendAssistantMessage } from "@/services/assistant";
import { ASSISTANT_WELCOME, useCustomerChat, type ChatMessage } from "./chat-context";

const ACCENT = "#ea580c";
const BORDER = "#e2e8f0";
const SIDEBAR_BG = "#f8fafc";

const SUGGESTIONS = [
  { icon: Package, label: "Where is my parcel?", prompt: "Where is my parcel?" },
  { icon: Banknote, label: "Calculate delivery fee", prompt: "How much does it cost to deliver a parcel?" },
  { icon: Map, label: "View delivery zones", prompt: "What are the delivery zones you serve?" },
];

function AssistantAvatar() {
  return (
    <div
      className="flex size-8 shrink-0 items-center justify-center rounded-full text-xs font-bold text-white shadow-sm"
      style={{ backgroundColor: ACCENT }}
      aria-hidden="true"
    >
      SD
    </div>
  );
}

function MarkdownText({ text }: { text: string }) {
  return (
    <div className="text-sm leading-relaxed">
      <ReactMarkdown
        components={{
          p: ({ children }) => <p className="mb-2 last:mb-0">{children}</p>,
          strong: ({ children }) => <strong className="font-semibold">{children}</strong>,
          ul: ({ children }) => <ul className="mb-2 list-disc pl-5 last:mb-0">{children}</ul>,
          ol: ({ children }) => <ol className="mb-2 list-decimal pl-5 last:mb-0">{children}</ol>,
          li: ({ children }) => <li className="mb-0.5">{children}</li>,
          code: ({ children }) => (
            <code className="rounded bg-black/5 px-1 py-0.5 font-mono text-xs">{children}</code>
          ),
          pre: ({ children }) => (
            <pre className="mb-2 overflow-x-auto rounded-lg bg-black/5 p-2 text-xs last:mb-0">{children}</pre>
          ),
          table: ({ children }) => (
            <div className="mb-2 overflow-x-auto last:mb-0">
              <table className="w-full border-collapse text-xs">{children}</table>
            </div>
          ),
          th: ({ children }) => (
            <th className="border px-2 py-1 text-left font-semibold bg-gray-50" style={{ borderColor: BORDER }}>
              {children}
            </th>
          ),
          td: ({ children }) => (
            <td className="border px-2 py-1" style={{ borderColor: BORDER }}>
              {children}
            </td>
          ),
          a: ({ children, href }) => (
            <a href={href} className="underline font-medium" style={{ color: ACCENT }} target="_blank" rel="noreferrer">
              {children}
            </a>
          ),
        }}
      >
        {text}
      </ReactMarkdown>
    </div>
  );
}

function MessageBubble({ message }: { message: ChatMessage }) {
  const isUser = message.role === "user";
  return (
    <div className={`flex gap-3 ${isUser ? "justify-end" : "justify-start"}`}>
      {!isUser && <AssistantAvatar />}
      <div
        className={
          isUser
            ? "max-w-[75%] rounded-2xl rounded-br-md px-4 py-3 text-sm text-white shadow-sm"
            : "max-w-[75%] rounded-2xl rounded-bl-md border bg-white px-4 py-3 shadow-sm"
        }
        style={isUser ? { backgroundColor: ACCENT } : { borderColor: BORDER }}
      >
        {isUser ? (
          <p className="whitespace-pre-wrap text-sm leading-relaxed">{message.text}</p>
        ) : (
          <MarkdownText text={message.text} />
        )}
      </div>
    </div>
  );
}

export function ChatPanel({ token }: { token: string }) {
  const {
    conversations,
    activeConversation,
    selectConversation,
    startNewChat,
    deleteConversation,
    appendMessage,
  } = useCustomerChat();
  const [input, setInput] = useState("");
  const [chatSearch, setChatSearch] = useState("");
  const [sending, setSending] = useState(false);
  const messagesEndRef = useRef<HTMLDivElement>(null);

  const scrollToBottom = () => {
    messagesEndRef.current?.scrollIntoView({ behavior: "smooth" });
  };

  useEffect(() => {
    scrollToBottom();
  }, [activeConversation?.messages, sending]);

  const filteredConversations = useMemo(() => {
    const term = chatSearch.trim().toLowerCase();
    if (!term) return conversations;
    return conversations.filter(
      (c) =>
        c.title.toLowerCase().includes(term) ||
        c.messages.some((m) => m.text.toLowerCase().includes(term)),
    );
  }, [conversations, chatSearch]);

  const send = async (text: string) => {
    const trimmed = text.trim();
    if (!trimmed || sending) return;

    appendMessage("user", trimmed);
    setInput("");
    setSending(true);

    try {
      const { reply } = await sendAssistantMessage(trimmed, token);
      appendMessage("assistant", reply);
    } catch (err) {
      appendMessage("assistant", `Sorry, something went wrong: ${formatApiError(err)}`);
    } finally {
      setSending(false);
    }
  };

  const messages = activeConversation?.messages ?? [];
  const showEmpty = messages.length === 0;

  return (
    <Card
      className="flex flex-row h-[42rem] w-full overflow-hidden rounded-xl border shadow-sm bg-white"
      style={{ borderColor: BORDER }}
    >
      {/* Sidebar — New chat, search, chat list (Left Side) */}
      <aside
        className="flex w-60 shrink-0 flex-col border-r h-full sm:w-72"
        style={{ backgroundColor: SIDEBAR_BG, borderColor: BORDER }}
      >
        <div className="space-y-3 border-b p-3.5" style={{ borderColor: BORDER }}>
          <Button
            className="w-full font-semibold text-white shadow-sm hover:opacity-90"
            style={{ backgroundColor: ACCENT }}
            onClick={() => {
              if (conversations.length === 0 || activeConversation?.messages.length! > 1) {
                startNewChat();
              }
            }}
          >
            <MessageSquarePlus className="mr-2 size-4" />
            New Chat
          </Button>
          <div className="relative">
            <Search className="absolute left-3 top-1/2 size-4 -translate-y-1/2 text-muted-foreground" />
            <Input
              value={chatSearch}
              onChange={(e) => setChatSearch(e.target.value)}
              placeholder="Search chats…"
              className="h-9 border-[#e2e8f0] bg-white pl-9 text-xs shadow-none"
              aria-label="Search past chats"
            />
          </div>
        </div>

        <div className="min-h-0 flex-1 overflow-y-auto p-2">
          {filteredConversations.length === 0 ? (
            <p className="px-2 py-8 text-center text-xs text-muted-foreground">
              {conversations.length === 0 ? "No chats yet" : "No chats match"}
            </p>
          ) : (
            filteredConversations.map((conv) => (
              <div
                key={conv.id}
                className={`group mb-1 flex items-center justify-between rounded-lg px-3 py-2.5 text-sm transition-colors cursor-pointer ${
                  conv.id === activeConversation?.id ? "bg-white shadow-sm border" : "hover:bg-white/60"
                }`}
                style={conv.id === activeConversation?.id ? { borderColor: BORDER } : { borderColor: "transparent" }}
                onClick={() => selectConversation(conv.id)}
              >
                <span className="min-w-0 flex-1 truncate text-left font-medium text-slate-700">
                  {conv.title}
                </span>
                <button
                  type="button"
                  onClick={(e) => {
                    e.stopPropagation();
                    deleteConversation(conv.id);
                  }}
                  className="hidden shrink-0 text-muted-foreground hover:text-red-600 group-hover:block p-1"
                  aria-label="Delete chat"
                >
                  <Trash2 className="size-3.5" />
                </button>
              </div>
            ))
          )}
        </div>
      </aside>

      {/* Main chat body area (Right Side) */}
      <div className="flex min-w-0 flex-1 flex-col h-full bg-white">
        {/* Header */}
        <div
          className="flex items-center gap-3 border-b px-5 py-3.5 shrink-0"
          style={{ borderColor: BORDER, backgroundColor: SIDEBAR_BG }}
        >
          <AssistantAvatar />
          <div>
            <p className="text-sm font-semibold text-slate-900">SwiftDrop AI Assistant</p>
            <p className="flex items-center gap-1.5 text-xs text-muted-foreground">
              <span className="inline-block size-1.5 rounded-full bg-green-500 animate-pulse" />
              Online • Live tracking & rates
            </p>
          </div>
        </div>

        {/* Message feed / chat body */}
        <div className="min-h-0 flex-1 space-y-5 overflow-y-auto p-5 bg-[#fafbfc]">
          {showEmpty ? (
            <div className="flex h-full flex-col items-center justify-center gap-3 text-center p-6">
              <AssistantAvatar />
              <div>
                <p className="font-semibold text-slate-800 text-base">How can I help you today?</p>
                <p className="mt-1 text-sm text-muted-foreground max-w-md">{ASSISTANT_WELCOME}</p>
              </div>
            </div>
          ) : (
            messages.map((message) => <MessageBubble key={message.id} message={message} />)
          )}
          {sending && (
            <div className="flex gap-3">
              <AssistantAvatar />
              <div
                className="rounded-2xl rounded-bl-md border bg-white px-4 py-3 text-sm text-muted-foreground shadow-sm flex items-center gap-2"
                style={{ borderColor: BORDER }}
              >
                <span className="size-2 rounded-full bg-slate-400 animate-bounce" style={{ animationDelay: "0ms" }} />
                <span className="size-2 rounded-full bg-slate-400 animate-bounce" style={{ animationDelay: "150ms" }} />
                <span className="size-2 rounded-full bg-slate-400 animate-bounce" style={{ animationDelay: "300ms" }} />
              </div>
            </div>
          )}
          <div ref={messagesEndRef} />
        </div>

        {/* Quick action suggestion chips */}
        <div className="flex flex-wrap gap-2 px-5 py-2.5 border-t bg-white shrink-0" style={{ borderColor: BORDER }}>
          {SUGGESTIONS.map(({ icon: Icon, label, prompt }) => (
            <button
              key={label}
              type="button"
              onClick={() => send(prompt)}
              disabled={sending}
              className="inline-flex items-center gap-1.5 rounded-full border px-3 py-1.5 text-xs font-medium text-slate-600 transition-colors hover:bg-orange-50 hover:text-orange-600 hover:border-orange-200 disabled:opacity-50"
              style={{ borderColor: BORDER }}
            >
              <Icon className="size-3.5 text-orange-500" />
              {label}
            </button>
          ))}
        </div>

        {/* Input bar */}
        <form
          className="flex gap-2 border-t p-3.5 bg-white shrink-0"
          style={{ borderColor: BORDER }}
          onSubmit={(e) => {
            e.preventDefault();
            send(input);
          }}
        >
          <Input
            value={input}
            onChange={(e) => setInput(e.target.value)}
            placeholder="Ask about your parcel tracking or delivery fees…"
            disabled={sending}
            className="flex-1 h-10 border-[#cbd5e1] text-sm focus-visible:ring-1 focus-visible:ring-orange-500"
            aria-label="Message the assistant"
          />
          <Button
            type="submit"
            disabled={sending || !input.trim()}
            className="h-10 px-5 font-semibold text-white shadow-sm hover:opacity-90"
            style={{ backgroundColor: ACCENT }}
          >
            <SendHorizonal className="mr-1.5 size-4" />
            Send
          </Button>
        </form>
      </div>
    </Card>
  );
}
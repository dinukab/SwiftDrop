"use client";

import Link from "next/link";
import { useEffect, useId, useRef, useState, type KeyboardEvent } from "react";
import { usePathname, useRouter, useSearchParams } from "next/navigation";
import { AnimatePresence, motion } from "framer-motion";
import { Bot, MessageCircle, SendHorizonal, X } from "lucide-react";
import { cn } from "cn";
import { ApiError, formatApiError } from "@/lib/api";
import { useAuth } from "@/lib/auth-context";
import { sendAssistantMessage } from "@/services/assistant";
import { REDUCED_MOTION_QUERY, useMediaQuery } from "../hero/use-media-query";
import {
  ASSISTANT_COPY,
  MAX_MESSAGE_LENGTH,
  OPEN_CHAT_PARAM,
  SIGN_IN_HREF,
  SUGGESTIONS,
} from "./data";

type ChatMessage = { id: number; role: "user" | "assistant"; text: string };

const FOCUS_RING = "outline-none focus-visible:ring-3 focus-visible:ring-brand";

/** Floating chat launcher and panel in the bottom-right corner of the landing page. */
export function ChatWidget() {
  const router = useRouter();
  const pathname = usePathname();
  const searchParams = useSearchParams();
  const { token, isLoading, isAuthenticated, hasRole } = useAuth();
  const reducedMotion = useMediaQuery(REDUCED_MOTION_QUERY);
  const panelId = useId();
  const titleId = useId();

  // Opens straight away when sign-in returns here with ?chat=open.
  const [open, setOpen] = useState(() => searchParams.get(OPEN_CHAT_PARAM) === "open");
  const [messages, setMessages] = useState<ChatMessage[]>([
    { id: 0, role: "assistant", text: ASSISTANT_COPY.greeting },
  ]);
  const [input, setInput] = useState("");
  const [sending, setSending] = useState(false);
  const nextId = useRef(1);
  const launcherRef = useRef<HTMLButtonElement>(null);
  const inputRef = useRef<HTMLInputElement>(null);
  const panelRef = useRef<HTMLDivElement>(null);
  const endRef = useRef<HTMLDivElement>(null);

  const canChat = isAuthenticated && Boolean(token) && hasRole("CUSTOMER");

  // Drop the one-off ?chat=open flag so a reload or shared link doesn't keep reopening the panel.
  useEffect(() => {
    if (searchParams.get(OPEN_CHAT_PARAM) !== "open") return;
    const params = new URLSearchParams(searchParams.toString());
    params.delete(OPEN_CHAT_PARAM);
    const query = params.toString();
    router.replace(query ? `${pathname}?${query}` : pathname, { scroll: false });
  }, [pathname, router, searchParams]);

  // Moves focus into the panel when it opens.
  useEffect(() => {
    if (!open) return;
    const target = canChat ? inputRef.current : panelRef.current;
    target?.focus();
  }, [open, canChat]);

  // Keeps the newest message in view.
  useEffect(() => {
    endRef.current?.scrollIntoView({ block: "end", behavior: reducedMotion ? "auto" : "smooth" });
  }, [messages, sending, reducedMotion]);

  function close() {
    setOpen(false);
    launcherRef.current?.focus();
  }

  function handlePanelKeyDown(event: KeyboardEvent<HTMLDivElement>) {
    if (event.key === "Escape") {
      event.stopPropagation();
      close();
    }
  }

  async function send(text: string) {
    const trimmed = text.trim();
    if (!trimmed || sending || !token) return;

    setMessages((prev) => [...prev, { id: nextId.current++, role: "user", text: trimmed }]);
    setInput("");
    setSending(true);
    try {
      const { reply } = await sendAssistantMessage(trimmed, token);
      setMessages((prev) => [...prev, { id: nextId.current++, role: "assistant", text: reply }]);
    } catch (err) {
      const text =
        err instanceof ApiError && err.status === 403
          ? ASSISTANT_COPY.forbidden
          : `${ASSISTANT_COPY.errorPrefix} ${formatApiError(err)}`;
      setMessages((prev) => [...prev, { id: nextId.current++, role: "assistant", text }]);
    } finally {
      setSending(false);
      inputRef.current?.focus();
    }
  }

  return (
    <div className="font-body">
      <AnimatePresence>
        {open && (
          <motion.div
            ref={panelRef}
            id={panelId}
            role="dialog"
            aria-modal="false"
            aria-labelledby={titleId}
            tabIndex={-1}
            onKeyDown={handlePanelKeyDown}
            initial={{ opacity: 0, y: reducedMotion ? 0 : 16, scale: reducedMotion ? 1 : 0.98 }}
            animate={{ opacity: 1, y: 0, scale: 1 }}
            exit={{ opacity: 0, y: reducedMotion ? 0 : 16, scale: reducedMotion ? 1 : 0.98 }}
            transition={{ duration: reducedMotion ? 0 : 0.2, ease: "easeOut" }}
            className="fixed inset-x-3 bottom-24 z-40 flex h-[min(36rem,calc(100dvh-8rem))] origin-bottom-right flex-col overflow-hidden rounded-2xl border border-line bg-white shadow-[0_24px_60px_-20px_rgb(10_22_48/0.45)] outline-none sm:inset-x-auto sm:right-6 sm:w-[24rem]"
          >
            <div className="flex items-center gap-3 bg-navy px-4 py-3 text-white">
              <span className="grid size-9 shrink-0 place-items-center rounded-xl bg-brand text-navy">
                <Bot aria-hidden="true" className="size-5" />
              </span>
              <div className="min-w-0 flex-1">
                <h2 id={titleId} className="font-display text-base font-semibold leading-tight">
                  {ASSISTANT_COPY.title}
                </h2>
                <p className="truncate text-xs text-ink-light">{ASSISTANT_COPY.subtitle}</p>
              </div>
              <button
                type="button"
                onClick={close}
                aria-label={ASSISTANT_COPY.launcherClose}
                className={cn("grid size-9 place-items-center rounded-lg text-white/80 hover:bg-white/10 hover:text-white", FOCUS_RING)}
              >
                <X aria-hidden="true" className="size-5" />
              </button>
            </div>

            <div className="min-h-0 flex-1 space-y-3 overflow-y-auto bg-surface px-4 py-4" aria-live="polite">
              {messages.map((message) => (
                <div key={message.id} className={message.role === "user" ? "flex justify-end" : "flex justify-start"}>
                  <p
                    className={cn(
                      "max-w-[85%] whitespace-pre-wrap rounded-2xl px-3.5 py-2.5 text-sm leading-relaxed",
                      message.role === "user"
                        ? "rounded-br-md bg-navy text-white"
                        : "rounded-bl-md border border-line bg-white text-navy"
                    )}
                  >
                    {message.text}
                  </p>
                </div>
              ))}

              {sending && (
                <div className="flex justify-start">
                  <p className="rounded-2xl rounded-bl-md border border-line bg-white px-3.5 py-2.5 text-sm text-ink-muted">
                    {ASSISTANT_COPY.thinking}
                  </p>
                </div>
              )}

              {!isLoading && !canChat && (
                <div className="space-y-3 rounded-2xl border border-line bg-white p-4 text-sm text-navy">
                  {isAuthenticated ? (
                    <p>{ASSISTANT_COPY.notCustomerText}</p>
                  ) : (
                    <>
                      <p className="leading-relaxed">{ASSISTANT_COPY.signedOutText}</p>
                      <div className="flex flex-wrap gap-2">
                        <Link
                          href={SIGN_IN_HREF}
                          className={cn("inline-flex h-10 items-center rounded-xl bg-navy px-4 font-semibold text-white hover:bg-navy-800", FOCUS_RING)}
                        >
                          {ASSISTANT_COPY.signIn}
                        </Link>
                        <Link
                          href="/signup"
                          className={cn("inline-flex h-10 items-center rounded-xl border border-navy/20 px-4 font-semibold text-navy hover:bg-surface", FOCUS_RING)}
                        >
                          {ASSISTANT_COPY.createAccount}
                        </Link>
                      </div>
                    </>
                  )}
                </div>
              )}

              {canChat && messages.length === 1 && !sending && (
                <div className="flex flex-wrap gap-2 pt-1">
                  {SUGGESTIONS.map((suggestion) => (
                    <button
                      key={suggestion}
                      type="button"
                      onClick={() => send(suggestion)}
                      className={cn("rounded-full border border-line bg-white px-3 py-1.5 text-xs font-medium text-navy hover:border-brand/40 hover:text-brand-text", FOCUS_RING)}
                    >
                      {suggestion}
                    </button>
                  ))}
                </div>
              )}
              <div ref={endRef} />
            </div>

            {canChat && (
              <div className="border-t border-line bg-white px-3 pt-3 pb-2">
                <form
                  className="flex items-center gap-2"
                  onSubmit={(event) => {
                    event.preventDefault();
                    send(input);
                  }}
                >
                  <input
                    ref={inputRef}
                    value={input}
                    onChange={(event) => setInput(event.target.value)}
                    placeholder={ASSISTANT_COPY.placeholder}
                    aria-label={ASSISTANT_COPY.inputLabel}
                    maxLength={MAX_MESSAGE_LENGTH}
                    disabled={sending}
                    className="h-11 min-w-0 flex-1 rounded-xl border border-line bg-surface px-3 text-base text-navy placeholder:text-ink-muted/80 focus:border-brand/50 focus:outline-none focus-visible:ring-3 focus-visible:ring-brand/40 disabled:opacity-60 sm:text-sm"
                  />
                  <button
                    type="submit"
                    disabled={sending || !input.trim()}
                    aria-label={ASSISTANT_COPY.send}
                    className={cn("grid size-11 shrink-0 place-items-center rounded-xl bg-brand text-navy transition-colors hover:bg-[#ff7f36] disabled:opacity-50", FOCUS_RING)}
                  >
                    <SendHorizonal aria-hidden="true" className="size-5" />
                  </button>
                </form>
                <p className="mt-2 px-1 text-[11px] leading-snug text-ink-muted">{ASSISTANT_COPY.footnote}</p>
              </div>
            )}
          </motion.div>
        )}
      </AnimatePresence>

      <button
        ref={launcherRef}
        type="button"
        onClick={() => (open ? close() : setOpen(true))}
        aria-expanded={open}
        aria-controls={open ? panelId : undefined}
        aria-label={open ? ASSISTANT_COPY.launcherClose : ASSISTANT_COPY.launcherOpen}
        className={cn(
          "fixed right-4 bottom-4 z-40 grid size-14 place-items-center rounded-full bg-brand text-navy shadow-[0_12px_30px_-8px_rgb(255_106_19/0.6)] transition-transform hover:scale-105 motion-reduce:transition-none motion-reduce:hover:scale-100 sm:right-6 sm:bottom-6",
          "outline-none focus-visible:ring-3 focus-visible:ring-navy focus-visible:ring-offset-2"
        )}
      >
        {open ? <X aria-hidden="true" className="size-6" /> : <MessageCircle aria-hidden="true" className="size-6" />}
      </button>
    </div>
  );
}

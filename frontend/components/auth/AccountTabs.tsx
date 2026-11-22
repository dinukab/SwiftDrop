"use client";

import { useRef, type KeyboardEvent } from "react";
import { cn } from "cn";

export type AccountType = "customer" | "merchant";

export const ACCOUNT_TABS: { id: AccountType; label: string }[] = [
  { id: "customer", label: "Customer" },
  { id: "merchant", label: "Merchant" },
];

export function parseAccountType(value: string | null): AccountType {
  return value?.trim().toLowerCase() === "merchant" ? "merchant" : "customer";
}

export function accountTabId(idPrefix: string, tab: AccountType) {
  return `${idPrefix}-tab-${tab}`;
}

export function accountPanelId(idPrefix: string) {
  return `${idPrefix}-panel`;
}

type AccountTabsProps = {
  /** Keeps tab and panel ids unique per page. */
  idPrefix: string;
  active: AccountType;
  onChange: (tab: AccountType) => void;
  disabled?: boolean;
};

/** WAI-ARIA tabs with a roving tab stop: Tab reaches the active tab, arrow keys move between tabs. */
export function AccountTabs({ idPrefix, active, onChange, disabled }: AccountTabsProps) {
  const tabRefs = useRef<(HTMLButtonElement | null)[]>([]);

  function handleKeyDown(event: KeyboardEvent<HTMLButtonElement>, index: number) {
    const last = ACCOUNT_TABS.length - 1;
    let next: number | null = null;
    if (event.key === "ArrowRight") next = index === last ? 0 : index + 1;
    if (event.key === "ArrowLeft") next = index === 0 ? last : index - 1;
    if (event.key === "Home") next = 0;
    if (event.key === "End") next = last;
    if (next === null) return;

    event.preventDefault();
    onChange(ACCOUNT_TABS[next].id);
    tabRefs.current[next]?.focus();
  }

  return (
    <div
      role="tablist"
      aria-label="Account type"
      className="grid grid-cols-2 gap-1 rounded-xl border border-line bg-surface p-1"
    >
      {ACCOUNT_TABS.map((tab, index) => {
        const selected = tab.id === active;
        return (
          <button
            key={tab.id}
            ref={(element) => {
              tabRefs.current[index] = element;
            }}
            id={accountTabId(idPrefix, tab.id)}
            type="button"
            role="tab"
            aria-selected={selected}
            aria-controls={accountPanelId(idPrefix)}
            tabIndex={selected ? 0 : -1}
            disabled={disabled}
            onClick={() => onChange(tab.id)}
            onKeyDown={(event) => handleKeyDown(event, index)}
            className={cn(
              "h-10 rounded-lg text-sm font-semibold transition-colors outline-none focus-visible:ring-3 focus-visible:ring-brand disabled:opacity-60",
              selected ? "bg-white text-navy shadow-xs" : "text-ink-muted hover:text-navy"
            )}
          >
            {tab.label}
          </button>
        );
      })}
    </div>
  );
}

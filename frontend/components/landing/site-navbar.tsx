"use client";

import Link from "next/link";
import { useCallback, useEffect, useId, useRef, useState, useSyncExternalStore } from "react";
import { ArrowRight, LayoutDashboard, Menu, Package, X } from "lucide-react";
import { cn } from "cn";
import { buttonVariants } from "@/components/ui/button";
import { useAuth } from "@/lib/auth-context";
import { getDashboardPathForRoles } from "@/lib/redirect";

type NavLink = {
  label: string;
  href: string;
  /** Id of the landing-page section the link scrolls to; drives the active highlight. */
  sectionId?: string;
};

/** `light` sits on the page background; `dark` sits over the navy hero. */
type Tone = "light" | "dark";

const NAV_LINKS: NavLink[] = [
  { label: "Services", href: "/#services", sectionId: "services" },
  { label: "How it Works", href: "/#how-it-works", sectionId: "how-it-works" },
  { label: "Pricing", href: "/#pricing", sectionId: "pricing" },
  { label: "Track", href: "/track" },
  { label: "Contact", href: "/#contact", sectionId: "contact" },
];

/** Matches Tailwind's `lg` breakpoint, where the inline links replace the hamburger. */
const DESKTOP_QUERY = "(min-width: 64rem)";

function subscribeToScroll(onChange: () => void) {
  window.addEventListener("scroll", onChange, { passive: true });
  return () => window.removeEventListener("scroll", onChange);
}

function useHasScrolled(threshold = 8) {
  return useSyncExternalStore(
    subscribeToScroll,
    () => window.scrollY > threshold,
    () => false
  );
}

function useActiveSection(ids: string[]) {
  const [active, setActive] = useState<string | null>(null);
  const key = ids.join(",");

  useEffect(() => {
    const sections = key
      .split(",")
      .map((id) => document.getElementById(id))
      .filter((el): el is HTMLElement => el !== null);
    if (sections.length === 0) return;

    // A section counts as active while it crosses a band just below the sticky header.
    const observer = new IntersectionObserver(
      (entries) => {
        const visible = entries.find((entry) => entry.isIntersecting);
        if (visible) setActive(visible.target.id);
      },
      { rootMargin: "-80px 0px -60% 0px" }
    );
    sections.forEach((section) => observer.observe(section));
    return () => observer.disconnect();
  }, [key]);

  return active;
}

function Logo({ tone, onClick }: { tone: Tone; onClick?: () => void }) {
  const dark = tone === "dark";
  return (
    <Link
      href="/"
      onClick={onClick}
      aria-label="SwiftDrop home"
      className="group flex items-center gap-2.5 rounded-lg outline-none focus-visible:ring-3 focus-visible:ring-ring/50"
    >
      
      <span className={cn("text-lg font-extrabold tracking-tight", dark ? "text-white" : "text-foreground")}>
        Swift<span className={dark ? "text-brand" : "text-primary"}>Drop</span>
      </span>
    </Link>
  );
}

function AuthActions({
  layout,
  tone,
  onNavigate,
}: {
  layout: "inline" | "stacked";
  tone: Tone;
  onNavigate?: () => void;
}) {
  const { user, isAuthenticated, isLoading } = useAuth();
  const stacked = layout === "stacked";
  const dark = tone === "dark";
  const size = stacked ? "h-11 w-full text-base" : "h-9 px-4";
  // On navy the main action is white with navy text; on the light page it is the brand orange.
  const primary = dark
    ? "bg-white text-navy shadow-sm hover:bg-white/90"
    : "shadow-sm shadow-primary/25";

  if (isLoading) {
    const skeleton = cn("animate-pulse rounded-lg", dark ? "bg-white/10" : "bg-muted");
    return (
      <div className={cn("flex gap-2", stacked && "flex-col")} aria-hidden="true">
        <div className={cn(skeleton, stacked ? "h-11 w-full" : "h-9 w-[4.5rem]")} />
        <div className={cn(skeleton, stacked ? "h-11 w-full" : "h-9 w-24")} />
      </div>
    );
  }

  if (isAuthenticated && user) {
    return (
      <Link
        href={getDashboardPathForRoles(user.roles)}
        onClick={onNavigate}
        className={cn(buttonVariants({ variant: "default" }), size, "font-semibold", primary)}
      >
        <LayoutDashboard aria-hidden="true" />
        Dashboard
      </Link>
    );
  }

  return (
    <div className={cn("flex items-center gap-2", stacked && "flex-col-reverse items-stretch")}>
      <Link
        href="/login"
        onClick={onNavigate}
        className={cn(
          buttonVariants({ variant: stacked ? "outline" : "ghost" }),
          size,
          "font-semibold",
          dark
            ? "border-white/20 bg-transparent text-white hover:bg-white/10 hover:text-white dark:bg-transparent"
            : "text-foreground",
          dark && !stacked && "border-transparent"
        )}
      >
        Login
      </Link>
      <Link
        href="/signup"
        onClick={onNavigate}
        className={cn(buttonVariants({ variant: "default" }), size, "font-semibold", primary)}
      >
        Sign Up
        <ArrowRight
          aria-hidden="true"
          className="transition-transform duration-200 group-hover/button:translate-x-0.5"
        />
      </Link>
    </div>
  );
}

export function SiteNavbar({ tone = "light" }: { tone?: Tone }) {
  const [menuOpen, setMenuOpen] = useState(false);
  const scrolled = useHasScrolled();
  const activeSection = useActiveSection(
    NAV_LINKS.flatMap((link) => (link.sectionId ? [link.sectionId] : []))
  );
  const toggleRef = useRef<HTMLButtonElement>(null);
  const menuId = useId();
  const dark = tone === "dark";

  const closeMenu = useCallback(() => setMenuOpen(false), []);

  useEffect(() => {
    if (!menuOpen) return;

    const onKeyDown = (event: KeyboardEvent) => {
      if (event.key === "Escape") {
        setMenuOpen(false);
        toggleRef.current?.focus();
      }
    };
    const desktop = window.matchMedia(DESKTOP_QUERY);
    const onBreakpoint = (event: MediaQueryListEvent) => {
      if (event.matches) setMenuOpen(false);
    };

    const previousOverflow = document.body.style.overflow;
    document.body.style.overflow = "hidden";
    document.addEventListener("keydown", onKeyDown);
    desktop.addEventListener("change", onBreakpoint);

    return () => {
      document.body.style.overflow = previousOverflow;
      document.removeEventListener("keydown", onKeyDown);
      desktop.removeEventListener("change", onBreakpoint);
    };
  }, [menuOpen]);

  const solid = scrolled || menuOpen;

  return (
    <header
      className={cn(
        "sticky top-0 z-50 w-full border-b transition-[background-color,border-color,box-shadow] duration-300",
        dark
          ? solid
            ? "border-white/10 bg-navy/85 shadow-[0_1px_16px_-4px_rgb(0_0_0/0.4)] backdrop-blur-lg"
            : "border-transparent bg-transparent"
          : solid
            ? "border-border bg-background/85 shadow-[0_1px_12px_-4px_rgb(25_24_23/0.08)] backdrop-blur-lg supports-[backdrop-filter]:bg-background/70"
            : "border-transparent bg-background"
      )}
    >
      <div className="mx-auto flex h-16 max-w-7xl items-center justify-between gap-6 px-4 sm:px-6 lg:px-8">
        <Logo tone={tone} onClick={closeMenu} />

        <nav aria-label="Main" className="hidden lg:block">
          <ul className="flex items-center gap-1">
            {NAV_LINKS.map((link) => {
              const isActive = link.sectionId !== undefined && link.sectionId === activeSection;
              return (
                <li key={link.href}>
                  <Link
                    href={link.href}
                    aria-current={isActive ? "true" : undefined}
                    data-active={isActive}
                    className={cn(
                      "relative inline-flex h-9 items-center rounded-lg px-3.5 text-sm font-medium transition-colors outline-none focus-visible:ring-3 focus-visible:ring-ring/50",
                      "after:absolute after:inset-x-3.5 after:-bottom-[13px] after:h-0.5 after:origin-center after:scale-x-0 after:rounded-full after:transition-transform after:duration-300",
                      "data-[active=true]:after:scale-x-100",
                      dark
                        ? "text-white after:bg-brand hover:bg-white/[0.06] hover:text-white data-[active=true]:text-white"
                        : "text-muted-foreground after:bg-primary hover:bg-foreground/[0.04] hover:text-foreground data-[active=true]:text-foreground"
                    )}
                  >
                    {link.label}
                  </Link>
                </li>
              );
            })}
          </ul>
        </nav>

        <div className="flex items-center gap-2">
          <div className="hidden sm:block">
            <AuthActions layout="inline" tone={tone} />
          </div>
          <button
            ref={toggleRef}
            type="button"
            onClick={() => setMenuOpen((open) => !open)}
            aria-expanded={menuOpen}
            aria-controls={menuId}
            aria-label={menuOpen ? "Close menu" : "Open menu"}
            className={cn(
              "relative grid size-10 place-items-center rounded-lg transition-colors outline-none focus-visible:ring-3 focus-visible:ring-ring/50 lg:hidden",
              dark ? "text-white hover:bg-white/10" : "text-foreground hover:bg-muted"
            )}
          >
            <Menu
              aria-hidden="true"
              className={cn(
                "absolute size-5 transition-all duration-200",
                menuOpen ? "rotate-90 scale-50 opacity-0" : "rotate-0 scale-100 opacity-100"
              )}
            />
            <X
              aria-hidden="true"
              className={cn(
                "absolute size-5 transition-all duration-200",
                menuOpen ? "rotate-0 scale-100 opacity-100" : "-rotate-90 scale-50 opacity-0"
              )}
            />
          </button>
        </div>
      </div>

      {menuOpen && (
        <>
          <div
            aria-hidden="true"
            onClick={closeMenu}
            className={cn(
              "fixed inset-x-0 top-16 bottom-0 backdrop-blur-[2px] animate-in fade-in duration-200 lg:hidden",
              dark ? "bg-black/40" : "bg-foreground/20"
            )}
          />
          <div
            id={menuId}
            className={cn(
              "absolute inset-x-0 top-full max-h-[calc(100dvh-4rem)] overflow-y-auto border-b shadow-xl animate-in fade-in slide-in-from-top-2 duration-200 lg:hidden",
              dark ? "border-white/10 bg-navy" : "border-border bg-background"
            )}
          >
            <nav aria-label="Mobile" className="mx-auto max-w-7xl px-4 pt-3 pb-6 sm:px-6">
              <ul className="flex flex-col">
                {NAV_LINKS.map((link) => {
                  const isActive = link.sectionId !== undefined && link.sectionId === activeSection;
                  return (
                    <li
                      key={link.href}
                      className={cn("border-b last:border-b-0", dark ? "border-white/10" : "border-border/70")}
                    >
                      <Link
                        href={link.href}
                        onClick={closeMenu}
                        aria-current={isActive ? "true" : undefined}
                        className={cn(
                          "flex min-h-12 items-center justify-between rounded-lg px-2 text-base font-semibold transition-colors outline-none focus-visible:ring-3 focus-visible:ring-ring/50",
                          dark
                            ? cn("hover:text-brand", isActive ? "text-brand" : "text-white")
                            : cn("hover:text-primary", isActive ? "text-primary" : "text-foreground")
                        )}
                      >
                        {link.label}
                        <ArrowRight
                          aria-hidden="true"
                          className={cn("size-4", dark ? "text-white/40" : "text-muted-foreground")}
                        />
                      </Link>
                    </li>
                  );
                })}
              </ul>
              <div className="mt-5">
                <AuthActions layout="stacked" tone={tone} onNavigate={closeMenu} />
              </div>
            </nav>
          </div>
        </>
      )}
    </header>
  );
}

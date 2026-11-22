import Link from "next/link";
import { ArrowUp } from "lucide-react";
import { TrackingInput } from "../hero/TrackingInput";
import { FOOTER_COLUMNS, FOOTER_COPY } from "./data";

const LINK_CLASS =
  "rounded-sm text-sm text-ink-light transition-colors outline-none hover:text-white focus-visible:ring-3 focus-visible:ring-brand";

export function Footer() {
  // Rendered at build time for this static page, so the year updates with each deployment.
  const year = new Date().getFullYear();

  return (
    <footer aria-labelledby="footer-heading" className="bg-navy font-body text-ink-light">
      <h2 id="footer-heading" className="sr-only">
        Footer
      </h2>

      <div className="mx-auto max-w-7xl px-4 pt-16 pb-8 sm:px-6 lg:px-8 lg:pt-20">
        <div className="grid gap-12 lg:grid-cols-[minmax(0,1.2fr)_minmax(0,2fr)] lg:gap-16">
          <div>
            <Link
              href="/"
              aria-label={`${FOOTER_COPY.brand} home`}
              className="inline-block rounded-lg text-2xl font-extrabold tracking-tight text-white outline-none focus-visible:ring-3 focus-visible:ring-brand"
            >
              Swift<span className="text-brand">Drop</span>
            </Link>
            <p className="mt-4 max-w-sm leading-relaxed">{FOOTER_COPY.tagline}</p>

            <p className="mt-8 font-display text-sm font-semibold text-white">{FOOTER_COPY.trackingHeading}</p>
            <TrackingInput className="mt-3 max-w-md" />
          </div>

          <nav aria-label="Footer" className="grid grid-cols-2 gap-8 sm:grid-cols-3">
            {FOOTER_COLUMNS.map((column) => (
              <div key={column.title}>
                <h3 className="font-display text-sm font-semibold tracking-wide text-white">{column.title}</h3>
                <ul className="mt-4 space-y-3">
                  {column.links.map((link) => (
                    <li key={link.label}>
                      <Link href={link.href} className={LINK_CLASS}>
                        {link.label}
                      </Link>
                    </li>
                  ))}
                </ul>
              </div>
            ))}
          </nav>
        </div>

        <div className="mt-14 flex flex-col gap-4 border-t border-white/10 pt-6 text-sm sm:flex-row sm:items-center sm:justify-between">
          <p>
            &copy; {year} {FOOTER_COPY.brand}. {FOOTER_COPY.rights}
          </p>
          <div className="flex items-center gap-6">
            <Link href={FOOTER_COPY.staffPortal.href} className={LINK_CLASS}>
              {FOOTER_COPY.staffPortal.label}
            </Link>
            {/* A bare "#" scrolls to the top of the page in every browser. */}
            <a href="#" className={`${LINK_CLASS} inline-flex items-center gap-1.5`}>
              {FOOTER_COPY.backToTop}
              <ArrowUp aria-hidden="true" className="size-4" />
            </a>
          </div>
        </div>
      </div>
    </footer>
  );
}

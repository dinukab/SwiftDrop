"use client";

import { useId, useState, type FormEvent } from "react";
import { useRouter } from "next/navigation";
import { AlertCircle, ArrowRight, Package } from "lucide-react";
import { cn } from "cn";
import { TRACKING_FORM, TRACKING_NUMBER_PATTERN } from "./data";

type TrackingInputProps = {
  /** Receives the normalized tracking number. Without it, the form opens the tracking page. */
  onTrack?: (trackingNumber: string) => void;
  className?: string;
};

/** Accepts lowercase and the spaces or dashes people add when copying a number. */
function normalize(value: string): string {
  return value.replace(/[\s-]/g, "").toUpperCase();
}

export function TrackingInput({ onTrack, className }: TrackingInputProps) {
  const router = useRouter();
  const inputId = useId();
  const errorId = useId();
  const [value, setValue] = useState("");
  const [error, setError] = useState<string | null>(null);

  function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    const trackingNumber = normalize(value);

    if (trackingNumber === "") {
      setError(TRACKING_FORM.errorEmpty);
      return;
    }
    if (!TRACKING_NUMBER_PATTERN.test(trackingNumber)) {
      setError(TRACKING_FORM.errorFormat);
      return;
    }

    setError(null);
    if (onTrack) {
      onTrack(trackingNumber);
    } else {
      router.push(`/track?code=${encodeURIComponent(trackingNumber)}`);
    }
  }

  return (
    <div className={className}>
      <form
        onSubmit={handleSubmit}
        noValidate
        className={cn(
          "flex flex-col gap-2 rounded-xl bg-white p-1 shadow-[0_18px_40px_-12px_rgb(0_0_0/0.45)] ring-brand transition-shadow sm:flex-row",
          "has-[input:focus-visible]:ring-3",
          error && "ring-3 ring-status-failed"
        )}
      >
        <div className="relative flex flex-1 items-center">
          <Package aria-hidden="true" className="pointer-events-none absolute left-4 size-5 text-ink-muted" />
          <label htmlFor={inputId} className="sr-only">
            {TRACKING_FORM.label}
          </label>
          <input
            id={inputId}
            name="code"
            type="text"
            value={value}
            onChange={(event) => {
              setValue(event.target.value);
              if (error) setError(null);
            }}
            placeholder={TRACKING_FORM.placeholder}
            autoComplete="off"
            autoCapitalize="characters"
            spellCheck={false}
            aria-invalid={error ? true : undefined}
            aria-describedby={error ? errorId : undefined}
            className="h-[52px] w-full rounded-xl bg-transparent pr-3 pl-12 text-base font-semibold tracking-wide text-navy uppercase outline-none placeholder:font-medium placeholder:text-ink-muted/80"
          />
        </div>
        <button
          type="submit"
          className="group inline-flex min-h-[52px] items-center justify-center gap-2 rounded-xl bg-brand px-6 text-base font-semibold text-navy transition-colors outline-none hover:bg-[#ff7f36] focus-visible:ring-3 focus-visible:ring-navy focus-visible:ring-offset-2 active:translate-y-px"
        >
          {TRACKING_FORM.submit}
          <ArrowRight aria-hidden="true" className="size-4 transition-transform group-hover:translate-x-0.5" />
        </button>
      </form>

      {error && (
        <p
          id={errorId}
          role="alert"
          className="mt-3 flex items-start gap-2 text-sm font-medium text-red-200"
        >
          <AlertCircle aria-hidden="true" className="mt-0.5 size-4 shrink-0 text-red-300" />
          {error}
        </p>
      )}
    </div>
  );
}

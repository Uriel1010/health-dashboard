import { useEffect, useRef } from "react";

import { formatDateTime } from "../format";
import type { SnapshotDetailResponse } from "../types";

type RawSnapshotModalProps = {
  detail: SnapshotDetailResponse | null;
  loading: boolean;
  error: string | null;
  onClose: () => void;
};

const focusableSelector = 'button, [href], input, select, textarea, [tabindex]:not([tabindex="-1"])';

export function RawSnapshotModal({ detail, loading, error, onClose }: RawSnapshotModalProps) {
  const dialogRef = useRef<HTMLDivElement | null>(null);
  const closeButtonRef = useRef<HTMLButtonElement | null>(null);
  const previousFocusRef = useRef<HTMLElement | null>(null);

  useEffect(() => {
    previousFocusRef.current = document.activeElement instanceof HTMLElement ? document.activeElement : null;
    closeButtonRef.current?.focus();

    function onKeyDown(event: KeyboardEvent) {
      if (event.key === "Escape") {
        onClose();
        return;
      }
      if (event.key !== "Tab" || !dialogRef.current) return;

      const focusable = Array.from(dialogRef.current.querySelectorAll<HTMLElement>(focusableSelector)).filter(
        (element) => !element.hasAttribute("disabled"),
      );
      if (!focusable.length) return;
      const first = focusable[0];
      const last = focusable[focusable.length - 1];

      if (event.shiftKey && document.activeElement === first) {
        event.preventDefault();
        last.focus();
      } else if (!event.shiftKey && document.activeElement === last) {
        event.preventDefault();
        first.focus();
      }
    }

    window.addEventListener("keydown", onKeyDown);
    return () => {
      window.removeEventListener("keydown", onKeyDown);
      previousFocusRef.current?.focus();
    };
  }, [onClose]);

  return (
    <div
      className="fixed inset-0 z-50 flex items-center justify-center bg-slate-950/75 p-2 sm:p-4"
      role="presentation"
      onMouseDown={(event) => {
        if (event.target === event.currentTarget) onClose();
      }}
    >
      <div
        ref={dialogRef}
        className="max-h-[94vh] w-full max-w-4xl overflow-hidden rounded-lg border border-app-border bg-app-surface shadow-soft"
        role="dialog"
        aria-modal="true"
        aria-labelledby="raw-modal-title"
        aria-describedby="raw-modal-warning"
      >
        <div className="flex flex-col gap-4 border-b border-app-border p-4 sm:flex-row sm:items-start sm:justify-between">
          <div>
            <h2 id="raw-modal-title" className="text-xl font-semibold text-app-text">Raw snapshot</h2>
            <p id="raw-modal-warning" className="mt-2 rounded-lg border border-amber-500/30 bg-amber-500/10 p-3 text-sm leading-6 text-amber-800 dark:text-amber-200">
              Raw snapshot may contain sensitive health data. Keep this dashboard private/LAN-only unless authentication or Cloudflare Access is added.
            </p>
          </div>
          <button
            ref={closeButtonRef}
            type="button"
            onClick={onClose}
            className="self-start rounded-lg border border-app-border px-3 py-2 text-sm font-semibold text-app-text outline-none transition hover:border-app-accent focus-visible:ring-2 focus-visible:ring-app-accent/40"
          >
            Close
          </button>
        </div>
        <div className="max-h-[70vh] overflow-auto p-3 sm:p-4">
          <div aria-live="polite">
            {loading ? <p className="text-app-muted">Loading snapshot...</p> : null}
            {error ? <p className="text-rose-700 dark:text-rose-300">{error}</p> : null}
          </div>
          {detail ? (
            <>
              <div className="mb-4 grid gap-3 text-sm text-app-muted sm:grid-cols-2">
                <p>Device: <span className="font-medium text-app-text">{detail.device.deviceName ?? detail.device.deviceId}</span></p>
                <p>Captured: <span dir="ltr" className="font-medium text-app-text">{formatDateTime(detail.snapshot.capturedAt)}</span></p>
              </div>
              <pre className="max-h-[56vh] overflow-auto rounded-lg border border-app-border bg-slate-950 p-3 text-xs leading-5 text-slate-100 sm:p-4">
                {JSON.stringify(detail, null, 2)}
              </pre>
            </>
          ) : null}
        </div>
      </div>
    </div>
  );
}

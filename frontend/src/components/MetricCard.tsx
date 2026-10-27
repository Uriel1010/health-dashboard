import { formatDateTime } from "../format";
import type { DataStatus } from "../status";
import { StatusBadge } from "./StatusBadge";

type MetricCardProps = {
  label: string;
  value: string;
  unit?: string;
  accent: string;
  status: DataStatus;
  timeRange: string;
  explanation: string;
  capturedAt?: string | null;
};

export function MetricCard({ label, value, unit, accent, status, timeRange, explanation, capturedAt }: MetricCardProps) {
  const showUnit = unit && status === "OK";
  return (
    <section
      className="rounded-lg border border-app-border bg-app-surface p-4 shadow-soft focus-within:border-app-accent"
      aria-label={`${label}: ${value}${showUnit ? ` ${unit}` : ""}. Status: ${status}. ${timeRange}. ${explanation}`}
    >
      <div className="flex items-start justify-between gap-3">
        <div className="min-w-0">
          <p className="text-sm font-medium text-app-muted">{label}</p>
          <div className="mt-3 flex min-h-10 items-baseline gap-2">
            <p className="break-words text-2xl font-semibold tracking-normal text-app-text sm:text-3xl">{value}</p>
            {showUnit ? <span className="text-sm font-medium text-app-muted">{unit}</span> : null}
          </div>
        </div>
        <span className={`h-3 w-3 shrink-0 rounded-full ${accent}`} aria-hidden="true" />
      </div>
      <div className="mt-4 flex flex-wrap items-center gap-2">
        <StatusBadge status={status} />
        <span className="rounded-full border border-app-border bg-app-elevated px-2.5 py-1 text-xs font-medium text-app-muted">{timeRange}</span>
      </div>
      <p className="mt-3 text-sm leading-6 text-app-muted">{explanation}</p>
      <p className="mt-3 text-xs text-app-muted">Captured <span dir="ltr">{formatDateTime(capturedAt)}</span></p>
    </section>
  );
}

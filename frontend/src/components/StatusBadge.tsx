import type { DataStatus, FreshnessStatus } from "../status";

type StatusBadgeProps = {
  status: DataStatus | FreshnessStatus;
};

const statusClasses: Record<string, string> = {
  OK: "border-emerald-500/30 bg-emerald-500/10 text-emerald-700 dark:text-emerald-300",
  Fresh: "border-emerald-500/30 bg-emerald-500/10 text-emerald-700 dark:text-emerald-300",
  Delayed: "border-amber-500/30 bg-amber-500/10 text-amber-700 dark:text-amber-300",
  Stale: "border-rose-500/30 bg-rose-500/10 text-rose-700 dark:text-rose-300",
  Error: "border-rose-500/30 bg-rose-500/10 text-rose-700 dark:text-rose-300",
  "No data": "border-app-border bg-app-elevated text-app-muted",
  "Permission missing": "border-amber-500/30 bg-amber-500/10 text-amber-700 dark:text-amber-300",
  "Not synced yet": "border-app-border bg-app-elevated text-app-muted",
  "No sync": "border-app-border bg-app-elevated text-app-muted",
};

export function StatusBadge({ status }: StatusBadgeProps) {
  return (
    <span className={`inline-flex items-center rounded-full border px-2.5 py-1 text-xs font-semibold ${statusClasses[status]}`}>
      {status}
    </span>
  );
}

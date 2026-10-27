import { formatDateTime } from "../format";
import type { FreshnessStatus } from "../status";
import { StatusBadge } from "./StatusBadge";

type SyncStatusProps = {
  receivedAt?: string | null;
  capturedAt?: string | null;
  deviceName?: string | null;
  deviceId?: string | null;
  timezone?: string | null;
  freshness: FreshnessStatus;
  loading?: boolean;
};

export function SyncStatus({ receivedAt, capturedAt, deviceName, deviceId, timezone, freshness, loading }: SyncStatusProps) {
  const age = receivedAt ? relativeTime(receivedAt) : "No sync yet";
  return (
    <section className="min-w-0 rounded-xl border border-app-border bg-app-elevated p-4 shadow-soft sm:p-5" aria-labelledby="dashboard-title">
      <div className="flex flex-col gap-4 lg:flex-row lg:items-start lg:justify-between">
        <div>
          <p className="text-sm font-medium uppercase tracking-[0.18em] text-app-muted">Sync status</p>
          <h1 id="dashboard-title" className="mt-2 text-2xl font-semibold text-app-text sm:text-3xl">Health Dashboard</h1>
          <p className="mt-2 text-sm text-app-muted">
            {deviceName ?? "Waiting for your first Android snapshot"}
            {deviceId ? <span className="mt-1 block break-all font-mono text-xs sm:ml-2 sm:mt-0 sm:inline-block" aria-label={`Device id ${deviceId}`}>{deviceId}</span> : null}
          </p>
        </div>
        <div className="grid gap-3 sm:grid-cols-2 lg:min-w-[460px]">
          <div className="rounded-lg border border-app-border bg-app-surface px-4 py-3">
            <p className="text-sm font-medium text-app-muted">Latest sync</p>
            <div className="mt-2 flex flex-wrap items-center gap-2">
              <p className="text-xl font-semibold text-app-text">{loading ? "Refreshing..." : age}</p>
              <StatusBadge status={freshness} />
            </div>
          </div>
          <div className="rounded-lg border border-app-border bg-app-surface px-4 py-3">
            <p className="text-sm font-medium text-app-muted">Timezone</p>
            <p className="mt-2 text-xl font-semibold text-app-text">{timezone ?? "Not available"}</p>
          </div>
        </div>
      </div>
      <dl className="mt-5 grid gap-3 text-sm text-app-muted sm:grid-cols-2">
        <div><dt className="inline">Captured: </dt><dd dir="ltr" className="inline font-medium text-app-text">{formatDateTime(capturedAt)}</dd></div>
        <div><dt className="inline">Received: </dt><dd dir="ltr" className="inline font-medium text-app-text">{formatDateTime(receivedAt)}</dd></div>
      </dl>
    </section>
  );
}

function relativeTime(value: string) {
  const deltaSeconds = Math.round((Date.now() - new Date(value).getTime()) / 1000);
  const abs = Math.abs(deltaSeconds);
  if (abs < 60) return "just now";
  if (abs < 3600) return `${Math.round(abs / 60)} min ago`;
  if (abs < 86400) return `${Math.round(abs / 3600)} hr ago`;
  return `${Math.round(abs / 86400)} days ago`;
}

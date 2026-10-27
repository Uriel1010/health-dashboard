import { Fragment } from "react";

import { formatDateTime } from "../format";
import { getFreshness, getMetricStatus } from "../status";
import type { RecentSnapshot } from "../types";
import { StatusBadge } from "./StatusBadge";

type HistoryTableProps = {
  snapshots: RecentSnapshot[];
  expandedId: number | null;
  onToggle: (id: number) => void;
  onViewRaw: (id: number) => void;
};

export function HistoryTable({ snapshots, expandedId, onToggle, onViewRaw }: HistoryTableProps) {
  return (
    <section className="min-w-0 rounded-xl border border-app-border bg-app-surface p-4 shadow-soft" aria-labelledby="recent-syncs-title">
      <h2 id="recent-syncs-title" className="text-lg font-semibold text-app-text">Recent sync history</h2>

      <div className="mt-4 space-y-3 md:hidden">
        {snapshots.length ? snapshots.map((snapshot) => {
          const expanded = expandedId === snapshot.id;
          return (
            <article key={snapshot.id} className="overflow-hidden rounded-xl border border-app-border bg-app-elevated">
              <div className="p-4">
                <div className="flex items-start justify-between gap-3">
                  <div className="min-w-0">
                    <p className="text-[10px] font-bold uppercase tracking-[0.16em] text-app-muted">Captured</p>
                    <p dir="ltr" className="mt-1 text-sm font-semibold text-app-text">{formatDateTime(snapshot.capturedAt)}</p>
                    <p dir="ltr" className="mt-1 text-xs text-app-muted">Received {formatDateTime(snapshot.receivedAt)}</p>
                  </div>
                  <StatusBadge status={getSnapshotStatus(snapshot)} />
                </div>

                <dl className="mt-4 grid grid-cols-3 gap-2">
                  <MobileMetric label="Steps" value={formatValue(snapshot.stepsToday)} />
                  <MobileMetric label="Distance" value={formatValue(snapshot.distanceKmToday, " km", 1)} />
                  <MobileMetric label="Calories" value={formatValue(snapshot.totalCaloriesKcalToday, " kcal")} />
                </dl>

                <div className="mt-4 flex items-center justify-between gap-3 border-t border-app-border pt-3">
                  <div className="flex items-center gap-2 text-xs text-app-muted">
                    <span>Freshness</span>
                    <StatusBadge status={getFreshness(snapshot.receivedAt)} />
                  </div>
                  <button
                    type="button"
                    onClick={() => onToggle(snapshot.id)}
                    className="min-h-10 rounded-lg border border-app-border px-3 py-2 text-xs font-semibold text-app-text outline-none transition hover:border-app-accent focus-visible:ring-2 focus-visible:ring-app-accent/40"
                    aria-expanded={expanded}
                  >
                    {expanded ? "Hide details" : "Details"}
                  </button>
                </div>
              </div>

              {expanded ? (
                <div className="space-y-3 border-t border-app-border bg-app-surface p-4">
                  <DetailBlock title="Permissions" value={summarizeJson(snapshot.permissions)} />
                  <DetailBlock title="Errors" value={summarizeJson(snapshot.errors)} />
                  <DetailBlock title="Recent exercises" value={summarizeExercises(snapshot.recentExercises)} />
                  <button
                    type="button"
                    onClick={() => onViewRaw(snapshot.id)}
                    className="min-h-11 w-full rounded-lg bg-app-accent px-4 py-2 text-sm font-semibold text-white outline-none transition hover:opacity-90 focus-visible:ring-2 focus-visible:ring-app-accent/40"
                  >
                    View raw snapshot
                  </button>
                </div>
              ) : null}
            </article>
          );
        }) : <p className="rounded-xl border border-dashed border-app-border px-4 py-8 text-center text-sm text-app-muted">No sync history for this device and date range yet</p>}
      </div>

      <div className="mt-4 hidden overflow-x-auto md:block" role="region" aria-label="Scrollable recent sync table" tabIndex={0}>
        <table className="min-w-[920px] text-left text-sm lg:min-w-full">
          <caption className="sr-only">Recent health snapshots for the selected device</caption>
          <thead className="text-app-muted">
            <tr>
              <th className="pb-3 font-medium">Captured</th>
              <th className="pb-3 font-medium">Received</th>
              <th className="pb-3 font-medium">Freshness</th>
              <th className="pb-3 text-right font-medium">Steps</th>
              <th className="pb-3 text-right font-medium">Distance</th>
              <th className="pb-3 text-right font-medium">Calories</th>
              <th className="pb-3 font-medium">Status</th>
              <th className="pb-3 text-right font-medium">Details</th>
            </tr>
          </thead>
          <tbody className="divide-y divide-app-border">
            {snapshots.length ? snapshots.map((snapshot) => {
              const expanded = expandedId === snapshot.id;
              const status = getSnapshotStatus(snapshot);
              return (
                <Fragment key={snapshot.id}>
                  <tr>
                    <td dir="ltr" className="py-3 text-app-text">{formatDateTime(snapshot.capturedAt)}</td>
                    <td dir="ltr" className="py-3 text-app-muted">{formatDateTime(snapshot.receivedAt)}</td>
                    <td className="py-3"><StatusBadge status={getFreshness(snapshot.receivedAt)} /></td>
                    <td className="py-3 text-right font-medium text-app-text">{formatValue(snapshot.stepsToday)}</td>
                    <td className="py-3 text-right font-medium text-app-text">{formatValue(snapshot.distanceKmToday, " km", 1)}</td>
                    <td className="py-3 text-right font-medium text-app-text">{formatValue(snapshot.totalCaloriesKcalToday, " kcal")}</td>
                    <td className="py-3"><StatusBadge status={status} /></td>
                    <td className="py-3 text-right">
                      <button
                        type="button"
                        onClick={() => onToggle(snapshot.id)}
                        className="rounded-lg border border-app-border px-3 py-1.5 text-sm font-medium text-app-text outline-none transition hover:border-app-accent focus-visible:border-app-accent focus-visible:ring-2 focus-visible:ring-app-accent/40"
                        aria-expanded={expanded}
                        aria-label={`${expanded ? "Collapse" : "Expand"} snapshot captured ${formatDateTime(snapshot.capturedAt)}`}
                      >
                        {expanded ? "Collapse" : "Expand"}
                      </button>
                    </td>
                  </tr>
                  {expanded ? (
                    <tr key={`${snapshot.id}-details`}>
                      <td colSpan={8} className="bg-app-elevated/60 px-4 py-4">
                        <div className="grid gap-4 md:grid-cols-3">
                          <DetailBlock title="Permissions" value={summarizeJson(snapshot.permissions)} />
                          <DetailBlock title="Errors" value={summarizeJson(snapshot.errors)} />
                          <DetailBlock title="Recent exercises" value={summarizeExercises(snapshot.recentExercises)} />
                        </div>
                        <button
                          type="button"
                          onClick={() => onViewRaw(snapshot.id)}
                          className="mt-4 rounded-lg bg-app-accent px-4 py-2 text-sm font-semibold text-white outline-none transition hover:opacity-90 focus-visible:ring-2 focus-visible:ring-app-accent/40"
                         aria-label={`View raw snapshot ${snapshot.id}`}>
                          View raw snapshot
                        </button>
                      </td>
                    </tr>
                  ) : null}
                </Fragment>
              );
            }) : (
              <tr>
                <td colSpan={8} className="py-6 text-center text-app-muted">No sync history for this device and date range yet</td>
              </tr>
            )}
          </tbody>
        </table>
      </div>
    </section>
  );
}

function MobileMetric({ label, value }: { label: string; value: string }) {
  return (
    <div className="min-w-0 rounded-lg border border-app-border bg-app-surface px-2 py-3 text-center">
      <dt className="truncate text-[10px] font-bold uppercase tracking-wide text-app-muted">{label}</dt>
      <dd className="mt-1 truncate text-xs font-semibold text-app-text">{value}</dd>
    </div>
  );
}

function DetailBlock({ title, value }: { title: string; value: string }) {
  return (
    <div className="rounded-lg border border-app-border bg-app-surface p-3">
      <p className="text-xs font-semibold uppercase tracking-wide text-app-muted">{title}</p>
      <p className="mt-2 text-sm text-app-text">{value}</p>
    </div>
  );
}

function getSnapshotStatus(snapshot: RecentSnapshot) {
  if (Array.isArray(snapshot.errors) && snapshot.errors.length > 0) return "Error";
  return getMetricStatus(snapshot, "stepsToday", typeof snapshot.stepsToday === "number");
}

function formatValue(value: number | null, unit = "", digits = 0) {
  if (typeof value !== "number") return "No data";
  return `${new Intl.NumberFormat("en-US", { maximumFractionDigits: digits }).format(value)}${unit}`;
}

function summarizeJson(value: unknown) {
  if (Array.isArray(value)) return value.length ? `${value.length} item(s)` : "None";
  if (value && typeof value === "object") return Object.keys(value).length ? `${Object.keys(value).length} field(s)` : "None";
  if (typeof value === "string") return value;
  return "None";
}

function summarizeExercises(value: unknown) {
  if (!Array.isArray(value) || value.length === 0) return "No recent exercises";
  return value
    .slice(0, 3)
    .map((item) => {
      if (!item || typeof item !== "object") return "Exercise";
      const record = item as Record<string, unknown>;
      const title = typeof record.title === "string" ? record.title : "Exercise";
      const minutes = typeof record.durationMinutes === "number" ? `, ${Math.round(record.durationMinutes)} min` : "";
      return `${title}${minutes}`;
    })
    .join("; ");
}

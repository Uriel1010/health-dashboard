export function EmptyState() {
  return (
    <section className="rounded-lg border border-dashed border-app-border bg-app-surface p-6 shadow-soft">
      <h2 className="text-xl font-semibold text-app-text">No snapshots yet</h2>
      <p className="mt-2 max-w-2xl text-sm leading-6 text-app-muted">
        Send a test snapshot from your LAN machine or Android sender to populate the dashboard.
      </p>
      <pre className="mt-5 overflow-x-auto rounded-lg border border-app-border bg-slate-950 p-4 text-xs leading-5 text-slate-100">
{`curl -sS -X POST http://localhost:8015/api/v1/ingest/health-snapshot \\
  -H "Authorization: Bearer \${INGEST_API_TOKEN}" \\
  -H "Content-Type: application/json" \\
  -d '{"deviceId":"pixel-test","capturedAt":"2026-05-19T01:15:00+03:00","metrics":{"stepsToday":8421}}'`}
      </pre>
    </section>
  );
}

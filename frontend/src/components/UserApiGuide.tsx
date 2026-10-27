import { useState } from "react";

import { PUBLIC_API_BASE_URL } from "../apiV2";

type Props = {
  username: string;
  nutritionDate: string;
  serverTimezone: string;
  resetHour: number;
  ingestToken: string;
};

type AuthKind = "none" | "session" | "ingest";
type Method = "GET" | "POST" | "PATCH" | "DELETE";

type Endpoint = {
  id: string;
  method: Method;
  title: string;
  path: string;
  examplePath: string;
  auth: AuthKind;
  description: string;
  parameters?: string;
  response: string;
  body?: string;
  savesCookie?: boolean;
};

type ApiSection = {
  id: string;
  title: string;
  description: string;
  endpoints: Endpoint[];
};

export function UserApiGuide({ username, nutritionDate, serverTimezone, resetHour, ingestToken }: Props) {
  const [copied, setCopied] = useState("");
  const sections = buildSections(username, nutritionDate, serverTimezone, resetHour);
  const endpointCount = sections.reduce((total, section) => total + section.endpoints.length, 0);
  const completeManual = buildCompleteManual(sections, serverTimezone, resetHour);

  async function copy(value: string, id: string) {
    try {
      let complete = false;
      if (navigator.clipboard?.writeText) {
        try {
          await navigator.clipboard.writeText(value);
          complete = true;
        } catch {
          complete = false;
        }
      }
      if (!complete) {
        const field = document.createElement("textarea");
        field.value = value;
        field.setAttribute("readonly", "");
        field.style.position = "fixed";
        field.style.left = "-9999px";
        document.body.appendChild(field);
        field.select();
        complete = document.execCommand("copy");
        field.remove();
      }
      if (!complete) throw new Error("Copy was not accepted");
      setCopied(id);
      window.setTimeout(() => setCopied((current) => current === id ? "" : current), 1800);
    } catch {
      setCopied("error");
    }
  }

  return (
    <details className="group min-w-0 overflow-hidden rounded-[1.25rem] border border-app-border bg-app-surface shadow-soft sm:rounded-[1.5rem]">
      <summary className="flex cursor-pointer list-none items-center justify-between gap-3 px-4 py-4 marker:content-none sm:gap-5 sm:px-6 sm:py-5">
        <div className="flex min-w-0 items-center gap-3 sm:gap-4">
          <div className="grid h-10 w-10 shrink-0 place-items-center rounded-xl bg-slate-950 font-mono text-xs font-bold text-emerald-300 shadow-inner sm:h-11 sm:w-11 sm:rounded-2xl sm:text-sm">
            <span aria-hidden="true">&gt;_</span>
          </div>
          <div className="min-w-0">
            <p className="text-xs font-bold uppercase tracking-[0.18em] text-app-accent">Developer access</p>
            <h2 className="mt-1 text-lg font-semibold">Complete user API manual</h2>
            <p className="mt-1 text-xs leading-5 text-app-muted sm:text-sm">{endpointCount} regular-user operations · every section is copyable</p>
          </div>
        </div>
        <span className="shrink-0 rounded-full border border-app-border px-2.5 py-1 text-xs font-bold text-app-muted transition group-open:rotate-180 sm:px-3 sm:py-1.5" aria-hidden="true">↓</span>
      </summary>

      <div className="min-w-0 border-t border-app-border px-4 pb-5 pt-4 sm:px-6 sm:pb-6 sm:pt-5">
        <div className="grid gap-3 lg:grid-cols-[minmax(0,1fr)_auto_auto] lg:items-center">
          <div className="min-w-0">
            <div className="flex flex-wrap items-center gap-2">
              <p className="text-xs font-bold uppercase tracking-[0.16em] text-app-muted">Public API base URL</p>
              <span className="rounded-full bg-emerald-500/10 px-2 py-0.5 text-[10px] font-bold text-emerald-600 dark:text-emerald-300">HTTPS VERIFIED</span>
            </div>
            <code className="mt-2 block break-all font-mono text-sm font-semibold text-app-text">{PUBLIC_API_BASE_URL}</code>
          </div>
          <CopyButton label="Copy base URL" copied={copied === "base"} onClick={() => copy(PUBLIC_API_BASE_URL, "base")} />
          <CopyButton primary label="Copy complete manual" copied={copied === "manual"} onClick={() => copy(completeManual, "manual")} />
        </div>

        <div className="mt-5 grid gap-3 sm:grid-cols-2">
          <AuthCard
            tone="emerald"
            title="Write authentication"
            description="Health uploads and meal writes use your private bearer token."
            value={'Authorization: Bearer ' + (ingestToken || "<YOUR_INGEST_TOKEN>")}
            copied={copied === "auth-ingest"}
            onCopy={() => copy('Authorization: Bearer ' + (ingestToken || "<YOUR_INGEST_TOKEN>"), "auth-ingest")}
          />
          <AuthCard
            tone="sky"
            title="Read authentication"
            description="Account, health, and nutrition reads use the login session cookie."
            value="-b session.cookies"
            copied={copied === "auth-session"}
            onCopy={() => copy("-b session.cookies", "auth-session")}
          />
        </div>

        {ingestToken ? (
          <div className="mt-4 flex flex-col gap-3 rounded-2xl border border-amber-400/30 bg-amber-400/[0.08] p-4 sm:flex-row sm:items-center sm:justify-between">
            <div><p className="text-sm font-semibold">One-time token is ready</p><p className="mt-1 text-xs text-app-muted">The ingest examples below contain it. Copy it before leaving or refreshing.</p></div>
            <CopyButton label="Copy ingest token" copied={copied === "token"} onClick={() => copy(ingestToken, "token")} />
          </div>
        ) : null}

        <div className="mt-5 space-y-3">
          {sections.map((section) => (
            <details key={section.id} className="group/section overflow-hidden rounded-2xl border border-app-border bg-app-elevated">
              <summary className="flex cursor-pointer list-none items-center justify-between gap-3 px-4 py-4 marker:content-none sm:px-5">
                <div className="min-w-0">
                  <div className="flex flex-wrap items-center gap-2">
                    <h3 className="font-semibold text-app-text">{section.title}</h3>
                    <span className="rounded-full bg-app-accent/10 px-2 py-0.5 text-[10px] font-bold text-app-accent">{section.endpoints.length} ENDPOINTS</span>
                  </div>
                  <p className="mt-1 text-xs leading-5 text-app-muted">{section.description}</p>
                </div>
                <span className="shrink-0 text-sm text-app-muted transition group-open/section:rotate-180" aria-hidden="true">↓</span>
              </summary>
              <div className="space-y-3 border-t border-app-border p-3 sm:p-4">
                {section.endpoints.map((endpoint) => (
                  <EndpointCard
                    key={endpoint.id}
                    endpoint={endpoint}
                    ingestToken={ingestToken}
                    copied={copied}
                    onCopy={copy}
                  />
                ))}
              </div>
            </details>
          ))}
        </div>

        <div className="mt-4 rounded-2xl border border-app-border bg-app-elevated p-4">
          <div className="flex flex-col gap-3 sm:flex-row sm:items-start sm:justify-between">
            <div>
              <p className="text-xs font-bold uppercase tracking-[0.16em] text-app-accent">Error reference</p>
              <p className="mt-2 text-sm leading-6 text-app-muted">401 invalid credential · 403 password change required or forbidden · 404 resource not owned · 409 conflict · 422 invalid input</p>
            </div>
            <CopyButton compact label="Copy errors" copied={copied === "errors"} onClick={() => copy(ERROR_REFERENCE, "errors")} />
          </div>
        </div>

        <p aria-live="polite" className={"mt-4 text-xs " + (copied === "error" ? "text-rose-600" : "text-app-muted")}>
          {copied === "error" ? "Copy failed. Select the text manually." : "The complete-manual copy always uses an ingest-token placeholder. A newly generated token appears only in individual ingest examples."}
        </p>
      </div>
    </details>
  );
}

function EndpointCard({ endpoint, ingestToken, copied, onCopy }: {
  endpoint: Endpoint;
  ingestToken: string;
  copied: string;
  onCopy: (value: string, id: string) => void;
}) {
  const fullUrl = PUBLIC_API_BASE_URL + endpoint.examplePath;
  const curl = curlFor(endpoint, ingestToken || "<YOUR_INGEST_TOKEN>");
  const details = endpointDetails(endpoint);
  return (
    <article className="min-w-0 overflow-hidden rounded-xl border border-app-border bg-app-surface">
      <div className="p-4">
        <div className="flex flex-col gap-3 sm:flex-row sm:items-start sm:justify-between">
          <div className="min-w-0">
            <div className="flex flex-wrap items-center gap-2">
              <MethodBadge method={endpoint.method} />
              <h4 className="font-semibold text-app-text">{endpoint.title}</h4>
              <span className="rounded-full border border-app-border px-2 py-0.5 text-[10px] font-bold uppercase text-app-muted">{authLabel(endpoint.auth)}</span>
            </div>
            <code className="mt-2 block break-all font-mono text-xs text-app-accent">{endpoint.path}</code>
            <p className="mt-2 text-sm leading-6 text-app-muted">{endpoint.description}</p>
            {endpoint.parameters ? <p className="mt-2 text-xs leading-5 text-app-muted"><strong className="text-app-text">Parameters:</strong> {endpoint.parameters}</p> : null}
            <p className="mt-1 text-xs leading-5 text-app-muted"><strong className="text-app-text">Returns:</strong> {endpoint.response}</p>
          </div>
          <div className="grid shrink-0 grid-cols-2 gap-2 sm:flex sm:flex-wrap sm:justify-end">
            <CopyButton compact label="Copy URL" copied={copied === endpoint.id + "-url"} onClick={() => onCopy(fullUrl, endpoint.id + "-url")} />
            <CopyButton compact label="Copy details" copied={copied === endpoint.id + "-details"} onClick={() => onCopy(details, endpoint.id + "-details")} />
          </div>
        </div>
      </div>

      {endpoint.body ? (
        <CodeBlock
          label="JSON request body"
          value={endpoint.body}
          copied={copied === endpoint.id + "-body"}
          onCopy={() => onCopy(endpoint.body || "", endpoint.id + "-body")}
        />
      ) : null}
      <CodeBlock
        label="Curl example"
        value={curl}
        copied={copied === endpoint.id + "-curl"}
        onCopy={() => onCopy(curl, endpoint.id + "-curl")}
      />
    </article>
  );
}

function CodeBlock({ label, value, copied, onCopy }: { label: string; value: string; copied: boolean; onCopy: () => void }) {
  return (
    <div className="min-w-0 border-t border-white/10 bg-slate-950">
      <div className="flex items-center justify-between gap-3 border-b border-white/10 px-3 py-2 sm:px-4">
        <span className="text-[10px] font-bold uppercase tracking-[0.16em] text-slate-400">{label}</span>
        <CopyButton dark compact label={"Copy " + label.toLowerCase()} copied={copied} onClick={onCopy} />
      </div>
      <pre className="max-h-72 w-full min-w-0 overflow-auto overscroll-contain p-3 text-[11px] leading-5 text-slate-200 sm:p-4 sm:text-xs"><code>{value}</code></pre>
    </div>
  );
}

function AuthCard({ tone, title, description, value, copied, onCopy }: {
  tone: "emerald" | "sky";
  title: string;
  description: string;
  value: string;
  copied: boolean;
  onCopy: () => void;
}) {
  const style = tone === "emerald"
    ? "border-emerald-500/20 bg-emerald-500/[0.06]"
    : "border-sky-500/20 bg-sky-500/[0.06]";
  return (
    <div className={"min-w-0 rounded-2xl border p-4 " + style}>
      <div className="flex flex-col gap-3 sm:flex-row sm:items-start sm:justify-between">
        <div className="min-w-0">
          <p className="text-xs font-bold uppercase tracking-[0.16em] text-app-text">{title}</p>
          <p className="mt-2 text-sm leading-6 text-app-muted">{description}</p>
          <code className="mt-2 block break-all text-xs text-app-text">{value}</code>
        </div>
        <CopyButton compact label="Copy auth" copied={copied} onClick={onCopy} />
      </div>
    </div>
  );
}

function MethodBadge({ method }: { method: Method }) {
  const color = {
    GET: "bg-sky-500/10 text-sky-600 dark:text-sky-300",
    POST: "bg-emerald-500/10 text-emerald-600 dark:text-emerald-300",
    PATCH: "bg-amber-500/10 text-amber-700 dark:text-amber-300",
    DELETE: "bg-rose-500/10 text-rose-600 dark:text-rose-300",
  }[method];
  return <span className={"rounded-md px-2 py-1 font-mono text-[10px] font-bold " + color}>{method}</span>;
}

function CopyButton({ label, copied, compact = false, primary = false, dark = false, onClick }: {
  label: string;
  copied: boolean;
  compact?: boolean;
  primary?: boolean;
  dark?: boolean;
  onClick: () => void;
}) {
  const stateClass = copied
    ? "border-emerald-500 bg-emerald-500 text-white"
    : primary
      ? "border-app-accent bg-app-accent text-white hover:brightness-110"
      : dark
        ? "border-slate-700 bg-slate-900 text-slate-200 hover:border-emerald-400"
        : "border-app-border bg-app-surface text-app-text hover:border-app-accent";
  const sizeClass = compact ? "min-h-9 px-3 py-1.5 text-[11px]" : "min-h-11 w-full px-4 py-2.5 text-sm sm:w-auto";
  return (
    <button type="button" onClick={onClick}
      className={"shrink-0 rounded-xl border font-bold transition focus:outline-none focus:ring-2 focus:ring-app-accent/30 " + sizeClass + " " + stateClass}
      aria-label={label}>
      {copied ? "Copied!" : label}
    </button>
  );
}

function buildSections(username: string, nutritionDate: string, serverTimezone: string, resetHour: number): ApiSection[] {
  const rangeStart = nutritionDate.slice(0, 8) + "01";
  const loginBody = json({ username, password: "<YOUR_PASSWORD>" });
  const passwordBody = json({
    currentPassword: "<CURRENT_OR_TEMPORARY_PASSWORD>",
    newPassword: "<NEW_PASSWORD_OF_AT_LEAST_12_CHARACTERS>",
  });
  const snapshotBody = json({
    deviceId: "my-phone",
    deviceName: "My phone",
    capturedAt: nutritionDate + "T12:30:00+03:00",
    timezone: serverTimezone,
    metrics: {
      stepsToday: 8421,
      distanceKmToday: 6.2,
      activeCaloriesKcalToday: 540,
      totalCaloriesKcalToday: 2200,
      latestHeartRateBpm: 78,
      restingHeartRateBpm: 62,
      sleepMinutesLastNight: 438,
      latestWeightKg: 78.4,
    },
  });
  const mealBody = json({
    clientMealId: "curl-meal-001",
    deviceId: "curl-client",
    eatenAt: nutritionDate + "T13:00:00+03:00",
    timezone: serverTimezone,
    mealType: "lunch",
    title: "Chicken and rice",
    notes: "Homemade",
    caloriesKcal: 720,
    proteinG: 48,
    carbsG: 82,
    fatG: 20,
    fiberG: 7,
    sugarG: 5,
    sodiumMg: 640,
  });

  return [
    {
      id: "account",
      title: "Account session",
      description: "Log in, replace a temporary password, inspect the current account, and log out.",
      endpoints: [
        {
          id: "login", method: "POST", title: "Log in", path: "/api/v1/auth/login", examplePath: "/api/v1/auth/login",
          auth: "none", savesCookie: true, body: loginBody,
          description: "Authenticates the account and writes a fixed-lifetime HTTP-only session cookie to session.cookies.",
          response: "Current user, reset boundary, server timezone, and current nutrition date.",
        },
        {
          id: "change-password", method: "POST", title: "Change password", path: "/api/v1/auth/change-password", examplePath: "/api/v1/auth/change-password",
          auth: "session", body: passwordBody,
          description: "Required after first login and available later for self-service password changes.",
          response: "Updated current user. Other browser sessions are revoked.",
        },
        {
          id: "current-user", method: "GET", title: "Current account", path: "/api/v1/auth/me", examplePath: "/api/v1/auth/me",
          auth: "session", description: "Returns the account associated with the current browser session.",
          response: "User identity, status, reset hour, timezone, and current nutrition date.",
        },
        {
          id: "logout", method: "POST", title: "Log out", path: "/api/v1/auth/logout", examplePath: "/api/v1/auth/logout",
          auth: "session", description: "Revokes the current session and clears its cookie.",
          response: "204 No Content.",
        },
      ],
    },
    {
      id: "preferences",
      title: "Preferences and ingest token",
      description: "Control the nutrition-day boundary and the one active Android/API write token.",
      endpoints: [
        {
          id: "preferences", method: "PATCH", title: "Set nutrition reset hour", path: "/api/v1/account/preferences", examplePath: "/api/v1/account/preferences",
          auth: "session", body: json({ nutritionResetHour: resetHour }),
          description: "Sets an integer reset hour from 0 through 23 in " + serverTimezone + ". Historical meals are reclassified immediately.",
          response: "Updated current user and nutrition boundary.",
        },
        {
          id: "token-status", method: "GET", title: "Ingest-token status", path: "/api/v1/account/ingest-token", examplePath: "/api/v1/account/ingest-token",
          auth: "session", description: "Checks whether an ingest token exists. The stored secret is never returned.",
          response: "Active status, safe token prefix, and creation time.",
        },
        {
          id: "token-rotate", method: "POST", title: "Generate or rotate token", path: "/api/v1/account/ingest-token/rotate", examplePath: "/api/v1/account/ingest-token/rotate",
          auth: "session", description: "Creates a token and immediately revokes any previous ingest token.",
          response: "The full token once, plus its prefix and creation time. Copy it immediately.",
        },
        {
          id: "token-revoke", method: "DELETE", title: "Revoke token", path: "/api/v1/account/ingest-token", examplePath: "/api/v1/account/ingest-token",
          auth: "session", description: "Stops future health and meal writes using the current token.",
          response: "204 No Content.",
        },
      ],
    },
    {
      id: "health",
      title: "Devices and health snapshots",
      description: "Read user-owned devices and snapshots, or upload a new snapshot with the ingest token.",
      endpoints: [
        {
          id: "devices", method: "GET", title: "List devices", path: "/api/v1/devices", examplePath: "/api/v1/devices",
          auth: "session", description: "Lists only devices owned by the signed-in user.",
          response: "Array of user-owned devices.",
        },
        {
          id: "health-latest", method: "GET", title: "Latest snapshot", path: "/api/v1/health/latest?deviceId={deviceId}", examplePath: "/api/v1/health/latest?deviceId=my-phone",
          auth: "session", parameters: "deviceId is optional; without it, the most recently seen owned device is selected.",
          description: "Returns the latest health snapshot for one owned device.",
          response: "Device and latest snapshot.",
        },
        {
          id: "health-recent", method: "GET", title: "Recent snapshots", path: "/api/v1/health/recent", examplePath: "/api/v1/health/recent?deviceId=my-phone&limit=20",
          auth: "session", parameters: "deviceId required; limit 1–100 (default 20); optional from/to ISO 8601 datetimes.",
          description: "Lists recent snapshots for one owned device.",
          response: "Recent snapshots ordered newest first.",
        },
        {
          id: "health-history", method: "GET", title: "Metric history", path: "/api/v1/health/history", examplePath: "/api/v1/health/history?deviceId=my-phone&metric=steps_today",
          auth: "session", parameters: "deviceId and metric required; optional from/to. Metrics include steps_today, distance_km_today, calories, heart rate, sleep, and weight.",
          description: "Returns a time series for a supported health metric.",
          response: "Timestamped metric values for the owned device.",
        },
        {
          id: "health-detail", method: "GET", title: "Snapshot detail", path: "/api/v1/health/snapshot/{snapshotId}", examplePath: "/api/v1/health/snapshot/123",
          auth: "session", parameters: "snapshotId must belong to the current user.",
          description: "Returns snapshot details including the sensitive original raw payload.",
          response: "Device, normalized snapshot, and raw health payload.",
        },
        {
          id: "health-upload", method: "POST", title: "Upload health snapshot", path: "/api/v1/ingest/health-snapshot", examplePath: "/api/v1/ingest/health-snapshot",
          auth: "ingest", body: snapshotBody,
          description: "Uploads an Android health snapshot. Ownership is taken from the token; no user ID is accepted.",
          response: "Stored snapshot acknowledgement.",
        },
      ],
    },
    {
      id: "nutrition-reads",
      title: "Nutrition reads",
      description: "Read meals, daily totals, ranges, and complete rolling averages using nutrition-day dates.",
      endpoints: [
        {
          id: "meals-list", method: "GET", title: "List meals", path: "/api/v1/nutrition/meals", examplePath: "/api/v1/nutrition/meals?date=" + nutritionDate,
          auth: "session", parameters: "Use date, or a from/to nutrition-date range.",
          description: "Lists owned meals with original localDate and calculated nutritionDate.",
          response: "Meals and the applied nutrition-day boundary.",
        },
        {
          id: "nutrition-daily", method: "GET", title: "Daily nutrition summary", path: "/api/v1/nutrition/summary/daily?date={YYYY-MM-DD}", examplePath: "/api/v1/nutrition/summary/daily?date=" + nutritionDate,
          auth: "session", parameters: "date is a nutrition-day label.",
          description: "Totals calories, macros, fiber, sugar, sodium, meal count, and meal types.",
          response: "Complete daily nutrition totals.",
        },
        {
          id: "nutrition-range", method: "GET", title: "Nutrition range summary", path: "/api/v1/nutrition/summary/range?from={date}&to={date}", examplePath: "/api/v1/nutrition/summary/range?from=" + rangeStart + "&to=" + nutritionDate,
          auth: "session", parameters: "from and to are inclusive nutrition-day labels.",
          description: "Returns daily totals across an inclusive date range.",
          response: "One summary for each nutrition day in the range.",
        },
        {
          id: "nutrition-trends", method: "GET", title: "Rolling nutrition trends", path: "/api/v1/nutrition/trends?to={YYYY-MM-DD}", examplePath: "/api/v1/nutrition/trends?to=" + nutritionDate,
          auth: "session", parameters: "to is the final visible nutrition day.",
          description: "Returns 30 daily points with fixed-divisor 7-, 14-, and 30-day averages; missing days count as zero.",
          response: "Daily macros, rolling averages, hasMeals, and isIncomplete.",
        },
      ],
    },
    {
      id: "meal-writes",
      title: "Create and manage meals",
      description: "The signed-in dashboard uses its session; external apps create, edit, and soft-delete owned meals with the ingest token.",
      endpoints: [
        {
          id: "meal-create", method: "POST", title: "Create or idempotently update meal", path: "/api/v1/nutrition/meals", examplePath: "/api/v1/nutrition/meals",
          auth: "ingest", body: mealBody,
          description: "Creates a meal. Reusing clientMealId with the same device and owner updates instead of duplicating it.",
          parameters: "mealType: breakfast, lunch, dinner, snack, or other.",
          response: "Stored meal including localDate and calculated nutritionDate.",
        },
        {
          id: "meal-update", method: "PATCH", title: "Update meal", path: "/api/v1/nutrition/meals/{mealId}", examplePath: "/api/v1/nutrition/meals/456",
          auth: "ingest", body: json({ title: "Updated meal title", caloriesKcal: 680 }),
          description: "Updates only the submitted fields on a meal owned by the token user.",
          response: "Updated meal.",
        },
        {
          id: "meal-delete", method: "DELETE", title: "Delete meal", path: "/api/v1/nutrition/meals/{mealId}", examplePath: "/api/v1/nutrition/meals/456",
          auth: "ingest", description: "Soft-deletes an owned meal so it disappears from lists, summaries, and trends.",
          response: "Deletion acknowledgement.",
        },
      ],
    },
  ];
}

function curlFor(endpoint: Endpoint, ingestToken: string) {
  const args: string[] = [];
  if (endpoint.method !== "GET") args.push("-X " + endpoint.method);
  if (endpoint.savesCookie) args.push("-c session.cookies");
  if (endpoint.auth === "session") args.push("-b session.cookies");
  if (endpoint.auth === "ingest") args.push('-H "Authorization: Bearer ' + ingestToken + '"');
  if (endpoint.body) {
    args.push('-H "Content-Type: application/json"');
    args.push("-d '" + endpoint.body + "'");
  }
  args.push('"' + PUBLIC_API_BASE_URL + endpoint.examplePath + '"');
  return ["curl -sS \\", ...args.map((arg, index) => "  " + arg + (index === args.length - 1 ? "" : " \\"))].join("\n");
}

function endpointDetails(endpoint: Endpoint) {
  return [
    endpoint.method + " " + endpoint.path,
    "Authentication: " + authLabel(endpoint.auth),
    "Purpose: " + endpoint.description,
    endpoint.parameters ? "Parameters: " + endpoint.parameters : "",
    "Returns: " + endpoint.response,
  ].filter(Boolean).join("\n");
}

function buildCompleteManual(sections: ApiSection[], serverTimezone: string, resetHour: number) {
  const start = String(resetHour).padStart(2, "0") + ":00";
  const end = String((resetHour + 23) % 24).padStart(2, "0") + ":59";
  const lines = [
    "HEALTH DASHBOARD — REGULAR USER API",
    "Base URL: " + PUBLIC_API_BASE_URL,
    "Nutrition day: " + start + "–" + end + ", " + serverTimezone,
    "",
    "AUTHENTICATION",
    "Reads/settings: HTTP-only session cookie (-b session.cookies)",
    "Health and meal writes: Authorization: Bearer <YOUR_INGEST_TOKEN>",
    "",
  ];
  sections.forEach((section) => {
    lines.push(section.title.toUpperCase(), section.description, "");
    section.endpoints.forEach((endpoint) => {
      lines.push(endpointDetails(endpoint));
      if (endpoint.body) lines.push("JSON body:", endpoint.body);
      lines.push("Curl:", curlFor(endpoint, "<YOUR_INGEST_TOKEN>"), "");
    });
  });
  lines.push("ERRORS", ERROR_REFERENCE);
  return lines.join("\n");
}

function authLabel(auth: AuthKind) {
  return auth === "session" ? "Session cookie" : auth === "ingest" ? "Ingest token" : "No existing credential";
}

function json(value: unknown) {
  return JSON.stringify(value, null, 2);
}

const ERROR_REFERENCE = [
  '{"detail":"Human-readable error"}',
  "401 — Missing, expired, or invalid session/ingest token",
  "403 — Password change required or operation not allowed",
  "404 — Resource does not exist for the current user",
  "409 — Conflicting unique value",
  "422 — Invalid field, date range, metric, timezone, or payload",
].join("\n");

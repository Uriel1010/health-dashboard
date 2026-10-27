import { useCallback, useEffect, useMemo, useState } from "react";
import type { FormEvent } from "react";

import { accountApi, adminUsersApi, ApiError, authApi, dashboardApi } from "./apiV2";
import type { ManualMealPayload } from "./apiV2";
import type { CurrentUser, FoodGoals, IngestTokenStatus, NutritionTrendsResponse } from "./accountTypes";
import { HistoryTable } from "./components/HistoryTable";
import { MealEntryModal } from "./components/MealEntryModal";
import { MetricCard } from "./components/MetricCard";
import { NutritionTrendChart, type MacroKey } from "./components/NutritionTrendChart";
import { RawSnapshotModal } from "./components/RawSnapshotModal";
import { SyncStatus } from "./components/SyncStatus";
import { UserApiGuide } from "./components/UserApiGuide";
import { getFreshness, getMetricStatus, jsonItemCount } from "./status";
import type { DataStatus } from "./status";
import type { Device, Meal, NutritionSummaryResponse, RecentSnapshot, Snapshot, SnapshotDetailResponse } from "./types";

type Card = {
  label: string;
  value: string;
  unit?: string;
  accent: string;
  status: DataStatus;
  timeRange: string;
  explanation: string;
  capturedAt?: string | null;
};

const macroOptions: { key: MacroKey; label: string; unit: string }[] = [
  { key: "caloriesKcal", label: "Calories", unit: "kcal" },
  { key: "proteinG", label: "Protein", unit: "g" },
  { key: "carbsG", label: "Carbs", unit: "g" },
  { key: "fatG", label: "Fat", unit: "g" },
];

export default function AppV2() {
  const [user, setUser] = useState<CurrentUser | null>(null);
  const [checking, setChecking] = useState(true);

  useEffect(() => {
    authApi.me().then(setUser).catch(() => setUser(null)).finally(() => setChecking(false));
  }, []);

  if (checking) return <FullPageMessage title="Opening your private dashboard" detail="Checking your secure session…" />;
  if (!user) return <LoginScreen onLogin={setUser} />;
  if (user.mustChangePassword) return <PasswordChangeScreen user={user} onChanged={setUser} />;
  return <Dashboard user={user} onUserChange={setUser} onLogout={() => setUser(null)} />;
}

function LoginScreen({ onLogin }: { onLogin: (user: CurrentUser) => void }) {
  const [username, setUsername] = useState("");
  const [password, setPassword] = useState("");
  const [error, setError] = useState("");
  const [busy, setBusy] = useState(false);

  async function submit(event: FormEvent) {
    event.preventDefault();
    setBusy(true);
    setError("");
    try {
      onLogin(await authApi.login(username, password));
    } catch (err) {
      setError(message(err));
    } finally {
      setBusy(false);
    }
  }

  return (
    <main className="relative min-h-screen overflow-hidden bg-app-bg text-app-text">
      <div className="pointer-events-none absolute inset-0 opacity-70 [background-image:radial-gradient(circle_at_15%_20%,rgba(37,99,235,.16),transparent_28%),radial-gradient(circle_at_85%_75%,rgba(5,150,105,.13),transparent_30%)]" />
      <div className="relative mx-auto grid min-h-screen max-w-6xl items-center gap-12 px-5 py-12 lg:grid-cols-[1.15fr_.85fr]">
        <section>
          <p className="text-xs font-bold uppercase tracking-[0.28em] text-app-accent">Private health workspace</p>
          <h1 className="mt-5 max-w-2xl text-5xl font-semibold leading-[1.02] tracking-[-0.04em] sm:text-6xl">
            Your health history, held in confidence.
          </h1>
          <p className="mt-6 max-w-xl text-lg leading-8 text-app-muted">
            Personal metrics, nutrition records, and device syncs are isolated to your account on this self-hosted server.
          </p>
          <div className="mt-10 flex flex-wrap gap-3 text-sm text-app-muted">
            {["Private sessions", "Per-user ingestion", "No public registration"].map((label) => (
              <span key={label} className="rounded-full border border-app-border bg-app-surface/70 px-4 py-2">{label}</span>
            ))}
          </div>
        </section>
        <form onSubmit={submit} className="rounded-[2rem] border border-app-border bg-app-surface p-7 shadow-[0_30px_100px_rgba(15,23,42,.15)] sm:p-9">
          <div className="mb-8">
            <div className="flex h-11 w-11 items-center justify-center rounded-2xl bg-app-accent text-xl font-bold text-white">H</div>
            <h2 className="mt-6 text-2xl font-semibold tracking-tight">Sign in</h2>
            <p className="mt-2 text-sm text-app-muted">Use the account created by your administrator.</p>
          </div>
          <Field label="Username" value={username} onChange={setUsername} autoComplete="username" />
          <div className="mt-4"><Field label="Password" type="password" value={password} onChange={setPassword} autoComplete="current-password" /></div>
          {error ? <p className="mt-4 rounded-xl bg-rose-500/10 px-3 py-2 text-sm text-rose-700 dark:text-rose-300">{error}</p> : null}
          <button disabled={busy} className="mt-6 w-full rounded-xl bg-app-accent px-4 py-3 font-semibold text-white transition hover:brightness-110 disabled:opacity-60">
            {busy ? "Signing in…" : "Enter dashboard"}
          </button>
        </form>
      </div>
    </main>
  );
}

function PasswordChangeScreen({ user, onChanged }: { user: CurrentUser; onChanged: (user: CurrentUser) => void }) {
  const [current, setCurrent] = useState("");
  const [next, setNext] = useState("");
  const [confirm, setConfirm] = useState("");
  const [error, setError] = useState("");

  async function submit(event: FormEvent) {
    event.preventDefault();
    if (next !== confirm) return setError("New passwords do not match.");
    try {
      onChanged(await authApi.changePassword(current, next));
    } catch (err) {
      setError(message(err));
    }
  }

  return (
    <main className="grid min-h-screen place-items-center bg-app-bg px-4 text-app-text">
      <form onSubmit={submit} className="w-full max-w-md rounded-[2rem] border border-app-border bg-app-surface p-8 shadow-soft">
        <p className="text-xs font-bold uppercase tracking-[0.2em] text-app-accent">Welcome, {user.displayName}</p>
        <h1 className="mt-3 text-3xl font-semibold tracking-tight">Choose a private password</h1>
        <p className="mt-3 text-sm leading-6 text-app-muted">Replace the one-time password before opening health data.</p>
        <div className="mt-7 space-y-4">
          <Field label="Temporary password" type="password" value={current} onChange={setCurrent} autoComplete="current-password" />
          <Field label="New password" type="password" value={next} onChange={setNext} autoComplete="new-password" />
          <Field label="Confirm new password" type="password" value={confirm} onChange={setConfirm} autoComplete="new-password" />
        </div>
        {error ? <p className="mt-4 text-sm text-rose-600">{error}</p> : null}
        <button className="mt-6 w-full rounded-xl bg-app-accent px-4 py-3 font-semibold text-white">Save password</button>
      </form>
    </main>
  );
}

function Dashboard({ user, onUserChange, onLogout }: { user: CurrentUser; onUserChange: (user: CurrentUser) => void; onLogout: () => void }) {
  const [devices, setDevices] = useState<Device[]>([]);
  const [selectedDevice, setSelectedDevice] = useState("");
  const [latest, setLatest] = useState<Snapshot | null>(null);
  const [latestDevice, setLatestDevice] = useState<Device | null>(null);
  const [recent, setRecent] = useState<RecentSnapshot[]>([]);
  const [selectedDate, setSelectedDate] = useState(user.currentNutritionDate);
  const [meals, setMeals] = useState<Meal[]>([]);
  const [summary, setSummary] = useState<NutritionSummaryResponse | null>(null);
  const [trends, setTrends] = useState<NutritionTrendsResponse | null>(null);
  const [macro, setMacro] = useState<MacroKey>("caloriesKcal");
  const [goals, setGoals] = useState<FoodGoals>(user.nutritionGoals);
  const [error, setError] = useState("");
  const [loading, setLoading] = useState(true);
  const [raw, setRaw] = useState<SnapshotDetailResponse | null>(null);
  const [rawError, setRawError] = useState("");
  const [expanded, setExpanded] = useState<number | null>(null);
  const [revealedIngestToken, setRevealedIngestToken] = useState("");
  const [deletingMealId, setDeletingMealId] = useState<number | null>(null);
  const [mealEditorOpen, setMealEditorOpen] = useState(false);
  const [mealNotice, setMealNotice] = useState("");
  const [deletingDeviceId, setDeletingDeviceId] = useState<string | null>(null);

  const load = useCallback(async () => {
    setLoading(true);
    setError("");
    try {
      const deviceList = await dashboardApi.devices();
      setDevices(deviceList);
      const target = selectedDevice || deviceList[0]?.deviceId || "";
      if (!selectedDevice && target) setSelectedDevice(target);
      const nutritionPromise = Promise.all([
        dashboardApi.meals(selectedDate),
        dashboardApi.dailyNutrition(selectedDate),
        dashboardApi.nutritionTrends(selectedDate),
      ]);
      if (target) {
        const [latestResponse, recentResponse, nutrition] = await Promise.all([
          dashboardApi.latest(target),
          dashboardApi.recent(target),
          nutritionPromise,
        ]);
        setLatest(latestResponse.snapshot);
        setLatestDevice(latestResponse.device);
        setRecent(recentResponse.snapshots);
        setMeals(nutrition[0].meals);
        setSummary(nutrition[1]);
        setTrends(nutrition[2]);
      } else {
        const nutrition = await nutritionPromise;
        setLatest(null);
        setLatestDevice(null);
        setRecent([]);
        setMeals(nutrition[0].meals);
        setSummary(nutrition[1]);
        setTrends(nutrition[2]);
      }
    } catch (err) {
      if (err instanceof ApiError && err.status === 401) onLogout();
      else setError(message(err));
    } finally {
      setLoading(false);
    }
  }, [onLogout, selectedDate, selectedDevice]);

  useEffect(() => { load(); }, [load]);
  async function deleteDevice(device: Device) {
    const label = device.deviceName || device.deviceId;
    if (!window.confirm(`Delete device "${label}" and all of its snapshots?`)) return;
    setDeletingDeviceId(device.deviceId);
    setError("");
    try {
      await dashboardApi.deleteDevice(device.deviceId);
      if (selectedDevice === device.deviceId) {
        setSelectedDevice("");
        setLatest(null);
        setLatestDevice(null);
        setRecent([]);
      }
      await load();
    } catch (err) {
      if (err instanceof ApiError && err.status === 401) onLogout();
      else setError(message(err));
    } finally {
      setDeletingDeviceId(null);
    }
  }

  async function deleteMeal(meal: Meal) {
    if (!window.confirm(`Delete meal "${meal.title}"?`)) return;
    setDeletingMealId(meal.id);
    setError("");
    try {
      await dashboardApi.deleteMeal(meal.id);
      await load();
    } catch (err) {
      if (err instanceof ApiError && err.status === 401) onLogout();
      else setError(message(err));
    } finally {
      setDeletingMealId(null);
    }
  }

  async function createManualMeal(payload: ManualMealPayload, nutritionDate: string) {
    setError("");
    setMealNotice("");
    try {
      await dashboardApi.createMeal(payload);
      setMealEditorOpen(false);
      setMealNotice("Meal added. Daily totals and trends are up to date.");
      if (nutritionDate === selectedDate) await load();
      else setSelectedDate(nutritionDate);
    } catch (err) {
      if (err instanceof ApiError && err.status === 401) onLogout();
      throw err;
    }
  }

  useEffect(() => {
    const key = `health-dashboard.foodGoals.${user.id}`;
    const raw = localStorage.getItem(key);
    if (!raw) return;
    try {
      const legacy = { ...user.nutritionGoals, ...JSON.parse(raw) } as FoodGoals;
      setGoals(legacy);
      authApi.updateNutritionGoals(legacy).then((updated) => {
        localStorage.removeItem(key);
        onUserChange(updated);
      }).catch((err) => setError(message(err)));
    } catch { localStorage.removeItem(key); }
  }, [user.id]);
  useEffect(() => {
    const timer = window.setInterval(load, 60_000);
    return () => window.clearInterval(timer);
  }, [load]);

  const cards = useMemo(() => metricCards(latest), [latest]);
  const chosenMacro = macroOptions.find((item) => item.key === macro)!;

  async function logout() {
    await authApi.logout().catch(() => undefined);
    onLogout();
  }

  async function openRaw(id: number) {
    setRawError("");
    try {
      setRaw(await dashboardApi.snapshot(id));
    } catch (err) {
      setRawError(message(err));
    }
  }

  function updateGoal(key: keyof FoodGoals, value: string) {
    const updated = { ...goals, [key]: Math.max(0, Number(value) || 0) };
    setGoals(updated);
    authApi.updateNutritionGoals(updated).then(onUserChange).catch((err) => setError(message(err)));
  }

  return (
    <main className="min-h-screen overflow-x-hidden bg-app-bg text-app-text">
      <div className="mx-auto flex w-full max-w-7xl flex-col gap-4 px-3 py-3 sm:gap-6 sm:px-6 sm:py-5 lg:px-8">
        <header className="flex flex-col gap-4 rounded-[1.25rem] border border-app-border bg-app-surface px-4 py-4 shadow-soft sm:flex-row sm:items-center sm:justify-between sm:rounded-[1.5rem] sm:px-5">
          <div className="flex items-center gap-4">
            <div className="flex h-11 w-11 items-center justify-center rounded-2xl bg-app-accent font-bold text-white">H</div>
            <div>
              <p className="text-xs font-bold uppercase tracking-[0.2em] text-app-muted">Health dashboard</p>
              <h1 className="mt-1 text-xl font-semibold">{user.displayName}</h1>
            </div>
          </div>
          <div className="grid grid-cols-2 gap-2 sm:flex sm:flex-wrap sm:items-center">
            {user.isAdmin ? <span className="col-span-2 rounded-full bg-app-accent/10 px-3 py-1 text-center text-xs font-bold text-app-accent sm:col-span-1">Sole administrator</span> : null}
            <button onClick={load} className="min-h-11 rounded-xl border border-app-border px-3 py-2 text-sm font-semibold hover:border-app-accent">Refresh</button>
            <button onClick={logout} className="min-h-11 rounded-xl border border-app-border px-3 py-2 text-sm font-semibold hover:border-rose-400">Sign out</button>
          </div>
        </header>

        {error ? <div className="rounded-xl border border-rose-300 bg-rose-500/10 px-4 py-3 text-sm text-rose-700 dark:text-rose-300">{error}</div> : null}

        <SyncStatus
          receivedAt={latest?.receivedAt}
          capturedAt={latest?.capturedAt}
          deviceName={latestDevice?.deviceName}
          deviceId={latestDevice?.deviceId ?? selectedDevice}
          timezone={latest?.timezone}
          freshness={getFreshness(latest?.receivedAt)}
          loading={loading}
        />

        <section className="grid gap-4 sm:grid-cols-2 lg:grid-cols-3 xl:grid-cols-4" aria-label="Health metric cards">
          {cards.map((card) => <MetricCard key={card.label} {...card} />)}
        </section>

        <section className="rounded-[1.25rem] border border-app-border bg-app-surface p-4 shadow-soft sm:rounded-[1.5rem] sm:p-5">
          <div className="flex flex-col gap-4 lg:flex-row lg:items-end lg:justify-between">
            <div>
              <p className="text-xs font-bold uppercase tracking-[0.18em] text-app-accent">Nutrition day</p>
              <h2 className="mt-2 text-2xl font-semibold">Daily fuel</h2>
              <p className="mt-1 text-sm text-app-muted">{boundaryLabel(user)} · {user.serverTimezone}</p>
            </div>
            <div className="flex w-full flex-col gap-3 sm:flex-row sm:items-end lg:w-auto">
              <label className="block flex-1 text-sm font-semibold text-app-muted">Nutrition date
                <input type="date" value={selectedDate} onChange={(event) => setSelectedDate(event.target.value)}
                  className="mt-2 block min-h-11 w-full rounded-xl border border-app-border bg-app-elevated px-3 py-2 text-app-text" />
              </label>
              <button type="button" onClick={() => { setMealNotice(""); setMealEditorOpen(true); }}
                className="min-h-11 rounded-xl bg-app-accent px-5 py-2.5 font-semibold text-white shadow-lg shadow-blue-500/20 transition hover:brightness-110">
                + Add meal
              </button>
            </div>
          </div>

          <div className="mt-5 grid gap-3 sm:grid-cols-2 xl:grid-cols-4">
            {macroProgress("Calories", summary?.caloriesKcal ?? 0, goals.caloriesKcal, "kcal", "bg-rose-500")}
            {macroProgress("Protein", summary?.proteinG ?? 0, goals.proteinG, "g", "bg-sky-500")}
            {macroProgress("Carbs", summary?.carbsG ?? 0, goals.carbsG, "g", "bg-amber-500")}
            {macroProgress("Fat", summary?.fatG ?? 0, goals.fatG, "g", "bg-violet-500")}
          </div>

          <details className="mt-4 rounded-xl border border-app-border bg-app-elevated p-4">
            <summary className="cursor-pointer font-semibold">Daily goals</summary>
            <div className="mt-4 grid gap-3 sm:grid-cols-2 xl:grid-cols-4">
              {(Object.keys(goals) as (keyof FoodGoals)[]).map((key) => (
                <label key={key} className="text-sm font-medium text-app-muted">{goalLabel(key)}
                  <input type="number" min="0" value={goals[key]} onChange={(event) => updateGoal(key, event.target.value)}
                    className="mt-2 w-full rounded-xl border border-app-border bg-app-surface px-3 py-2 text-app-text" />
                </label>
              ))}
            </div>
          </details>

          {mealNotice ? <p role="status" className="mt-4 rounded-xl border border-emerald-400/30 bg-emerald-500/10 px-4 py-3 text-sm text-emerald-700 dark:text-emerald-300">{mealNotice}</p> : null}

          <div className="mt-5 grid gap-3 lg:grid-cols-2">
            {meals.length ? meals.map((meal) => (
              <article key={meal.id} className="rounded-xl border border-app-border bg-app-elevated p-4">
                <div className="flex flex-col gap-2 sm:flex-row sm:justify-between sm:gap-3">
                  <div className="min-w-0"><p className="text-xs font-bold uppercase tracking-wide text-app-muted">{time(meal.eatenAt)} · {meal.mealType}</p><h3 className="mt-1 break-words font-semibold">{meal.title}</h3></div>
                  <div className="flex shrink-0 items-start gap-2">
                    <strong>{number(meal.caloriesKcal)} kcal</strong>
                    <button
                      type="button"
                      onClick={() => deleteMeal(meal)}
                      disabled={deletingMealId === meal.id}
                      className="grid h-7 w-7 place-items-center rounded-full border border-rose-400/30 text-sm font-bold text-rose-500 transition hover:bg-rose-500/10 disabled:cursor-wait disabled:opacity-50"
                      aria-label={`Delete ${meal.title}`}
                      title="Delete meal"
                    >
                      ×
                    </button>
                  </div>
                </div>
                <p className="mt-3 text-sm text-app-muted">P {number(meal.proteinG)}g · C {number(meal.carbsG)}g · F {number(meal.fatG)}g</p>
              </article>
            )) : <p className="rounded-xl border border-dashed border-app-border p-6 text-center text-sm text-app-muted lg:col-span-2">No meals in this nutrition day.</p>}
          </div>
        </section>

        <section className="min-w-0 rounded-[1.25rem] border border-app-border bg-app-surface p-4 shadow-soft sm:rounded-[1.5rem] sm:p-5">
          <div className="flex flex-col gap-4 sm:flex-row sm:items-end sm:justify-between">
            <div><p className="text-xs font-bold uppercase tracking-[0.18em] text-app-accent">30 day view</p><h2 className="mt-2 text-2xl font-semibold">Nutrition rhythm</h2><p className="mt-1 text-sm text-app-muted">Missing days count as zero in every rolling average.</p></div>
            <div className="grid grid-cols-2 gap-2 sm:flex sm:flex-wrap">
              {macroOptions.map((item) => <button key={item.key} onClick={() => setMacro(item.key)}
                className={`min-h-11 rounded-full px-3 py-2 text-sm font-semibold ${macro === item.key ? "bg-app-accent text-white" : "border border-app-border bg-app-elevated"}`}>{item.label}</button>)}
            </div>
          </div>
          <div className="mt-5">{trends ? <NutritionTrendChart points={trends.points} metric={macro} unit={chosenMacro.unit} /> : null}</div>
        </section>

        <div className="grid min-w-0 gap-4 sm:gap-6 xl:grid-cols-[minmax(0,1fr)_360px]">
          <HistoryTable snapshots={recent} expandedId={expanded} onToggle={(id) => setExpanded((current) => current === id ? null : id)} onViewRaw={openRaw} />
          <aside className="min-w-0 space-y-4">
            <AccountPanel user={user} onUserChange={(updated) => { onUserChange(updated); setSelectedDate(updated.currentNutritionDate); }} onTokenRevealed={setRevealedIngestToken} />
            {user.isAdmin ? <AdminPanel /> : null}
            <section className="rounded-[1.5rem] border border-app-border bg-app-surface p-5 shadow-soft">
              <h2 className="font-semibold">Your devices</h2>
              <div className="mt-3 space-y-2">{devices.map((device) => <div key={device.deviceId}
                className={`flex items-center gap-2 rounded-xl border p-3 ${selectedDevice === device.deviceId ? "border-app-accent bg-app-elevated" : "border-app-border"}`}>
                <button type="button" onClick={() => setSelectedDevice(device.deviceId)} className="min-w-0 flex-1 text-left">
                  <strong className="block truncate">{device.deviceName ?? device.deviceId}</strong><span className="break-all text-xs text-app-muted">{device.deviceId}</span>
                </button>
                <button
                  type="button"
                  onClick={() => deleteDevice(device)}
                  disabled={deletingDeviceId === device.deviceId}
                  className="grid h-8 w-8 shrink-0 place-items-center rounded-full border border-rose-400/30 text-sm font-bold text-rose-500 transition hover:bg-rose-500/10 disabled:cursor-wait disabled:opacity-50"
                  aria-label={`Delete device ${device.deviceName || device.deviceId}`}
                  title="Delete device"
                >
                  ×
                </button>
              </div>)}</div>
            </section>
          </aside>
        </div>

        <UserApiGuide
          username={user.username}
          nutritionDate={selectedDate}
          serverTimezone={user.serverTimezone}
          resetHour={user.nutritionResetHour}
          ingestToken={revealedIngestToken}
        />
      </div>
      {mealEditorOpen ? <MealEntryModal
        nutritionDate={selectedDate}
        currentNutritionDate={user.currentNutritionDate}
        resetHour={user.nutritionResetHour}
        timezone={user.serverTimezone}
        onClose={() => setMealEditorOpen(false)}
        onSave={createManualMeal}
      /> : null}
      {(raw || rawError) ? <RawSnapshotModal detail={raw} loading={false} error={rawError || null} onClose={() => { setRaw(null); setRawError(""); }} /> : null}
    </main>
  );
}

function AccountPanel({ user, onUserChange, onTokenRevealed }: { user: CurrentUser; onUserChange: (user: CurrentUser) => void; onTokenRevealed: (token: string) => void }) {
  const [token, setToken] = useState<IngestTokenStatus | null>(null);
  const [revealed, setRevealed] = useState("");
  const [notice, setNotice] = useState("");
  const [currentPassword, setCurrentPassword] = useState("");
  const [newPassword, setNewPassword] = useState("");
  const [passwordConfirmation, setPasswordConfirmation] = useState("");

  useEffect(() => { accountApi.tokenStatus().then(setToken).catch((err) => setNotice(message(err))); }, []);

  async function saveHour(value: number) {
    try {
      onUserChange(await authApi.updateResetHour(value));
      setNotice("Nutrition boundary updated.");
    } catch (err) { setNotice(message(err)); }
  }

  async function rotate() {
    if (!window.confirm("Rotate your ingest token? The previous Android token will stop working immediately.")) return;
    try {
      const created = await accountApi.rotateToken();
      setToken(created);
      setRevealed(created.token);
      onTokenRevealed(created.token);
      setNotice("Copy this token now. It will not be shown again.");
    } catch (err) { setNotice(message(err)); }
  }

  async function revoke() {
    if (!window.confirm("Revoke your active Android ingest token?")) return;
    await accountApi.revokeToken();
    setToken({ active: false, prefix: null, createdAt: null });
    setRevealed("");
    onTokenRevealed("");
  }

  async function changePassword(event: FormEvent) {
    event.preventDefault();
    if (newPassword !== passwordConfirmation) {
      setNotice("New passwords do not match.");
      return;
    }
    try {
      onUserChange(await authApi.changePassword(currentPassword, newPassword));
      setCurrentPassword("");
      setNewPassword("");
      setPasswordConfirmation("");
      setNotice("Password updated. Other browser sessions were signed out.");
    } catch (err) {
      setNotice(message(err));
    }
  }

  return (
    <details className="rounded-[1.5rem] border border-app-border bg-app-surface p-5 shadow-soft">
      <summary className="cursor-pointer font-semibold">Account & ingestion</summary>
      <div className="mt-5 space-y-5">
        <label className="block text-sm font-semibold text-app-muted">Nutrition reset hour
          <select value={user.nutritionResetHour} onChange={(event) => saveHour(Number(event.target.value))}
            className="mt-2 w-full rounded-xl border border-app-border bg-app-elevated px-3 py-2 text-app-text">
            {Array.from({ length: 24 }, (_, hour) => <option key={hour} value={hour}>{String(hour).padStart(2, "0")}:00</option>)}
          </select>
        </label>
        <div>
          <p className="text-sm font-semibold">Android ingest token</p>
          <p className="mt-1 text-xs text-app-muted">{token?.active ? `Active · ${token.prefix}…` : "No active token"}</p>
          {revealed ? <code className="mt-3 block break-all rounded-xl bg-slate-950 p-3 text-xs text-emerald-300">{revealed}</code> : null}
          <div className="mt-3 flex flex-wrap gap-2"><button onClick={rotate} className="min-h-11 flex-1 rounded-lg bg-app-accent px-3 py-2 text-xs font-bold text-white">{token?.active ? "Rotate" : "Generate"}</button>
            {token?.active ? <button onClick={revoke} className="rounded-lg border border-rose-300 px-3 py-2 text-xs font-bold text-rose-600">Revoke</button> : null}</div>
        </div>
        <form onSubmit={changePassword} className="space-y-3 border-t border-app-border pt-4">
          <p className="text-sm font-semibold">Change password</p>
          <Field label="Current password" type="password" value={currentPassword} onChange={setCurrentPassword} autoComplete="current-password" />
          <Field label="New password" type="password" value={newPassword} onChange={setNewPassword} autoComplete="new-password" />
          <Field label="Confirm new password" type="password" value={passwordConfirmation} onChange={setPasswordConfirmation} autoComplete="new-password" />
          <button className="rounded-lg border border-app-border px-3 py-2 text-xs font-bold hover:border-app-accent">Update password</button>
        </form>
        {notice ? <p className="text-xs text-app-muted">{notice}</p> : null}
      </div>
    </details>
  );
}

function AdminPanel() {
  const [users, setUsers] = useState<CurrentUser[]>([]);
  const [username, setUsername] = useState("");
  const [display, setDisplay] = useState("");
  const [secret, setSecret] = useState("");
  const [notice, setNotice] = useState("");

  const refresh = useCallback(() => adminUsersApi.list().then(setUsers).catch((err) => setNotice(message(err))), []);
  useEffect(() => { refresh(); }, [refresh]);

  async function create(event: FormEvent) {
    event.preventDefault();
    try {
      const created = await adminUsersApi.create(username, display);
      setSecret(created.temporaryPassword);
      setUsername("");
      setDisplay("");
      setNotice(`Created ${created.user.username}. Share the temporary password securely.`);
      refresh();
    } catch (err) { setNotice(message(err)); }
  }

  async function reset(id: number) {
    if (!window.confirm("Reset this user's password and sign them out?")) return;
    try {
      const result = await adminUsersApi.resetPassword(id);
      setSecret(result.temporaryPassword);
      setNotice(`Temporary password for ${result.user.username}. It is shown once.`);
      refresh();
    } catch (err) { setNotice(message(err)); }
  }

  async function toggle(target: CurrentUser) {
    try {
      await adminUsersApi.setActive(target.id, !target.isActive);
      refresh();
    } catch (err) { setNotice(message(err)); }
  }

  return (
    <details className="rounded-[1.5rem] border border-app-border bg-app-surface p-5 shadow-soft">
      <summary className="cursor-pointer font-semibold">User administration</summary>
      <form onSubmit={create} className="mt-5 space-y-3">
        <Field label="Username" value={username} onChange={setUsername} />
        <Field label="Display name" value={display} onChange={setDisplay} />
        <button className="w-full rounded-xl bg-app-accent px-3 py-2 text-sm font-bold text-white">Add regular user</button>
      </form>
      {secret ? <code className="mt-4 block break-all rounded-xl bg-slate-950 p-3 text-xs text-amber-300">{secret}</code> : null}
      {notice ? <p className="mt-3 text-xs text-app-muted">{notice}</p> : null}
      <div className="mt-4 space-y-2">{users.filter((item) => !item.isAdmin).map((item) => (
        <div key={item.id} className="rounded-xl border border-app-border p-3">
          <div className="flex items-start justify-between gap-2"><div><strong className="block text-sm">{item.displayName}</strong><span className="text-xs text-app-muted">@{item.username}</span></div>
            <span className={`rounded-full px-2 py-1 text-[10px] font-bold ${item.isActive ? "bg-emerald-500/10 text-emerald-600" : "bg-rose-500/10 text-rose-600"}`}>{item.isActive ? "ACTIVE" : "DISABLED"}</span></div>
          <div className="mt-3 flex flex-wrap gap-2"><button onClick={() => reset(item.id)} className="min-h-10 rounded-lg border border-app-border px-2 py-1 text-xs font-semibold">Reset password</button>
            <button onClick={() => toggle(item)} className="rounded-lg border border-app-border px-2 py-1 text-xs font-semibold">{item.isActive ? "Disable" : "Enable"}</button></div>
        </div>
      ))}</div>
    </details>
  );
}

function Field({ label, value, onChange, type = "text", autoComplete }: { label: string; value: string; onChange: (value: string) => void; type?: string; autoComplete?: string }) {
  return <label className="block text-sm font-semibold text-app-muted">{label}<input required type={type} value={value} onChange={(event) => onChange(event.target.value)} autoComplete={autoComplete}
    className="mt-2 w-full rounded-xl border border-app-border bg-app-elevated px-3 py-2.5 text-app-text outline-none focus:border-app-accent focus:ring-2 focus:ring-app-accent/20" /></label>;
}

function FullPageMessage({ title, detail }: { title: string; detail: string }) {
  return <main className="grid min-h-screen place-items-center bg-app-bg text-app-text"><div className="text-center"><div className="mx-auto h-10 w-10 animate-pulse rounded-2xl bg-app-accent" /><h1 className="mt-5 text-xl font-semibold">{title}</h1><p className="mt-2 text-sm text-app-muted">{detail}</p></div></main>;
}

function metricCards(snapshot: Snapshot | null): Card[] {
  const values: [string, keyof Snapshot, number | null | undefined, string | undefined, string, string][] = [
    ["Steps", "stepsToday", snapshot?.stepsToday, undefined, "bg-blue-500", "Steps reported for today."],
    ["Distance", "distanceKmToday", snapshot?.distanceKmToday, "km", "bg-emerald-500", "Distance covered today."],
    ["Active calories", "activeCaloriesKcalToday", snapshot?.activeCaloriesKcalToday, "kcal", "bg-orange-500", "Calories burned through activity."],
    ["Total calories", "totalCaloriesKcalToday", snapshot?.totalCaloriesKcalToday, "kcal", "bg-rose-500", "Total daily energy burn."],
    ["Latest heart rate", "latestHeartRateBpm", snapshot?.latestHeartRateBpm, "bpm", "bg-red-500", "Most recent heart-rate sample."],
    ["Resting heart rate", "restingHeartRateBpm", snapshot?.restingHeartRateBpm, "bpm", "bg-pink-500", "Latest resting heart rate."],
    ["Sleep", "sleepMinutesLastNight", snapshot?.sleepMinutesLastNight, "min", "bg-indigo-500", "Last night's sleep duration."],
    ["Weight", "latestWeightKg", snapshot?.latestWeightKg, "kg", "bg-cyan-500", "Most recent body weight."],
  ];
  const cards = values.map(([label, key, value, unit, accent, explanation]) => {
    const valid = typeof value === "number";
    const state = getMetricStatus(snapshot, String(key), valid);
    return { label, value: valid ? number(value, unit === "km" || unit === "kg" ? 1 : 0) : state, unit, accent, status: state, timeRange: "Latest sync", explanation, capturedAt: snapshot?.capturedAt };
  });
  const count = jsonItemCount(snapshot?.recentExercises);
  cards.push({ label: "Recent exercises", value: count ? String(count) : "No data", unit: "sessions", accent: "bg-lime-500", status: getMetricStatus(snapshot, "recentExercises", count > 0), timeRange: "Recent", explanation: "Exercise sessions in the latest sync.", capturedAt: snapshot?.capturedAt });
  return cards;
}

function macroProgress(label: string, value: number, goal: number, unit: string, accent: string) {
  const percentage = goal ? Math.min(100, Math.round(value / goal * 100)) : 0;
  return <div className="rounded-xl border border-app-border bg-app-elevated p-4"><div className="flex items-baseline justify-between gap-3"><span className="text-sm font-semibold text-app-muted">{label}</span><strong>{number(value)} / {number(goal)} {unit}</strong></div><div className="mt-3 h-2 overflow-hidden rounded-full bg-app-border"><div className={`h-full rounded-full ${accent}`} style={{ width: `${percentage}%` }} /></div></div>;
}

function boundaryLabel(user: CurrentUser) {
  const start = String(user.nutritionResetHour).padStart(2, "0");
  const end = String((user.nutritionResetHour + 23) % 24).padStart(2, "0");
  return `Day runs ${start}:00–${end}:59`;
}
function goalLabel(key: keyof FoodGoals) { return ({ caloriesKcal: "Calories (kcal)", proteinG: "Protein (g)", carbsG: "Carbs (g)", fatG: "Fat (g)", fiberG: "Fiber (g)" })[key]; }
function time(value: string) { return new Date(value).toLocaleTimeString("en-GB", { hour: "2-digit", minute: "2-digit" }); }
function number(value: number, digits = 0) { return new Intl.NumberFormat("en-US", { maximumFractionDigits: digits }).format(value); }
function message(error: unknown) { return error instanceof ApiError ? error.detail : error instanceof Error ? error.message : "Something went wrong"; }

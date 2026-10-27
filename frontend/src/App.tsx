import { useCallback, useEffect, useMemo, useState } from "react";

import { ApiError, getDailyNutritionSummary, getDevices, getHistory, getLatest, getMeals, getRecent, getSnapshot } from "./api";
import { DATE_RANGE_OPTIONS, type DateRangeKey, getDateRangeParams } from "./dateRanges";
import { EmptyState } from "./components/EmptyState";
import { HistoryTable } from "./components/HistoryTable";
import { MetricCard } from "./components/MetricCard";
import { MiniLineChart } from "./components/MiniLineChart";
import { RawSnapshotModal } from "./components/RawSnapshotModal";
import { SyncStatus } from "./components/SyncStatus";
import { getFreshness, getMetricStatus, jsonItemCount } from "./status";
import type { DataStatus } from "./status";
import type { Device, HistoryPoint, Meal, NutritionSummaryResponse, RecentSnapshot, Snapshot, SnapshotDetailResponse } from "./types";

type ChartMetricKey = "steps_today" | "distance_km_today" | "active_calories_kcal_today" | "total_calories_kcal_today";

type MetricCardModel = {
  label: string;
  value: string;
  unit?: string;
  accent: string;
  status: DataStatus;
  timeRange: string;
  explanation: string;
  capturedAt?: string | null;
};

type FoodGoals = {
  caloriesKcal: number;
  proteinG: number;
  carbsG: number;
  fatG: number;
};

const chartMetrics: Record<ChartMetricKey, { label: string; color: string; unit?: string }> = {
  steps_today: { label: "Steps", color: "#2563eb" },
  distance_km_today: { label: "Distance", color: "#059669", unit: "km" },
  active_calories_kcal_today: { label: "Active calories", color: "#ea580c", unit: "kcal" },
  total_calories_kcal_today: { label: "Total calories", color: "#e11d48", unit: "kcal" },
};

const defaultSelectedChartMetrics: ChartMetricKey[] = ["steps_today", "distance_km_today", "active_calories_kcal_today"];
const READ_TOKEN_STORAGE_KEY = "health-dashboard.readToken";
const FOOD_GOALS_STORAGE_KEY = "health-dashboard.foodGoals";
const defaultFoodGoals: FoodGoals = { caloriesKcal: 2200, proteinG: 140, carbsG: 250, fatG: 75 };

function App() {
  const [devices, setDevices] = useState<Device[]>([]);
  const [selectedDeviceId, setSelectedDeviceId] = useState<string>("");
  const [dateRange, setDateRange] = useState<DateRangeKey>("last24h");
  const [latestSnapshot, setLatestSnapshot] = useState<Snapshot | null>(null);
  const [latestDevice, setLatestDevice] = useState<Device | null>(null);
  const [recentSnapshots, setRecentSnapshots] = useState<RecentSnapshot[]>([]);
  const [history, setHistory] = useState<Record<ChartMetricKey, HistoryPoint[]>>({
    steps_today: [],
    distance_km_today: [],
    active_calories_kcal_today: [],
    total_calories_kcal_today: [],
  });
  const [selectedChartMetrics, setSelectedChartMetrics] = useState<ChartMetricKey[]>(defaultSelectedChartMetrics);
  const [autoRefresh, setAutoRefresh] = useState(true);
  const [loading, setLoading] = useState(true);
  const [refreshing, setRefreshing] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [lastLoadedAt, setLastLoadedAt] = useState<Date | null>(null);
  const [expandedSnapshotId, setExpandedSnapshotId] = useState<number | null>(null);
  const [rawDetail, setRawDetail] = useState<SnapshotDetailResponse | null>(null);
  const [rawLoading, setRawLoading] = useState(false);
  const [rawError, setRawError] = useState<string | null>(null);
  const [readToken, setReadToken] = useState(() => window.localStorage.getItem(READ_TOKEN_STORAGE_KEY) ?? "");
  const [readTokenDraft, setReadTokenDraft] = useState(readToken);
  const [selectedMealDate, setSelectedMealDate] = useState(todayInputDate());
  const [meals, setMeals] = useState<Meal[]>([]);
  const [nutritionSummary, setNutritionSummary] = useState<NutritionSummaryResponse | null>(null);
  const [foodGoals, setFoodGoals] = useState<FoodGoals>(() => loadFoodGoals());

  const loadDashboard = useCallback(async (manual = false) => {
    try {
      setError(null);
      if (manual) setRefreshing(true);
      else setLoading(true);

      const readOptions = { readToken };
      const [deviceList, mealList, dailyNutrition] = await Promise.all([
        getDevices(readOptions),
        getMeals({ date: selectedMealDate }, readOptions),
        getDailyNutritionSummary(selectedMealDate, readOptions),
      ]);
      setDevices(deviceList);
      setMeals(mealList.meals);
      setNutritionSummary(dailyNutrition);

      const targetDeviceId = selectedDeviceId || chooseDefaultDevice(deviceList)?.deviceId || "";
      if (!selectedDeviceId && targetDeviceId) setSelectedDeviceId(targetDeviceId);

      if (!targetDeviceId) {
        setLatestSnapshot(null);
        setLatestDevice(null);
        setRecentSnapshots([]);
        setHistory({ steps_today: [], distance_km_today: [], active_calories_kcal_today: [], total_calories_kcal_today: [] });
        return;
      }

      const rangeParams = getDateRangeParams(dateRange);
      const [latest, recent, histories] = await Promise.all([
        getLatest(targetDeviceId, readOptions),
        getRecent({ deviceId: targetDeviceId, limit: 20, ...rangeParams }, readOptions),
        Promise.all(
          (Object.keys(chartMetrics) as ChartMetricKey[]).map(async (metric) => [metric, (await getHistory(targetDeviceId, metric, rangeParams, readOptions)).points] as const),
        ),
      ]);

      setLatestDevice(latest.device);
      setLatestSnapshot(latest.snapshot);
      setRecentSnapshots(recent.snapshots);
      setHistory(Object.fromEntries(histories) as Record<ChartMetricKey, HistoryPoint[]>);
      setLastLoadedAt(new Date());
    } catch (err) {
      setError(formatApiError(err));
    } finally {
      setLoading(false);
      setRefreshing(false);
    }
  }, [dateRange, selectedDeviceId, readToken, selectedMealDate]);

  useEffect(() => {
    loadDashboard();
  }, [loadDashboard]);

  useEffect(() => {
    if (!autoRefresh) return;
    const interval = window.setInterval(() => loadDashboard(), 60_000);
    return () => window.clearInterval(interval);
  }, [autoRefresh, loadDashboard]);

  const metricCards = useMemo(() => buildMetricCards(latestSnapshot), [latestSnapshot]);
  const freshness = getFreshness(latestSnapshot?.receivedAt);

  async function openRawSnapshot(snapshotId: number) {
    setRawDetail(null);
    setRawError(null);
    setRawLoading(true);
    try {
      setRawDetail(await getSnapshot(snapshotId, { readToken }));
    } catch (err) {
      setRawError(formatApiError(err));
    } finally {
      setRawLoading(false);
    }
  }

  function saveReadToken() {
    const nextToken = readTokenDraft.trim();
    const tokenChanged = nextToken !== readToken;
    setReadToken(nextToken);
    if (nextToken) window.localStorage.setItem(READ_TOKEN_STORAGE_KEY, nextToken);
    else window.localStorage.removeItem(READ_TOKEN_STORAGE_KEY);
    if (!tokenChanged) loadDashboard(true);
  }

  function clearReadToken() {
    setReadToken("");
    setReadTokenDraft("");
    window.localStorage.removeItem(READ_TOKEN_STORAGE_KEY);
  }

  function updateFoodGoal(key: keyof FoodGoals, value: string) {
    const nextGoals = { ...foodGoals, [key]: Math.max(0, Number(value) || 0) };
    setFoodGoals(nextGoals);
    window.localStorage.setItem(FOOD_GOALS_STORAGE_KEY, JSON.stringify(nextGoals));
  }

  function toggleChartMetric(metric: ChartMetricKey) {
    setSelectedChartMetrics((current) => {
      if (current.includes(metric)) return current.filter((item) => item !== metric);
      return [...current, metric];
    });
  }

  return (
    <main className="min-h-screen bg-app-bg text-app-text">
      <div className="mx-auto flex w-full max-w-7xl flex-col gap-6 px-4 py-5 sm:px-6 lg:px-8">
        <SyncStatus
          receivedAt={latestSnapshot?.receivedAt}
          capturedAt={latestSnapshot?.capturedAt}
          deviceName={latestDevice?.deviceName}
          deviceId={latestDevice?.deviceId ?? selectedDeviceId}
          timezone={latestSnapshot?.timezone}
          freshness={freshness}
          loading={loading || refreshing}
        />

        <section className="rounded-lg border border-app-border bg-app-surface p-4 shadow-soft" aria-labelledby="dashboard-controls-title">
          <h2 id="dashboard-controls-title" className="text-lg font-semibold text-app-text">Dashboard controls</h2>
          <div className="mt-4 grid gap-4 sm:grid-cols-2 xl:grid-cols-4">
            <label className="text-sm font-medium text-app-muted">
              Device
              <select
                value={selectedDeviceId}
                onChange={(event) => setSelectedDeviceId(event.target.value)}
                className="mt-2 w-full rounded-lg border border-app-border bg-app-elevated px-3 py-2 text-sm font-medium text-app-text outline-none focus-visible:border-app-accent focus-visible:ring-2 focus-visible:ring-app-accent/40"
              >
                {devices.map((device) => (
                  <option key={device.deviceId} value={device.deviceId}>
                    {device.deviceName ? `${device.deviceName} (${device.deviceId})` : device.deviceId}
                  </option>
                ))}
              </select>
            </label>

            <label className="text-sm font-medium text-app-muted">
              Date range
              <select
                value={dateRange}
                onChange={(event) => setDateRange(event.target.value as DateRangeKey)}
                className="mt-2 w-full rounded-lg border border-app-border bg-app-elevated px-3 py-2 text-sm font-medium text-app-text outline-none focus-visible:border-app-accent focus-visible:ring-2 focus-visible:ring-app-accent/40"
              >
                {DATE_RANGE_OPTIONS.map((option) => (
                  <option key={option.key} value={option.key}>{option.label}</option>
                ))}
              </select>
            </label>

            <div className="flex items-end gap-3">
              <button
                type="button"
                onClick={() => loadDashboard(true)}
                aria-label="Refresh dashboard data now"
                className="w-full rounded-lg bg-app-accent px-4 py-2.5 text-sm font-semibold text-white outline-none transition hover:opacity-90 focus-visible:ring-2 focus-visible:ring-app-accent/40 disabled:cursor-not-allowed disabled:opacity-60"
                disabled={loading || refreshing}
              >
                {refreshing ? "Refreshing..." : "Refresh"}
              </button>
            </div>

            <label className="flex items-center gap-3 rounded-lg border border-app-border bg-app-elevated px-3 py-2.5 text-sm font-medium text-app-text outline-none focus-within:ring-2 focus-within:ring-app-accent/40">
              <input
                type="checkbox"
                checked={autoRefresh}
                onChange={(event) => setAutoRefresh(event.target.checked)}
                aria-label="Toggle automatic refresh every 60 seconds"
                className="h-4 w-4 accent-blue-600"
              />
              Auto-refresh every 60s
            </label>
          </div>

          <div className="mt-4 grid gap-3 border-t border-app-border pt-4 sm:grid-cols-[1fr_auto_auto] sm:items-end">
            <label className="text-sm font-medium text-app-muted">
              Read token
              <input
                type="password"
                value={readTokenDraft}
                onChange={(event) => setReadTokenDraft(event.target.value)}
                onKeyDown={(event) => {
                  if (event.key === "Enter") saveReadToken();
                }}
                autoComplete="current-password"
                className="mt-2 w-full rounded-lg border border-app-border bg-app-elevated px-3 py-2 text-sm font-medium text-app-text outline-none focus-visible:border-app-accent focus-visible:ring-2 focus-visible:ring-app-accent/40"
              />
            </label>
            <button
              type="button"
              onClick={saveReadToken}
              className="rounded-lg bg-app-accent px-4 py-2.5 text-sm font-semibold text-white outline-none transition hover:opacity-90 focus-visible:ring-2 focus-visible:ring-app-accent/40"
            >
              Save token
            </button>
            <button
              type="button"
              onClick={clearReadToken}
              className="rounded-lg border border-app-border bg-app-elevated px-4 py-2.5 text-sm font-semibold text-app-text outline-none transition hover:border-app-accent focus-visible:ring-2 focus-visible:ring-app-accent/40"
            >
              Clear
            </button>
          </div>
          <p className="mt-2 text-xs text-app-muted">{readToken ? "Read token saved in this browser." : "Read token required for dashboard data."}</p>

          <div aria-live="polite" role="status" className="mt-4 text-sm text-app-muted">
            {error ? <span className="text-rose-700 dark:text-rose-300">API status: {error}</span> : null}
            {!error && loading ? <span>Loading dashboard data...</span> : null}
            {!error && !loading && lastLoadedAt ? <span>Last refreshed <span dir="ltr">{lastLoadedAt.toLocaleTimeString("en-GB", { hour: "2-digit", minute: "2-digit" })}</span></span> : null}
          </div>
        </section>

        {!latestSnapshot && !loading ? <EmptyState /> : null}

        {latestSnapshot ? (
          <>
            <section className="grid gap-4 sm:grid-cols-2 lg:grid-cols-3 xl:grid-cols-4" aria-label="Health metric cards">
              {metricCards.map((card) => (
                <MetricCard key={card.label} {...card} />
              ))}
            </section>

            <section className="rounded-lg border border-app-border bg-app-surface p-4 shadow-soft" aria-labelledby="food-diary-title">
              <div className="flex flex-col gap-4 lg:flex-row lg:items-start lg:justify-between">
                <div>
                  <h2 id="food-diary-title" className="text-lg font-semibold text-app-text">Food diary</h2>
                  <p className="mt-1 text-sm text-app-muted">Manual meals for the selected local day.</p>
                </div>
                <label className="text-sm font-medium text-app-muted">
                  Date
                  <input
                    type="date"
                    value={selectedMealDate}
                    onChange={(event) => setSelectedMealDate(event.target.value)}
                    className="mt-2 w-full rounded-lg border border-app-border bg-app-elevated px-3 py-2 text-sm font-medium text-app-text outline-none focus-visible:border-app-accent focus-visible:ring-2 focus-visible:ring-app-accent/40"
                  />
                </label>
              </div>

              <div className="mt-4 grid gap-3 sm:grid-cols-2 xl:grid-cols-4">
                {macroProgress("Calories", nutritionSummary?.caloriesKcal ?? 0, foodGoals.caloriesKcal, "kcal", "bg-rose-500")}
                {macroProgress("Protein", nutritionSummary?.proteinG ?? 0, foodGoals.proteinG, "g", "bg-sky-500")}
                {macroProgress("Carbs", nutritionSummary?.carbsG ?? 0, foodGoals.carbsG, "g", "bg-amber-500")}
                {macroProgress("Fat", nutritionSummary?.fatG ?? 0, foodGoals.fatG, "g", "bg-violet-500")}
              </div>

              <details className="mt-4 rounded-lg border border-app-border bg-app-elevated p-3">
                <summary className="cursor-pointer text-sm font-semibold text-app-text">Daily goals</summary>
                <div className="mt-3 grid gap-3 sm:grid-cols-2 xl:grid-cols-4">
                  {goalInput("Calories", "caloriesKcal", foodGoals.caloriesKcal, "kcal", updateFoodGoal)}
                  {goalInput("Protein", "proteinG", foodGoals.proteinG, "g", updateFoodGoal)}
                  {goalInput("Carbs", "carbsG", foodGoals.carbsG, "g", updateFoodGoal)}
                  {goalInput("Fat", "fatG", foodGoals.fatG, "g", updateFoodGoal)}
                </div>
              </details>

              <div className="mt-4 grid gap-3 lg:grid-cols-2">
                {meals.length ? meals.map((meal) => (
                  <article key={meal.id} className="rounded-lg border border-app-border bg-app-elevated p-4">
                    <div className="flex flex-wrap items-start justify-between gap-3">
                      <div>
                        <p className="text-xs font-semibold uppercase tracking-wide text-app-muted">{formatMealTime(meal.eatenAt)} · {meal.mealType}</p>
                        <h3 className="mt-1 text-base font-semibold text-app-text">{meal.title}</h3>
                      </div>
                      <p className="rounded-full border border-app-border px-3 py-1 text-sm font-semibold text-app-text">{formatNumber(meal.caloriesKcal, 0)} kcal</p>
                    </div>
                    <dl className="mt-3 grid grid-cols-3 gap-2 text-sm">
                      <div><dt className="text-app-muted">Protein</dt><dd className="font-semibold text-app-text">{formatNumber(meal.proteinG, 0)}g</dd></div>
                      <div><dt className="text-app-muted">Carbs</dt><dd className="font-semibold text-app-text">{formatNumber(meal.carbsG, 0)}g</dd></div>
                      <div><dt className="text-app-muted">Fat</dt><dd className="font-semibold text-app-text">{formatNumber(meal.fatG, 0)}g</dd></div>
                    </dl>
                    {meal.notes ? <p className="mt-3 text-sm text-app-muted">{meal.notes}</p> : null}
                  </article>
                )) : (
                  <div className="rounded-lg border border-app-border bg-app-elevated p-6 text-center text-sm text-app-muted lg:col-span-2">
                    No meals logged for this date.
                  </div>
                )}
              </div>
            </section>

            <section className="rounded-lg border border-app-border bg-app-surface p-4 shadow-soft" aria-labelledby="trends-title">
              <div className="flex flex-col gap-4 lg:flex-row lg:items-start lg:justify-between">
                <div>
                  <h2 id="trends-title" className="text-lg font-semibold text-app-text">Trends</h2>
                  <p className="mt-1 text-sm text-app-muted">Toggle metrics for the selected date range.</p>
                </div>
                <fieldset className="flex flex-wrap gap-2">
                  <legend className="sr-only">Metrics shown in trend charts</legend>
                  {(Object.keys(chartMetrics) as ChartMetricKey[]).map((metric) => (
                    <label key={metric} className="inline-flex items-center gap-2 rounded-full border border-app-border bg-app-elevated px-3 py-2 text-sm font-medium text-app-text outline-none focus-within:ring-2 focus-within:ring-app-accent/40">
                      <input
                        type="checkbox"
                        checked={selectedChartMetrics.includes(metric)}
                        onChange={() => toggleChartMetric(metric)}
                        aria-label={`Toggle ${chartMetrics[metric].label} trend`}
                        className="h-4 w-4 accent-blue-600"
                      />
                      {chartMetrics[metric].label}
                    </label>
                  ))}
                </fieldset>
              </div>
              <div className="mt-4 grid gap-4 lg:grid-cols-2">
                {selectedChartMetrics.length ? selectedChartMetrics.map((metric) => (
                  <MiniLineChart
                    key={metric}
                    title={`${chartMetrics[metric].label} trend`}
                    points={history[metric] ?? []}
                    color={chartMetrics[metric].color}
                    unit={chartMetrics[metric].unit}
                  />
                )) : (
                  <div className="rounded-lg border border-app-border bg-app-elevated p-6 text-center text-sm text-app-muted lg:col-span-2">
                    Select at least one metric to show trends.
                  </div>
                )}
              </div>
            </section>

            <div className="grid gap-4 xl:grid-cols-[1fr_320px]">
              <HistoryTable
                snapshots={recentSnapshots}
                expandedId={expandedSnapshotId}
                onToggle={(id) => setExpandedSnapshotId((current) => current === id ? null : id)}
                onViewRaw={openRawSnapshot}
              />
              <section className="rounded-lg border border-app-border bg-app-surface p-4 shadow-soft" aria-labelledby="devices-title">
                <h2 id="devices-title" className="text-lg font-semibold text-app-text">Devices</h2>
                <div className="mt-4 space-y-3">
                  {devices.map((device) => {
                    const isSelected = device.deviceId === (latestDevice?.deviceId ?? selectedDeviceId);
                    return (
                      <button
                        key={device.deviceId}
                        type="button"
                        onClick={() => setSelectedDeviceId(device.deviceId)}
                        className={`w-full rounded-lg border p-3 text-left outline-none transition hover:border-app-accent focus-visible:ring-2 focus-visible:ring-app-accent/40 ${
                          isSelected ? "border-app-accent bg-app-elevated" : "border-app-border"
                        }`}
                        aria-pressed={isSelected}
                      >
                        <p className="font-medium text-app-text">{device.deviceName ?? device.deviceId}</p>
                        <p className="mt-1 text-xs text-app-muted">{device.deviceId}</p>
                      </button>
                    );
                  })}
                </div>
              </section>
            </div>
          </>
        ) : null}
      </div>

      {(rawDetail || rawLoading || rawError) ? (
        <RawSnapshotModal
          detail={rawDetail}
          loading={rawLoading}
          error={rawError}
          onClose={() => {
            setRawDetail(null);
            setRawError(null);
            setRawLoading(false);
          }}
        />
      ) : null}
    </main>
  );
}

function todayInputDate() {
  return new Date().toISOString().slice(0, 10);
}

function loadFoodGoals(): FoodGoals {
  const raw = window.localStorage.getItem(FOOD_GOALS_STORAGE_KEY);
  if (!raw) return defaultFoodGoals;
  try {
    const parsed = JSON.parse(raw) as Partial<FoodGoals>;
    return {
      caloriesKcal: Number(parsed.caloriesKcal) || defaultFoodGoals.caloriesKcal,
      proteinG: Number(parsed.proteinG) || defaultFoodGoals.proteinG,
      carbsG: Number(parsed.carbsG) || defaultFoodGoals.carbsG,
      fatG: Number(parsed.fatG) || defaultFoodGoals.fatG,
    };
  } catch {
    return defaultFoodGoals;
  }
}

function macroProgress(label: string, value: number, goal: number, unit: string, accent: string) {
  const percent = goal > 0 ? Math.min(100, Math.round((value / goal) * 100)) : 0;
  return (
    <div className="rounded-lg border border-app-border bg-app-elevated p-3">
      <div className="flex items-baseline justify-between gap-3">
        <p className="text-sm font-medium text-app-muted">{label}</p>
        <p className="text-sm font-semibold text-app-text">{formatNumber(value, 0)} / {formatNumber(goal, 0)} {unit}</p>
      </div>
      <div className="mt-3 h-2 overflow-hidden rounded-full bg-app-border">
        <div className={"h-full " + accent} style={{ width: percent + "%" }} />
      </div>
    </div>
  );
}

function goalInput(label: string, key: keyof FoodGoals, value: number, unit: string, onChange: (key: keyof FoodGoals, value: string) => void) {
  return (
    <label className="text-sm font-medium text-app-muted">
      {label} ({unit})
      <input
        type="number"
        min="0"
        value={value}
        onChange={(event) => onChange(key, event.target.value)}
        className="mt-2 w-full rounded-lg border border-app-border bg-app-surface px-3 py-2 text-sm font-medium text-app-text outline-none focus-visible:border-app-accent focus-visible:ring-2 focus-visible:ring-app-accent/40"
      />
    </label>
  );
}

function formatMealTime(value: string) {
  return new Date(value).toLocaleTimeString("en-GB", { hour: "2-digit", minute: "2-digit" });
}

function chooseDefaultDevice(devices: Device[]) {
  return devices.find((device) => !isTestDevice(device)) ?? devices[0];
}

function isTestDevice(device: Device) {
  const haystack = `${device.deviceId} ${device.deviceName ?? ""}`.toLowerCase();
  return haystack.includes("test") || haystack.includes("smoke") || haystack.includes("pixel-test");
}

function buildMetricCards(snapshot: Snapshot | null): MetricCardModel[] {
  return [
    metricCard(snapshot, "Steps", "stepsToday", snapshot?.stepsToday, undefined, 0, "bg-blue-500", "Today", "Total steps reported for the current day."),
    metricCard(snapshot, "Distance", "distanceKmToday", snapshot?.distanceKmToday, "km", 1, "bg-emerald-500", "Today", "Estimated distance covered today."),
    metricCard(snapshot, "Active calories", "activeCaloriesKcalToday", snapshot?.activeCaloriesKcalToday, "kcal", 0, "bg-orange-500", "Today", "Calories burned through activity today."),
    metricCard(snapshot, "Total calories", "totalCaloriesKcalToday", snapshot?.totalCaloriesKcalToday, "kcal", 0, "bg-rose-500", "Today", "Total calories reported for today, including resting burn."),
    metricCard(snapshot, "Latest heart rate", "latestHeartRateBpm", snapshot?.latestHeartRateBpm, "bpm", 0, "bg-red-500", "Latest sample", "Most recent heart-rate sample in the snapshot."),
    metricCard(snapshot, "Resting heart rate", "restingHeartRateBpm", snapshot?.restingHeartRateBpm, "bpm", 0, "bg-pink-500", "Latest value", "Resting heart rate reported by Health Connect."),
    sleepCard(snapshot),
    metricCard(snapshot, "Weight", "latestWeightKg", snapshot?.latestWeightKg, "kg", 1, "bg-cyan-500", "Latest value", "Most recent body-weight measurement."),
    exerciseCard(snapshot),
  ];
}

function metricCard(
  snapshot: Snapshot | null,
  label: string,
  key: keyof Snapshot,
  rawValue: number | null | undefined,
  unit: string | undefined,
  digits: number,
  accent: string,
  timeRange: string,
  explanation: string,
): MetricCardModel {
  const hasValue = typeof rawValue === "number";
  const status = getMetricStatus(snapshot, String(key), hasValue);
  return {
    label,
    value: hasValue ? formatNumber(rawValue, digits) : status,
    unit,
    accent,
    status,
    timeRange,
    explanation,
    capturedAt: snapshot?.capturedAt,
  };
}

function sleepCard(snapshot: Snapshot | null): MetricCardModel {
  const minutes = snapshot?.sleepMinutesLastNight;
  const hasValue = typeof minutes === "number";
  const status = getMetricStatus(snapshot, "sleepMinutesLastNight", hasValue);
  return {
    label: "Sleep",
    value: hasValue ? `${Math.floor(minutes / 60)}h ${minutes % 60}m` : status,
    accent: "bg-indigo-500",
    status,
    timeRange: "Last night",
    explanation: "Sleep duration from the most recent overnight sleep window.",
    capturedAt: snapshot?.capturedAt,
  };
}

function exerciseCard(snapshot: Snapshot | null): MetricCardModel {
  const count = jsonItemCount(snapshot?.recentExercises);
  const status = getMetricStatus(snapshot, "recentExercises", count > 0);
  return {
    label: "Recent exercises",
    value: count > 0 ? String(count) : status,
    unit: count === 1 ? "session" : "sessions",
    accent: "bg-lime-500",
    status,
    timeRange: "Recent",
    explanation: "Exercise sessions included in the latest Android snapshot.",
    capturedAt: snapshot?.capturedAt,
  };
}

function formatNumber(value: number, digits = 0) {
  return new Intl.NumberFormat("en-US", { maximumFractionDigits: digits, minimumFractionDigits: digits }).format(value);
}

function formatApiError(err: unknown) {
  if (err instanceof ApiError) {
    if (err.status === 401) return "Read token required. Enter the dashboard read token and refresh.";
    if (err.status === 403) return "Read token rejected. Check the saved dashboard read token.";
    if (err.status === 503) return "Server READ_TOKEN is not configured. Set READ_TOKEN on the API and restart.";
    return err.detail || "API error: " + err.status;
  }
  if (err instanceof TypeError) return "Server unreachable. Check the API URL and service status.";
  return err instanceof Error ? err.message : "Unable to load dashboard";
}

export default App;

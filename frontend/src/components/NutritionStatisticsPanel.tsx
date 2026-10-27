import { useState } from "react";
import type { FoodGoals } from "../accountTypes";
import type { NutritionMetric, NutritionStatistics } from "../statisticsTypes";

const metrics: { key: NutritionMetric; label: string; unit: string }[] = [
  { key: "caloriesKcal", label: "Calories", unit: "kcal" },
  { key: "proteinG", label: "Protein", unit: "g" },
  { key: "carbsG", label: "Carbs", unit: "g" },
  { key: "fatG", label: "Fat", unit: "g" },
  { key: "fiberG", label: "Fiber", unit: "g" },
];
const amount = (value: number | null) => value === null ? "—" : new Intl.NumberFormat(undefined, { maximumFractionDigits: 1 }).format(value);
const date = (value: string) => new Intl.DateTimeFormat(undefined, { day: "numeric", month: "short" }).format(new Date(`${value}T12:00:00`));

export function NutritionStatisticsPanel({ data, goals }: { data: NutritionStatistics | null; goals: FoodGoals }) {
  const [period, setPeriod] = useState(7);
  const [metric, setMetric] = useState<NutritionMetric>("caloriesKcal");
  const window = data?.windows.find(item => item.days === period);
  const selected = metrics.find(item => item.key === metric)!;
  const points = data?.points.slice(-period) ?? [];
  const ceiling = Math.max(goals[metric], ...points.map(point => point.totals[metric] ?? 0), 1) * 1.12;
  const targetY = 136 - goals[metric] / ceiling * 120;
  return <section className="min-w-0 rounded-3xl border border-app-border bg-app-surface p-5 shadow-soft sm:p-6" aria-labelledby="statistics-title">
    <div className="flex flex-wrap items-start justify-between gap-4">
      <div><p className="text-xs font-bold uppercase tracking-[0.18em] text-app-accent">The bigger picture</p>
        <h2 id="statistics-title" className="mt-2 text-2xl font-semibold">Nutrition trends</h2>
        <p className="mt-2 text-sm text-app-muted">Completed nutrition days only. Today is excluded.</p></div>
      <div className="flex gap-1 rounded-2xl bg-app-elevated p-1" aria-label="Average period">
        {[7, 14, 30].map(days => <button key={days} type="button" aria-pressed={period === days} onClick={() => setPeriod(days)}
          className={`min-h-11 rounded-xl px-4 text-sm font-semibold ${period === days ? "bg-app-accent text-white" : "text-app-muted"}`}>{days} days</button>)}
      </div>
    </div>
    {!window ? <p className="mt-6 text-sm text-app-muted">Statistics are unavailable. Use Refresh to try again.</p> : <>
      <div className="mt-5 flex flex-wrap justify-between gap-2 text-sm text-app-muted">
        <span>{date(window.fromDate)} – {date(window.toDate)}</span><span>{window.loggedDays} of {period} days logged</span>
      </div>
      <div className="mt-4 grid grid-cols-2 gap-3 sm:grid-cols-3 xl:grid-cols-5">
        {metrics.map(item => <button key={item.key} type="button" aria-pressed={metric === item.key} onClick={() => setMetric(item.key)}
          className={`flex min-w-0 flex-col items-start rounded-2xl border p-4 text-left ${metric === item.key ? "border-app-accent bg-app-elevated" : "border-app-border"}`}>
          <span className="text-sm text-app-muted">{item.label}</span>
          <strong className="mt-2 block text-2xl font-semibold">{amount(window.average[item.key])}</strong>
          <span className="text-xs text-app-muted">{item.unit}/day</span>
          <span className="mt-2 block text-xs text-app-muted">Current goal {goals[item.key]} {item.unit}</span>
          <span className="mt-1 block text-xs text-app-muted">{window.coverage[item.key]} days with data</span>
        </button>)}
      </div>
      <div className="mt-6 flex flex-wrap items-center justify-between gap-2 text-sm"><strong>{selected.label} by day</strong><span className="text-app-muted">Dashed line: current daily goal</span></div>
      <svg viewBox="0 0 600 165" className="mt-3 w-full" role="img" aria-label={`${selected.label} across ${period} completed days; missing values are gaps. Current goal ${goals[metric]} ${selected.unit}.`}>
        {goals[metric] > 0 && <line x1="0" x2="600" y1={targetY} y2={targetY} stroke="currentColor" strokeOpacity="0.4" strokeDasharray="5 5" />}
        {points.map((point, index) => {
          const value = point.totals[metric]; const width = 600 / period; const height = value === null ? 0 : value / ceiling * 120;
          return <g key={point.date}><title>{point.date}: {value === null ? "No data" : `${amount(value)} ${selected.unit}`}</title>
            <rect x={index * width + 3} y={value === null ? 132 : 136 - Math.max(height, 2)} width={Math.max(width - 6, 3)} height={value === null ? 4 : Math.max(height, 2)} rx="3" fill="currentColor" className={value === null ? "text-app-muted" : "text-app-accent"} opacity={value === null ? 0.25 : 0.85} />
          </g>;
        })}
        <text x="0" y="160" fontSize="11" fill="currentColor">{date(window.fromDate)}</text><text x="600" y="160" textAnchor="end" fontSize="11" fill="currentColor">{date(window.toDate)}</text>
      </svg>
      <details className="mt-3 text-sm"><summary className="min-h-11 cursor-pointer py-3 font-semibold">Daily values</summary>
        <table className="w-full text-left"><thead><tr><th className="py-2">Date</th><th className="py-2 text-right">{selected.label} ({selected.unit})</th></tr></thead>
          <tbody>{[...points].reverse().map(point => <tr key={point.date} className="border-t border-app-border"><td className="py-2">{date(point.date)}</td><td className="py-2 text-right">{point.totals[metric] === null ? "No data" : amount(point.totals[metric])}</td></tr>)}</tbody></table>
      </details>
      <p className="mt-3 text-xs leading-relaxed text-app-muted">Averages use days with recorded meals, not missing days. A logged day may still be partial. Unknown nutrient values are excluded separately. Goals shown are your current goals, not historical targets. Day boundary: {String(data!.nutritionResetHour).padStart(2, "0")}:00 · {data!.serverTimezone}.</p>
    </>}
  </section>;
}

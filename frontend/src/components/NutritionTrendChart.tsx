import type { MacroValues, NutritionTrendPoint } from "../accountTypes";

export type MacroKey = keyof MacroValues;

const series = [
  { key: "daily" as const, label: "Daily", color: "#94a3b8", width: 1.5 },
  { key: "average7" as const, label: "7 day", color: "#22c55e", width: 2.5 },
  { key: "average14" as const, label: "14 day", color: "#0ea5e9", width: 2.5 },
  { key: "average30" as const, label: "30 day", color: "#f97316", width: 3 },
];

type Props = {
  points: NutritionTrendPoint[];
  metric: MacroKey;
  unit: string;
};

export function NutritionTrendChart({ points, metric, unit }: Props) {
  const width = 880;
  const height = 300;
  const pad = { left: 52, right: 18, top: 20, bottom: 38 };
  const values = points.flatMap((point) => series.map((item) => point[item.key][metric]));
  const maximum = Math.max(1, ...values) * 1.08;
  const x = (index: number) => pad.left + (index / Math.max(1, points.length - 1)) * (width - pad.left - pad.right);
  const y = (value: number) => pad.top + (1 - value / maximum) * (height - pad.top - pad.bottom);
  const latest = points[points.length - 1];

  return (
    <div className="overflow-hidden rounded-2xl border border-app-border bg-app-elevated">
      <div className="grid gap-3 border-b border-app-border px-4 py-4 sm:grid-cols-3">
        {series.slice(1).map((item) => (
          <div key={item.key}>
            <p className="text-xs font-semibold uppercase tracking-[0.16em] text-app-muted">{item.label} average</p>
            <p className="mt-1 text-xl font-semibold text-app-text">
              {format(latest?.[item.key][metric] ?? 0)} <span className="text-xs font-medium text-app-muted">{unit}</span>
            </p>
          </div>
        ))}
      </div>
      <div className="overflow-x-auto overscroll-x-contain p-2 sm:p-3">
        <svg viewBox={`0 0 ${width} ${height}`} className="min-w-[620px]" role="img" aria-label="Daily nutrition and rolling averages over 30 days">
          {[0, 0.25, 0.5, 0.75, 1].map((ratio) => {
            const value = maximum * (1 - ratio);
            const lineY = pad.top + ratio * (height - pad.top - pad.bottom);
            return (
              <g key={ratio}>
                <line x1={pad.left} x2={width - pad.right} y1={lineY} y2={lineY} stroke="rgb(var(--color-border))" strokeDasharray="3 5" />
                <text x={pad.left - 8} y={lineY + 4} textAnchor="end" className="fill-app-muted text-[10px]">{format(value)}</text>
              </g>
            );
          })}
          {series.map((item) => (
            <polyline
              key={item.key}
              points={points.map((point, index) => `${x(index)},${y(point[item.key][metric])}`).join(" ")}
              fill="none"
              stroke={item.color}
              strokeWidth={item.width}
              strokeLinejoin="round"
              strokeLinecap="round"
              opacity={item.key === "daily" ? 0.62 : 1}
            />
          ))}
          {points.map((point, index) => index % 5 === 0 || index === points.length - 1 ? (
            <text key={point.date} x={x(index)} y={height - 12} textAnchor="middle" className="fill-app-muted text-[10px]">
              {point.date.slice(5)}
            </text>
          ) : null)}
        </svg>
      </div>
      <div className="flex flex-wrap gap-4 border-t border-app-border px-4 py-3">
        {series.map((item) => (
          <span key={item.key} className="inline-flex items-center gap-2 text-xs font-medium text-app-muted">
            <span className="h-0.5 w-5 rounded-full" style={{ backgroundColor: item.color }} />{item.label}
          </span>
        ))}
        {latest?.isIncomplete ? <span className="w-full text-xs font-medium text-amber-600 sm:ml-auto sm:w-auto dark:text-amber-300">Current nutrition day is still in progress</span> : null}
      </div>
    </div>
  );
}

function format(value: number) {
  return new Intl.NumberFormat("en-US", { maximumFractionDigits: 1 }).format(value);
}

import { useMemo, useState } from "react";

import { formatDateTime } from "../format";
import type { HistoryPoint } from "../types";

type MiniLineChartProps = {
  title: string;
  points: HistoryPoint[];
  color: string;
  unit?: string;
};

export function MiniLineChart({ title, points, color, unit }: MiniLineChartProps) {
  const values = points.map((point) => point.value).filter((value): value is number => typeof value === "number");
  const path = useMemo(() => buildPath(points), [points]);
  const [activeIndex, setActiveIndex] = useState<number | null>(null);
  const activePoint = activeIndex !== null ? path?.points[activeIndex] : null;

  return (
    <section className="rounded-lg border border-app-border bg-app-surface p-4 shadow-soft">
      <h3 className="text-lg font-semibold text-app-text">{title}</h3>
      <p className="mt-1 text-sm text-app-muted">{values.length >= 2 ? `${values.length} usable points in this range.` : "Need at least 2 syncs for trend."}</p>
      <div className="relative mt-4 h-44">
        {path ? (
          <>
            <svg
              viewBox="0 0 320 130"
              className="h-full w-full"
              role="img"
              aria-label={`${title} trend with ${values.length} data points. Focus a point to read its value and captured time.`}
              onMouseLeave={() => setActiveIndex(null)}
            >
              <path d={path.area} fill={color} opacity="0.12" />
              <path d={path.line} fill="none" stroke={color} strokeLinecap="round" strokeLinejoin="round" strokeWidth="4" />
              {path.points.map((point, index) => (
                <circle
                  key={`${point.capturedAt}-${index}`}
                  cx={point.x}
                  cy={point.y}
                  r={activeIndex === index ? 5 : 3}
                  fill={color}
                  stroke="currentColor"
                  strokeWidth={activeIndex === index ? 2 : 0}
                  tabIndex={0}
                  role="button"
                  aria-label={`${title} ${formatNumber(point.value)}${unit ? ` ${unit}` : ""} at ${formatDateTime(point.capturedAt)}`}
                  onFocus={() => setActiveIndex(index)}
                  onBlur={() => setActiveIndex(null)}
                  onMouseEnter={() => setActiveIndex(index)}
                />
              ))}
            </svg>
            {activePoint ? (
              <div className="absolute left-3 top-3 rounded-lg border border-app-border bg-app-elevated px-3 py-2 text-xs shadow-soft">
                <p className="font-semibold text-app-text">{formatNumber(activePoint.value)}{unit ? ` ${unit}` : ""}</p>
                <p dir="ltr" className="mt-1 text-app-muted">{formatDateTime(activePoint.capturedAt)}</p>
              </div>
            ) : null}
          </>
        ) : (
          <div className="flex h-full items-center justify-center rounded-lg border border-app-border px-4 text-center text-sm text-app-muted">
            Need at least 2 syncs for trend
          </div>
        )}
      </div>
    </section>
  );
}

function buildPath(points: HistoryPoint[]) {
  const usable = points.filter((point): point is HistoryPoint & { value: number } => typeof point.value === "number");
  if (usable.length < 2) return null;
  const values = usable.map((point) => point.value);
  const min = Math.min(...values);
  const max = Math.max(...values);
  const range = max - min || 1;
  const width = 320;
  const height = 100;
  const top = 12;
  const plotted = usable.map((point, index) => {
    const x = (index / (usable.length - 1)) * width;
    const y = top + height - ((point.value - min) / range) * height;
    return { ...point, x, y };
  });
  const line = plotted.map((point, index) => `${index === 0 ? "M" : "L"} ${point.x.toFixed(1)} ${point.y.toFixed(1)}`).join(" ");
  const area = `${line} L ${width} 130 L 0 130 Z`;
  return { line, area, points: plotted };
}

function formatNumber(value: number) {
  return new Intl.NumberFormat("en-US", { maximumFractionDigits: 1 }).format(value);
}

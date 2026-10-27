export type DateRangeKey = "today" | "last24h" | "last7d" | "last30d";

export type DateRangeOption = {
  key: DateRangeKey;
  label: string;
};

export type DateRangeParams = {
  from?: string;
  to?: string;
};

export const DATE_RANGE_OPTIONS: DateRangeOption[] = [
  { key: "today", label: "Today" },
  { key: "last24h", label: "Last 24 hours" },
  { key: "last7d", label: "Last 7 days" },
  { key: "last30d", label: "Last 30 days" },
];

export function getDateRangeParams(range: DateRangeKey, now = new Date()): DateRangeParams {
  const to = now.toISOString();
  const from = new Date(now);

  if (range === "today") {
    from.setHours(0, 0, 0, 0);
  } else if (range === "last24h") {
    from.setHours(from.getHours() - 24);
  } else if (range === "last7d") {
    from.setDate(from.getDate() - 7);
  } else {
    from.setDate(from.getDate() - 30);
  }

  return { from: from.toISOString(), to };
}
